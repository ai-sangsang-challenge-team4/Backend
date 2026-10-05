package com.teacherhub.complaint.exception;

public class FinalAnalysisOnSubmitException extends RuntimeException {
    public FinalAnalysisOnSubmitException() {
        super("수정된 내용은 최종 제출 시 서버에서 재분석합니다. 제출 API를 호출해주세요.");
    }
}
