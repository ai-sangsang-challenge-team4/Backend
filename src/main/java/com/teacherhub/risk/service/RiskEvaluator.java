package com.teacherhub.risk.service;

import com.teacherhub.risk.dto.detection.RiskTagDetectionResult;
import com.teacherhub.risk.entity.RiskTag;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.enums.RiskTagCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RiskEvaluator {

    public record FinalRiskTagResponse(
            RiskTagCode code,
            boolean detected,
            boolean ruleDetected,
            boolean llmDetected,
            Double confidence,
            String evidence
    ) {}

    // 규칙 기반 및 LLM 위험 태그 결과 병합
    public List<FinalRiskTagResponse> merge(List<RiskTagDetectionResult> ruleResults, List<RiskTagDetectionResult> llmResults
    ) {

        Map<RiskTagCode, RiskTagDetectionResult> rules = index(ruleResults);
        Map<RiskTagCode, RiskTagDetectionResult> llms = index(llmResults);

        return Arrays.stream(RiskTagCode.values())
                .map(code -> {
                    RiskTagDetectionResult rule = rules.get(code);
                    RiskTagDetectionResult llm = llms.get(code);

                    boolean ruleDetected = rule != null && rule.detected();

                    boolean llmDetected = llm != null && llm.detected();

                    String evidence = Stream.of(rule, llm)
                            .filter(result ->
                                    result != null && result.detected()
                            )
                            .map(RiskTagDetectionResult::evidence)
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
    public Map<RiskTagCode, RiskTagDetectionResult> index(
            List<RiskTagDetectionResult> results
    ) {
        Objects.requireNonNull(results, "탐지 결과 목록이 필요합니다.");

        return results.stream()
                .collect(Collectors.toMap(
                        RiskTagDetectionResult::code,
                        Function.identity(),
                        this::mergeDuplicate
                ));
    }

    private RiskTagDetectionResult mergeDuplicate(
            RiskTagDetectionResult left,
            RiskTagDetectionResult right
    ) {
        boolean detected = left.detected() || right.detected();

        // 하나라도 감지됐다면 감지된 결과만 우선 사용
        var preferred = Stream.of(left, right)
                .filter(result -> result.detected() == detected)
                .toList();

        Double confidence = preferred.stream()
                .map(RiskTagDetectionResult::confidence)
                .filter(Objects::nonNull)
                .max(Double::compareTo)
                .orElse(null);

        String evidence = preferred.stream()
                .map(RiskTagDetectionResult::evidence)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .collect(Collectors.joining("\n"));

        return new RiskTagDetectionResult(
                left.code(),
                detected,
                confidence,
                evidence.isBlank() ? null : evidence
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
