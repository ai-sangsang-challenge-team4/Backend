package com.teacherhub.complaint.teacher.dto;

import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.Response;

import java.time.LocalDateTime;
import java.util.List;

public record TeacherConversationResponse(
        Long complaintId,
        String bufferedSummary,
        List<AllowedAction> allowedActions,
        List<TeacherReply> responses
) {
    public static TeacherConversationResponse from(
            Complaint complaint,
            String bufferedSummary,
            List<AllowedAction> allowedActions,
            List<Response> responses
    ) {
        return new TeacherConversationResponse(
                complaint.getId(),
                bufferedSummary,
                allowedActions,
                responses.stream().map(TeacherReply::from).toList()
        );
    }

    public record TeacherReply(
            Long responseId,
            String teacherName,
            String content,
            LocalDateTime sentAt
    ) {
        private static TeacherReply from(Response response) {
            return new TeacherReply(
                    response.getId(),
                    response.getTeacher().getUser().getName(),
                    response.getContent(),
                    response.getSentAt() == null ? response.getCreatedAt() : response.getSentAt()
            );
        }
    }
}
