package com.teacherhub.common.exception;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(name = "ErrorResponse", description = "모든 API에서 공통으로 사용하는 오류 응답")
public record ErrorResponse(
        @Schema(description = "서버에서 정의한 오류 코드", example = "COMPLAINT_NOT_FOUND")
        String code,
        @Schema(description = "사용자 또는 개발자가 확인할 수 있는 오류 메시지", example = "민원을 찾을 수 없습니다.")
        String message,
        @Schema(description = "오류 발생 시각", example = "2026-10-28T12:18:23.227")
        LocalDateTime timestamp,
        @Schema(description = "오류가 발생한 API 경로", example = "/complaints/42")
        String path
) {
    public static ErrorResponse of(String code, String message, String path) {
        return new ErrorResponse(code, message, LocalDateTime.now(), path);
    }
}
