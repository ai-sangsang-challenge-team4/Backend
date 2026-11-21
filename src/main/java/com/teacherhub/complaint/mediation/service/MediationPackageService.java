package com.teacherhub.complaint.mediation.service;

import com.teacherhub.common.exception.InternalSystemException;
import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.complaint.access.ComplaintAccessPolicy;
import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintEvent;
import com.teacherhub.complaint.entity.ComplaintMessage;
import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.enums.MessageStatus;
import com.teacherhub.complaint.exception.ComplaintAccessDeniedException;
import com.teacherhub.complaint.exception.ComplaintNotFoundException;
import com.teacherhub.complaint.history.ComplaintHistoryService;
import com.teacherhub.complaint.mediation.dto.MediationPackageContent;
import com.teacherhub.complaint.mediation.dto.MediationPackageContent.AnalysisSnapshot;
import com.teacherhub.complaint.mediation.dto.MediationPackageContent.HistoryEventSnapshot;
import com.teacherhub.complaint.mediation.dto.MediationPackageContent.HistorySnapshot;
import com.teacherhub.complaint.mediation.dto.MediationPackageContent.MessageSnapshot;
import com.teacherhub.complaint.mediation.dto.MediationPackageContent.MissingLogSnapshot;
import com.teacherhub.complaint.mediation.dto.MediationPackageContent.RevisionSuggestionSnapshot;
import com.teacherhub.complaint.mediation.dto.MediationPackageContent.RiskReviewSnapshot;
import com.teacherhub.complaint.mediation.dto.MediationPackageContent.RiskTagSnapshot;
import com.teacherhub.complaint.mediation.dto.MediationPackageDownload;
import com.teacherhub.complaint.mediation.dto.MediationPackageResponse;
import com.teacherhub.complaint.mediation.entity.MediationPackage;
import com.teacherhub.complaint.mediation.exception.MediationPackageConflictException;
import com.teacherhub.complaint.mediation.exception.MediationPackageNotFoundException;
import com.teacherhub.complaint.mediation.repository.MediationPackageRepository;
import com.teacherhub.complaint.repository.ComplaintEventRepository;
import com.teacherhub.complaint.repository.ComplaintMessageRepository;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.complaint.teacher.dto.query.TeacherHistoryResponse;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.entity.RiskReview;
import com.teacherhub.risk.masking.PiiMaskingContext;
import com.teacherhub.risk.masking.PiiMaskingService;
import com.teacherhub.risk.repository.ComplaintRiskTagRepository;
import com.teacherhub.risk.repository.RiskAnalysisRepository;
import com.teacherhub.risk.repository.RiskReviewRepository;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MediationPackageService {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

    private final ComplaintRepository complaintRepository;
    private final ComplaintMessageRepository messageRepository;
    private final ComplaintEventRepository eventRepository;
    private final RiskAnalysisRepository riskAnalysisRepository;
    private final ComplaintRiskTagRepository riskTagRepository;
    private final RiskReviewRepository riskReviewRepository;
    private final MediationPackageRepository packageRepository;
    private final UserRepository userRepository;
    private final ComplaintAccessPolicy complaintAccessPolicy;
    private final ComplaintHistoryService complaintHistoryService;
    private final PiiMaskingService piiMaskingService;
    private final ObjectMapper objectMapper;

    @Transactional
    public MediationPackageResponse create(
            Long userId,
            Long complaintId,
            boolean includeOriginalMessages,
            String idempotencyKey
    ) {
        Complaint complaint = complaintRepository.findForUpdate(complaintId)
                .orElseThrow(() -> new ComplaintNotFoundException("민원을 찾을 수 없습니다."));
        User user = findUser(userId);
        validatePackageAccess(user, complaint);
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);

        MediationPackage existing = packageRepository
                .findByComplaintIdAndCreatedByIdAndIdempotencyKey(
                        complaintId,
                        userId,
                        normalizedKey
                )
                .orElse(null);
        if (existing != null) {
            if (existing.isOriginalMessagesIncluded() != includeOriginalMessages) {
                throw new MediationPackageConflictException(
                        "같은 멱등 키로 다른 원문 포함 조건을 요청할 수 없습니다."
                );
            }
            return response(existing);
        }

        LocalDateTime generatedAt = LocalDateTime.now();
        String publicId = UUID.randomUUID().toString();
        MediationPackageContent content = buildContent(
                complaint,
                publicId,
                generatedAt,
                includeOriginalMessages
        );
        String snapshotJson = serialize(content);
        String checksum = sha256(snapshotJson.getBytes(StandardCharsets.UTF_8));
        MediationPackage saved = packageRepository.saveAndFlush(new MediationPackage(
                publicId,
                complaint,
                user,
                normalizedKey,
                includeOriginalMessages,
                snapshotJson,
                checksum,
                generatedAt
        ));
        eventRepository.save(ComplaintEvent.mediationPackageCreated(
                complaint,
                user,
                saved.getId(),
                includeOriginalMessages
        ));
        return response(saved, content);
    }

    public MediationPackageResponse find(Long userId, Long complaintId, String packageId) {
        User user = findUser(userId);
        Complaint complaint = findComplaint(complaintId);
        validatePackageAccess(user, complaint);
        return response(findPackage(complaintId, packageId));
    }

    public MediationPackageDownload download(Long userId, Long complaintId, String packageId) {
        User user = findUser(userId);
        Complaint complaint = findComplaint(complaintId);
        validatePackageAccess(user, complaint);
        MediationPackage mediationPackage = findPackage(complaintId, packageId);
        return new MediationPackageDownload(
                "mediation-package-" + complaintId + "-" + packageId + ".json",
                mediationPackage.getChecksumSha256(),
                mediationPackage.getSnapshotJson().getBytes(StandardCharsets.UTF_8)
        );
    }

    private MediationPackageContent buildContent(
            Complaint complaint,
            String packageId,
            LocalDateTime generatedAt,
            boolean includeOriginalMessages
    ) {
        Long complaintId = complaint.getId();
        List<ComplaintMessage> messages = messageRepository
                .findByComplaintIdAndStatusNotOrderByIdAsc(complaintId, MessageStatus.DRAFT);
        PiiMaskingContext maskingContext = PiiMaskingContext.from(complaint)
                .withAdditionalNames(packageActorNames(complaint, messages));
        List<RiskAnalysis> analyses = riskAnalysisRepository
                .findByComplaintIdAndCompletedTrueAndMessage_StatusNotOrderByIdAsc(
                        complaintId,
                        MessageStatus.DRAFT
                );
        Map<Long, List<RiskReview>> reviewsByAnalysis = riskReviewRepository
                .findByRiskAnalysisComplaintIdOrderByReviewedAtAscIdAsc(complaintId)
                .stream()
                .collect(Collectors.groupingBy(review -> review.getRiskAnalysis().getId()));
        TeacherHistoryResponse history = complaintHistoryService.findHistory(complaintId);

        return new MediationPackageContent(
                MediationPackage.CURRENT_SCHEMA_VERSION,
                packageId,
                generatedAt,
                new MediationPackageContent.ComplaintSummary(complaintId, complaint.getStatus()),
                includeOriginalMessages,
                messages.stream()
                        .map(message -> messageSnapshot(
                                message,
                                maskingContext,
                                includeOriginalMessages
                        ))
                        .toList(),
                analyses.stream()
                        .map(analysis -> analysisSnapshot(
                                analysis,
                                maskingContext,
                                reviewsByAnalysis.getOrDefault(analysis.getId(), List.of())
                        ))
                        .toList(),
                historySnapshot(history, maskingContext)
        );
    }

    private List<String> packageActorNames(
            Complaint complaint,
            List<ComplaintMessage> messages
    ) {
        Stream<String> parentName = Optional.ofNullable(complaint.getParent())
                .map(parent -> parent.getUser())
                .map(User::getName)
                .stream();
        Stream<String> senderNames = messages.stream()
                .map(ComplaintMessage::getSender)
                .filter(Objects::nonNull)
                .map(User::getName);
        return Stream.concat(parentName, senderNames)
                .filter(Objects::nonNull)
                .toList();
    }

    private MessageSnapshot messageSnapshot(
            ComplaintMessage message,
            PiiMaskingContext context,
            boolean includeOriginalMessages
    ) {
        return new MessageSnapshot(
                message.getId(),
                message.getSender().getRole(),
                message.getType(),
                message.getStatus(),
                message.getContentVersion(),
                message.getSentAt(),
                message.getReadAt(),
                mask(message.getContent(), context),
                includeOriginalMessages ? message.getContent() : null
        );
    }

    private AnalysisSnapshot analysisSnapshot(
            RiskAnalysis analysis,
            PiiMaskingContext context,
            List<RiskReview> reviews
    ) {
        List<RiskTagSnapshot> tags = riskTagRepository.findByRiskAnalysisId(analysis.getId())
                .stream()
                .map(tag -> new RiskTagSnapshot(
                        tag.getRiskTag().getCode(),
                        tag.getRiskTag().getName(),
                        tag.effectiveFinalState(),
                        maskNullable(tag.getEvidence(), context),
                        maskNullable(tag.getReason(), context)
                ))
                .toList();
        List<RevisionSuggestionSnapshot> suggestions = analysis.getTagRevisionSuggestions().stream()
                .map(suggestion -> new RevisionSuggestionSnapshot(
                        suggestion.getCode(),
                        mask(suggestion.getOriginalExpression(), context),
                        mask(suggestion.getSuggestedExpression(), context),
                        mask(suggestion.getReason(), context)
                ))
                .toList();
        var safetyFlags = analysis.safetyFlags();

        return new AnalysisSnapshot(
                analysis.getId(),
                analysis.getMessage().getId(),
                analysis.getContentVersion(),
                analysis.getRiskScore(),
                analysis.getRiskLevel(),
                analysis.getAnalysisMode(),
                analysis.getAnalyzedAt(),
                analysis.getModelName(),
                safetyFlags == null ? null : safetyFlags.credibleThreat(),
                safetyFlags == null ? null : safetyFlags.studentSafetyConcern(),
                safetyFlags == null ? null : safetyFlags.privacyExposure(),
                analysis.getRevisionStatus(),
                maskNullable(analysis.getRevisionReason(), context),
                maskNullable(analysis.getAiRevision(), context),
                tags,
                suggestions,
                reviews.stream().map(review -> new RiskReviewSnapshot(
                        review.getId(),
                        review.getPreviousScore(),
                        review.getPreviousLevel(),
                        review.getReviewedScore(),
                        review.getReviewedLevel(),
                        review.getDirection(),
                        maskNullable(review.getReason(), context),
                        review.getReviewedAt()
                )).toList()
        );
    }

    private HistorySnapshot historySnapshot(
            TeacherHistoryResponse history,
            PiiMaskingContext context
    ) {
        return new HistorySnapshot(
                history.logComplete(),
                history.missingLogs().stream()
                        .map(log -> new MissingLogSnapshot(
                                log.expectedType(),
                                log.referenceType(),
                                log.referenceId(),
                                maskNullable(log.description(), context)
                        ))
                        .toList(),
                history.events().stream()
                        .map(event -> new HistoryEventSnapshot(
                                event.eventId(),
                                event.type(),
                                event.occurredAt(),
                                event.referenceType(),
                                event.referenceId(),
                                event.previousStatus(),
                                event.newStatus(),
                                event.riskScore(),
                                event.riskLevel(),
                                event.previousRiskScore(),
                                event.previousRiskLevel(),
                                maskNullable(event.reason(), context)
                        ))
                        .toList()
        );
    }

    private MediationPackage findPackage(Long complaintId, String packageId) {
        return packageRepository.findByPublicIdAndComplaintId(packageId, complaintId)
                .orElseThrow(() -> new MediationPackageNotFoundException(
                        "대응 기록 패키지를 찾을 수 없습니다."
                ));
    }

    private Complaint findComplaint(Long complaintId) {
        return complaintRepository.findDetailById(complaintId)
                .orElseThrow(() -> new ComplaintNotFoundException("민원을 찾을 수 없습니다."));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("사용자를 찾을 수 없습니다."));
    }

    private void validatePackageAccess(User user, Complaint complaint) {
        complaintAccessPolicy.validateAssignedTeacher(user, complaint);
        if (complaint.getStatus() == ComplaintStatus.DRAFT) {
            throw new ComplaintAccessDeniedException("제출되지 않은 민원은 패키지로 만들 수 없습니다.");
        }
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("멱등 키가 필요합니다.");
        }
        String normalized = idempotencyKey.trim();
        if (normalized.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new IllegalArgumentException("멱등 키는 100자를 초과할 수 없습니다.");
        }
        return normalized;
    }

    private String mask(String value, PiiMaskingContext context) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return piiMaskingService.mask(value, context);
    }

    private String maskNullable(String value, PiiMaskingContext context) {
        return mask(value, context);
    }

    private String serialize(MediationPackageContent content) {
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JacksonException e) {
            throw new InternalSystemException("대응 기록 패키지를 직렬화하지 못했습니다.");
        }
    }

    private MediationPackageContent deserialize(String snapshotJson) {
        try {
            return objectMapper.readValue(snapshotJson, MediationPackageContent.class);
        } catch (JacksonException e) {
            throw new InternalSystemException("저장된 대응 기록 패키지를 읽지 못했습니다.");
        }
    }

    private MediationPackageResponse response(MediationPackage mediationPackage) {
        return response(mediationPackage, deserialize(mediationPackage.getSnapshotJson()));
    }

    private MediationPackageResponse response(
            MediationPackage mediationPackage,
            MediationPackageContent content
    ) {
        return new MediationPackageResponse(
                mediationPackage.getPublicId(),
                mediationPackage.getComplaint().getId(),
                mediationPackage.getSchemaVersion(),
                mediationPackage.isOriginalMessagesIncluded(),
                mediationPackage.getChecksumSha256(),
                mediationPackage.getCreatedAt(),
                content
        );
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
