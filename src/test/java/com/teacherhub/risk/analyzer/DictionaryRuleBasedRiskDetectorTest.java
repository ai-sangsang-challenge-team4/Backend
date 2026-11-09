package com.teacherhub.risk.analyzer;

import com.teacherhub.risk.config.RiskRuleCsvLoader;
import com.teacherhub.risk.dto.detection.RiskTagDetectionResult;
import com.teacherhub.risk.entity.RiskDetectionRule;
import com.teacherhub.risk.enums.RiskTagCode;
import com.teacherhub.risk.enums.RiskTagState;
import com.teacherhub.risk.repository.RiskDetectionRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DictionaryRuleBasedRiskDetectorTest {

    private final RiskDetectionRuleRepository repository =
            mock(RiskDetectionRuleRepository.class);
    private final DictionaryRuleBasedRiskDetector detector =
            new DictionaryRuleBasedRiskDetector(repository);

    @BeforeEach
    void setUp() {
        List<RiskDetectionRule> rules = new RiskRuleCsvLoader()
                .load()
                .stream()
                .map(seed -> new RiskDetectionRule(
                        seed.ruleId(),
                        seed.sourceTagCode(),
                        seed.tagCode(),
                        seed.expression(),
                        seed.matchType(),
                        seed.contextRequired(),
                        seed.contextSubtype(),
                        seed.normalizationRule(),
                        "v1.1"
                ))
                .toList();

        when(repository.findAllByEnabledTrueOrderByIdAsc())
                .thenReturn(rules);

        detector.refresh();
    }

    @Test
    void returnsContextCandidateInsteadOfPresentForKeywordMatch() {
        RiskTagDetectionResult result = find(
                detector.detect("교사로서 정말 무능하다고 생각합니다."),
                RiskTagCode.INSULT
        );

        assertThat(result.state()).isEqualTo(RiskTagState.BOUNDARY);
        assertThat(result.detected()).isFalse();
        assertThat(result.evidenceSpans())
                .singleElement()
                .satisfies(span -> {
                    assertThat(span.text()).isEqualTo("무능");
                    assertThat(span.start()).isEqualTo(8);
                    assertThat(span.end()).isEqualTo(10);
                });
        assertThat(result.reason())
                .contains("RD-INSULT-01")
                .contains("TARGET_CHECK");
    }

    @Test
    void normalizesWhitespaceForPhraseMatch() {
        RiskTagDetectionResult result = find(
                detector.detect("이 일은 두고     보겠습니다."),
                RiskTagCode.THREAT
        );

        assertThat(result.state()).isEqualTo(RiskTagState.BOUNDARY);
        assertThat(result.evidenceSpans())
                .extracting(span -> span.text())
                .contains("두고     보");
    }

    @Test
    void keepsHistoryHintUnknownWithoutHistoryContext() {
        RiskTagDetectionResult result = find(
                detector.detect("지난번에도 말씀드렸습니다."),
                RiskTagCode.REPEAT
        );

        assertThat(result.state()).isEqualTo(RiskTagState.UNKNOWN);
        assertThat(result.reason())
                .contains("RD-REPEAT-07")
                .contains("HISTORY_REQUIRED");
    }

    @Test
    void excludesKnownFalsePositiveExpressions() {
        assertThat(detector.detect("이것이 사건의 시발점입니다."))
                .noneMatch(result -> result.code() == RiskTagCode.PROFANITY);

        assertThat(detector.detect("정말 죽여주는 공연이었습니다."))
                .noneMatch(result -> result.code() == RiskTagCode.THREAT);
    }

    @Test
    void compilesPythonStylePatternsAndFindsCandidates() {
        RiskTagDetectionResult profanity = find(
                detector.detect("정말 시1발이라고 말했습니다."),
                RiskTagCode.PROFANITY
        );

        RiskTagDetectionResult privacy = find(
                detector.detect("그 학생 연락처를 알려주세요."),
                RiskTagCode.PRIVACY
        );

        assertThat(profanity.state()).isEqualTo(RiskTagState.BOUNDARY);
        assertThat(profanity.reason()).contains("RD-PROF-09");
        assertThat(privacy.state()).isEqualTo(RiskTagState.BOUNDARY);
        assertThat(privacy.reason()).contains("RD-PII-08");
    }

    @Test
    void excludesAttendanceNumberFromThirdPartyPrivacyPattern() {
        assertThat(detector.detect("그 학생 출석번호를 알려주세요."))
                .noneMatch(result -> result.code() == RiskTagCode.PRIVACY);
    }

    private RiskTagDetectionResult find(
            List<RiskTagDetectionResult> results,
            RiskTagCode code
    ) {
        return results.stream()
                .filter(result -> result.code() == code)
                .findFirst()
                .orElseThrow();
    }
}
