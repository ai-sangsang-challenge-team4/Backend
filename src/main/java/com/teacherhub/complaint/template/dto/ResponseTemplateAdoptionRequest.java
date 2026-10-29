package com.teacherhub.complaint.template.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record ResponseTemplateAdoptionRequest(
        @NotNull(message = "템플릿 변수 입력이 필요합니다.")
        Map<String, String> variables,

        @NotBlank(message = "교사가 확인한 최종 답변이 필요합니다.")
        @Size(max = 1500, message = "답변은 1500자 이하로 입력해주세요.")
        String finalContent
) {
}
