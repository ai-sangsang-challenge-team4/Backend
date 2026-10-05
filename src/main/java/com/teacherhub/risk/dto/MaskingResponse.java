package com.teacherhub.risk.dto;

// 민감 정보 마스킹 결과
public record MaskingResponse(
        Long complaintId,
        long contentVersion,
        String maskedContent) {
}
