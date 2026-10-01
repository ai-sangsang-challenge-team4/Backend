package com.teacherhub.risk.service;

import com.teacherhub.risk.dto.RiskDetectionResult;
import com.teacherhub.risk.dto.FinalRiskTagResponse;
import com.teacherhub.risk.enums.RiskTagCode;
import org.junit.jupiter.api.Test;
import java.util.List;

import static com.teacherhub.risk.enums.RiskTagCode.*;
import static org.assertj.core.api.Assertions.assertThat;

class RiskResultMergerTest {
    private final RiskResultMerger merger = new RiskResultMerger();

    @Test
    void combinesSourcesWithoutDuplicatingTagsAndPreservesEvidence() {
        var tags = merger.merge(List.of(
                new RiskDetectionResult(PROFANITY, true, null, "rule only"),
                new RiskDetectionResult(THREAT, true, null, "rule evidence")
        ), List.of(
                new RiskDetectionResult(THREAT, true, 0.9, "llm evidence"),
                new RiskDetectionResult(UNFAIR_REQUEST, true, 0.8, "llm only"),
                new RiskDetectionResult(PROFANITY, false, 0.1, "not detected")
        ));
        assertThat(tags).extracting(FinalRiskTagResponse::code).containsExactly(RiskTagCode.values());
        assertThat(tags).contains(
                new FinalRiskTagResponse(PROFANITY, true, true, false, 0.1, "rule only"),
                new FinalRiskTagResponse(THREAT, true, true, true, 0.9, "rule evidence\nllm evidence"),
                new FinalRiskTagResponse(UNFAIR_REQUEST, true, false, true, 0.8, "llm only"),
                new FinalRiskTagResponse(PRIVACY, false, false, false, null, null)
        );
    }

    @Test
    void emptyDetectionsReturnAllTagsAsUndetected() {
        assertThat(merger.merge(List.of(), List.of())).hasSize(RiskTagCode.values().length)
                .allSatisfy(tag -> {
                    assertThat(tag.detected()).isFalse();
                    assertThat(tag.ruleDetected()).isFalse();
                    assertThat(tag.llmDetected()).isFalse();
                    assertThat(tag.confidence()).isNull();
                    assertThat(tag.evidence()).isNull();
                });
    }

}
