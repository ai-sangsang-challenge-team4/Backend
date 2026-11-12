package com.teacherhub.risk.analyzer;

import com.teacherhub.risk.dto.detection.EvidenceSpan;
import com.teacherhub.risk.dto.detection.RiskTagDetectionResult;
import com.teacherhub.risk.dto.llm.LlmAnalysisMetadata;
import com.teacherhub.risk.dto.llm.LlmRiskAnalysisResult;
import com.teacherhub.risk.dto.llm.RepeatAnalysisContext;
import com.teacherhub.risk.dto.llm.SafetyFlags;
import com.teacherhub.risk.enums.RiskTagCode;
import com.teacherhub.risk.enums.RiskTagState;

import java.util.Arrays;
import java.util.List;

/** 고정 결과가 필요한 단위·통합 테스트 전용 fake입니다. */
public class MockLlmRiskAnalyzer implements LlmRiskAnalyzer {

    @Override
    public LlmRiskAnalysisResult analyze(
            String maskedContent,
            List<RiskTagDetectionResult> ruleResults,
            RepeatAnalysisContext repeatContext
    ) {
        return new LlmRiskAnalysisResult(
                Arrays.stream(RiskTagCode.values())
                        .map(code -> {
                            boolean detected = code == RiskTagCode.THREAT
                                    || code == RiskTagCode.UNFAIR_REQUEST;
                            String evidence = code == RiskTagCode.THREAT
                                    ? "[MOCK LLM] 협박 문맥 예시"
                                    : code == RiskTagCode.UNFAIR_REQUEST
                                    ? "[MOCK LLM] 부당 요구 문맥 예시"
                                    : null;
                            String reason = code == RiskTagCode.THREAT
                                    ? "[MOCK LLM] 불이익을 암시하는 문맥입니다."
                                    : code == RiskTagCode.UNFAIR_REQUEST
                                    ? "[MOCK LLM] 일방적인 조치를 요구하는 문맥입니다."
                                    : null;
                            return new RiskTagDetectionResult(
                                    code,
                                    detected ? RiskTagState.PRESENT : RiskTagState.ABSENT,
                                    detected ? (code == RiskTagCode.THREAT ? 0.9 : 0.8) : 0.9,
                                    detected ? 1 : 0,
                                    detected ? 1 : 0,
                                    evidence == null
                                            ? List.of()
                                            : List.of(EvidenceSpan.unlocated(evidence)),
                                    reason
                            );
                        })
                        .toList(),
                new LlmAnalysisMetadata("mock-llm", 0.0),
                new SafetyFlags(false, false, false)
        );
    }
}
