package com.teacherhub.escalation.service;

import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.complaint.entity.ComplaintEvent;
import com.teacherhub.complaint.entity.ComplaintMessage;
import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.enums.MessageEventType;
import com.teacherhub.complaint.enums.MessageStatus;
import com.teacherhub.complaint.repository.ComplaintEventRepository;
import com.teacherhub.complaint.repository.ComplaintMessageRepository;
import com.teacherhub.complaint.history.service.ComplaintHistoryService;
import com.teacherhub.complaint.teacher.dto.query.TeacherConversationResponse;
import com.teacherhub.complaint.teacher.dto.query.TeacherHistoryResponse;
import com.teacherhub.complaint.teacher.dto.query.TeacherRiskResponse;
import com.teacherhub.escalation.dto.EscalationMessageRequest;
import com.teacherhub.escalation.dto.EscalationStateResponse;
import com.teacherhub.escalation.dto.AdminEscalationSummaryResponse;
import com.teacherhub.escalation.dto.EscalationShareContentResponse;
import com.teacherhub.escalation.entity.ComplaintEscalation;
import com.teacherhub.escalation.exception.EscalationAccessDeniedException;
import com.teacherhub.escalation.exception.EscalationNotFoundException;
import com.teacherhub.escalation.exception.EscalationStateException;
import com.teacherhub.escalation.repository.ComplaintEscalationRepository;
import com.teacherhub.escalation.enums.EscalationStatus;
import com.teacherhub.escalation.enums.EscalationScope;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.enums.RiskReviewStatus;
import com.teacherhub.risk.entity.RiskReview;
import com.teacherhub.risk.entity.EmergencyRiskReviewRequest;
import com.teacherhub.risk.masking.PiiMaskingService;
import com.teacherhub.risk.repository.ComplaintRiskTagRepository;
import com.teacherhub.risk.repository.RiskReviewRepository;
import com.teacherhub.risk.repository.EmergencyRiskReviewRequestRepository;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.enums.UserRole;
import com.teacherhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.LocalDateTime;

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
    private final EmergencyRiskReviewRequestRepository emergencyReviewRequestRepository;
    private final PiiMaskingService piiMaskingService;
    private final ComplaintHistoryService complaintHistoryService;

    public List<AdminEscalationSummaryResponse> findAll(
            Long userId,
            EscalationStatus status,
            RiskLevel riskLevel
    ) {
        User admin = findAdmin(userId);
        return escalationRepository.findByAssignedAdminIdOrderBySharedAtDesc(admin.getId()).stream()
                .map(this::summaryResponse)
                .filter(response -> status == null || response.escalationStatus() == status)
                .filter(response -> riskLevel == null || response.currentRiskLevel() == riskLevel)
                .toList();
    }

    public AdminEscalationSummaryResponse findOne(Long userId, Long escalationId) {
        User admin = findAdmin(userId);
        return summaryResponse(findAssigned(escalationId, admin));
    }

    // 공유 내용 탭
    public EscalationShareContentResponse findMessages(Long userId, Long escalationId) {
        User admin = findAdmin(userId);
        ComplaintEscalation escalation = findAssigned(escalationId, admin);
        List<ComplaintMessage> sourceMessages = messageRepository
                .findByComplaintIdAndStatusNotOrderByIdAsc(
                        escalation.getComplaint().getId(),
                        MessageStatus.DRAFT
                );
        LocalDateTime submittedAt = submittedAt(escalation);
        List<TeacherConversationResponse.Message> maskedMessages =
                escalation.getScopes().contains(EscalationScope.MASKING_MESSAGES)
                        ? sourceMessages.stream()
                        .map(message -> TeacherConversationResponse.Message.from(
                                message,
                                maskedContent(message),
                                submittedAt,
                                false
                        ))
                        .toList()
                        : List.of();
        List<TeacherConversationResponse.Message> originalMessages =
                escalation.getScopes().contains(EscalationScope.ORIGINAL_MESSAGES)
                        ? sourceMessages.stream()
                        .map(message -> TeacherConversationResponse.Message.from(
                                message,
                                message.getContent(),
                                submittedAt,
                                false
                        ))
                        .toList()
                        : List.of();

        return new EscalationShareContentResponse(
                escalation.getReason(),
                maskedMessages,
                originalMessages,
                escalation.getScopes().contains(EscalationScope.MEDIATION_PACKAGE)
        );
    }

    // 위험 분석 탭
    public TeacherRiskResponse findRisk(Long userId, Long escalationId) {
        User admin = findAdmin(userId);
        ComplaintEscalation escalation = findAssigned(escalationId, admin);

        RiskReview latestReview = latestReview(escalation);

        return TeacherRiskResponse.from(
                escalation.getRiskAnalysis(),
                riskTagRepository.findByRiskAnalysisId(escalation.getRiskAnalysis().getId()),
                latestReview,
                emergencyReviewRequestRepository
                        .findFirstByComplaintIdOrderByRequestedAtDescIdDesc(
                                escalation.getComplaint().getId()
                        )
                        .map(com.teacherhub.risk.entity.EmergencyRiskReviewRequest::isSafetyLock)
                        .orElse(false)
        );
    }

    // 처리 기록 탭
    public TeacherHistoryResponse findHistory(Long userId, Long escalationId) {
        User admin = findAdmin(userId);
        ComplaintEscalation escalation = findAssigned(escalationId, admin);

        return complaintHistoryService.findHistory(escalation.getComplaint().getId());
    }

    @Transactional
    public EscalationStateResponse startReview(
            Long userId,
            Long escalationId,
            EscalationMessageRequest request
    ) {
        User admin = findAdmin(userId);
        ComplaintEscalation escalation = findAssigned(escalationId, admin);
        escalation.startReview(request.message());
        ComplaintEscalation saved = escalationRepository.saveAndFlush(escalation);
        eventRepository.save(ComplaintEvent.escalationChanged(
                saved.getComplaint(),
                admin,
                saved.getId(),
                MessageEventType.ESCALATION_REVIEW_STARTED,
                saved.getReviewMemo() == null
                        ? "관리자 검토를 시작했습니다."
                        : saved.getReviewMemo()
        ));
        return stateResponse(saved);
    }

    @Transactional
    public EscalationStateResponse complete(
            Long userId,
            Long escalationId,
            EscalationMessageRequest request
    ) {
        User admin = findAdmin(userId);
        ComplaintEscalation escalation = findAssigned(escalationId, admin);
        emergencyReviewRequestRepository
                .findFirstByComplaintIdOrderByRequestedAtDescIdDesc(
                        escalation.getComplaint().getId()
                )
                .filter(emergencyReview ->
                        emergencyReview.getStatus() == RiskReviewStatus.PENDING_ADMIN_APPROVAL
                )
                .ifPresent(emergencyReview -> {
                    throw new EscalationStateException(
                            "Emergency 재검토 요청을 승인하거나 거부한 뒤 처리 완료할 수 있습니다."
                    );
                });
        escalation.complete(request.message().trim());
        ComplaintEscalation saved = escalationRepository.saveAndFlush(escalation);
        eventRepository.save(ComplaintEvent.escalationChanged(
                saved.getComplaint(),
                admin,
                saved.getId(),
                MessageEventType.ESCALATION_COMPLETED,
                request.message().trim()
        ));
        return stateResponse(saved);
    }

    private AdminEscalationSummaryResponse summaryResponse(ComplaintEscalation escalation) {
        RiskReview latestReview = latestReview(escalation);
        EmergencyRiskReviewRequest emergencyReviewRequest = emergencyReviewRequestRepository
                .findFirstByComplaintIdOrderByRequestedAtDescIdDesc(escalation.getComplaint().getId())
                .orElse(null);
        return AdminEscalationSummaryResponse.from(
                escalation,
                latestReview,
                emergencyReviewRequest
        );
    }

    private EscalationStateResponse stateResponse(ComplaintEscalation escalation) {
        RiskReview latestReview = latestReview(escalation);
        return EscalationStateResponse.from(
                escalation,
                latestReview,
                emergencyReviewRequestRepository
                        .findFirstByComplaintIdOrderByRequestedAtDescIdDesc(
                                escalation.getComplaint().getId()
                        )
                        .orElse(null)
        );
    }

    private RiskReview latestReview(ComplaintEscalation escalation) {
        return riskReviewRepository
                .findFirstByRiskAnalysisIdOrderByReviewedAtDescIdDesc(
                        escalation.getRiskAnalysis().getId()
                )
                .orElse(null);
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

    private LocalDateTime submittedAt(ComplaintEscalation escalation) {
        return eventRepository
                .findFirstByComplaintIdAndEventTypeAndNewStatusOrderByOccurredAtDescIdDesc(
                        escalation.getComplaint().getId(),
                        MessageEventType.STATUS_CHANGED,
                        ComplaintStatus.OPEN
                )
                .map(ComplaintEvent::getOccurredAt)
                .orElseThrow(() -> new IllegalStateException("민원 제출 이력을 찾을 수 없습니다."));
    }

    private String maskedContent(ComplaintMessage message) {
        if (message.getMaskedContent() != null && !message.getMaskedContent().isBlank()) {
            return message.getMaskedContent();
        }
        return piiMaskingService.mask(message.getContent());
    }

}
