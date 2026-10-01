package com.teacherhub.complaint.exception;

public class EmptyComplaintContentException extends RuntimeException {
    public EmptyComplaintContentException(String message) {
        super(message);
    }
}
