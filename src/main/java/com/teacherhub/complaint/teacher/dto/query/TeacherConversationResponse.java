package com.teacherhub.complaint.teacher.dto.query;

import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintMessage;
import com.teacherhub.complaint.enums.MessageStatus;
import com.teacherhub.complaint.enums.MessageType;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.teacherhub.complaint.teacher.dto.AllowedAction;
import com.teacherhub.user.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

// 교사용 대화 탭 읽기 응답
public record TeacherConversationResponse(
        Long complaintId,
        @Schema(description = "위험도별 허용 행동. 완료된 분석이 없으면 REQUEST_MANUAL_REVIEW")
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
                        bufferedSummaries.get(message.getId()) == null
                                ? message.getContent()
                                : bufferedSummaries.get(message.getId()),
                        submittedAt,
                        bufferedSummaries.get(message.getId()) != null
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
            MessageStatus status,
            LocalDateTime sentAt,
            LocalDateTime readAt,
            boolean summarized
    ) {
        public static Message from(
                ComplaintMessage message,
                String content,
                LocalDateTime initialSubmittedAt,
                boolean summarized
        ) {
            return new Message(
                    "message:" + message.getId(),
                    message.getType() == MessageType.PARENT_INQUIRY
                            ? UserRole.PARENT
                            : UserRole.TEACHER,
                    message.getSender().getName(),
                    content,
                    message.getStatus(),
                    message.getSentAt() == null
                            ? initialSubmittedAt
                            : message.getSentAt(),
                    message.getReadAt(),
                    summarized
            );
        }
    }
}
