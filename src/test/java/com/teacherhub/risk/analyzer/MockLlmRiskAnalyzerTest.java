package com.teacherhub.risk.analyzer;

import com.teacherhub.risk.enums.RiskTagCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockLlmRiskAnalyzerTest {

    private final MockLlmRiskAnalyzer analyzer = new MockLlmRiskAnalyzer();

    @Test
    void blankMaskedContentUsesFallbackEvidence() {
        var result = analyzer.analyze("", List.of(), null);

        assertThat(result.tags())
                .filteredOn(tag -> tag.code() == RiskTagCode.THREAT)
                .singleElement()
                .satisfies(tag -> assertThat(tag.evidenceSpans())
                        .singleElement()
                        .satisfies(evidence -> assertThat(evidence.text()).isNotBlank()));
        assertThat(result.tags())
                .filteredOn(tag -> tag.code() == RiskTagCode.UNFAIR_REQUEST)
                .singleElement()
                .satisfies(tag -> assertThat(tag.evidenceSpans())
                        .singleElement()
                        .satisfies(evidence -> assertThat(evidence.text()).isNotBlank()));
    }
}
