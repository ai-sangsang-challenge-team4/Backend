package com.teacherhub.escalation.exception;

public class EscalationNotFoundException extends RuntimeException {
    public EscalationNotFoundException(String message) {
        super(message);
    }
}
