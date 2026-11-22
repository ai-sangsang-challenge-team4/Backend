package com.teacherhub.risk.analyzer;

import com.fasterxml.jackson.databind.JsonNode;
import com.teacherhub.risk.entity.RiskDetectionRule;
import com.teacherhub.risk.enums.RiskTagCode;
import com.teacherhub.risk.repository.RiskDetectionRuleRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuleGoldensetExportMainTest {
    private DictionaryRuleBasedRiskDetector productionDetector() {
        List<RiskDetectionRule> originalRules = RuleGoldensetExportMain.loadProductionRules();
        assertThat(originalRules).hasSize(72);
        RiskDetectionRuleRepository repo = mock(RiskDetectionRuleRepository.class);
        when(repo.findAllByEnabledTrueOrderByIdAsc()).thenReturn(originalRules);
        DictionaryRuleBasedRiskDetector detector = new DictionaryRuleBasedRiskDetector(repo);
        detector.refresh();
        return detector;
    }

    @Test
    void exportsAllEightTagsWithoutInvokingAnyModel() {
        JsonNode tags = RuleGoldensetExportMain.detectAllTags(
                productionDetector(), "교사로서 정말 무능하다고 생각합니다.");
        assertThat(tags.size()).isEqualTo(8);
        assertThat(tags.findValuesAsText("code"))
                .containsExactlyInAnyOrderElementsOf(
                        java.util.Arrays.stream(RiskTagCode.values()).map(Enum::name).toList());
        assertThat(tags.findValuesAsText("state")).contains("BOUNDARY", "ABSENT");
        JsonNode insult = null;
        for (JsonNode tag : tags) {
            if ("INSULT".equals(tag.path("code").asText())) {
                insult = tag;
            }
        }
        assertThat(insult).isNotNull();
        assertThat(insult.path("state").asText()).isEqualTo("BOUNDARY");
        assertThat(insult.path("evidenceSpans").size()).isGreaterThan(0);
    }

    @Test
    void unmatchedTagsAreAbsentAndRepeatRemainsUnknownWhenHistoryIsMentioned() {
        JsonNode tags = RuleGoldensetExportMain.detectAllTags(
                productionDetector(), "지난번에도 말씀드렸습니다.");
        JsonNode repeat = null;
        for (JsonNode tag : tags) {
            if ("REPEAT".equals(tag.path("code").asText())) repeat = tag;
        }
        assertThat(repeat).isNotNull();
        assertThat(repeat.path("state").asText()).isEqualTo("UNKNOWN");
        assertThat(tags.findValuesAsText("code")).hasSize(8);
    }

    @Test
    void commonPoliteRequestHasNoPresentTags() {
        JsonNode tags = RuleGoldensetExportMain.detectAllTags(
                productionDetector(), "숙제 제출 기한을 알려주세요.");
        assertThat(tags.findValuesAsText("state")).doesNotContain("PRESENT");
    }
}
