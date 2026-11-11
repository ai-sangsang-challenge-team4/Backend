package com.teacherhub.risk.analyzer;

import com.teacherhub.ai.exception.AiResponseInvalidException;
import com.teacherhub.risk.dto.detection.EvidenceSpan;
import com.teacherhub.risk.dto.detection.RiskTagDetectionResult;
import com.teacherhub.risk.dto.llm.LlmRiskAnalysisResult;
import com.teacherhub.risk.enums.RiskTagCode;
import com.teacherhub.risk.enums.RiskTagState;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** LLM 출력을 저장하거나 점수 계산에 사용하기 전 검증하는 결정론적 경계입니다. */
public final class LlmRiskOutputValidator {

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

        List<RiskTagDetectionResult> tags = result.tags();
        if (tags.size() > RiskTagCode.values().length) {
            throw invalid();
        }

        boolean extendedSchema = result.safetyFlags() != null
                || tags.stream().anyMatch(tag -> tag != null
                && (tag.severity() != null || tag.repeatCount() != null));
        if (extendedSchema && (result.safetyFlags() == null
                || tags.size() != RiskTagCode.values().length
                || tags.stream().anyMatch(tag -> tag == null || tag.repeatCount() == null))) {
            throw invalid();
        }

        Set<RiskTagCode> uniqueCodes = new HashSet<>();
        for (RiskTagDetectionResult tag : tags) {
            validateTag(tag, maskedContent, uniqueCodes);
        }
        return result;
    }

    private static void validateTag(
            RiskTagDetectionResult tag,
            String maskedContent,
            Set<RiskTagCode> uniqueCodes
    ) {
        if (tag == null || tag.code() == null || tag.state() == null
                || !uniqueCodes.add(tag.code())) {
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

        validateSeverity(tag);

        List<EvidenceSpan> spans = tag.evidenceSpans();
        if (spans == null || spans.size() > MAX_EVIDENCE_SPANS_PER_TAG) {
            throw invalid();
        }
        for (EvidenceSpan span : spans) {
            validateEvidence(span, maskedContent);
        }
    }

    private static void validateSeverity(RiskTagDetectionResult tag) {
        Integer severity = tag.severity();
        Integer repeatCount = tag.repeatCount();
        boolean extendedTag = severity != null || repeatCount != null;
        if (!extendedTag) {
            return;
        }
        if (repeatCount == null || repeatCount < 0 || repeatCount > MAX_REPEAT_COUNT) {
            throw invalid();
        }
        if (tag.state() == RiskTagState.PRESENT) {
            if (severity == null || severity < 1 || severity > 3) {
                throw invalid();
            }
            return;
        }
        if (tag.state() == RiskTagState.ABSENT) {
            if (!Integer.valueOf(0).equals(severity)) {
                throw invalid();
            }
            return;
        }
        if (severity != null) {
            throw invalid();
        }
    }

    private static void validateEvidence(EvidenceSpan span, String maskedContent) {
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
            if (!maskedContent.substring(span.start(), span.end()).equals(span.text())) {
                throw invalid();
            }
        }
    }

    private static AiResponseInvalidException invalid() {
        return new AiResponseInvalidException("AI 위험 분석 응답 형식이 올바르지 않습니다.");
    }
}
