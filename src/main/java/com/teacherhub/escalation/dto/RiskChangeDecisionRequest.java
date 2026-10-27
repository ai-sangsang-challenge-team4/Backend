package com.teacherhub.escalation.dto;

import com.teacherhub.escalation.enums.RiskReviewStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// EMERGENCY 위험도 변경 요청에 대한 관리자 결정
public record RiskChangeDecisionRequest(
        @NotNull(message = "위험도 변경 결정 상태가 필요합니다.")
        RiskReviewStatus status,

        @NotBlank(message = "관리자 메시지를 입력해주세요.")
        @Size(max = 500, message = "관리자 메시지는 500자 이하로 입력해주세요.")
        String message
) {
}
