package com.teacherhub.complaint.teacher.dto.reply;

import com.teacherhub.complaint.entity.ComplaintMessage;

import java.time.LocalDateTime;

// 임시 저장된 답변 응답
public record TeacherResponseDraftResponse(
        Long responseDraftId,
        Long complaintId,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static TeacherResponseDraftResponse from(ComplaintMessage response) {
        return new TeacherResponseDraftResponse(
                response.getId(),
                response.getComplaint().getId(),
                response.getContent(),
                response.getCreatedAt(),
                response.getUpdatedAt()
        );
    }
}
