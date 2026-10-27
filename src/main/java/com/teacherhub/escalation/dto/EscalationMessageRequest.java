package com.teacherhub.escalation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 관리자 검토 단계에서 남기는 메시지
public record EscalationMessageRequest(
        @NotBlank(message = "관리자 메시지를 입력해주세요.")
        @Size(max = 500, message = "관리자 메시지는 500자 이하로 입력해주세요.")
        String message
) {
}
