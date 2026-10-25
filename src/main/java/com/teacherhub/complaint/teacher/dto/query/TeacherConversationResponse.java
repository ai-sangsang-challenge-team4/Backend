package com.teacherhub.complaint.teacher.dto.query;

import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintMessage;
import com.teacherhub.complaint.enums.MessageType;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.teacherhub.complaint.teacher.dto.AllowedAction;
import com.teacherhub.user.enums.UserRole;

// 교사용 대화 탭 읽기 응답
public record TeacherConversationResponse(
        Long complaintId,
        List<AllowedAction> allowedActions,
        List<Message> messages
) {
    public static TeacherConversationResponse from(
            Complaint complaint,
            Map<Long, String> bufferedSummaries,
            List<AllowedAction> allowedActions,
            LocalDateTime submittedAt,
            List<ComplaintMessage> sourceMessages
    ) {
        List<Message> messages = sourceMessages.stream()
                .map(message -> Message.from(
                        message,
                        bufferedSummaries.get(message.getId()),
                        submittedAt
                ))
                .sorted(Comparator.comparing(Message::sentAt))
                .toList();

        return new TeacherConversationResponse(complaint.getId(), allowedActions, messages);
    }

    public record Message(
            String messageId,
            UserRole senderRole,
            String senderName,
            String content,
            LocalDateTime sentAt,
            boolean summarized
    ) {
        private static Message from(
                ComplaintMessage message,
                String initialSummary,
                LocalDateTime initialSubmittedAt
        ) {
            boolean summarized = initialSummary != null;
            return new Message(
                    "message:" + message.getId(),
                    message.getType() == MessageType.PARENT_INQUIRY
                            ? UserRole.PARENT
                            : UserRole.TEACHER,
                    message.getSender().getName(),
                    summarized ? initialSummary : message.getContent(),
                    message.getSentAt() == null
                            ? initialSubmittedAt
                            : message.getSentAt(),
                    summarized
            );
        }
    }
}
