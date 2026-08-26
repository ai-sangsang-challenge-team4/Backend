package com.teacherhub.common.exception;

import java.time.OffsetDateTime;

public record ErrorResponse(
        String status,
        String code,
        String message,
        OffsetDateTime timestamp,
        String path
) {
}