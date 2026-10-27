package com.teacherhub.escalation.exception;

public class EscalationReasonRequiredException extends RuntimeException {
    public EscalationReasonRequiredException(String message) {
        super(message);
    }
}
