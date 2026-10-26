package com.teacherhub.complaint.teacher.dto.reply;

import com.teacherhub.complaint.teacher.provider.TeacherSupportContentProvider.GeneratedResponseDraft;

import java.time.LocalDateTime;

// 교사 AI 초안 응답
public record TeacherAiDraftResponse(
        Long complaintId,
        String content,
        String modelName,
        LocalDateTime generatedAt
) {
    public static TeacherAiDraftResponse from(
            Long complaintId,
            GeneratedResponseDraft generated,
            LocalDateTime generatedAt
    ) {
        return new TeacherAiDraftResponse(
                complaintId,
                generated.content(),
                generated.modelName(),
                generatedAt
        );
    }
}
