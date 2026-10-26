package com.teacherhub.escalation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CompleteEscalationRequest(
        @NotNull(message = "공유 건 버전이 필요합니다.")
        @PositiveOrZero(message = "공유 건 버전은 0 이상이어야 합니다.")
        Long version,

        @NotBlank(message = "검토 결과를 입력해주세요.")
        @Size(max = 2000, message = "검토 결과는 2000자 이하로 입력해주세요.")
        String result
) {
}
