package com.teacherhub.complaint.query.dto;

import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintStatus;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.school.entity.SchoolClass;
import com.teacherhub.user.enums.UserRole;

import java.time.LocalDateTime;

public record ComplaintListResponse(
        Long complaintId,
        Long studentId,
        String studentName,
        String counterpartName,
        UserRole counterpartRole,
        Integer academicYear,
        Integer grade,
        Integer classNo,
        ComplaintStatus status,
        Long riskAnalysisId,
        RiskLevel riskLevel,
        String aiSummary,
        LocalDateTime latestReceivedAt
) {
    public static ComplaintListResponse from(
            Complaint complaint,
            RiskAnalysis analysis,
            SchoolClass schoolClass,
            String aiSummary,
            LocalDateTime latestReceivedAt,
            UserRole viewerRole
    ) {
        String counterpartName = viewerRole == UserRole.PARENT
                ? complaint.getTeacher() == null ? null : complaint.getTeacher().getUser().getName()
                : complaint.getParent().getUser().getName();
        UserRole counterpartRole = viewerRole == UserRole.PARENT ? UserRole.TEACHER : UserRole.PARENT;

        return new ComplaintListResponse(
                complaint.getId(),
                complaint.getStudent().getId(),
                complaint.getStudent().getName(),
                counterpartName,
                counterpartRole,
                schoolClass == null ? null : schoolClass.getAcademicYear(),
                schoolClass == null ? null : schoolClass.getGrade(),
                schoolClass == null ? null : schoolClass.getClassNo(),
                complaint.getStatus(),
                analysis == null ? null : analysis.getId(),
                analysis == null ? null : analysis.getRiskLevel(),
                aiSummary,
                latestReceivedAt
        );
    }
}
