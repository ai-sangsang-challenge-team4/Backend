package com.teacherhub.risk.analyzer;

import com.teacherhub.ai.exception.AiResponseInvalidException;
import com.teacherhub.risk.dto.detection.EvidenceSpan;
import com.teacherhub.risk.dto.detection.RiskTagDetectionResult;
import com.teacherhub.risk.dto.llm.LlmRiskAnalysisResult;
import com.teacherhub.risk.dto.llm.RepeatAnalysisContext;
import com.teacherhub.risk.enums.RiskTagCode;
import com.teacherhub.risk.enums.RiskTagState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** LLM 출력을 저장하거나 점수 계산에 사용하기 전 검증하는 결정론적 경계입니다. */
public final class LlmRiskOutputValidator {

    private static final Logger log = LoggerFactory.getLogger(LlmRiskOutputValidator.class);
    private static final int MAX_TAG_ENTRIES = 32;
    private static final int MAX_MODEL_NAME_LENGTH = 100;
    private static final int MAX_REASON_LENGTH = 2_000;
    private static final int MAX_EVIDENCE_LENGTH = 1_000;
    private static final int MAX_EVIDENCE_SPANS_PER_TAG = 20;
    private static final int MAX_REPEAT_COUNT = 10_000;

    private LlmRiskOutputValidator() {
    }

    public static LlmRiskAnalysisResult validate(
            LlmRiskAnalysisResult result,
            String maskedContent
    ) {
        return validate(result, maskedContent, null);
    }

    public static LlmRiskAnalysisResult validate(
            LlmRiskAnalysisResult result,
            String maskedContent,
            RepeatAnalysisContext repeatContext
    ) {
        if (result == null || result.metadata() == null || result.tags() == null
                || maskedContent == null || maskedContent.isBlank()) {
            throw invalid();
        }

        String modelName = result.metadata().modelName();
        Double temperature = result.metadata().temperature();
        if (modelName == null || modelName.isBlank()
                || modelName.length() > MAX_MODEL_NAME_LENGTH
                || temperature == null || !Double.isFinite(temperature)
                || temperature < 0.0 || temperature > 2.0) {
            throw invalid();
        }

        List<RiskTagDetectionResult> rawTags = result.tags();
        if (rawTags.size() > MAX_TAG_ENTRIES) {
            throw invalid();
        }
        List<RiskTagDetectionResult> tags = rawTags.stream()
                .filter(tag -> tag != null && tag.code() != null && tag.state() != null)
                .toList();
        int discardedTags = rawTags.size() - tags.size();
        if (discardedTags > 0) {
            log.warn(
                    "Unsupported AI risk tags discarded. capability=risk-analysis discardedCount={}",
                    discardedTags
            );
        }
        if (tags.size() > RiskTagCode.values().length) {
            throw invalid();
        }

        boolean extendedSchema = result.safetyFlags() != null
                || tags.stream().anyMatch(tag -> tag != null
                && (tag.severity() != null || tag.repeatCount() != null));
        if (extendedSchema && (result.safetyFlags() == null
                || tags.size() != RiskTagCode.values().length
                || tags.stream().anyMatch(tag -> tag == null))) {
            throw invalid();
        }

        Set<RiskTagCode> uniqueCodes = new HashSet<>();
        List<RiskTagDetectionResult> normalizedTags = new ArrayList<>();
        for (RiskTagDetectionResult tag : tags) {
            normalizedTags.add(validateTag(
                    tag,
                    maskedContent,
                    uniqueCodes,
                    extendedSchema,
                    repeatContext
            ));
        }
        return new LlmRiskAnalysisResult(
                List.copyOf(normalizedTags),
                result.metadata(),
                result.safetyFlags()
        );
    }

    private static RiskTagDetectionResult validateTag(
            RiskTagDetectionResult tag,
            String maskedContent,
            Set<RiskTagCode> uniqueCodes,
            boolean extendedSchema,
            RepeatAnalysisContext repeatContext
    ) {
        if (!uniqueCodes.add(tag.code())) {
            throw invalid();
        }

        Double confidence = tag.confidence();
        if (confidence != null && (!Double.isFinite(confidence)
                || confidence < 0.0 || confidence > 1.0)) {
            throw invalid();
        }
        if (tag.reason() != null && tag.reason().length() > MAX_REASON_LENGTH) {
            throw invalid();
        }

        validateSeverity(tag, extendedSchema);

        List<EvidenceSpan> spans = tag.evidenceSpans();
        if (spans == null || spans.size() > MAX_EVIDENCE_SPANS_PER_TAG) {
            throw invalid();
        }
        List<EvidenceSpan> supportedSpans = new ArrayList<>();
        for (EvidenceSpan span : spans) {
            if (isSupportedEvidence(span, maskedContent)) {
                supportedSpans.add(span);
            }
        }
        int discardedEvidenceCount = spans.size() - supportedSpans.size();
        if (discardedEvidenceCount > 0) {
            log.warn(
                    "Unsupported AI risk evidence discarded. capability=risk-analysis code={} discardedCount={}",
                    tag.code(),
                    discardedEvidenceCount
            );
        }

        RiskTagState normalizedState = tag.state();
        Integer normalizedSeverity = tag.severity();
        Integer normalizedRepeatCount = tag.repeatCount();
        if (normalizedState == RiskTagState.PRESENT && supportedSpans.isEmpty()) {
            normalizedState = RiskTagState.BOUNDARY;
            normalizedSeverity = null;
            normalizedRepeatCount = null;
            log.warn(
                    "AI risk tag downgraded. capability=risk-analysis code={} reason=EVIDENCE_NOT_FOUND",
                    tag.code()
            );
        }
        if (tag.code() == RiskTagCode.REPEAT
                && normalizedState == RiskTagState.PRESENT
                && repeatContext != null
                && !repeatContext.historyAvailable()) {
            normalizedState = RiskTagState.UNKNOWN;
            normalizedSeverity = null;
            normalizedRepeatCount = null;
            supportedSpans.clear();
            log.warn(
                    "AI risk tag downgraded. capability=risk-analysis code=REPEAT reason=HISTORY_UNAVAILABLE"
            );
        }

        return new RiskTagDetectionResult(
                tag.code(),
                normalizedState,
                tag.confidence(),
                normalizedSeverity,
                normalizedRepeatCount,
                List.copyOf(supportedSpans),
                tag.reason()
        );
    }

    private static void validateSeverity(
            RiskTagDetectionResult tag,
            boolean extendedSchema
    ) {
        Integer severity = tag.severity();
        Integer repeatCount = tag.repeatCount();
        if (!extendedSchema) {
            return;
        }
        if (tag.state() == RiskTagState.PRESENT) {
            if (severity == null || severity < 1 || severity > 3
                    || !validRepeatCount(repeatCount)) {
                throw invalid();
            }
            return;
        }
        if (tag.state() == RiskTagState.ABSENT) {
            if (!Integer.valueOf(0).equals(severity)
                    || !Integer.valueOf(0).equals(repeatCount)) {
                throw invalid();
            }
            return;
        }
        if (severity != null || repeatCount != null) {
            throw invalid();
        }
    }

    private static boolean validRepeatCount(Integer repeatCount) {
        return repeatCount != null
                && repeatCount >= 0
                && repeatCount <= MAX_REPEAT_COUNT;
    }

    private static boolean isSupportedEvidence(EvidenceSpan span, String maskedContent) {
        if (span == null || span.text() == null || span.text().isBlank()
                || span.text().length() > MAX_EVIDENCE_LENGTH) {
            throw invalid();
        }
        if (span.start() != null) {
            if (span.end() == null
                    || span.start() < 0
                    || span.start() > span.end()
                    || span.end() > maskedContent.length()) {
                throw invalid();
            }
            return maskedContent.substring(span.start(), span.end()).equals(span.text());
        }
        return maskedContent.contains(span.text());
    }

    private static AiResponseInvalidException invalid() {
        return new AiResponseInvalidException("AI 위험 분석 응답 형식이 올바르지 않습니다.");
    }
}
