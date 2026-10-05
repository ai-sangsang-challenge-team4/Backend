package com.teacherhub.risk.analyzer;

import com.teacherhub.risk.analyzer.ComplaintRevisionGenerator.FinalRevision;
import com.teacherhub.risk.analyzer.ComplaintRevisionGenerator.RiskEvaluation;
import com.teacherhub.risk.service.RiskEvaluator.FinalRiskTagResponse;
import com.teacherhub.risk.analyzer.ComplaintRevisionGenerator.TagRevisionSuggestion;
import java.util.List;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class MockComplaintRevisionGenerator implements ComplaintRevisionGenerator {
    @Override
    public RevisionResult generate(String maskedContent, RiskEvaluation riskAnalysis) {
        String detectedCodes = riskAnalysis.tags().stream()
                .filter(FinalRiskTagResponse::detected)
                .map(tag -> tag.code().name())
                .collect(Collectors.joining(", "));

        if (detectedCodes.isEmpty()) {
            return new RevisionResult(new FinalRevision(
                    "[MOCK] 탐지된 위험 요소가 없어 수정하지 않았습니다.", maskedContent), List.of());
        }

        List<TagRevisionSuggestion> suggestions = riskAnalysis.tags().stream()
                .filter(FinalRiskTagResponse::detected)
                .map(tag -> new TagRevisionSuggestion(tag.code(),
                        tag.evidence() == null ? "" : tag.evidence(),
                        "[MOCK] 해당 사항에 대한 확인과 검토를 요청드립니다.",
                        "[MOCK] " + tag.code() + " 표현을 객관적인 요청으로 완화한 예시입니다."))
                .toList();

        // 연결 확인용 예시입니다. 실제 문맥에 따른 수정은 LLM 연동 시 구현합니다.
        return new RevisionResult(new FinalRevision(
                "[MOCK] 최종 위험 요소(" + detectedCodes + ")를 반영한 수정안 예시입니다.",
                "현재 상황에 대해 확인 부탁드리며, 필요한 경우 관련 절차에 따라 문의하고자 합니다."), suggestions);
    }
}
