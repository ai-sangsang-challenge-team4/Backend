package com.teacherhub.escalation.service;

import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.complaint.entity.ComplaintMessage;
import com.teacherhub.complaint.entity.ComplaintEvent;
import com.teacherhub.complaint.enums.MessageEventType;
import com.teacherhub.complaint.enums.MessageStatus;
import com.teacherhub.complaint.repository.ComplaintEventRepository;
import com.teacherhub.complaint.repository.ComplaintMessageRepository;
import com.teacherhub.complaint.teacher.dto.query.TeacherHistoryResponse;
import com.teacherhub.complaint.teacher.dto.query.TeacherRiskResponse;
import com.teacherhub.complaint.teacher.policy.TeacherRiskScale;
import com.teacherhub.escalation.dto.CompleteEscalationRequest;
import com.teacherhub.escalation.dto.EscalationResponse;
import com.teacherhub.escalation.dto.EscalationShareContentResponse;
import com.teacherhub.escalation.dto.StartEscalationReviewRequest;
import com.teacherhub.escalation.entity.ComplaintEscalation;
import com.teacherhub.escalation.exception.EscalationAccessDeniedException;
import com.teacherhub.escalation.exception.EscalationNotFoundException;
import com.teacherhub.escalation.exception.EscalationVersionConflictException;
import com.teacherhub.escalation.repository.ComplaintEscalationRepository;
import com.teacherhub.escalation.enums.EscalationStatus;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.entity.RiskReview;
import com.teacherhub.risk.masking.PiiMaskingService;
import com.teacherhub.risk.repository.ComplaintRiskTagRepository;
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
public class AdminEscalationService {

    private final ComplaintEscalationRepository escalationRepository;
    private final UserRepository userRepository;
    private final ComplaintMessageRepository messageRepository;
    private final ComplaintEventRepository eventRepository;
    private final ComplaintRiskTagRepository riskTagRepository;
    private final RiskReviewRepository riskReviewRepository;
    private final TeacherRiskScale teacherRiskScale;
    private final PiiMaskingService piiMaskingService;

    public List<EscalationResponse> findAll(
            Long userId,
            EscalationStatus status,
            RiskLevel riskLevel
    ) {
        User admin = findAdmin(userId);
        return escalationRepository.findByAssignedAdminIdOrderBySharedAtDesc(admin.getId()).stream()
                .filter(escalation -> status == null || escalation.getStatus() == status)
                .filter(escalation -> riskLevel == null
                        || escalation.getRiskAnalysis().getRiskLevel() == riskLevel)
                .map(EscalationResponse::from)
                .toList();
    }

    public EscalationResponse findOne(Long userId, Long escalationId) {
        User admin = findAdmin(userId);
        return EscalationResponse.from(findAssigned(escalationId, admin));
    }

    // 공유 내용 탭
    public EscalationShareContentResponse findMessages(Long userId, Long escalationId) {
        User admin = findAdmin(userId);
        ComplaintEscalation escalation = findAssigned(escalationId, admin);
        List<ComplaintMessage> messages = messageRepository
                .findByComplaintIdAndStatusNotOrderByIdAsc(
                        escalation.getComplaint().getId(),
                        MessageStatus.DRAFT
                );

        return EscalationShareContentResponse.from(
                escalation,
                messages,
                this::maskedContent
        );
    }

    // 위험 분석 탭
    public TeacherRiskResponse findRisk(Long userId, Long escalationId) {
        User admin = findAdmin(userId);
        ComplaintEscalation escalation = findAssigned(escalationId, admin);

        RiskReview latestReview = riskReviewRepository
                .findFirstByRiskAnalysisIdOrderByReviewedAtDescIdDesc(
                        escalation.getRiskAnalysis().getId()
                )
                .orElse(null);

        return TeacherRiskResponse.from(
                escalation.getRiskAnalysis(),
                riskTagRepository.findByRiskAnalysisId(escalation.getRiskAnalysis().getId()),
                latestReview,
                teacherRiskScale.initialScoreFor(escalation.getRiskAnalysis().getRiskLevel())
        );
    }

    // 처리 기록 탭
    public TeacherHistoryResponse findHistory(Long userId, Long escalationId) {
        User admin = findAdmin(userId);
        ComplaintEscalation escalation = findAssigned(escalationId, admin);

        return TeacherHistoryResponse.from(
                escalation.getComplaint().getId(),
                eventRepository.findByComplaintIdOrderByOccurredAtDescIdDesc(
                        escalation.getComplaint().getId()
                )
        );
    }

    @Transactional
    public EscalationResponse startReview(
            Long userId,
            Long escalationId,
            StartEscalationReviewRequest request
    ) {
        User admin = findAdmin(userId);
        ComplaintEscalation escalation = findAssigned(escalationId, admin);
        validateVersion(escalation, request.version());
        escalation.startReview();
        ComplaintEscalation saved = escalationRepository.saveAndFlush(escalation);
        eventRepository.save(ComplaintEvent.escalationChanged(
                saved.getComplaint(),
                admin,
                saved.getId(),
                MessageEventType.ESCALATION_REVIEW_STARTED,
                "관리자 검토를 시작했습니다."
        ));
        return EscalationResponse.from(saved);
    }

    @Transactional
    public EscalationResponse complete(
            Long userId,
            Long escalationId,
            CompleteEscalationRequest request
    ) {
        User admin = findAdmin(userId);
        ComplaintEscalation escalation = findAssigned(escalationId, admin);
        validateVersion(escalation, request.version());
        escalation.complete(request.result().trim());
        ComplaintEscalation saved = escalationRepository.saveAndFlush(escalation);
        eventRepository.save(ComplaintEvent.escalationChanged(
                saved.getComplaint(),
                admin,
                saved.getId(),
                MessageEventType.ESCALATION_COMPLETED,
                request.result().trim()
        ));
        return EscalationResponse.from(saved);
    }

    private ComplaintEscalation findAssigned(Long escalationId, User admin) {
        ComplaintEscalation escalation = escalationRepository.findDetailById(escalationId)
                .orElseThrow(() -> new EscalationNotFoundException("공유 건을 찾을 수 없습니다."));
        if (!escalation.getAssignedAdmin().getId().equals(admin.getId())) {
            throw new EscalationAccessDeniedException("공유받지 않은 관리자는 이 공유 건에 접근할 수 없습니다.");
        }
        return escalation;
    }

    private User findAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("사용자를 찾을 수 없습니다."));
        if (user.getRole() != UserRole.ADMIN) {
            throw new EscalationAccessDeniedException("관리자 권한이 필요합니다.");
        }
        return user;
    }

    private void validateVersion(ComplaintEscalation escalation, Long requestedVersion) {
        if (!escalation.getVersion().equals(requestedVersion)) {
            throw new EscalationVersionConflictException(
                    "공유 건이 다른 요청에 의해 수정되었습니다. 최신 상태를 다시 조회해주세요."
            );
        }
    }

    private String maskedContent(ComplaintMessage message) {
        if (message.getMaskedContent() != null && !message.getMaskedContent().isBlank()) {
            return message.getMaskedContent();
        }
        return piiMaskingService.mask(message.getContent());
    }
}
