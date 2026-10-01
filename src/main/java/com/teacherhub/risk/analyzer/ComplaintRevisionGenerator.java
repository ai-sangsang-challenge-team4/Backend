package com.teacherhub.risk.analyzer;

import com.teacherhub.risk.dto.ComplaintRevisionResult;
import com.teacherhub.risk.dto.FinalRiskResult;

public interface ComplaintRevisionGenerator {
    // 마스킹된 원문과 병합 완료된 위험 분석 결과를 바탕으로 수정안을 생성합니다.
    ComplaintRevisionResult generate(String maskedContent, FinalRiskResult riskAnalysis);
}
