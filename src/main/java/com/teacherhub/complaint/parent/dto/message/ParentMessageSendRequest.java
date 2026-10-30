package com.teacherhub.complaint.parent.dto.message;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "학부모 추가 문의 전송 요청")
public record ParentMessageSendRequest(
        @Schema(
                description = "마지막으로 확인한 초안의 내용 버전. 누락 시 CONTENT_VERSION_CONFLICT를 반환합니다.",
                example = "2",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Long expectedContentVersion
) {
}
