package com.teacherhub.escalation.dto;

import java.util.List;

public record TeacherEscalationPageResponse(
        List<TeacherEscalationSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        boolean hasNext,
        boolean hasPrevious
) {
}
