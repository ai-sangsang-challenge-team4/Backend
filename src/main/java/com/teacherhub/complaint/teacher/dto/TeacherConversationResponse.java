package com.teacherhub.complaint.teacher.dto;

import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.Response;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import com.teacherhub.user.enums.UserRole;

// 교사용 대화 탭 읽기 응답
public record TeacherConversationResponse(
        Long complaintId,
        List<AllowedAction> allowedActions,
        List<Message> messages
) {
    public static TeacherConversationResponse from(
            Complaint complaint,
            String bufferedSummary,
            List<AllowedAction> allowedActions,
            LocalDateTime submittedAt,
            List<Response> responses
    ) {
        Message initialComplaint = new Message(
                "complaint:" + complaint.getId(),
                UserRole.PARENT,
                complaint.getParent().getUser().getName(),
                bufferedSummary,
                submittedAt,
                true
        );

        List<Message> messages = Stream.concat(
                        Stream.of(initialComplaint),
                        responses.stream().map(Message::from)
                )
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
        private static Message from(Response response) {
            return new Message(
                    "response:" + response.getId(),
                    UserRole.TEACHER,
                    response.getTeacher().getUser().getName(),
                    response.getContent(),
                    response.getSentAt() == null ? response.getCreatedAt() : response.getSentAt(),
                    false
            );
        }
    }
}
