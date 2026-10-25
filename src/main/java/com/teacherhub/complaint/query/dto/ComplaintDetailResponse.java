package com.teacherhub.complaint.query.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.enums.WaitingFor;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.entity.RiskReview;
import com.teacherhub.risk.enums.RiskLevel;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ComplaintDetailResponse(
        Long complaintId,
        Long studentId,
        String studentName,
        String parentName,
        String teacherName,
        String content,
        ComplaintStatus status,
        WaitingFor waitingFor,
        RiskLevel riskLevel,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ComplaintDetailResponse from(
            Complaint complaint,
            RiskAnalysis analysis,
            RiskReview latestReview,
            boolean includeContent
    ) {
        return new ComplaintDetailResponse(
                complaint.getId(),
                complaint.getStudent().getId(),
                complaint.getStudent().getName(),
                complaint.getParent().getUser().getName(),
                complaint.getTeacher() == null ? null : complaint.getTeacher().getUser().getName(),
                includeContent ? complaint.getContent() : null,
                complaint.getStatus(),
                complaint.getWaitingFor(),
                analysis == null
                        ? null
                        : latestReview == null
                                ? analysis.getRiskLevel()
                                : latestReview.getReviewedLevel(),
                complaint.getCreatedAt(),
                complaint.getUpdatedAt()
        );
    }
}
