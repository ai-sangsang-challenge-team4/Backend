package com.teacherhub.risk.analyzer;

import com.teacherhub.ai.exception.AiResponseInvalidException;
import com.teacherhub.risk.enums.RiskTagCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PythonComplaintRevisionGeneratorTest {

    private final PythonComplaintRevisionGenerator generator =
            new PythonComplaintRevisionGenerator("http://localhost", 1, 1);

    @Test
    void parsesAllowedRevisionTagCode() {
        assertThat(generator.parseRiskTagCode("THREAT")).isEqualTo(RiskTagCode.THREAT);
    }

    @Test
    void rejectsUnknownOrMissingRevisionTagCodeAsInvalidAiOutput() {
        assertThatThrownBy(() -> generator.parseRiskTagCode("NOT_ALLOWED"))
                .isInstanceOf(AiResponseInvalidException.class);
        assertThatThrownBy(() -> generator.parseRiskTagCode(null))
                .isInstanceOf(AiResponseInvalidException.class);
    }
}
