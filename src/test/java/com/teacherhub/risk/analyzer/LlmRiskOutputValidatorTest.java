package com.teacherhub.risk.analyzer;

import com.teacherhub.ai.exception.AiResponseInvalidException;
import com.teacherhub.risk.dto.detection.EvidenceSpan;
import com.teacherhub.risk.dto.detection.RiskTagDetectionResult;
import com.teacherhub.risk.dto.llm.LlmAnalysisMetadata;
import com.teacherhub.risk.dto.llm.LlmRiskAnalysisResult;
import com.teacherhub.risk.enums.RiskTagCode;
import com.teacherhub.risk.enums.RiskTagState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmRiskOutputValidatorTest {

    @Test
    void acceptsAllowedSchemaAndUnlocatedEvidence() {
        var result = new LlmRiskAnalysisResult(
                List.of(new RiskTagDetectionResult(
                        RiskTagCode.THREAT,
                        RiskTagState.PRESENT,
                        0.85,
                        List.of(EvidenceSpan.unlocated("불이익을 암시하는 문맥")),
                        "문맥상 위협 가능성이 있습니다."
                )),
                new LlmAnalysisMetadata("claude-test", 0.0)
        );

        assertThatCode(() -> LlmRiskOutputValidator.validate(result, "마스킹된 민원"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingMetadataDuplicateTagsAndInvalidConfidence() {
        var duplicateTags = new LlmRiskAnalysisResult(
                List.of(
                        tag(RiskTagCode.THREAT, 0.8),
                        tag(RiskTagCode.THREAT, 0.9)
                ),
                new LlmAnalysisMetadata("claude-test", 0.0)
        );
        var invalidConfidence = new LlmRiskAnalysisResult(
                List.of(tag(RiskTagCode.PRIVACY, 1.1)),
                new LlmAnalysisMetadata("claude-test", 0.0)
        );

        assertThatThrownBy(() -> LlmRiskOutputValidator.validate(
                new LlmRiskAnalysisResult(List.of(), null),
                "마스킹된 민원"
        )).isInstanceOf(AiResponseInvalidException.class);
        assertThatThrownBy(() -> LlmRiskOutputValidator.validate(duplicateTags, "마스킹된 민원"))
                .isInstanceOf(AiResponseInvalidException.class);
        assertThatThrownBy(() -> LlmRiskOutputValidator.validate(invalidConfidence, "마스킹된 민원"))
                .isInstanceOf(AiResponseInvalidException.class);
    }

    @Test
    void rejectsEvidenceCoordinatesOutsideMaskedInput() {
        var result = new LlmRiskAnalysisResult(
                List.of(new RiskTagDetectionResult(
                        RiskTagCode.THREAT,
                        RiskTagState.PRESENT,
                        0.8,
                        List.of(new EvidenceSpan("근거", 0, 50)),
                        "위협 가능성"
                )),
                new LlmAnalysisMetadata("claude-test", 0.0)
        );

        assertThatThrownBy(() -> LlmRiskOutputValidator.validate(result, "짧은 입력"))
                .isInstanceOf(AiResponseInvalidException.class);
    }

    @Test
    void rejectsEvidenceCoordinatesThatPointToDifferentText() {
        var result = new LlmRiskAnalysisResult(
                List.of(new RiskTagDetectionResult(
                        RiskTagCode.THREAT,
                        RiskTagState.PRESENT,
                        0.8,
                        List.of(new EvidenceSpan("다른", 0, 2)),
                        "위협 가능성"
                )),
                new LlmAnalysisMetadata("claude-test", 0.0)
        );

        assertThatThrownBy(() -> LlmRiskOutputValidator.validate(result, "실제 입력"))
                .isInstanceOf(AiResponseInvalidException.class);
    }

    private RiskTagDetectionResult tag(RiskTagCode code, double confidence) {
        return new RiskTagDetectionResult(
                code,
                RiskTagState.ABSENT,
                confidence,
                List.of(),
                null
        );
    }
}
