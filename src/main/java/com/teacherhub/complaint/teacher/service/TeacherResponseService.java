package com.teacherhub.complaint.teacher.service;

import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.complaint.access.ComplaintAccessPolicy;
import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintMessage;
import com.teacherhub.complaint.entity.ComplaintEvent;
import com.teacherhub.complaint.enums.MessageType;
import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.enums.MessageStatus;
import com.teacherhub.complaint.enums.WaitingFor;
import com.teacherhub.complaint.exception.ComplaintNotFoundException;
import com.teacherhub.complaint.exception.ResponseDraftNotFoundException;
import com.teacherhub.complaint.exception.ResponseNotEditableException;
import com.teacherhub.complaint.repository.ComplaintMessageRepository;
import com.teacherhub.complaint.repository.ComplaintEventRepository;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.complaint.teacher.dto.reply.TeacherAiDraftResponse;
import com.teacherhub.complaint.teacher.dto.reply.TeacherResponseDraftResponse;
import com.teacherhub.complaint.teacher.dto.reply.TeacherResponseRequest;
import com.teacherhub.complaint.teacher.dto.reply.TeacherResponseSubmissionResponse;
import com.teacherhub.complaint.teacher.provider.TeacherSupportContentProvider;
import com.teacherhub.risk.repository.EmergencyRiskReviewRequestRepository;
import com.teacherhub.risk.repository.RiskAnalysisRepository;
import com.teacherhub.risk.repository.RiskReviewRepository;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeacherResponseService {

    private final ComplaintRepository complaintRepository;
    private final ComplaintMessageRepository messageRepository;
    private final ComplaintEventRepository eventRepository;
    private final UserRepository userRepository;
    private final ComplaintAccessPolicy complaintAccessPolicy;
    private final TeacherSupportContentProvider supportContentProvider;
    private final EmergencyRiskReviewRequestRepository emergencyReviewRequestRepository;
    private final RiskAnalysisRepository riskAnalysisRepository;
    private final RiskReviewRepository riskReviewRepository;

    @Transactional(noRollbackFor = ResponseNotEditableException.class)
    // 교사 AI 초안 생성
    public TeacherAiDraftResponse generateAiDraft(Long userId, Long complaintId) {
        User user = findUser(userId);
        Complaint complaint = findEditableComplaintForUpdate(user, complaintId, "AI 초안 생성");
        var generated = supportContentProvider.createResponseDraft(conversationContext(complaint));
        ComplaintEvent event = eventRepository.save(ComplaintEvent.aiDraftGenerated(complaint));
        return TeacherAiDraftResponse.from(complaint.getId(), generated, event.getOccurredAt());
    }

    // 임시 저장된 답변 조회
    public TeacherResponseDraftResponse findDraft(Long userId, Long complaintId) {
        User user = findUser(userId);
        Complaint complaint = findTeacherComplaint(user, complaintId);
        ComplaintMessage draft = messageRepository
                .findFirstByComplaintIdAndSenderIdAndTypeAndStatusOrderByIdDesc(
                        complaintId,
                        user.getId(),
                        MessageType.TEACHER_REPLY,
                        MessageStatus.DRAFT
                )
                .orElseThrow(() -> new ResponseDraftNotFoundException(
                        "저장된 답변 초안을 찾을 수 없습니다."
                ));
        return TeacherResponseDraftResponse.from(draft);
    }

    // 답변 초안 저장
    @Transactional(noRollbackFor = ResponseNotEditableException.class)
    public TeacherResponseDraftResponse saveDraft(
            Long userId,
            Long complaintId,
            TeacherResponseRequest request
    ) {
        User user = findUser(userId);
        Complaint complaint = findEditableComplaintForUpdate(user, complaintId, "답변 임시저장");
        ComplaintMessage draft = messageRepository
                .findFirstByComplaintIdAndSenderIdAndTypeAndStatusOrderByIdDesc(
                        complaintId,
                        user.getId(),
                        MessageType.TEACHER_REPLY,
                        MessageStatus.DRAFT
                )
                .orElseGet(() -> new ComplaintMessage(
                        complaint,
                        user,
                        MessageType.TEACHER_REPLY,
                        request.content()
                ));

        if (draft.getId() != null) {
            draft.updateDraft(request.content());
        }
        ComplaintMessage saved = messageRepository.saveAndFlush(draft);
        return TeacherResponseDraftResponse.from(saved);
    }

    // 답변 최종 제출
    @Transactional(noRollbackFor = ResponseNotEditableException.class)
    public TeacherResponseSubmissionResponse submit(
            Long userId,
            Long complaintId,
            TeacherResponseRequest request
    ) {
        return submit(userId, complaintId, request, UUID.randomUUID().toString());
    }

    @Transactional(noRollbackFor = ResponseNotEditableException.class)
    public TeacherResponseSubmissionResponse submit(
            Long userId,
            Long complaintId,
            TeacherResponseRequest request,
            String idempotencyKey
    ) {
        User user = findUser(userId);
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        var existing = messageRepository.findBySenderIdAndIdempotencyKey(user.getId(), normalizedKey);
        if (existing.isPresent()) {
            return existingSubmission(existing.get(), complaintId, request);
        }

        Complaint complaint = findComplaintForUpdate(user, complaintId);
        existing = messageRepository.findIdempotentForUpdate(user.getId(), normalizedKey);
        if (existing.isPresent()) {
            return existingSubmission(existing.get(), complaintId, request);
        }
        ensureResponseAllowed(complaint, user, "답변 전송");
        ensureEditable(complaint);
        ComplaintMessage response = messageRepository
                .findFirstByComplaintIdAndSenderIdAndTypeAndStatusOrderByIdDesc(
                        complaintId,
                        user.getId(),
                        MessageType.TEACHER_REPLY,
                        MessageStatus.DRAFT
                )
                .orElseGet(() -> new ComplaintMessage(
                        complaint,
                        user,
                        MessageType.TEACHER_REPLY,
                        request.content()
                ));

        if (response.getId() != null) {
            response.updateDraft(request.content());
        }
        markLatestParentMessageRead(complaintId, user);
        response.assignIdempotencyKey(normalizedKey);
        response.send();
        complaint.teacherMessageSent();
        ComplaintMessage saved = messageRepository.saveAndFlush(response);
        eventRepository.save(ComplaintEvent.messageSent(saved));
        return TeacherResponseSubmissionResponse.from(saved);
    }

    @Transactional(noRollbackFor = ResponseNotEditableException.class)
    public void close(Long userId, Long complaintId) {
        User user = findUser(userId);
        Complaint complaint = findComplaintForUpdate(user, complaintId);
        ensureResponseAllowed(complaint, user, "상담 완료 처리");
        ensureClosable(complaint);
        complaint.closeByTeacher();
        eventRepository.save(ComplaintEvent.statusChanged(
                complaint,
                user,
                ComplaintStatus.OPEN,
                ComplaintStatus.CLOSED
        ));
    }

    // 민원 조회 및 교사 권한 검증
    private Complaint findEditableComplaintForUpdate(User user, Long complaintId, String operation) {
        Complaint complaint = findComplaintForUpdate(user, complaintId);
        ensureResponseAllowed(complaint, user, operation);
        ensureEditable(complaint);
        return complaint;
    }

    private Complaint findComplaintForUpdate(User user, Long complaintId) {
        Complaint complaint = complaintRepository.findForUpdate(complaintId)
                .orElseThrow(() -> new ComplaintNotFoundException("민원을 찾을 수 없습니다."));
        complaintAccessPolicy.validateAssignedTeacher(user, complaint);
        return complaint;
    }

    private void ensureResponseAllowed(Complaint complaint, User user, String operation) {
        Long complaintId = complaint.getId();
        boolean safetyLock = emergencyReviewRequestRepository
                .findFirstByComplaintIdOrderByRequestedAtDescIdDesc(complaintId)
                .map(com.teacherhub.risk.entity.EmergencyRiskReviewRequest::isSafetyLock)
                .orElse(false);
        boolean emergencyRisk = effectiveRiskLevel(complaintId) == RiskLevel.EMERGENCY;
        if (safetyLock || emergencyRisk) {
            eventRepository.save(ComplaintEvent.responseBlockedByEmergencyPolicy(
                    complaint,
                    user,
                    operation
            ));
            throw new ResponseNotEditableException(
                    "Emergency 위험도 또는 안전 잠금이 유지되는 동안 " + operation
                            + "을(를) 수행할 수 없습니다. 재검토 절차를 확인해주세요."
            );
        }
    }

    private RiskLevel effectiveRiskLevel(Long complaintId) {
        return riskAnalysisRepository
                .findFirstByComplaintIdAndCompletedTrueAndMessage_StatusNotOrderByIdDesc(
                        complaintId,
                        MessageStatus.DRAFT
                )
                .map(analysis -> riskReviewRepository
                        .findFirstByRiskAnalysisIdOrderByReviewedAtDescIdDesc(analysis.getId())
                        .map(review -> review.getReviewedLevel())
                        .orElse(analysis.getRiskLevel()))
                .orElse(null);
    }

    private void ensureEditable(Complaint complaint) {
        if (complaint.getStatus() != ComplaintStatus.OPEN
                || complaint.getWaitingFor() != WaitingFor.TEACHER) {
            throw new ResponseNotEditableException("접수되어 답변 대기 중인 민원만 답변할 수 있습니다.");
        }
    }

    private void ensureClosable(Complaint complaint) {
        if (complaint.getStatus() != ComplaintStatus.OPEN) {
            throw new ResponseNotEditableException("진행 중인 민원만 상담 완료 처리할 수 있습니다.");
        }
        if (complaint.getWaitingFor() != WaitingFor.PARENT) {
            throw new ResponseNotEditableException(
                    "교사 답변 전송 후 학부모 응답을 기다리는 민원만 상담 완료 처리할 수 있습니다."
            );
        }
    }

    private TeacherResponseSubmissionResponse existingSubmission(
            ComplaintMessage sent,
            Long complaintId,
            TeacherResponseRequest request
    ) {
        if (!sent.getComplaint().getId().equals(complaintId)
                || sent.getType() != MessageType.TEACHER_REPLY
                || !sent.isSent()
                || !sent.getContent().equals(request.content())) {
            throw new ResponseNotEditableException("동일한 멱등 키가 다른 답변 요청에 사용되었습니다.");
        }
        return TeacherResponseSubmissionResponse.from(sent);
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key 헤더가 필요합니다.");
        }
        String normalized = idempotencyKey.trim();
        if (normalized.length() > 100) {
            throw new IllegalArgumentException("Idempotency-Key는 100자 이하로 입력해주세요.");
        }
        return normalized;
    }

    private Complaint findTeacherComplaint(User user, Long complaintId) {
        Complaint complaint = complaintRepository.findDetailById(complaintId)
                .orElseThrow(() -> new ComplaintNotFoundException("민원을 찾을 수 없습니다."));
        complaintAccessPolicy.validateAssignedTeacher(user, complaint);
        return complaint;
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("사용자를 찾을 수 없습니다."));
    }

    private String conversationContext(Complaint complaint) {
        return messageRepository
                .findByComplaintIdAndStatusNotOrderByIdAsc(complaint.getId(), MessageStatus.DRAFT)
                .stream()
                .map(message -> message.getType() + ": " + message.getContent())
                .collect(Collectors.joining("\n\n"));
    }

    private void markLatestParentMessageRead(Long complaintId, User actor) {
        messageRepository.findFirstByComplaintIdAndTypeAndStatusNotOrderByIdDesc(
                complaintId,
                MessageType.PARENT_INQUIRY,
                MessageStatus.DRAFT
        ).filter(ComplaintMessage::markRead)
                .ifPresent(message -> eventRepository.save(ComplaintEvent.messageRead(message, actor)));
    }
}
