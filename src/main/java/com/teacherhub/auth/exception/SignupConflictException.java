package com.teacherhub.auth.exception;

public class SignupConflictException extends RuntimeException {
    public SignupConflictException(String message) {
        super(message);
    }
}
