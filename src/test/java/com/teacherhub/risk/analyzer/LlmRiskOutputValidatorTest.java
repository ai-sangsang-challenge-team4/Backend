package com.teacherhub.risk.analyzer;

import com.teacherhub.ai.exception.AiResponseInvalidException;
import com.teacherhub.risk.dto.detection.EvidenceSpan;
import com.teacherhub.risk.dto.detection.RiskTagDetectionResult;
import com.teacherhub.risk.dto.llm.LlmAnalysisMetadata;
import com.teacherhub.risk.dto.llm.LlmRiskAnalysisResult;
import com.teacherhub.risk.dto.llm.SafetyFlags;
import com.teacherhub.risk.enums.RiskTagCode;
import com.teacherhub.risk.enums.RiskTagState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    void rejectsNegativeOrReversedEvidenceCoordinatesAsInvalidAiOutput() {
        EvidenceSpan negativeSpan = mock(EvidenceSpan.class);
        when(negativeSpan.text()).thenReturn("근");
        when(negativeSpan.start()).thenReturn(-1);
        when(negativeSpan.end()).thenReturn(1);
        EvidenceSpan reversedSpan = mock(EvidenceSpan.class);
        when(reversedSpan.text()).thenReturn("근");
        when(reversedSpan.start()).thenReturn(2);
        when(reversedSpan.end()).thenReturn(1);

        var negativeStart = resultWithSpan(negativeSpan);
        var reversedRange = resultWithSpan(reversedSpan);

        assertThatThrownBy(() -> LlmRiskOutputValidator.validate(negativeStart, "근거 입력"))
                .isInstanceOf(AiResponseInvalidException.class);
        assertThatThrownBy(() -> LlmRiskOutputValidator.validate(reversedRange, "근거 입력"))
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

    @Test
    void acceptsExtendedSeverityRepeatCountAndTriStateSafetyFlags() {
        var result = new LlmRiskAnalysisResult(
                extendedTags(RiskTagCode.PROFANITY, RiskTagState.PRESENT, 2),
                new LlmAnalysisMetadata("claude-test", 0.0),
                new SafetyFlags(false, null, false)
        );

        assertThatCode(() -> LlmRiskOutputValidator.validate(result, "마스킹된 민원"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsSeverityThatDoesNotMatchStateAndPartialExtendedSchema() {
        var wrongSeverity = new LlmRiskAnalysisResult(
                extendedTags(RiskTagCode.PROFANITY, RiskTagState.ABSENT, 2),
                new LlmAnalysisMetadata("claude-test", 0.0),
                new SafetyFlags(false, false, false)
        );
        var partial = new LlmRiskAnalysisResult(
                List.of(new RiskTagDetectionResult(
                        RiskTagCode.PROFANITY,
                        RiskTagState.PRESENT,
                        0.9,
                        1,
                        1,
                        List.of(EvidenceSpan.unlocated("표현")),
                        null
                )),
                new LlmAnalysisMetadata("claude-test", 0.0),
                new SafetyFlags(false, false, false)
        );

        assertThatThrownBy(() -> LlmRiskOutputValidator.validate(wrongSeverity, "마스킹된 민원"))
                .isInstanceOf(AiResponseInvalidException.class);
        assertThatThrownBy(() -> LlmRiskOutputValidator.validate(partial, "마스킹된 민원"))
                .isInstanceOf(AiResponseInvalidException.class);
    }

    @Test
    void acceptsNullSeverityAndRepeatCountForBoundaryAndUnknown() {
        var boundary = new LlmRiskAnalysisResult(
                extendedTags(RiskTagCode.LEGAL, RiskTagState.BOUNDARY, null),
                new LlmAnalysisMetadata("claude-test", 0.0),
                new SafetyFlags(false, false, false)
        );
        var unknown = new LlmRiskAnalysisResult(
                extendedTags(RiskTagCode.REPEAT, RiskTagState.UNKNOWN, null),
                new LlmAnalysisMetadata("claude-test", 0.0),
                new SafetyFlags(null, null, null)
        );

        assertThatCode(() -> LlmRiskOutputValidator.validate(boundary, "마스킹된 민원"))
                .doesNotThrowAnyException();
        assertThatCode(() -> LlmRiskOutputValidator.validate(unknown, "마스킹된 민원"))
                .doesNotThrowAnyException();
    }

    private List<RiskTagDetectionResult> extendedTags(
            RiskTagCode selected,
            RiskTagState selectedState,
            Integer selectedSeverity
    ) {
        return Arrays.stream(RiskTagCode.values())
                .map(code -> new RiskTagDetectionResult(
                        code,
                        code == selected ? selectedState : RiskTagState.ABSENT,
                        0.9,
                        code == selected ? selectedSeverity : Integer.valueOf(0),
                        code == selected
                                ? repeatCountFor(selectedState)
                                : Integer.valueOf(0),
                        code == selected && selectedState == RiskTagState.PRESENT
                                ? List.of(EvidenceSpan.unlocated("표현"))
                                : List.of(),
                        null
                ))
                .toList();
    }

    private Integer repeatCountFor(RiskTagState state) {
        return switch (state) {
            case PRESENT -> 2;
            case ABSENT -> 0;
            case BOUNDARY, UNKNOWN -> null;
        };
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

    private LlmRiskAnalysisResult resultWithSpan(EvidenceSpan span) {
        return new LlmRiskAnalysisResult(
                List.of(new RiskTagDetectionResult(
                        RiskTagCode.THREAT,
                        RiskTagState.PRESENT,
                        0.8,
                        List.of(span),
                        "위협 가능성"
                )),
                new LlmAnalysisMetadata("claude-test", 0.0)
        );
    }
}
