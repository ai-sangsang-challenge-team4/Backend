package com.teacherhub.complaint.exception;

public class ReanalysisLimitException extends RuntimeException {
    public ReanalysisLimitException() {
        super("재분석은 1회만 가능합니다. 재분석 완료 후에는 내용을 수정할 수 없습니다.");
    }
}
