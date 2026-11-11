package com.teacherhub.common.exception;

/** 업무 상태 충돌과 구분해야 하는 백엔드 내부 오류입니다. */
public class InternalSystemException extends RuntimeException {

    public InternalSystemException(String message) {
        super(message);
    }

    public InternalSystemException(String message, Throwable cause) {
        super(message, cause);
    }
}
