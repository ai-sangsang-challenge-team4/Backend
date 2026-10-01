package com.teacherhub.complaint.exception;

public class ComplaintNotDraftException extends RuntimeException {
    public ComplaintNotDraftException(String message) {
        super(message);
    }
}
