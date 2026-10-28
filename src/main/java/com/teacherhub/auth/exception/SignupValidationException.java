package com.teacherhub.auth.exception;

public class SignupValidationException extends RuntimeException {
    public SignupValidationException(String message) {
        super(message);
    }
}
