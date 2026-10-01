package com.teacherhub.risk.analyzer;

import com.teacherhub.complaint.dto.RiskyExpressionResponse;
import com.teacherhub.risk.dto.LLMRiskAnalysisResult;
import com.teacherhub.risk.dto.RiskDetectionResult;
import com.teacherhub.risk.enums.RiskTagCode;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class MockLlmRiskAnalyzer implements LlmRiskAnalyzer {
    @Override
    public LLMRiskAnalysisResult analyze(String maskedContent) {
        // 모든 값은 파이프라인 연결 확인용 예시이며 실제 내용을 분석하지 않습니다.
        return new LLMRiskAnalysisResult(
                List.of(
                        new RiskDetectionResult(RiskTagCode.THREAT, true, 0.9, "[MOCK LLM] 협박 문맥 예시"),
                        new RiskDetectionResult(RiskTagCode.UNFAIR_REQUEST, true, 0.8, "[MOCK LLM] 부당 요구 문맥 예시")
                ),
                "mock-llm",
                0.0,
                "[MOCK LLM] 위험 요소 탐지 및 수정본 반환 예시입니다.",
                List.of(
                        new RiskyExpressionResponse("가만두지 않겠습니다", "[MOCK LLM] 협박 표현 예시"),
                        new RiskyExpressionResponse("무조건 원하는 대로 처리하세요", "[MOCK LLM] 부당 요구 예시")
                )
        );
    }
}
