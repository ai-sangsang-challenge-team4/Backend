package com.teacherhub.complaint.parent.dto.message;

import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintMessage;
import com.teacherhub.complaint.enums.MessageType;
import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.enums.MessageStatus;
import com.teacherhub.complaint.enums.WaitingFor;
import com.teacherhub.user.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

public record ComplaintConversationResponse(
        Long complaintId,
        ComplaintStatus status,
        WaitingFor waitingFor,
        List<Message> messages
) {
    public static ComplaintConversationResponse from(
            Complaint complaint,
            List<ComplaintMessage> messages
    ) {
        return new ComplaintConversationResponse(
                complaint.getId(),
                complaint.getStatus(),
                complaint.getWaitingFor(),
                messages.stream().map(Message::from).toList()
        );
    }

    @Schema(name = "ParentConversationMessage", description = "학부모용 민원 대화 메시지")
    public record Message(
            Long messageId,
            MessageType type,
            UserRole senderRole,
            String senderName,
            String content,
            MessageStatus status,
            LocalDateTime sentAt,
            LocalDateTime readAt
    ) {
        private static Message from(ComplaintMessage message) {
            return new Message(
                    message.getId(),
                    message.getType(),
                    message.getType() == MessageType.PARENT_INQUIRY
                            ? UserRole.PARENT
                            : UserRole.TEACHER,
                    message.getSender().getName(),
                    message.getContent(),
                    message.getStatus(),
                    message.getSentAt(),
                    message.getReadAt()
            );
        }
    }
}
