package com.teacherhub.complaint.query.dto;

import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintStatus;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.enums.RiskLevel;

import java.time.LocalDateTime;

public record ComplaintListResponse(
        Long complaintId,
        Long studentId,
        String studentName,
        ComplaintStatus status,
        Long riskAnalysisId,
        RiskLevel riskLevel,
        LocalDateTime createdAt
) {
    public static ComplaintListResponse from(Complaint complaint, RiskAnalysis analysis) {
        return new ComplaintListResponse(
                complaint.getId(),
                complaint.getStudent().getId(),
                complaint.getStudent().getName(),
                complaint.getStatus(),
                analysis == null ? null : analysis.getId(),
                analysis == null ? null : analysis.getRiskLevel(),
                complaint.getCreatedAt()
        );
    }
}
