package com.teacherhub.complaint.parent.dto.complaint;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


@Schema(description = "민원 초안 수정 요청")
public record ComplaintDraftUpdateRequest(
        @Schema(description = "수정할 민원 원문(최대 5000자)", example = "상담 가능한 날짜와 시간을 알려주세요.")
        @NotBlank(message = "민원 내용을 입력해주세요.")
        @Size(max = 5_000, message = "민원 내용은 5000자 이하로 입력해주세요.")
        String content
) {
}
