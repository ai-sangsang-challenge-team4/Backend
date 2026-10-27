package com.teacherhub.escalation.exception;

public class EscalationVersionConflictException extends RuntimeException {
    public EscalationVersionConflictException(String message) {
        super(message);
    }
}
