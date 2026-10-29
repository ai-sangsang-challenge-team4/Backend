package com.teacherhub.complaint.parent.service;

import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.complaint.access.ComplaintAccessPolicy;
import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintMessage;
import com.teacherhub.complaint.enums.MessageType;
import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.entity.ComplaintEvent;
import com.teacherhub.complaint.enums.MessageStatus;
import com.teacherhub.complaint.enums.WaitingFor;
import com.teacherhub.complaint.exception.ComplaintNotFoundException;
import com.teacherhub.complaint.exception.MessageDraftNotFoundException;
import com.teacherhub.complaint.exception.MessageNotEditableException;
import com.teacherhub.complaint.parent.dto.message.ComplaintConversationResponse;
import com.teacherhub.complaint.parent.dto.message.ParentMessageRequest;
import com.teacherhub.complaint.parent.dto.message.ParentMessageResponse;
import com.teacherhub.complaint.repository.ComplaintMessageRepository;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.complaint.repository.ComplaintEventRepository;
import com.teacherhub.risk.service.RiskAnalysisService;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParentMessageService {

    private final ComplaintRepository complaintRepository;
    private final ComplaintMessageRepository messageRepository;
    private final ComplaintEventRepository eventRepository;
    private final UserRepository userRepository;
    private final ComplaintAccessPolicy complaintAccessPolicy;
    private final RiskAnalysisService riskAnalysisService;

    @Transactional
    public ParentMessageResponse saveDraft(
            Long userId,
            Long complaintId,
            ParentMessageRequest request
    ) {
        User user = findUser(userId);
        Complaint complaint = findParentTurnForUpdate(user, complaintId);
        ComplaintMessage draft = findSavedDraft(complaintId, userId)
                .orElseGet(() -> new ComplaintMessage(
                        complaint,
                        user,
                        MessageType.PARENT_INQUIRY,
                        request.content()
                ));

        if (draft.getId() != null) {
            draft.updateDraft(request.content());
        }
        return ParentMessageResponse.from(messageRepository.saveAndFlush(draft));
    }

    public ParentMessageResponse findDraft(Long userId, Long complaintId) {
        User user = findUser(userId);
        Complaint complaint = findParentComplaint(user, complaintId);
        if (complaint.getStatus() != ComplaintStatus.OPEN
                || complaint.getWaitingFor() != WaitingFor.PARENT) {
            throw new MessageDraftNotFoundException("현재 작성할 수 있는 추가 문의 초안이 없습니다.");
        }
        return ParentMessageResponse.from(findSavedDraft(complaintId, userId)
                .orElseThrow(() -> new MessageDraftNotFoundException(
                        "저장된 추가 문의 초안을 찾을 수 없습니다."
                )));
    }

    @Transactional
    public ParentMessageResponse submit(Long userId, Long complaintId, Long messageId) {
        User user = findUser(userId);
        Complaint complaint = findParentTurnForUpdate(user, complaintId);
        ComplaintMessage message = findOwnedDraft(userId, complaintId, messageId);

        riskAnalysisService.analyzeMessageForSubmission(userId, complaintId, messageId);
        markLatestTeacherMessageRead(complaintId, user);
        message.send();
        complaint.parentMessageSent();

        ComplaintMessage saved = messageRepository.saveAndFlush(message);
        eventRepository.save(ComplaintEvent.messageSent(saved));
        return ParentMessageResponse.from(saved);
    }

    @Transactional
    public ComplaintConversationResponse findConversation(Long userId, Long complaintId) {
        User user = findUser(userId);
        Complaint complaint = findParentComplaint(user, complaintId);
        markLatestTeacherMessageRead(complaintId, user);
        return ComplaintConversationResponse.from(
                complaint,
                messageRepository.findByComplaintIdAndStatusNotOrderByIdAsc(
                        complaintId,
                        MessageStatus.DRAFT
                )
        );
    }

    @Transactional
    public void close(Long userId, Long complaintId) {
        User user = findUser(userId);
        Complaint complaint = findParentTurnForUpdate(user, complaintId);
        markLatestTeacherMessageRead(complaintId, user);
        complaint.close();
        eventRepository.save(ComplaintEvent.statusChanged(
                complaint,
                user,
                ComplaintStatus.OPEN,
                ComplaintStatus.CLOSED
        ));
    }

    private java.util.Optional<ComplaintMessage> findSavedDraft(Long complaintId, Long userId) {
        return messageRepository.findFirstByComplaintIdAndSenderIdAndTypeAndStatusOrderByIdDesc(
                complaintId,
                userId,
                MessageType.PARENT_INQUIRY,
                MessageStatus.DRAFT
        );
    }

    private ComplaintMessage findOwnedDraft(Long userId, Long complaintId, Long messageId) {
        return messageRepository.findById(messageId)
                .filter(message -> message.getComplaint().getId().equals(complaintId))
                .filter(message -> message.getSender().getId().equals(userId))
                .filter(message -> message.getType() == MessageType.PARENT_INQUIRY)
                .filter(ComplaintMessage::isDraft)
                .orElseThrow(() -> new ComplaintNotFoundException(
                        "작성 중인 추가 문의 메시지를 찾을 수 없습니다."
                ));
    }

    private void markLatestTeacherMessageRead(Long complaintId, User actor) {
        messageRepository.findFirstByComplaintIdAndTypeAndStatusNotOrderByIdDesc(
                complaintId,
                MessageType.TEACHER_REPLY,
                MessageStatus.DRAFT
        ).filter(ComplaintMessage::markRead)
                .ifPresent(message -> eventRepository.save(ComplaintEvent.messageRead(message, actor)));
    }

    private Complaint findParentTurnForUpdate(User user, Long complaintId) {
        Complaint complaint = complaintRepository.findForUpdate(complaintId)
                .orElseThrow(() -> new ComplaintNotFoundException("민원을 찾을 수 없습니다."));
        complaintAccessPolicy.validateParentOwner(user, complaint);
        if (complaint.getStatus() != ComplaintStatus.OPEN
                || complaint.getWaitingFor() != WaitingFor.PARENT) {
            throw new MessageNotEditableException(
                    "학부모가 추가 문의를 작성하거나 종료할 차례가 아닙니다."
            );
        }
        return complaint;
    }

    private Complaint findParentComplaint(User user, Long complaintId) {
        Complaint complaint = complaintRepository.findDetailById(complaintId)
                .orElseThrow(() -> new ComplaintNotFoundException("민원을 찾을 수 없습니다."));
        complaintAccessPolicy.validateParentOwner(user, complaint);
        return complaint;
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("사용자를 찾을 수 없습니다."));
    }
}
