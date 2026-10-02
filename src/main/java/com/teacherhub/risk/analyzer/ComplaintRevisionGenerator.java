package com.teacherhub.risk.analyzer;

import com.teacherhub.risk.dto.RiskFindingsResponse.FinalRevision;
import com.teacherhub.risk.enums.RiskTagCode;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.service.RiskEvaluator.FinalRiskTagResponse;
import java.util.List;

public interface ComplaintRevisionGenerator {
    RevisionResult generate(String maskedContent, RiskEvaluation riskAnalysis);

    record RevisionResult(FinalRevision finalRevision, List<TagRevisionSuggestion> tagSuggestions) {
        public RevisionResult {
            tagSuggestions = List.copyOf(tagSuggestions);
        }
    }

    record TagRevisionSuggestion(RiskTagCode code, String originalExpression,
            String suggestedExpression, String reason) {}

    /** Internal context for generating revisions, separate from public summary responses. */
    record RiskEvaluation(Long analysisId, Long complaintId,
            List<FinalRiskTagResponse> tags, Integer riskScore,
            RiskLevel riskLevel, String aiReason) {}
}
