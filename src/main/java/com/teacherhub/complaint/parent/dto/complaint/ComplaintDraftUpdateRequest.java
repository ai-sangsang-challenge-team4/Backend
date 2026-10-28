package com.teacherhub.complaint.parent.dto.complaint;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;


@Schema(description = "민원 초안 수정 요청")
public record ComplaintDraftUpdateRequest(
        @Schema(description = "수정할 민원 원문", example = "상담 가능한 날짜와 시간을 알려주세요.")
        @NotBlank(message = "민원 내용을 입력해주세요.")
        String content
) {
}
