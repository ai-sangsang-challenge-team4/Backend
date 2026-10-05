package com.teacherhub.risk.dto;
import java.util.List;
import com.teacherhub.risk.enums.RiskTagCode;

// 위험 분석 상세 결과 (위험 태그별로 분류된 발견 사항)
public record RiskFindingsResponse(

        List<Finding> findings,
        FinalRevision finalRevision) {

    public record Finding(
            RiskTagCode code,
            List<String> evidences,
            List<Suggestion> revisionSuggestions) {}

    public record Suggestion(
            String originalExpression,
            String suggestedExpression,
            String reason) {}

    public record FinalRevision(String reason, String content) {}
}
