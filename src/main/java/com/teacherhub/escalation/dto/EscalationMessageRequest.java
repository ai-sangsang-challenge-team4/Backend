package com.teacherhub.escalation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 관리자 검토 단계에서 남기는 메시지
@Schema(description = "관리자 검토 메시지 요청")
public record EscalationMessageRequest(
        @Schema(description = "검토 메모 또는 처리 결과(최대 500자)", example = "학생 안전 조치를 확인했습니다.")
        @NotBlank(message = "관리자 메시지를 입력해주세요.")
        @Size(max = 500, message = "관리자 메시지는 500자 이하로 입력해주세요.")
        String message
) {
}
