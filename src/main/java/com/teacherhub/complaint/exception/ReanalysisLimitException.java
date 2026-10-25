package com.teacherhub.complaint.exception;

public class ReanalysisLimitException extends RuntimeException {
    public ReanalysisLimitException() {
        super("해당 메시지의 AI 분석은 최대 2회까지 가능합니다.");
    }
}
