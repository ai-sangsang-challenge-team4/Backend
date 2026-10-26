package com.teacherhub.complaint.parent.dto.complaint;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 최초 문의
public record ComplaintCreateRequest(
        @NotNull(message = "학생을 선택해주세요.")
        Long studentId,

        @NotNull(message = "문의 대상을 선택해주세요.")
        Long teacherId,

        @NotBlank(message = "민원 제목을 입력해주세요.")
        @Size(max = 100, message = "민원 제목은 100자 이하로 입력해주세요.")
        String title,

        @NotBlank(message = "민원 내용을 입력해주세요.")
        String content
) {
}
