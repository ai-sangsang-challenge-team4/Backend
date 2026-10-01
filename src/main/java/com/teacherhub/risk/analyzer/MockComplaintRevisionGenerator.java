package com.teacherhub.risk.analyzer;

import com.teacherhub.risk.dto.ComplaintRevisionResult;
import com.teacherhub.risk.dto.FinalRiskResult;
import com.teacherhub.risk.dto.FinalRiskTagResponse;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class MockComplaintRevisionGenerator implements ComplaintRevisionGenerator {
    @Override
    public ComplaintRevisionResult generate(String maskedContent, FinalRiskResult riskAnalysis) {
        String detectedCodes = riskAnalysis.tags().stream()
                .filter(FinalRiskTagResponse::detected)
                .map(tag -> tag.code().name())
                .collect(Collectors.joining(", "));

        if (detectedCodes.isEmpty()) {
            return new ComplaintRevisionResult(riskAnalysis.complaintId(),
                    "[MOCK] 탐지된 위험 요소가 없어 수정하지 않았습니다.", maskedContent);
        }

        // 연결 확인용 예시입니다. 실제 문맥에 따른 수정은 LLM 연동 시 구현합니다.
        return new ComplaintRevisionResult(riskAnalysis.complaintId(),
                "[MOCK] 최종 위험 요소(" + detectedCodes + ")를 반영한 수정안 예시입니다.",
                "현재 상황에 대해 확인 부탁드리며, 필요한 경우 관련 절차에 따라 문의하고자 합니다.");
    }
}
