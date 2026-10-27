package com.teacherhub.complaint.teacher.service;

import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.complaint.access.ComplaintAccessPolicy;
import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintEvent;
import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.enums.MessageEventType;
import com.teacherhub.complaint.enums.WaitingFor;
import com.teacherhub.complaint.exception.ComplaintNotFoundException;
import com.teacherhub.escalation.entity.ComplaintEscalation;
import com.teacherhub.escalation.exception.AdminApprovalRequiredException;
import com.teacherhub.escalation.repository.ComplaintEscalationRepository;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.complaint.repository.ComplaintEventRepository;
import com.teacherhub.complaint.teacher.dto.risk.TeacherRiskReviewRequest;
import com.teacherhub.complaint.teacher.dto.risk.TeacherRiskReviewResponse;
import com.teacherhub.complaint.teacher.policy.TeacherRiskScale;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.entity.RiskReview;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.enums.RiskReviewDirection;
import com.teacherhub.risk.exception.RiskAnalysisNotFoundException;
import com.teacherhub.risk.exception.RiskReviewNotAllowedException;
import com.teacherhub.risk.exception.RiskReviewReasonRequiredException;
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
public class TeacherRiskReviewService {

    private final ComplaintRepository complaintRepository;
    private final RiskAnalysisRepository riskAnalysisRepository;
    private final RiskReviewRepository riskReviewRepository;
    private final ComplaintEscalationRepository escalationRepository;
    private final ComplaintEventRepository eventRepository;
    private final UserRepository userRepository;
    private final ComplaintAccessPolicy complaintAccessPolicy;
    private final TeacherRiskScale teacherRiskScale;

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

        // 하향 조정에 대한 검토 사유가 없는 경우
        if (direction == RiskReviewDirection.LOWERED && reason == null) {
            throw new RiskReviewReasonRequiredException(
                    "위험도를 하향 조정할 때는 검토 사유가 필요합니다."
            );
        }

        if (previousLevel == RiskLevel.EMERGENCY && request.riskScore() != previousScore) {
            ComplaintEscalation escalation = escalationRepository.findByComplaintId(complaintId)
                    .orElseThrow(() -> new AdminApprovalRequiredException(
                            "EMERGENCY 위험도 변경을 요청하려면 먼저 관리자에게 공유해야 합니다."
                    ));
            if (!escalation.getRiskAnalysis().getId().equals(analysis.getId())) {
                throw new AdminApprovalRequiredException(
                        "현재 위험 분석에 연결된 관리자 공유 건이 필요합니다."
                );
            }
            escalation.requestRiskChange(
                    previousScore,
                    previousLevel,
                    request.riskScore(),
                    reviewedLevel,
                    reason
            );
            ComplaintEscalation saved = escalationRepository.saveAndFlush(escalation);
            eventRepository.save(ComplaintEvent.escalationChanged(
                    complaint,
                    user,
                    saved.getId(),
                    MessageEventType.ESCALATION_RISK_CHANGE_REQUESTED,
                    reason
            ));
            return TeacherRiskReviewResponse.pendingApproval(
                    complaint.getId(),
                    analysis.getId(),
                    previousScore,
                    previousLevel,
                    request.riskScore(),
                    reviewedLevel,
                    direction,
                    reason,
                    user.getName(),
                    saved.getRiskChangeRequestedAt()
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
        return TeacherRiskReviewResponse.from(saved);
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
