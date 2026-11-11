package com.teacherhub.risk.exception;

import java.util.Objects;

/** 위험 분석의 내부 안전 단계 실패를 외부 AI 장애와 구분합니다. */
public final class RiskAnalysisProcessingException extends RuntimeException {

    public enum FailureStage {
        PRIVACY_MASKING(
                "PRIVACY_MASKING_FAILED",
                "개인정보 보호 처리에 실패하여 위험 분석을 완료하지 못했습니다."
        ),
        RULE_PROCESSING(
                "RISK_RULE_PROCESSING_FAILED",
                "위험 규칙 분석에 실패하여 위험 분석을 완료하지 못했습니다."
        ),
        SCORE_CALCULATION(
                "RISK_SCORE_CALCULATION_FAILED",
                "위험 점수 계산에 실패하여 위험 분석을 완료하지 못했습니다."
        ),
        ANALYSIS_SAVE(
                "RISK_ANALYSIS_SAVE_FAILED",
                "위험 분석 결과 저장에 실패하여 요청을 완료하지 못했습니다."
        );

        private final String errorCode;
        private final String userMessage;

        FailureStage(String errorCode, String userMessage) {
            this.errorCode = errorCode;
            this.userMessage = userMessage;
        }
    }

    private final FailureStage failureStage;

    private RiskAnalysisProcessingException(
            FailureStage failureStage,
            Throwable cause
    ) {
        super(Objects.requireNonNull(failureStage, "실패 단계가 필요합니다.").userMessage, cause);
        this.failureStage = failureStage;
    }

    public static RiskAnalysisProcessingException privacyMasking(Throwable cause) {
        return new RiskAnalysisProcessingException(
                FailureStage.PRIVACY_MASKING,
                cause
        );
    }

    public static RiskAnalysisProcessingException ruleProcessing(Throwable cause) {
        return new RiskAnalysisProcessingException(
                FailureStage.RULE_PROCESSING,
                cause
        );
    }

    public static RiskAnalysisProcessingException scoreCalculation(Throwable cause) {
        return new RiskAnalysisProcessingException(
                FailureStage.SCORE_CALCULATION,
                cause
        );
    }

    public static RiskAnalysisProcessingException analysisSave(Throwable cause) {
        return new RiskAnalysisProcessingException(
                FailureStage.ANALYSIS_SAVE,
                cause
        );
    }

    public FailureStage failureStage() {
        return failureStage;
    }

    public String errorCode() {
        return failureStage.errorCode;
    }

    public String userMessage() {
        return failureStage.userMessage;
    }
}
