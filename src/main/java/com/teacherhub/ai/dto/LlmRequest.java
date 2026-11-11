package com.teacherhub.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "LLM 분석 요청")
public record LlmRequest(
        @Schema(description = "분석할 텍스트(최대 5000자)", example = "상담 일정과 관련해 확인을 요청드립니다.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "분석할 텍스트를 입력해주세요.")
        @Size(max = 5_000, message = "분석할 텍스트는 5000자 이하로 입력해주세요.")
        String content
) {
}
