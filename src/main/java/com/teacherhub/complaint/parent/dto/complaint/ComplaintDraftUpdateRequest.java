package com.teacherhub.complaint.parent.dto.complaint;

import jakarta.validation.constraints.NotBlank;


public record ComplaintDraftUpdateRequest(
        @NotBlank(message = "민원 내용을 입력해주세요.")
        String content
) {
}
