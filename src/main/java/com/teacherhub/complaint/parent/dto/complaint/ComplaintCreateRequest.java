package com.teacherhub.complaint.parent.dto.complaint;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 최초 문의
@Schema(description = "민원 초안 생성 요청")
public record ComplaintCreateRequest(
        @Schema(description = "GET /users/me 응답의 students 목록에서 선택한 자녀의 studentId", example = "10")
        @NotNull(message = "학생을 선택해주세요.")
        Long studentId,

        @Schema(description = "문의 대상 교사 ID", example = "20")
        @NotNull(message = "문의 대상을 선택해주세요.")
        Long teacherId,

        @Schema(description = "민원 제목(최대 100자)", example = "상담 일정 확인 요청")
        @NotBlank(message = "민원 제목을 입력해주세요.")
        @Size(max = 100, message = "민원 제목은 100자 이하로 입력해주세요.")
        String title,

        @Schema(description = "민원 원문", example = "학생 상담 일정과 진행 절차를 확인하고 싶습니다.")
        @NotBlank(message = "민원 내용을 입력해주세요.")
        String content
) {
}
