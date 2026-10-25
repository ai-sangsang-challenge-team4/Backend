package com.teacherhub.complaint.teacher.dto.reply;

import com.teacherhub.complaint.entity.AiDraft;

import java.time.LocalDateTime;

// 교사 AI 초안 응답
public record TeacherAiDraftResponse(
        Long aiDraftId,
        Long complaintId,
        String content,
        String modelName,
        LocalDateTime createdAt
) {
    public static TeacherAiDraftResponse from(AiDraft aiDraft) {
        return new TeacherAiDraftResponse(
                aiDraft.getId(),
                aiDraft.getComplaint().getId(),
                aiDraft.getDraftContent(),
                aiDraft.getModelName(),
                aiDraft.getCreatedAt()
        );
    }
}
