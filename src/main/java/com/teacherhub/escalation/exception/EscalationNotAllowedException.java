package com.teacherhub.escalation.exception;

public class EscalationNotAllowedException extends RuntimeException {
    public EscalationNotAllowedException(String message) {
        super(message);
    }
}
