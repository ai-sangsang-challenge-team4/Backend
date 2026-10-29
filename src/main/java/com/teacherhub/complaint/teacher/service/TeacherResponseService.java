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
import com.teacherhub.user.entity.User;
import com.teacherhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

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

    @Transactional
    // 교사 AI 초안 생성
    public TeacherAiDraftResponse generateAiDraft(Long userId, Long complaintId) {
        User user = findUser(userId);
        Complaint complaint = findEditableComplaintForUpdate(user, complaintId);
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
    @Transactional
    public TeacherResponseDraftResponse saveDraft(
            Long userId,
            Long complaintId,
            TeacherResponseRequest request
    ) {
        User user = findUser(userId);
        Complaint complaint = findEditableComplaintForUpdate(user, complaintId);
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
    @Transactional
    public TeacherResponseSubmissionResponse submit(
            Long userId,
            Long complaintId,
            TeacherResponseRequest request
    ) {
        User user = findUser(userId);
        Complaint complaint = findEditableComplaintForUpdate(user, complaintId);
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
        response.send();
        complaint.teacherMessageSent();
        ComplaintMessage saved = messageRepository.saveAndFlush(response);
        eventRepository.save(ComplaintEvent.messageSent(saved));
        return TeacherResponseSubmissionResponse.from(saved);
    }

    // 민원 조회 및 교사 권한 검증
    private Complaint findEditableComplaintForUpdate(User user, Long complaintId) {
        Complaint complaint = complaintRepository.findForUpdate(complaintId)
                .orElseThrow(() -> new ComplaintNotFoundException("민원을 찾을 수 없습니다."));
        complaintAccessPolicy.validateAssignedTeacher(user, complaint);
        if (complaint.getStatus() != ComplaintStatus.OPEN
                || complaint.getWaitingFor() != WaitingFor.TEACHER) {
            throw new ResponseNotEditableException("접수되어 답변 대기 중인 민원만 답변할 수 있습니다.");
        }
        return complaint;
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
