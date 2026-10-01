package com.teacherhub.risk.service;

import com.teacherhub.risk.dto.FinalRiskTagResponse;
import com.teacherhub.risk.entity.RiskTag;
import com.teacherhub.risk.enums.RiskLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.List;
import java.util.Map;
import static com.teacherhub.risk.enums.RiskTagCode.*;
import static org.assertj.core.api.Assertions.*;

class RiskScoreCalculatorTest {
    private final RiskScoreCalculator calculator = new RiskScoreCalculator();

    @ParameterizedTest
    @CsvSource({"0,LOW", "1,LOW", "2,MEDIUM", "4,MEDIUM", "5,HIGH", "60,HIGH"})
    void classifiesBoundaryScores(int score, RiskLevel expected) {
        assertThat(calculator.levelFor(score)).isEqualTo(expected);
    }

    @Test
    void countsOnlyDetectedCodesOnceUsingConfiguredWeights() {
        var detected = new FinalRiskTagResponse(THREAT, true, true, true, 0.9, "evidence");
        var absent = new FinalRiskTagResponse(PRIVACY, false, false, false, null, null);
        var definitions = Map.of(THREAT, new RiskTag(THREAT, "threat", 3),
                PRIVACY, new RiskTag(PRIVACY, "privacy", 10));
        assertThat(calculator.calculate(List.of(detected, detected, absent), definitions)).isEqualTo(3);
        assertThat(calculator.calculate(List.of(), definitions)).isZero();
        assertThatThrownBy(() -> calculator.calculate(List.of(detected), Map.of()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> calculator.levelFor(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
