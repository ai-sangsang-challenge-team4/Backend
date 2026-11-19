package com.teacherhub.risk.dto.response.parent;

import com.teacherhub.risk.enums.RiskTagCode;
import com.teacherhub.risk.enums.RevisionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

// 민원의 위험 근거, 수정 제안 및 최종 수정본 응답
@Schema(name = "RiskRevisionResponse")
public record ParentRiskFindingsResponse(
        RevisionStatus revisionStatus,
        List<Finding> findings,
        FinalRevision finalRevision
) {
    public record Finding(
            RiskTagCode code,
            List<String> evidences,
            List<Suggestion> revisionSuggestions
    ) {}

    public record Suggestion(
            String originalExpression,
            String suggestedExpression,
            String reason
    ) {}

    public record FinalRevision(String reason, String content) {}
}
