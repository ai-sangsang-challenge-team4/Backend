package com.teacherhub.risk.service;

import com.teacherhub.risk.dto.FinalRiskTagResponse;
import com.teacherhub.risk.dto.RiskDetectionResult;
import com.teacherhub.risk.entity.RiskTag;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.enums.RiskTagCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RiskEvaluator {

    // 규칙 기반 및 LLM 위험 태그 결과 병합
    public List<FinalRiskTagResponse> merge(List<RiskDetectionResult> ruleResults, List<RiskDetectionResult> llmResults
    ) {

        Map<RiskTagCode, RiskDetectionResult> rules = index(ruleResults);
        Map<RiskTagCode, RiskDetectionResult> llms = index(llmResults);

        return Arrays.stream(RiskTagCode.values())
                .map(code -> {
                    RiskDetectionResult rule = rules.get(code);
                    RiskDetectionResult llm = llms.get(code);

                    boolean ruleDetected = rule != null && rule.detected();

                    boolean llmDetected = llm != null && llm.detected();

                    String evidence = Stream.of(rule, llm)
                            .filter(result ->
                                    result != null && result.detected()
                            )
                            .map(RiskDetectionResult::evidence)
                            .filter(value ->
                                    value != null && !value.isBlank()
                            )
                            .distinct()
                            .collect(Collectors.joining("\n"));

                    return new FinalRiskTagResponse(
                            code,
                            ruleDetected || llmDetected,
                            ruleDetected,
                            llmDetected,
                            llm == null ? null : llm.confidence(),
                            evidence.isEmpty() ? null : evidence
                    );
                })
                .toList();
    }

    // 위험 태그 결과를 코드별로 인덱싱
    public Map<RiskTagCode, RiskDetectionResult> index( List<RiskDetectionResult> results
    ) {
        return results.stream()
                .collect(
                        Collectors.toMap(
                                RiskDetectionResult::code,
                                Function.identity()
                        )
                );
    }

    // 위험 태그 결과를 기반으로 점수 계산
    public int calculate( List<FinalRiskTagResponse> tags, Map<RiskTagCode, RiskTag> definitions
    ) {
        return tags.stream()
                .filter(FinalRiskTagResponse::detected)
                .map(FinalRiskTagResponse::code)
                .distinct()
                .mapToInt(code -> {
                    RiskTag tag = definitions.get(code);
                    if (tag == null
                            || tag.getDefaultScore() == null
                            || tag.getDefaultScore() < 0) {
                        throw new IllegalStateException(
                                "위험 태그의 점수 설정을 확인해주세요: " + code
                        );
                    }
                    return tag.getDefaultScore();
                })
                .sum();
    }

    // 위험 수준 계산
    public RiskLevel levelFor(int score) {
        if (score < 0) {
            throw new IllegalArgumentException(
                    "위험 점수는 음수일 수 없습니다."
            );
        }
        if (score <= 1) {
            return RiskLevel.LOW;
        }
        if (score <= 4) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.HIGH;
    }

}

