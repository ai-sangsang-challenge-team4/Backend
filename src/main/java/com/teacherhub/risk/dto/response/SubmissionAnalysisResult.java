package com.teacherhub.risk.dto.response;

import com.teacherhub.risk.dto.response.parent.ParentRiskSummaryResponse;
import com.teacherhub.risk.enums.SubmissionAnalysisStatus;

public record SubmissionAnalysisResult(
        SubmissionAnalysisStatus status,
        ParentRiskSummaryResponse summary,
        long analyzedContentVersion
) {
}
