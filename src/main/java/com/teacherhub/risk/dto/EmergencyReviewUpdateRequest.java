package com.teacherhub.risk.dto;

import com.teacherhub.risk.enums.RiskReviewStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Emergency 재검토 결정 및 안전 잠금 변경 요청")
public record EmergencyReviewUpdateRequest(
        @Schema(
                description = "검토자 결정. 결정을 변경하지 않을 때는 생략",
                example = "APPLIED",
                allowableValues = {"APPLIED", "REJECTED"},
                nullable = true
        )
        RiskReviewStatus status,

        @Schema(
                description = "안전 잠금 상태. 잠금을 변경하지 않을 때는 생략",
                example = "false",
                nullable = true
        )
        Boolean safetyLock,

        @Schema(
                description = "결정 또는 잠금 변경 사유. 선택 입력",
                example = "검토 결과 위험도 변경을 승인합니다.",
                nullable = true
        )
        @Size(max = 500, message = "검토자 메시지는 500자 이하로 입력해주세요.")
        String message
) {
}
