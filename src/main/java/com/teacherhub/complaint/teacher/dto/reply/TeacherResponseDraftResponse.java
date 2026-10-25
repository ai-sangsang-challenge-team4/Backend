package com.teacherhub.complaint.teacher.dto.reply;

import com.teacherhub.complaint.entity.Response;

import java.time.LocalDateTime;

// 임시 저장된 답변 응답
public record TeacherResponseDraftResponse(
        Long responseDraftId,
        Long complaintId,
        String content,
        Long aiDraftId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static TeacherResponseDraftResponse from(Response response) {
        return new TeacherResponseDraftResponse(
                response.getId(),
                response.getComplaint().getId(),
                response.getContent(),
                response.getAiDraft() == null ? null : response.getAiDraft().getId(),
                response.getCreatedAt(),
                response.getUpdatedAt()
        );
    }
}
