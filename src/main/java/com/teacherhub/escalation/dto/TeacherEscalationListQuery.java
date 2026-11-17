package com.teacherhub.escalation.dto;

import com.teacherhub.escalation.enums.EscalationStatus;
import com.teacherhub.risk.enums.RiskLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@Schema(description = "교사 관리자 공유 내역 조회 조건")
public record TeacherEscalationListQuery(
        @Schema(description = "0부터 시작하는 페이지 번호", example = "0", defaultValue = "0")
        @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.")
        @Max(value = 214_748_364, message = "페이지 번호가 허용 범위를 초과했습니다.")
        Integer page,

        @Schema(description = "민원 제목·학생명 검색어", example = "생활지도", nullable = true)
        @Size(max = 100, message = "검색어는 100자 이하로 입력해주세요.")
        String keyword,

        @Schema(description = "공유 처리 상태", example = "UNDER_REVIEW", nullable = true)
        EscalationStatus status,

        @Schema(description = "현재 위험 등급", example = "EMERGENCY", nullable = true)
        RiskLevel riskLevel
) {
    public static final int PAGE_SIZE = 10;

    public int resolvedPage() {
        return page == null ? 0 : page;
    }

    public String normalizedKeyword() {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }
}
