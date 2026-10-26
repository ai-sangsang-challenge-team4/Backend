package com.teacherhub.complaint.exception;

public class MessageDraftNotFoundException extends RuntimeException {
    public MessageDraftNotFoundException(String message) {
        super(message);
    }
}
