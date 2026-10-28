package com.teacherhub.risk.service;

import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.complaint.access.ComplaintAccessPolicy;
import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintEvent;
import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.enums.MessageEventType;
import com.teacherhub.complaint.enums.WaitingFor;
import com.teacherhub.complaint.exception.ComplaintNotFoundException;
import com.teacherhub.complaint.repository.ComplaintEventRepository;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.complaint.teacher.policy.TeacherRiskScale;
import com.teacherhub.escalation.entity.ComplaintEscalation;
import com.teacherhub.escalation.exception.AdminNotFoundException;
import com.teacherhub.escalation.exception.EscalationStateException;
import com.teacherhub.escalation.repository.ComplaintEscalationRepository;
import com.teacherhub.risk.dto.EmergencyReviewCreateRequest;
import com.teacherhub.risk.dto.EmergencyReviewResponse;
import com.teacherhub.risk.dto.EmergencyReviewUpdateRequest;
import com.teacherhub.risk.entity.EmergencyRiskReviewRequest;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.entity.RiskReview;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.enums.RiskReviewDirection;
import com.teacherhub.risk.enums.RiskReviewStatus;
import com.teacherhub.risk.exception.EmergencyReviewAccessDeniedException;
import com.teacherhub.risk.exception.EmergencyReviewDuplicateException;
import com.teacherhub.risk.exception.EmergencyReviewNotFoundException;
import com.teacherhub.risk.exception.RiskAnalysisNotFoundException;
import com.teacherhub.risk.exception.RiskReviewNotAllowedException;
import com.teacherhub.risk.exception.RiskReviewReasonRequiredException;
import com.teacherhub.risk.repository.EmergencyRiskReviewRequestRepository;
import com.teacherhub.risk.repository.RiskAnalysisRepository;
import com.teacherhub.risk.repository.RiskReviewRepository;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.enums.UserRole;
import com.teacherhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmergencyReviewService {

    private final ComplaintRepository complaintRepository;
    private final RiskAnalysisRepository riskAnalysisRepository;
    private final RiskReviewRepository riskReviewRepository;
    private final EmergencyRiskReviewRequestRepository requestRepository;
    private final ComplaintEscalationRepository escalationRepository;
    private final ComplaintEventRepository eventRepository;
    private final UserRepository userRepository;
    private final ComplaintAccessPolicy complaintAccessPolicy;
    private final TeacherRiskScale teacherRiskScale;

    @Transactional
    public EmergencyReviewResponse request(
            Long userId,
            Long complaintId,
            EmergencyReviewCreateRequest request
    ) {
        User user = findUser(userId);
        Complaint complaint = complaintRepository.findForUpdate(complaintId)
                .orElseThrow(() -> new ComplaintNotFoundException("민원을 찾을 수 없습니다."));
        complaintAccessPolicy.validateAssignedTeacher(user, complaint);
        if (request.reason() == null || request.reason().isBlank()) {
            throw new RiskReviewReasonRequiredException("Emergency 재검토 사유가 필요합니다.");
        }
        if (request.riskScore() < 1 || request.riskScore() > 6) {
            throw new RiskReviewNotAllowedException(
                    "Emergency 재검토 요청 점수는 1점 이상 6점 이하여야 합니다."
            );
        }
        if (complaint.getStatus() != ComplaintStatus.OPEN
                || complaint.getWaitingFor() != WaitingFor.TEACHER) {
            throw new RiskReviewNotAllowedException(
                    "답변 대기 중인 민원만 Emergency 재검토를 요청할 수 있습니다."
            );
        }

        if (requestRepository
                .findActiveByComplaintId(complaintId, RiskReviewStatus.PENDING_ADMIN_APPROVAL)
                .isPresent()) {
            throw new EmergencyReviewDuplicateException(
                    "이미 처리 중이거나 안전 잠금이 유지 중인 Emergency 재검토 요청이 있습니다."
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
        if (previousLevel != RiskLevel.EMERGENCY || request.riskScore() >= previousScore) {
            throw new RiskReviewNotAllowedException(
                    "현재 Emergency 위험도를 하향하는 경우에만 재검토를 요청할 수 있습니다."
            );
        }

        User reviewer = findReviewer(complaintId, request.reviewerId());
        EmergencyRiskReviewRequest saved = requestRepository.saveAndFlush(
                new EmergencyRiskReviewRequest(
                        complaint,
                        analysis,
                        complaint.getTeacher(),
                        reviewer,
                        previousScore,
                        previousLevel,
                        request.riskScore(),
                        teacherRiskScale.levelFor(request.riskScore()),
                        request.reason().trim()
                )
        );
        eventRepository.save(ComplaintEvent.emergencyReviewChanged(
                complaint,
                user,
                saved.getId(),
                MessageEventType.EMERGENCY_REVIEW_REQUESTED,
                saved.getReason()
        ));
        return EmergencyReviewResponse.from(saved);
    }

    public EmergencyReviewResponse findCurrent(Long userId, Long complaintId) {
        User user = findUser(userId);
        Complaint complaint = complaintRepository.findDetailById(complaintId)
                .orElseThrow(() -> new ComplaintNotFoundException("민원을 찾을 수 없습니다."));
        complaintAccessPolicy.validateAssignedTeacher(user, complaint);
        return EmergencyReviewResponse.from(requestRepository
                .findFirstByComplaintIdOrderByRequestedAtDescIdDesc(complaintId)
                .orElseThrow(() -> new EmergencyReviewNotFoundException(
                        "Emergency 재검토 요청을 찾을 수 없습니다."
                )));
    }

    public EmergencyReviewResponse findForReviewer(Long userId, Long requestId) {
        User reviewer = findAdmin(userId);
        return EmergencyReviewResponse.from(findAssigned(requestId, reviewer, false));
    }

    public List<EmergencyReviewResponse> findAllForReviewer(Long userId) {
        User reviewer = findAdmin(userId);
        return requestRepository.findByReviewerIdOrderByRequestedAtDescIdDesc(reviewer.getId())
                .stream()
                .map(EmergencyReviewResponse::from)
                .toList();
    }

    @Transactional
    public EmergencyReviewResponse update(
            Long userId,
            Long requestId,
            EmergencyReviewUpdateRequest update
    ) {
        validateUpdate(update);
        User reviewer = findAdmin(userId);
        EmergencyRiskReviewRequest request = findAssigned(requestId, reviewer, true);
        if (update.status() == RiskReviewStatus.PENDING_ADMIN_APPROVAL) {
            throw new EscalationStateException("승인 대기 상태는 검토자가 결정할 수 없습니다.");
        }

        String message = normalizeMessage(update.message());
        MessageEventType decisionEventType = null;
        if (update.status() == RiskReviewStatus.APPLIED) {
            request.approve(message);
            RiskReview review = riskReviewRepository.save(new RiskReview(
                    request.getRiskAnalysis(),
                    request.getRequestedBy(),
                    request.getPreviousRiskScore(),
                    request.getPreviousRiskLevel(),
                    request.getRequestedRiskScore(),
                    request.getRequestedRiskLevel(),
                    RiskReviewDirection.LOWERED,
                    request.getReason()
            ));
            eventRepository.save(ComplaintEvent.riskReviewed(
                    request.getComplaint(),
                    request.getRequestedBy().getUser(),
                    review.getId(),
                    review.getPreviousScore(),
                    review.getPreviousLevel(),
                    review.getReviewedScore(),
                    review.getReviewedLevel(),
                    review.getReason()
            ));
            decisionEventType = MessageEventType.EMERGENCY_REVIEW_APPROVED;
        } else if (update.status() == RiskReviewStatus.REJECTED) {
            request.reject(message);
            decisionEventType = MessageEventType.EMERGENCY_REVIEW_REJECTED;
        }

        boolean safetyLockChanged = update.safetyLock() != null
                && request.isSafetyLock() != update.safetyLock();
        if (update.safetyLock() != null) {
            request.updateSafetyLock(update.safetyLock());
        }
        EmergencyRiskReviewRequest saved = requestRepository.saveAndFlush(request);
        if (decisionEventType != null) {
            eventRepository.save(ComplaintEvent.emergencyReviewChanged(
                    saved.getComplaint(),
                    reviewer,
                    saved.getId(),
                    decisionEventType,
                    message == null ? decisionMessage(update.status()) : message
            ));
        }
        if (safetyLockChanged) {
            eventRepository.save(ComplaintEvent.emergencyReviewChanged(
                    saved.getComplaint(),
                    reviewer,
                    saved.getId(),
                    MessageEventType.EMERGENCY_SAFETY_LOCK_UPDATED,
                    message == null || message.isBlank()
                            ? safetyLockMessage(saved.isSafetyLock())
                            : message
            ));
        }
        return EmergencyReviewResponse.from(saved);
    }

    public boolean isSafetyLocked(Long complaintId) {
        return requestRepository
                .findFirstByComplaintIdAndSafetyLockTrueOrderByRequestedAtDescIdDesc(complaintId)
                .isPresent();
    }

    private void validateUpdate(EmergencyReviewUpdateRequest update) {
        if (update.status() == null && update.safetyLock() == null) {
            throw new IllegalArgumentException("변경할 재검토 상태 또는 안전 잠금 상태가 필요합니다.");
        }
    }

    private String normalizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        return message.trim();
    }

    private String decisionMessage(RiskReviewStatus status) {
        return status == RiskReviewStatus.APPLIED
                ? "Emergency 재검토 요청을 승인합니다."
                : "Emergency 재검토 요청을 거부합니다.";
    }

    private String safetyLockMessage(boolean safetyLock) {
        return safetyLock ? "안전 잠금을 유지합니다." : "안전 잠금을 해제합니다.";
    }

    private EmergencyRiskReviewRequest findAssigned(
            Long requestId,
            User reviewer,
            boolean forUpdate
    ) {
        EmergencyRiskReviewRequest request = (forUpdate
                ? requestRepository.findForUpdate(requestId)
                : requestRepository.findById(requestId))
                .orElseThrow(() -> new EmergencyReviewNotFoundException(
                        "Emergency 재검토 요청을 찾을 수 없습니다."
                ));
        if (!request.getReviewer().getId().equals(reviewer.getId())) {
            throw new EmergencyReviewAccessDeniedException(
                    "지정 검토자만 Emergency 재검토 요청을 처리할 수 있습니다."
            );
        }
        return request;
    }

    private User findReviewer(Long complaintId, Long reviewerId) {
        if (reviewerId != null) {
            return userRepository.findById(reviewerId)
                    .filter(user -> user.getRole() == UserRole.ADMIN)
                    .orElseThrow(() -> new AdminNotFoundException("지정 검토자를 찾을 수 없습니다."));
        }
        return escalationRepository.findByComplaintId(complaintId)
                .map(ComplaintEscalation::getAssignedAdmin)
                .orElseGet(() -> userRepository.findFirstByRoleOrderByIdAsc(UserRole.ADMIN)
                        .orElseThrow(() -> new AdminNotFoundException("지정할 검토자를 찾을 수 없습니다.")));
    }

    private User findAdmin(Long userId) {
        User user = findUser(userId);
        if (user.getRole() != UserRole.ADMIN) {
            throw new EmergencyReviewAccessDeniedException("관리자 권한이 필요합니다.");
        }
        return user;
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("사용자를 찾을 수 없습니다."));
    }
}
