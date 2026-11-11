package com.teacherhub.ai.exception;

/** AI 요청 직렬화·설정처럼 백엔드 내부에서 발생한 통합 오류입니다. */
public class AiRequestPreparationException extends RuntimeException {

    public AiRequestPreparationException(String message, Throwable cause) {
        super(message, cause);
    }

    public AiRequestPreparationException(String message) {
        super(message);
    }
}
