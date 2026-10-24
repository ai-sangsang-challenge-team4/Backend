package com.teacherhub.complaint.exception;

public class ComplaintEditLimitException extends RuntimeException {
    public ComplaintEditLimitException() {
        super("민원 내용은 1회만 수정할 수 있습니다.");
    }
}
