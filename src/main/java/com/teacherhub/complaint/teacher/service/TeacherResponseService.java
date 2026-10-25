package com.teacherhub.complaint.teacher.service;

import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.complaint.access.ComplaintAccessPolicy;
import com.teacherhub.complaint.entity.AiDraft;
import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintStatus;
import com.teacherhub.complaint.entity.ComplaintStatusHistory;
import com.teacherhub.complaint.entity.Response;
import com.teacherhub.complaint.exception.AiDraftNotFoundException;
import com.teacherhub.complaint.exception.ComplaintNotFoundException;
import com.teacherhub.complaint.exception.ResponseDraftNotFoundException;
import com.teacherhub.complaint.exception.ResponseNotEditableException;
import com.teacherhub.complaint.repository.AiDraftRepository;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.complaint.repository.ComplaintStatusHistoryRepository;
import com.teacherhub.complaint.repository.ResponseRepository;
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

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeacherResponseService {

    private final ComplaintRepository complaintRepository;
    private final AiDraftRepository aiDraftRepository;
    private final ResponseRepository responseRepository;
    private final ComplaintStatusHistoryRepository statusHistoryRepository;
    private final UserRepository userRepository;
    private final ComplaintAccessPolicy complaintAccessPolicy;
    private final TeacherSupportContentProvider supportContentProvider;

    @Transactional
    // 교사 AI 초안 생성
    public TeacherAiDraftResponse generateAiDraft(Long userId, Long complaintId) {
        User user = findUser(userId);
        Complaint complaint = findEditableComplaintForUpdate(user, complaintId);
        var generated = supportContentProvider.createResponseDraft(complaint.getContent());
        AiDraft aiDraft = aiDraftRepository.saveAndFlush(new AiDraft(
                complaint,
                generated.content(),
                generated.summary(),
                generated.modelName()
        ));
        return TeacherAiDraftResponse.from(aiDraft);
    }

    // 임시 저장된 답변 조회
    public TeacherResponseDraftResponse findDraft(Long userId, Long complaintId) {
        User user = findUser(userId);
        Complaint complaint = findTeacherComplaint(user, complaintId);
        Response draft = responseRepository
                .findFirstByComplaintIdAndTeacherIdAndSentAtIsNullOrderByIdDesc(
                        complaintId,
                        complaint.getTeacher().getId()
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
        AiDraft aiDraft = findAiDraft(complaintId, request.aiDraftId());
        Response draft = responseRepository
                .findFirstByComplaintIdAndTeacherIdAndSentAtIsNullOrderByIdDesc(
                        complaintId,
                        complaint.getTeacher().getId()
                )
                .orElseGet(() -> new Response(
                        complaint,
                        complaint.getTeacher(),
                        aiDraft,
                        request.content(),
                        null
                ));

        if (draft.getId() != null) {
            draft.updateDraft(aiDraft, request.content());
        }
        Response saved = responseRepository.saveAndFlush(draft);
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
        AiDraft aiDraft = findAiDraft(complaintId, request.aiDraftId());
        Response response = responseRepository
                .findFirstByComplaintIdAndTeacherIdAndSentAtIsNullOrderByIdDesc(
                        complaintId,
                        complaint.getTeacher().getId()
                )
                .orElseGet(() -> new Response(
                        complaint,
                        complaint.getTeacher(),
                        aiDraft,
                        request.content(),
                        null
                ));

        if (response.getId() != null) {
            response.updateDraft(aiDraft, request.content());
        }
        response.submit(LocalDateTime.now());
        complaint.answer();
        Response saved = responseRepository.saveAndFlush(response);
        statusHistoryRepository.save(new ComplaintStatusHistory(
                complaint,
                user,
                ComplaintStatus.ANALYZED,
                ComplaintStatus.ANSWERED
        ));
        return TeacherResponseSubmissionResponse.from(saved);
    }

    // 민원 조회 및 교사 권한 검증
    private Complaint findEditableComplaintForUpdate(User user, Long complaintId) {
        Complaint complaint = complaintRepository.findForUpdate(complaintId)
                .orElseThrow(() -> new ComplaintNotFoundException("민원을 찾을 수 없습니다."));
        complaintAccessPolicy.validateAssignedTeacher(user, complaint);
        if (complaint.getStatus() != ComplaintStatus.ANALYZED) {
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

    private AiDraft findAiDraft(Long complaintId, Long aiDraftId) {
        if (aiDraftId == null) {
            return null;
        }
        return aiDraftRepository.findByIdAndComplaintId(aiDraftId, complaintId)
                .orElseThrow(() -> new AiDraftNotFoundException(
                        "해당 민원의 AI 답변 초안을 찾을 수 없습니다."
                ));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("사용자를 찾을 수 없습니다."));
    }
}
