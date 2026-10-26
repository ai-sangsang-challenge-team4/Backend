package com.teacherhub.complaint.query.dto;

import java.util.List;

public record ComplaintPageResponse(
        List<ComplaintListResponse> content,
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
