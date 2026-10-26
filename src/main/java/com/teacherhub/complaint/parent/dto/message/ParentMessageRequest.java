package com.teacherhub.complaint.parent.dto.message;

import jakarta.validation.constraints.NotBlank;

public record ParentMessageRequest(
        @NotBlank(message = "추가 문의 내용을 입력해주세요.")
        String content
) {
}
