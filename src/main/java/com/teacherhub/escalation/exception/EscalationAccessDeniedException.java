package com.teacherhub.escalation.exception;

public class EscalationAccessDeniedException extends RuntimeException {
    public EscalationAccessDeniedException(String message) {
        super(message);
    }
}
