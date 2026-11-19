package com.teacherhub.risk.dto.response.parent;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.teacherhub.risk.dto.response.common.EmergencyOverrideResponse;
import com.teacherhub.risk.enums.RiskTagCode;
import com.teacherhub.risk.enums.RevisionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

// 민원의 위험 근거, 수정 제안 및 최종 수정본 응답
@Schema(name = "RiskRevisionResponse")
public record ParentRiskFindingsResponse(
        RevisionStatus revisionStatus,
        @JsonProperty("findings")
        List<RiskTagResult> riskTagResults,
        FinalRevision finalRevision,
        EmergencyOverrideResponse emergencyOverride
) {
    @Schema(name = "ParentRiskTagResult")
    public record RiskTagResult(
            RiskTagCode code,
            @JsonProperty("evidences")
            List<String> evidence,
            List<RevisionSuggestion> revisionSuggestions
    ) {}

    @Schema(name = "Suggestion")
    public record RevisionSuggestion(
            String originalExpression,
            String suggestedExpression,
            String reason
    ) {}

    public record FinalRevision(String reason, String content) {}
}
