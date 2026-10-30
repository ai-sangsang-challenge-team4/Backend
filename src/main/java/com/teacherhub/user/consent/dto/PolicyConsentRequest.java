package com.teacherhub.user.consent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

@Schema(description = "현재 정책 동의 요청")
public record PolicyConsentRequest(
        @NotNull(message = "필수 정책 동의 여부가 필요합니다.")
        @AssertTrue(message = "필수 정책에 동의해야 합니다.")
        @Schema(description = "이용약관·개인정보 처리 등 필수 정책 동의 여부", example = "true")
        Boolean requiredAgreed,

        @NotNull(message = "선택 정책 동의 여부가 필요합니다.")
        @Schema(description = "마케팅·알림 등 선택 정책 동의 여부", example = "false")
        Boolean optionalAgreed
) {
}
