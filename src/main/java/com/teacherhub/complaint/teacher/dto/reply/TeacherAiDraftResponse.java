package com.teacherhub.complaint.teacher.dto.reply;

import com.teacherhub.complaint.teacher.provider.TeacherSupportContentProvider.GeneratedResponseDraft;
import com.teacherhub.complaint.teacher.provider.TeacherSupportContentProvider.RagReference;

import java.time.LocalDateTime;
import java.util.List;

// 교사 AI 초안 응답
public record TeacherAiDraftResponse(
        Long complaintId,
        String content,
        String modelName,
        LocalDateTime generatedAt,
        List<RagReference> references
) {
    public TeacherAiDraftResponse(Long complaintId, String content, String modelName, LocalDateTime generatedAt) {
        this(complaintId, content, modelName, generatedAt, List.of());
    }

    public static TeacherAiDraftResponse from(
            Long complaintId,
            GeneratedResponseDraft generated,
            LocalDateTime generatedAt
    ) {
        return new TeacherAiDraftResponse(
                complaintId,
                generated.content(),
                generated.modelName(),
                generatedAt,
                generated.references()
        );
    }
}
