package com.teacherhub.complaint.teacher.service;

import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.complaint.access.ComplaintAccessPolicy;
import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintEvent;
import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.enums.WaitingFor;
import com.teacherhub.complaint.exception.ComplaintNotFoundException;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.complaint.repository.ComplaintEventRepository;
import com.teacherhub.complaint.teacher.dto.risk.TeacherRiskReviewRequest;
import com.teacherhub.complaint.teacher.dto.risk.TeacherRiskReviewResponse;
import com.teacherhub.complaint.teacher.policy.TeacherRiskScale;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.entity.RiskReview;
import com.teacherhub.risk.dto.EmergencyReviewCreateRequest;
import com.teacherhub.risk.dto.EmergencyReviewResponse;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.enums.RiskReviewDirection;
import com.teacherhub.risk.exception.RiskAnalysisNotFoundException;
import com.teacherhub.risk.exception.RiskReviewNotAllowedException;
import com.teacherhub.risk.exception.RiskReviewReasonRequiredException;
import com.teacherhub.risk.repository.RiskAnalysisRepository;
import com.teacherhub.risk.repository.RiskReviewRepository;
import com.teacherhub.risk.service.EmergencyReviewService;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeacherRiskReviewService {

    private final ComplaintRepository complaintRepository;
    private final RiskAnalysisRepository riskAnalysisRepository;
    private final RiskReviewRepository riskReviewRepository;
    private final ComplaintEventRepository eventRepository;
    private final UserRepository userRepository;
    private final ComplaintAccessPolicy complaintAccessPolicy;
    private final TeacherRiskScale teacherRiskScale;
    private final EmergencyReviewService emergencyReviewService;

    @Transactional
    public TeacherRiskReviewResponse review(
            Long userId,
            Long complaintId,
            TeacherRiskReviewRequest request
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("사용자를 찾을 수 없습니다."));
        Complaint complaint = complaintRepository.findForUpdate(complaintId)
                .orElseThrow(() -> new ComplaintNotFoundException("민원을 찾을 수 없습니다."));
        complaintAccessPolicy.validateAssignedTeacher(user, complaint);

        if (complaint.getStatus() != ComplaintStatus.OPEN
                || complaint.getWaitingFor() != WaitingFor.TEACHER) {
            throw new RiskReviewNotAllowedException(
                    "답변 대기 중인 민원만 위험도를 검토할 수 있습니다."
            );
        }

        RiskAnalysis analysis = riskAnalysisRepository
                .findFirstByComplaintIdAndCompletedTrueOrderByIdDesc(complaintId)
                .orElseThrow(() -> new RiskAnalysisNotFoundException(
                        "완료된 위험 분석 결과를 찾을 수 없습니다."
                ));

        RiskReview latestReview = riskReviewRepository
                .findFirstByRiskAnalysisIdOrderByReviewedAtDescIdDesc(analysis.getId())
                .orElse(null);
        int previousScore = latestReview == null
                ? teacherRiskScale.initialScoreFor(analysis.getRiskLevel())
                : latestReview.getReviewedScore();
        RiskLevel previousLevel = latestReview == null
                ? analysis.getRiskLevel()
                : latestReview.getReviewedLevel();
        RiskLevel reviewedLevel = teacherRiskScale.levelFor(request.riskScore());
        RiskReviewDirection direction = direction(previousScore, request.riskScore());
        String reason = normalizeReason(request.reason());

        // 위험도를 변경할 때는 상향·하향 모두 검토 사유가 필요합니다.
        if (direction != RiskReviewDirection.UNCHANGED && reason == null) {
            throw new RiskReviewReasonRequiredException(
                    "위험도를 변경할 때는 검토 사유가 필요합니다."
            );
        }

        if (previousLevel == RiskLevel.EMERGENCY && request.riskScore() != previousScore) {
            EmergencyReviewResponse emergencyReview = emergencyReviewService.request(
                    userId,
                    complaintId,
                    new EmergencyReviewCreateRequest(request.riskScore(), reason, null)
            );
            return TeacherRiskReviewResponse.pendingApproval(
                    emergencyReview.requestId(),
                    complaint.getId(),
                    analysis.getId(),
                    previousScore,
                    previousLevel,
                    request.riskScore(),
                    reviewedLevel,
                    direction,
                    reason,
                    user.getName(),
                    emergencyReview.requestedAt()
            );
        }

        RiskReview saved = riskReviewRepository.save(new RiskReview(
                analysis,
                complaint.getTeacher(),
                previousScore,
                previousLevel,
                request.riskScore(),
                reviewedLevel,
                direction,
                reason
        ));
        eventRepository.save(ComplaintEvent.riskReviewed(
                complaint,
                user,
                saved.getId(),
                saved.getPreviousScore(),
                saved.getPreviousLevel(),
                saved.getReviewedScore(),
                saved.getReviewedLevel(),
                saved.getReason()
        ));
        return TeacherRiskReviewResponse.from(
                saved,
                emergencyReviewService.isSafetyLocked(complaintId)
        );
    }

    private RiskReviewDirection direction(int previousScore, int reviewedScore) {
        if (reviewedScore < previousScore) {
            return RiskReviewDirection.LOWERED;
        }
        if (reviewedScore > previousScore) {
            return RiskReviewDirection.RAISED;
        }
        return RiskReviewDirection.UNCHANGED;
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        return reason.trim();
    }
}
