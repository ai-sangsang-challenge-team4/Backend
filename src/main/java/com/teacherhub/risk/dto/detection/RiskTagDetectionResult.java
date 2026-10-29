package com.teacherhub.risk.dto.detection;

import com.teacherhub.risk.enums.RiskTagCode;
import com.teacherhub.risk.enums.RiskTagState;

import java.util.List;

// 탐지기는 코드별로 하나의 결과를 반환하며 생략된 코드는 미탐지로 처리합니다.
public record RiskTagDetectionResult(
        RiskTagCode code,
        RiskTagState state,
        Double confidence,
        List<EvidenceSpan> evidenceSpans,
        String reason
) {
    public RiskTagDetectionResult {
        evidenceSpans = evidenceSpans == null ? List.of() : List.copyOf(evidenceSpans);
    }

    public RiskTagDetectionResult(
            RiskTagCode code,
            RiskTagState state,
            Double confidence,
            List<EvidenceSpan> evidenceSpans
    ) {
        this(code, state, confidence, evidenceSpans, null);
    }

    public RiskTagDetectionResult(
            RiskTagCode code,
            boolean detected,
            Double confidence,
            String evidence
    ) {
        this(code, detected, confidence, evidence, null);
    }

    public RiskTagDetectionResult(
            RiskTagCode code,
            boolean detected,
            Double confidence,
            String evidence,
            String reason
    ) {
        this(
                code,
                detected ? RiskTagState.PRESENT : RiskTagState.ABSENT,
                confidence,
                evidence == null || evidence.isBlank()
                        ? List.of()
                        : List.of(EvidenceSpan.unlocated(evidence)),
                reason
        );
    }

    public boolean detected() {
        return state == RiskTagState.PRESENT;
    }

    public String evidence() {
        return evidenceSpans.stream().map(EvidenceSpan::text).distinct()
                .reduce((left, right) -> left + "\n" + right).orElse(null);
    }

    public RiskTagDetectionResult locateEvidenceIn(String content) {
        return new RiskTagDetectionResult(
                code,
                state,
                confidence,
                evidenceSpans.stream().map(span -> span.locateIn(content)).toList(),
                reason
        );
    }
}
