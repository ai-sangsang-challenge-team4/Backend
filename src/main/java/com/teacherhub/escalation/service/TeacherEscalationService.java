package com.teacherhub.escalation.service;

import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.complaint.access.ComplaintAccessPolicy;
import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintEvent;
import com.teacherhub.complaint.enums.MessageEventType;
import com.teacherhub.complaint.repository.ComplaintEventRepository;
import com.teacherhub.escalation.dto.EscalationResponse;
import com.teacherhub.escalation.dto.ShareEscalationRequest;
import com.teacherhub.escalation.entity.ComplaintEscalation;
import com.teacherhub.escalation.enums.EscalationScope;
import com.teacherhub.escalation.exception.AdminNotFoundException;
import com.teacherhub.escalation.exception.EscalationNotAllowedException;
import com.teacherhub.escalation.exception.EscalationReasonRequiredException;
import com.teacherhub.escalation.repository.ComplaintEscalationRepository;
import com.teacherhub.complaint.exception.ComplaintNotFoundException;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.entity.RiskReview;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.exception.RiskAnalysisNotFoundException;
import com.teacherhub.risk.repository.RiskAnalysisRepository;
import com.teacherhub.risk.repository.RiskReviewRepository;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.enums.UserRole;
import com.teacherhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeacherEscalationService {

    private final ComplaintRepository complaintRepository;
    private final ComplaintEscalationRepository escalationRepository;
    private final RiskAnalysisRepository riskAnalysisRepository;
    private final RiskReviewRepository riskReviewRepository;
    private final ComplaintEventRepository eventRepository;
    private final UserRepository userRepository;
    private final ComplaintAccessPolicy complaintAccessPolicy;

    @Transactional
    public EscalationResponse share(Long userId, Long complaintId, ShareEscalationRequest request) {
        User teacherUser = findUser(userId);
        Complaint complaint = complaintRepository.findForUpdate(complaintId)
                .orElseThrow(() -> new ComplaintNotFoundException("민원을 찾을 수 없습니다."));
        complaintAccessPolicy.validateAssignedTeacher(teacherUser, complaint);

        Set<EscalationScope> scopes = normalizeScopes(request.scopes());
        String reason = normalizeReason(request.reason());
        User admin = findAdmin(request.adminId());

        RiskAnalysis analysis = requireEmergencyAnalysis(complaintId);

        ComplaintEscalation existing = escalationRepository.findByComplaintId(complaintId)
                .orElse(null);
        if (existing != null) {
            if (existing.matches(admin, scopes, reason)) {
                return EscalationResponse.from(existing);
            }
            throw new EscalationNotAllowedException("이미 다른 내용으로 공유된 민원입니다.");
        }

        ComplaintEscalation saved = escalationRepository.saveAndFlush(new ComplaintEscalation(
                complaint,
                analysis,
                complaint.getTeacher(),
                admin,
                scopes,
                reason
        ));
        eventRepository.save(ComplaintEvent.escalationChanged(
                complaint,
                teacherUser,
                saved.getId(),
                MessageEventType.ESCALATION_SHARED,
                reason
        ));
        return EscalationResponse.from(saved);
    }

    public EscalationResponse findStatus(Long userId, Long complaintId) {
        User teacherUser = findUser(userId);
        Complaint complaint = complaintRepository.findDetailById(complaintId)
                .orElseThrow(() -> new ComplaintNotFoundException("민원을 찾을 수 없습니다."));
        complaintAccessPolicy.validateAssignedTeacher(teacherUser, complaint);

        return escalationRepository.findByComplaintId(complaintId)
                .map(EscalationResponse::from)
                .orElseGet(() -> EscalationResponse.notShared(complaintId));
    }

    private RiskAnalysis requireEmergencyAnalysis(Long complaintId) {
        RiskAnalysis analysis = riskAnalysisRepository
                .findFirstByComplaintIdAndCompletedTrueOrderByIdDesc(complaintId)
                .orElseThrow(() -> new RiskAnalysisNotFoundException(
                        "완료된 위험 분석 결과를 찾을 수 없습니다."
                ));
        RiskReview latestReview = riskReviewRepository
                .findFirstByRiskAnalysisIdOrderByReviewedAtDescIdDesc(analysis.getId())
                .orElse(null);
        RiskLevel currentLevel = latestReview == null
                ? analysis.getRiskLevel()
                : latestReview.getReviewedLevel();
        if (currentLevel != RiskLevel.EMERGENCY) {
            throw new EscalationNotAllowedException("EMERGENCY 위험도의 민원만 관리자에게 공유할 수 있습니다.");
        }
        return analysis;
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("사용자를 찾을 수 없습니다."));
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new EscalationReasonRequiredException("관리자 공유 사유가 필요합니다.");
        }
        return reason.trim();
    }

    private User findAdmin(Long adminId) {
        if (adminId == null) {
            return userRepository.findFirstByRoleOrderByIdAsc(UserRole.ADMIN)
                    .orElseThrow(() -> new AdminNotFoundException("공유할 관리자를 찾을 수 없습니다."));
        }
        return userRepository.findById(adminId)
                .filter(user -> user.getRole() == UserRole.ADMIN)
                .orElseThrow(() -> new AdminNotFoundException("공유할 관리자를 찾을 수 없습니다."));
    }

    private Set<EscalationScope> normalizeScopes(Set<EscalationScope> requestedScopes) {
        Set<EscalationScope> scopes = new LinkedHashSet<>();
        scopes.add(EscalationScope.MASKING_MESSAGES);
        if (requestedScopes != null) {
            scopes.addAll(requestedScopes);
        }
        return Set.copyOf(scopes);
    }
}
