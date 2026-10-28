package com.teacherhub.risk.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Emergency 위험도 재검토 요청")
public record EmergencyReviewCreateRequest(
        @Min(value = 1, message = "위험 점수는 1점 이상이어야 합니다.")
        @Max(value = 6, message = "Emergency 재검토 요청 점수는 6점 이하여야 합니다.")
        int riskScore,

        @NotBlank(message = "Emergency 재검토 사유가 필요합니다.")
        @Size(max = 300, message = "재검토 사유는 300자 이하로 입력해주세요.")
        String reason,

        @Schema(description = "지정 검토자 ID. 생략하면 공유 담당 관리자 또는 기본 관리자를 지정합니다.")
        Long reviewerId
) {
}
