package com.teacherhub.complaint.teacher.dto.reply;

import com.teacherhub.complaint.entity.ComplaintStatus;
import com.teacherhub.complaint.entity.Response;

import java.time.LocalDateTime;

// 최종 제출된 답변
public record TeacherResponseSubmissionResponse(
        Long responseId,
        Long complaintId,
        String content,
        Long aiDraftId,
        ComplaintStatus complaintStatus,
        LocalDateTime sentAt
) {
    public static TeacherResponseSubmissionResponse from(Response response) {
        return new TeacherResponseSubmissionResponse(
                response.getId(),
                response.getComplaint().getId(),
                response.getContent(),
                response.getAiDraft() == null ? null : response.getAiDraft().getId(),
                response.getComplaint().getStatus(),
                response.getSentAt()
        );
    }
}
