package com.teacherhub.risk.analyzer;

import com.teacherhub.risk.dto.llm.LlmRiskAnalysisResult;

public interface LlmRiskAnalyzer {
    // 마스킹된 내용으로 위험 요소만 분석합니다. 수정안은 최종 결과 병합 후 생성합니다.
    LlmRiskAnalysisResult analyze(String maskedContent);
}
