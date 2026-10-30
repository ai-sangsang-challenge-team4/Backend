package com.teacherhub.complaint.parent.dto.message;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "학부모 추가 문의 전송 요청")
public record ParentMessageSendRequest(
        @NotNull(message = "전송할 내용 버전이 필요합니다.")
        @Schema(description = "마지막으로 확인한 초안의 내용 버전", example = "2")
        Long expectedContentVersion
) {
}
