package com.teacherhub.risk.service;

import com.teacherhub.risk.dto.detection.EvidenceSpan;
import com.teacherhub.risk.dto.detection.RiskTagDetectionResult;
import com.teacherhub.risk.entity.RiskTag;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.enums.RiskTagCode;
import com.teacherhub.risk.enums.RiskTagState;
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
            RiskTagState state,
            RiskTagState ruleState,
            RiskTagState llmState,
            Double confidence,
            List<EvidenceSpan> evidenceSpans,
            String reason
    ) {
        public FinalRiskTagResponse {
            evidenceSpans = evidenceSpans == null
                    ? List.of()
                    : List.copyOf(evidenceSpans);
        }

        public FinalRiskTagResponse(
                RiskTagCode code,
                boolean detected,
                boolean ruleDetected,
                boolean llmDetected,
                Double confidence,
                String evidence
        ) {
            this(
                    code,
                    detected,
                    ruleDetected,
                    llmDetected,
                    confidence,
                    evidence,
                    null
            );
        }

        public FinalRiskTagResponse(
                RiskTagCode code,
                boolean detected,
                boolean ruleDetected,
                boolean llmDetected,
                Double confidence,
                String evidence,
                String reason
        ) {
            this(
                    code,
                    detected
                            ? RiskTagState.PRESENT
                            : RiskTagState.ABSENT,
                    ruleDetected
                            ? RiskTagState.PRESENT
                            : RiskTagState.ABSENT,
                    llmDetected
                            ? RiskTagState.PRESENT
                            : RiskTagState.ABSENT,
                    confidence,
                    evidence == null || evidence.isBlank()
                            ? List.of()
                            : List.of(
                                    EvidenceSpan.unlocated(evidence)
                            ),
                    reason
            );
        }

        public boolean detected() {
            return state == RiskTagState.PRESENT;
        }

        public boolean ruleDetected() {
            return ruleState == RiskTagState.PRESENT;
        }

        public boolean llmDetected() {
            return llmState == RiskTagState.PRESENT;
        }

        public String evidence() {
            return evidenceSpans.stream()
                    .map(EvidenceSpan::text)
                    .distinct()
                    .reduce(
                            (left, right) ->
                                    left + "\n" + right
                    )
                    .orElse(null);
        }
    }

    // Rule은 1차 탐지 결과이며,
    // LLM 결과가 존재하는 경우 Claude의 2차 문맥 판단을 최종 상태로 사용합니다.
    public List<FinalRiskTagResponse> merge(
            List<RiskTagDetectionResult> ruleResults,
            List<RiskTagDetectionResult> llmResults
    ) {
        Map<RiskTagCode, RiskTagDetectionResult> rules =
                index(ruleResults);

        Map<RiskTagCode, RiskTagDetectionResult> llms =
                index(llmResults);

        return Arrays.stream(
                        RiskTagCode.values()
                )
                .map(code -> {
                    RiskTagDetectionResult rule =
                            rules.get(code);

                    RiskTagDetectionResult llm =
                            llms.get(code);

                    RiskTagState ruleState =
                            normalizedState(
                                    code,
                                    rule
                            );

                    RiskTagState llmState =
                            normalizedState(
                                    code,
                                    llm
                            );

                    RiskTagState finalState =
                            mergeState(
                                    code,
                                    rule,
                                    llm,
                                    ruleState,
                                    llmState
                            );

                    /*
                     * Claude가 해당 태그를 판단한 경우
                     * 최종 근거와 reason 역시 Claude 결과를 우선 사용합니다.
                     *
                     * Rule 결과는 detector-results에서
                     * 별도 source evidence로 계속 보존됩니다.
                     */
                    RiskTagDetectionResult finalSource =
                            llm != null
                                    ? llm
                                    : rule;

                    List<EvidenceSpan> evidenceSpans =
                            finalSource != null
                                    && (
                                    finalSource.state()
                                            == RiskTagState.PRESENT
                                            || finalSource.state()
                                            == RiskTagState.BOUNDARY
                            )
                                    ? finalSource.evidenceSpans()
                                    : List.of();

                    String reason =
                            finalSource != null
                                    && (
                                    finalSource.state()
                                            == RiskTagState.PRESENT
                                            || finalSource.state()
                                            == RiskTagState.BOUNDARY
                            )
                                    ? finalSource.reason()
                                    : null;

                    return new FinalRiskTagResponse(
                            code,
                            finalState,
                            ruleState,
                            llmState,
                            llm == null
                                    ? null
                                    : llm.confidence(),
                            evidenceSpans,
                            reason == null || reason.isBlank()
                                    ? null
                                    : reason
                    );
                })
                .toList();
    }

    private RiskTagState normalizedState(
            RiskTagCode code,
            RiskTagDetectionResult result
    ) {
        if (result == null) {
            return code == RiskTagCode.REPEAT
                    ? RiskTagState.UNKNOWN
                    : RiskTagState.ABSENT;
        }

        if (result.state() == RiskTagState.PRESENT
                && result.evidenceSpans().isEmpty()) {

            return RiskTagState.BOUNDARY;
        }

        return result.state();
    }

    /*
     * Rule = 1차 탐지
     * Claude = 2차 문맥 검토
     *
     * Claude 결과가 존재하면 Claude 판단이 최종 상태가 됩니다.
     * 따라서:
     *
     * Rule PRESENT + Claude ABSENT
     * -> ABSENT
     *
     * Rule ABSENT + Claude PRESENT
     * -> PRESENT
     *
     * Rule PRESENT + Claude BOUNDARY
     * -> BOUNDARY
     *
     * Rule PRESENT + Claude UNKNOWN
     * -> UNKNOWN
     *
     * Claude 결과가 없는 경우에만 Rule 결과를 사용합니다.
     */
    private RiskTagState mergeState(
            RiskTagCode code,
            RiskTagDetectionResult rule,
            RiskTagDetectionResult llm,
            RiskTagState ruleState,
            RiskTagState llmState
    ) {
        if (llm != null) {
            return llmState;
        }

        if (rule != null) {
            return ruleState;
        }

        return code == RiskTagCode.REPEAT
                ? RiskTagState.UNKNOWN
                : RiskTagState.ABSENT;
    }

    // 위험 태그 결과를 코드별로 인덱싱
    public Map<RiskTagCode, RiskTagDetectionResult> index(
            List<RiskTagDetectionResult> results
    ) {
        Objects.requireNonNull(
                results,
                "탐지 결과 목록이 필요합니다."
        );

        return results.stream()
                .collect(
                        Collectors.toMap(
                                RiskTagDetectionResult::code,
                                Function.identity(),
                                this::mergeDuplicate
                        )
                );
    }

    private RiskTagDetectionResult mergeDuplicate(
            RiskTagDetectionResult left,
            RiskTagDetectionResult right
    ) {
        RiskTagState state =
                mergeDuplicateState(
                        left.state(),
                        right.state()
                );

        var preferred =
                Stream.of(
                                left,
                                right
                        )
                        .filter(
                                result ->
                                        result.state() == state
                        )
                        .toList();

        Double confidence =
                preferred.stream()
                        .map(
                                RiskTagDetectionResult::confidence
                        )
                        .filter(
                                Objects::nonNull
                        )
                        .max(
                                Double::compareTo
                        )
                        .orElse(null);

        List<EvidenceSpan> evidenceSpans =
                preferred.stream()
                        .flatMap(
                                result ->
                                        result.evidenceSpans()
                                                .stream()
                        )
                        .distinct()
                        .toList();

        String reason =
                preferred.stream()
                        .map(
                                RiskTagDetectionResult::reason
                        )
                        .filter(
                                value ->
                                        value != null
                                                && !value.isBlank()
                        )
                        .distinct()
                        .collect(
                                Collectors.joining("\n")
                        );

        return new RiskTagDetectionResult(
                left.code(),
                state,
                confidence,
                evidenceSpans,
                reason.isBlank()
                        ? null
                        : reason
        );
    }

    private RiskTagState mergeDuplicateState(
            RiskTagState left,
            RiskTagState right
    ) {
        if (left == RiskTagState.PRESENT
                || right == RiskTagState.PRESENT) {

            return RiskTagState.PRESENT;
        }

        if (left == RiskTagState.BOUNDARY
                || right == RiskTagState.BOUNDARY) {

            return RiskTagState.BOUNDARY;
        }

        if (left == RiskTagState.UNKNOWN
                || right == RiskTagState.UNKNOWN) {

            return RiskTagState.UNKNOWN;
        }

        return RiskTagState.ABSENT;
    }

    // 위험 태그 결과를 기반으로 시스템 점수 계산
    public int calculate(
            List<FinalRiskTagResponse> tags,
            Map<RiskTagCode, RiskTag> definitions
    ) {
        return tags.stream()
                .filter(
                        FinalRiskTagResponse::detected
                )
                .map(
                        FinalRiskTagResponse::code
                )
                .distinct()
                .mapToInt(code -> {
                    RiskTag tag =
                            definitions.get(code);

                    if (tag == null
                            || tag.getDefaultScore() == null
                            || tag.getDefaultScore() < 0) {

                        throw new IllegalStateException(
                                "위험 태그의 점수 설정을 확인해주세요: "
                                        + code
                        );
                    }

                    return tag.getDefaultScore();
                })
                .sum();
    }

    // 위험 수준 계산
    public RiskLevel levelFor(
            int score
    ) {
        if (score < 0) {
            throw new IllegalArgumentException(
                    "위험 점수는 음수일 수 없습니다."
            );
        }

        if (score <= 20) {
            return RiskLevel.LOW;
        }

        if (score <= 40) {
            return RiskLevel.MEDIUM;
        }

        if (score <= 60) {
            return RiskLevel.HIGH;
        }

        return RiskLevel.EMERGENCY;
    }
}