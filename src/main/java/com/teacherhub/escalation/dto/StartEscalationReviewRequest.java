package com.teacherhub.escalation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record StartEscalationReviewRequest(
        @NotNull(message = "공유 건 버전이 필요합니다.")
        @PositiveOrZero(message = "공유 건 버전은 0 이상이어야 합니다.")
        Long version
) {
}
