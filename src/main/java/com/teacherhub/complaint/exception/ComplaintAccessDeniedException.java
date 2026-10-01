package com.teacherhub.complaint.exception;

public class ComplaintAccessDeniedException extends RuntimeException {
    public ComplaintAccessDeniedException(String message) {
        super(message);
    }
}
