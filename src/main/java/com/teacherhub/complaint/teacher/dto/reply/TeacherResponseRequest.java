package com.teacherhub.complaint.teacher.dto.reply;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 교사 답변 요청
@Schema(description = "교사 답변 저장 또는 제출 요청")
public record TeacherResponseRequest(
        @Schema(description = "교사 답변 내용(최대 1500자)", example = "확인 후 상담 가능한 일정을 안내드리겠습니다.")
        @NotBlank(message = "답변 내용을 입력해주세요.")
        @Size(max = 1500, message = "답변은 1500자 이하로 입력해주세요.")
        String content
) {
}
