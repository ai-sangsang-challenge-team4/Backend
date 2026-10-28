package com.teacherhub.escalation.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.teacherhub.escalation.enums.EscalationScope;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

// 공유 요청
@Schema(description = "교사의 관리자 공유 요청")
public record ShareEscalationRequest(
        @Schema(description = "배정할 관리자 ID. 생략하면 첫 번째 관리자를 자동 배정", example = "30", nullable = true)
        Long adminId,

        @Schema(description = "관리자에게 공개할 범위. MASKING_MESSAGES는 항상 포함",
                example = "[\"MASKING_MESSAGES\", \"MEDIATION_PACKAGE\"]")
        @JsonAlias("scope")
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        Set<EscalationScope> scopes,

        @Schema(description = "관리자 공유 사유(최대 500자)", example = "학생 안전 관련 공동 검토가 필요합니다.")
        @NotBlank(message = "공유 사유를 입력해주세요.")
        @Size(max = 500, message = "공유 사유는 500자 이하로 입력해주세요.")
        String reason
) {
}
