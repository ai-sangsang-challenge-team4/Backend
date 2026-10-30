package com.teacherhub.complaint.parent.dto.message;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "학부모 추가 문의 요청")
public record ParentMessageRequest(
        @Schema(description = "추가 문의 내용", example = "답변 감사합니다. 상담 시간도 확인 부탁드립니다.")
        @NotBlank(message = "추가 문의 내용을 입력해주세요.")
        String content,

        @Schema(
                description = "조회 또는 직전 저장 응답에서 받은 내용 버전. 기존 초안을 수정할 때 필수이며 최초 저장은 생략할 수 있습니다.",
                example = "2",
                nullable = true
        )
        Long expectedContentVersion
) {
}
