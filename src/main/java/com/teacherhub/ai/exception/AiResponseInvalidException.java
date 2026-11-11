package com.teacherhub.ai.exception;

public class AiResponseInvalidException extends AiProcessingException {

    public AiResponseInvalidException(String message) {
        super(message);
    }

    public AiResponseInvalidException(String message, Throwable cause) {
        super(message, cause);
    }
}
