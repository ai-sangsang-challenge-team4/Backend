package com.teacherhub.complaint.mediation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record MediationPackageCreateRequest(
        @Schema(description = "원문 포함 여부. false이면 마스킹본만 저장합니다.", defaultValue = "false")
        boolean includeOriginalMessages
) {
}
