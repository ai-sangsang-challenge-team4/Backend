package com.teacherhub.complaint.query.dto;

import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.risk.enums.RiskLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

// 쿼리 검색
@Schema(description = "민원 목록 조회 조건")
public record ComplaintListQuery(
        @Schema(description = "0부터 시작하는 페이지 번호", example = "0", defaultValue = "0")
        @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.")
        @Max(value = 214_748_364, message = "페이지 번호가 허용 범위를 초과했습니다.")
        Integer page,

        @Schema(description = "제목·내용 검색어", example = "상담", nullable = true)
        @Size(max = 100, message = "검색어는 100자 이하로 입력해주세요.")
        String keyword,

        @Schema(description = "민원 상태 필터", example = "OPEN", nullable = true)
        ComplaintStatus status,
        @Schema(description = "위험 등급 필터", example = "HIGH", nullable = true)
        RiskLevel riskLevel,
        @Schema(description = "정렬 방식", example = "LATEST", defaultValue = "LATEST")
        ComplaintListSort sort
) {
    public static final int PAGE_SIZE = 10;

    public static ComplaintListQuery defaults() {
        return new ComplaintListQuery(0, null, null, null, ComplaintListSort.LATEST);
    }

    public int resolvedPage() {
        return page == null ? 0 : page;
    }

    public String normalizedKeyword() {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    public ComplaintListSort resolvedSort() {
        return sort == null ? ComplaintListSort.LATEST : sort;
    }
}
