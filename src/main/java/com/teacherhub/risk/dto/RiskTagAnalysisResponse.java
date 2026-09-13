package com.teacherhub.risk.dto;

import java.util.List;

public record RiskTagAnalysisResponse(
        Long complaintId,
        List<RiskTagResponse> tags
) {
}