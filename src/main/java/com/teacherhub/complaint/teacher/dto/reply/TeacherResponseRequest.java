package com.teacherhub.complaint.teacher.dto.reply;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 교사 답변 요청
public record TeacherResponseRequest(
        @NotBlank(message = "답변 내용을 입력해주세요.")
        @Size(max = 1500, message = "답변은 1500자 이하로 입력해주세요.")
        String content,
        Long aiDraftId
) {
}
