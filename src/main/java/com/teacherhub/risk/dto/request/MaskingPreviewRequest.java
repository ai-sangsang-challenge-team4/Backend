package com.teacherhub.risk.dto.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record MaskingPreviewRequest(
        @Positive Long complaintId,
        @Positive Long studentId,
        @Positive Long teacherId,
        @Size(max = 100) String title,
        @Size(max = 5_000) String content
) {
}
