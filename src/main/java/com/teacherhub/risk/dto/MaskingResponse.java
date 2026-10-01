package com.teacherhub.risk.dto;

public record MaskingResponse(Long complaintId, long contentVersion, String maskedContent) {
}
