package com.teacherhub.complaint.teacher.dto.query;

import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintStatus;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.entity.RiskReview;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.school.entity.SchoolClass;

import java.time.LocalDateTime;

// 교사 화면 상단에서 모든 탭이 공유하는 민원 상세 정보
public record TeacherComplaintDetailResponse(
        Long complaintId,
        String studentName,
        String parentName,
        Integer academicYear,
        Integer grade,
        Integer classNo,
        ComplaintStatus status,
        LocalDateTime submittedAt,
        RiskLevel riskLevel
) {
    public static TeacherComplaintDetailResponse from(
            Complaint complaint,
            SchoolClass schoolClass,
            LocalDateTime submittedAt,
            RiskAnalysis analysis,
            RiskReview latestReview
    ) {
        return new TeacherComplaintDetailResponse(
                complaint.getId(),
                complaint.getStudent().getName(),
                complaint.getParent().getUser().getName(),
                schoolClass.getAcademicYear(),
                schoolClass.getGrade(),
                schoolClass.getClassNo(),
                complaint.getStatus(),
                submittedAt,
                latestReview == null ? analysis.getRiskLevel() : latestReview.getReviewedLevel()
        );
    }
}
