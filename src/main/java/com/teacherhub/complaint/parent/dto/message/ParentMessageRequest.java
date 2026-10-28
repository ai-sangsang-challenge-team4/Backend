package com.teacherhub.complaint.parent.dto.message;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "학부모 추가 문의 요청")
public record ParentMessageRequest(
        @Schema(description = "추가 문의 내용", example = "답변 감사합니다. 상담 시간도 확인 부탁드립니다.")
        @NotBlank(message = "추가 문의 내용을 입력해주세요.")
        String content
) {
}
