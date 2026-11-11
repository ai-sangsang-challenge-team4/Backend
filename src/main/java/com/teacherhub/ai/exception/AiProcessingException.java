package com.teacherhub.ai.exception;

/** 외부 AI 장애 또는 신뢰할 수 없는 AI 응답을 나타냅니다. */
public abstract class AiProcessingException extends RuntimeException {

    protected AiProcessingException(String message) {
        super(message);
    }

    protected AiProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
