package com.teacherhub.complaint.query.dto;

import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.risk.enums.RiskLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

// 쿼리 검색
public record ComplaintListQuery(
        @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.")
        @Max(value = 214_748_364, message = "페이지 번호가 허용 범위를 초과했습니다.")
        Integer page,

        @Size(max = 100, message = "검색어는 100자 이하로 입력해주세요.")
        String keyword,

        ComplaintStatus status,
        RiskLevel riskLevel,
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
