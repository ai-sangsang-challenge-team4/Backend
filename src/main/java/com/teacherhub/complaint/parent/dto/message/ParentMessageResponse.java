package com.teacherhub.complaint.parent.dto.message;

import com.teacherhub.complaint.entity.ComplaintMessage;
import com.teacherhub.complaint.enums.MessageStatus;
import com.teacherhub.complaint.enums.WaitingFor;

import java.time.LocalDateTime;

public record ParentMessageResponse(
        Long messageId,
        Long complaintId,
        String content,
        MessageStatus status,
        WaitingFor waitingFor,
        long contentVersion,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime sentAt
) {
    public static ParentMessageResponse from(ComplaintMessage message) {
        return new ParentMessageResponse(
                message.getId(),
                message.getComplaint().getId(),
                message.getContent(),
                message.getStatus(),
                message.getComplaint().getWaitingFor(),
                message.getContentVersion(),
                message.getCreatedAt(),
                message.getUpdatedAt(),
                message.getSentAt()
        );
    }
}
