package com.teacherhub.escalation.exception;

public class AdminApprovalRequiredException extends RuntimeException {
    public AdminApprovalRequiredException(String message) {
        super(message);
    }
}
