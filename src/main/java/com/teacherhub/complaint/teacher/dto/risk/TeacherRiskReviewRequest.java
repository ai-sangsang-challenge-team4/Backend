package com.teacherhub.complaint.teacher.dto.risk;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

// 교사가 검토한 점수와 사유
@Schema(description = "교사 위험도 검토 요청")
public record TeacherRiskReviewRequest(
        @Schema(description = "교사 검토 선택값(1~7). 서버에서 10을 곱해 위험 점수 10~70으로 환산합니다.", example = "5", minimum = "1", maximum = "7")
        @Min(value = 1, message = "위험 점수는 1점 이상이어야 합니다.")
        @Max(value = 7, message = "위험 점수는 7점 이하여야 합니다.")
        int riskScore,

        @Schema(description = "검토 사유. 위험도 상향·하향 변경 시 필수", example = "문맥상 실제 위협 의도는 낮다고 판단했습니다.", nullable = true)
        @Size(max = 300, message = "검토 사유는 300자 이하로 입력해주세요.")
        String reason
) {
}
