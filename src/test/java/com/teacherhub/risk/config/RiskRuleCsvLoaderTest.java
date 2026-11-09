package com.teacherhub.risk.config;

import com.teacherhub.risk.enums.RiskRuleMatchType;
import com.teacherhub.risk.enums.RiskTagCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RiskRuleCsvLoaderTest {

    private final RiskRuleCsvLoader loader = new RiskRuleCsvLoader();

    @Test
    void loadsAndMapsAllDictionaryRules() {
        var rules = loader.load();

        assertThat(rules).hasSize(72);
        assertThat(rules)
                .extracting(RiskRuleCsvLoader.RiskRuleSeed::ruleId)
                .doesNotHaveDuplicates();

        assertThat(rules)
                .filteredOn(rule -> rule.matchType() == RiskRuleMatchType.KEYWORD)
                .hasSize(43);
        assertThat(rules)
                .filteredOn(rule -> rule.matchType() == RiskRuleMatchType.PHRASE)
                .hasSize(9);
        assertThat(rules)
                .filteredOn(rule -> rule.matchType() == RiskRuleMatchType.PATTERN)
                .hasSize(4);
        assertThat(rules)
                .filteredOn(rule -> rule.matchType() == RiskRuleMatchType.HINT_ONLY)
                .hasSize(16);

        assertThat(rules)
                .extracting(RiskRuleCsvLoader.RiskRuleSeed::tagCode)
                .doesNotContainNull();
        assertThat(rules)
                .allSatisfy(rule -> assertThat(rule.sourceTagCode())
                        .isEqualTo(rule.tagCode().name()));
        assertThat(rules.stream()
                .map(RiskRuleCsvLoader.RiskRuleSeed::tagCode)
                .distinct()
                .toList())
                .containsExactlyInAnyOrderElementsOf(
                        java.util.Arrays.asList(RiskTagCode.values())
                );
    }

    @Test
    void parsesQuotedRegularExpressionContainingComma() {
        var values = RiskRuleCsvLoader.parseLine(
                "RD-PROF-09,GENERAL_PROFANITY_EMOTION,"
                        + "\"(씨|시)[.\\d]{0,3}(발|ㅂ)\",PATTERN,TRUE"
        );

        assertThat(values)
                .containsExactly(
                        "RD-PROF-09",
                        "GENERAL_PROFANITY_EMOTION",
                        "(씨|시)[.\\d]{0,3}(발|ㅂ)",
                        "PATTERN",
                        "TRUE"
                );
    }
}
