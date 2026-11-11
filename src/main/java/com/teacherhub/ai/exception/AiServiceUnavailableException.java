package com.teacherhub.ai.exception;

public class AiServiceUnavailableException extends AiProcessingException {

    public AiServiceUnavailableException(String message) {
        super(message);
    }

    public AiServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
