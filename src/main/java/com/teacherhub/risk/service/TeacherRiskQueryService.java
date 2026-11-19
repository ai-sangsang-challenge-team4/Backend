package com.teacherhub.risk.service;

import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.complaint.access.ComplaintAccessPolicy;
import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.enums.MessageStatus;
import com.teacherhub.complaint.exception.ComplaintAccessDeniedException;
import com.teacherhub.complaint.exception.ComplaintNotFoundException;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.risk.dto.response.teacher.TeacherRiskResponse;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.repository.ComplaintRiskTagRepository;
import com.teacherhub.risk.repository.EmergencyRiskReviewRequestRepository;
import com.teacherhub.risk.repository.RiskAnalysisRepository;
import com.teacherhub.risk.repository.RiskReviewRepository;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeacherRiskQueryService {

    private final ComplaintRepository complaintRepository;
    private final RiskAnalysisRepository riskAnalysisRepository;
    private final RiskReviewRepository riskReviewRepository;
    private final EmergencyRiskReviewRequestRepository emergencyReviewRequestRepository;
    private final ComplaintRiskTagRepository complaintRiskTagRepository;
    private final UserRepository userRepository;
    private final ComplaintAccessPolicy complaintAccessPolicy;

    public TeacherRiskResponse findRisk(Long userId, Long complaintId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("사용자를 찾을 수 없습니다."));
        Complaint complaint = complaintRepository.findDetailById(complaintId)
                .orElseThrow(() -> new ComplaintNotFoundException("민원을 찾을 수 없습니다."));
        complaintAccessPolicy.validateAssignedTeacher(user, complaint);
        if (complaint.getStatus() == ComplaintStatus.DRAFT) {
            throw new ComplaintAccessDeniedException("제출되지 않은 민원은 교사가 조회할 수 없습니다.");
        }

        RiskAnalysis analysis = riskAnalysisRepository
                .findFirstByComplaintIdAndCompletedTrueAndMessage_StatusNotOrderByIdDesc(
                        complaintId,
                        MessageStatus.DRAFT
                )
                .orElse(null);
        boolean safetyLock = emergencyReviewRequestRepository
                .findFirstByComplaintIdOrderByRequestedAtDescIdDesc(complaintId)
                .map(com.teacherhub.risk.entity.EmergencyRiskReviewRequest::isSafetyLock)
                .orElse(false);

        if (analysis == null) {
            return TeacherRiskResponse.unavailable(complaintId, safetyLock);
        }

        return TeacherRiskResponse.from(
                analysis,
                complaintRiskTagRepository.findByRiskAnalysisId(analysis.getId()),
                riskReviewRepository.findFirstByRiskAnalysisIdOrderByReviewedAtDescIdDesc(analysis.getId())
                        .orElse(null),
                safetyLock
        );
    }
}
