package com.teacherhub.complaint.exception;

public class AnalysisRequiredException extends RuntimeException {
    public AnalysisRequiredException() {
        super("최초 AI 분석과 수정안 확인을 완료한 후 제출해주세요.");
    }
}
