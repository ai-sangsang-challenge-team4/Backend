package com.teacherhub.risk.dto.response;

import com.teacherhub.risk.enums.SubmissionAnalysisStatus;

public record SubmissionAnalysisResult(
        SubmissionAnalysisStatus status,
        RiskSummaryResponse summary
) {
}
