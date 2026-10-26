package com.teacherhub.complaint.teacher.dto.risk;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

// 교사가 검토한 점수와 사유
public record TeacherRiskReviewRequest(
        @Min(value = 1, message = "위험 점수는 1점 이상이어야 합니다.")
        @Max(value = 7, message = "위험 점수는 7점 이하여야 합니다.")
        int riskScore,

        @Size(max = 300, message = "검토 사유는 300자 이하로 입력해주세요.")
        String reason
) {
}
