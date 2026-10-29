package com.teacherhub.user.consent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

@Schema(description = "현재 정책 전체 동의 요청")
public record PolicyConsentRequest(
        @NotNull(message = "전체 동의 여부가 필요합니다.")
        @AssertTrue(message = "전체 필수 항목에 동의해야 저장할 수 있습니다.")
        @Schema(description = "전체 필수 항목 동의 여부", example = "true")
        Boolean allAgreed
) {
}
