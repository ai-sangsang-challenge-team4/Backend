package com.teacherhub.risk.dto.response;

import com.teacherhub.risk.enums.RiskTagCode;
import com.teacherhub.risk.enums.RiskTagState;
import com.teacherhub.risk.dto.detection.EvidenceSpan;
import com.teacherhub.risk.dto.llm.SafetyFlags;

import java.util.List;

// 태그별 Rule 및 LLM 탐지 결과 응답
public record RiskDetectorResponse(
        Long analysisId,
        List<TagResult> results,
        LlmInfo llm,
        SafetyFlags safetyFlags
) {
    public record TagResult(
            RiskTagCode code,
            RiskTagState finalState,
            Detection rule,
            Detection llm
    ) {
        public boolean finalDetected() {
            return finalState == RiskTagState.PRESENT;
        }
    }

    public record Detection(
            RiskTagState state,
            Double confidence,
            Integer severity,
            Integer repeatCount,
            List<EvidenceSpan> evidenceSpans
    ) {
        public boolean detected() {
            return state == RiskTagState.PRESENT;
        }

        public List<String> evidences() {
            return evidenceSpans.stream().map(EvidenceSpan::text).toList();
        }

        public Detection(
                RiskTagState state,
                Double confidence,
                List<EvidenceSpan> evidenceSpans
        ) {
            this(state, confidence, null, null, evidenceSpans);
        }
    }

    public record LlmInfo(
            String modelName,
            Double temperature
    ) {}
}
