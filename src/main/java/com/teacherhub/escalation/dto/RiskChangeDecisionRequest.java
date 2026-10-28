package com.teacherhub.escalation.dto;

import com.teacherhub.escalation.enums.RiskReviewStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// EMERGENCY 위험도 변경 요청에 대한 관리자 결정
@Schema(description = "EMERGENCY 위험도 변경 결정 요청")
public record RiskChangeDecisionRequest(
        @Schema(description = "관리자 결정. APPLIED 또는 REJECTED", example = "APPLIED",
                allowableValues = {"APPLIED", "REJECTED"})
        @NotNull(message = "위험도 변경 결정 상태가 필요합니다.")
        RiskReviewStatus status,

        @Schema(description = "결정 사유(최대 500자)", example = "검토 결과 위험도 변경을 승인합니다.")
        @NotBlank(message = "관리자 메시지를 입력해주세요.")
        @Size(max = 500, message = "관리자 메시지는 500자 이하로 입력해주세요.")
        String message
) {
}
