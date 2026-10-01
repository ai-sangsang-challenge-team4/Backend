package com.teacherhub.risk.service;

import com.teacherhub.risk.dto.RiskDetectionResult;
import com.teacherhub.risk.dto.FinalRiskTagResponse;
import com.teacherhub.risk.enums.RiskTagCode;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class RiskResultMerger {

    public List<FinalRiskTagResponse> merge( List<RiskDetectionResult> ruleResults,  List<RiskDetectionResult> llmResults
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

    private Map<RiskTagCode, RiskDetectionResult> index( List<RiskDetectionResult> results
    ) {
        return results.stream()
                .collect(
                        Collectors.toMap(
                                RiskDetectionResult::code,
                                Function.identity()
                        )
                );
    }
}