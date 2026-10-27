package com.teacherhub.escalation.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.teacherhub.escalation.enums.EscalationScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

// 공유 요청
public record ShareEscalationRequest(
        Long adminId,

        @JsonAlias("scope")
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        Set<EscalationScope> scopes,

        @NotBlank(message = "공유 사유를 입력해주세요.")
        @Size(max = 500, message = "공유 사유는 500자 이하로 입력해주세요.")
        String reason
) {
}
