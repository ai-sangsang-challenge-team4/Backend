package com.teacherhub.complaint.teacher.service;

import com.teacherhub.common.exception.UnauthorizedException;
import com.teacherhub.complaint.access.ComplaintAccessPolicy;
import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintEvent;
import com.teacherhub.complaint.entity.ComplaintMessage;
import com.teacherhub.complaint.enums.MessageEventType;
import com.teacherhub.complaint.enums.MessageType;
import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.exception.ComplaintAccessDeniedException;
import com.teacherhub.complaint.exception.ComplaintNotFoundException;
import com.teacherhub.complaint.repository.ComplaintEventRepository;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.complaint.repository.ComplaintMessageRepository;
import com.teacherhub.complaint.history.service.ComplaintHistoryService;
import com.teacherhub.complaint.enums.MessageStatus;
import com.teacherhub.complaint.teacher.dto.query.TeacherComplaintDetailResponse;
import com.teacherhub.complaint.teacher.dto.query.TeacherConversationResponse;
import com.teacherhub.complaint.teacher.dto.query.TeacherHistoryResponse;
import com.teacherhub.complaint.teacher.dto.query.TeacherOriginalViewResponse;
import com.teacherhub.complaint.teacher.dto.query.TeacherRiskResponse;
import com.teacherhub.complaint.teacher.dto.AllowedAction;
import com.teacherhub.complaint.teacher.policy.TeacherSupportPolicy;
import com.teacherhub.complaint.teacher.policy.TeacherRiskScale;
import com.teacherhub.complaint.teacher.provider.TeacherSupportContentProvider;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.entity.RiskReview;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.repository.ComplaintRiskTagRepository;
import com.teacherhub.risk.repository.RiskAnalysisRepository;
import com.teacherhub.risk.repository.RiskReviewRepository;
import com.teacherhub.risk.repository.EmergencyRiskReviewRequestRepository;
import com.teacherhub.school.repository.StudentClassRepository;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TeacherComplaintQueryService {

    private final ComplaintRepository complaintRepository;
    private final ComplaintEventRepository eventRepository;
    private final ComplaintMessageRepository messageRepository;
    private final RiskAnalysisRepository riskAnalysisRepository;
    private final RiskReviewRepository riskReviewRepository;
    private final EmergencyRiskReviewRequestRepository emergencyReviewRequestRepository;
    private final ComplaintRiskTagRepository complaintRiskTagRepository;
    private final StudentClassRepository studentClassRepository;
    private final UserRepository userRepository;
    private final ComplaintAccessPolicy complaintAccessPolicy;
    private final ComplaintHistoryService complaintHistoryService;
    private final TeacherSupportContentProvider supportContentProvider;
    private final TeacherSupportPolicy supportPolicy;
    private final TeacherRiskScale teacherRiskScale;

    public TeacherComplaintDetailResponse findDetail(Long userId, Long complaintId) {
        User user = findUser(userId);
        Complaint complaint = findTeacherComplaint(user, complaintId);
        var schoolClass = studentClassRepository
                .findTopByStudent_IdOrderBySchoolClass_AcademicYearDesc(complaint.getStudent().getId())
                .orElseThrow(() -> new IllegalStateException("학생의 학급 정보를 찾을 수 없습니다."))
                .getSchoolClass();
        RiskAnalysis analysis = latestAnalysis(complaint).orElse(null);
        return TeacherComplaintDetailResponse.from(
                complaint,
                schoolClass,
                submittedAt(complaint),
                analysis,
                analysis == null ? null : latestReview(analysis)
        );
    }

    @Transactional
    public TeacherConversationResponse findConversation(Long userId, Long complaintId) {
        User user = findUser(userId);
        Complaint complaint = findTeacherComplaint(user, complaintId);
        RiskAnalysis analysis = latestAnalysis(complaint).orElse(null);
        RiskReview latestReview = analysis == null ? null : latestReview(analysis);
        boolean safetyLock = isSafetyLocked(complaintId);
        markLatestParentMessageRead(complaintId, user);
        List<ComplaintMessage> messages = messageRepository
                .findByComplaintIdAndStatusNotOrderByIdAsc(complaintId, MessageStatus.DRAFT);
        Map<Long, RiskLevel> latestRiskByMessage = latestRiskByMessage(complaintId);
        Map<Long, String> bufferedSummaries = createBufferedSummaries(
                complaintId,
                messages,
                latestRiskByMessage
        );

        return TeacherConversationResponse.from(
                complaint,
                bufferedSummaries,
                analysis == null && !safetyLock
                        ? List.of(AllowedAction.REQUEST_MANUAL_REVIEW)
                        : supportPolicy.allowedActions(
                                safetyLock
                                        ? RiskLevel.EMERGENCY
                                        : latestReview == null
                                        ? analysis.getRiskLevel()
                                        : latestReview.getReviewedLevel()
                        ),
                submittedAt(complaint),
                messages
        );
    }

    private Map<Long, String> createBufferedSummaries(
            Long complaintId,
            List<ComplaintMessage> messages,
            Map<Long, RiskLevel> latestRiskByMessage
    ) {
        Map<Long, String> summaries = new HashMap<>();
        messages.stream()
                .filter(message -> message.getType() == MessageType.PARENT_INQUIRY)
                .filter(message -> latestRiskByMessage.get(message.getId()) != null)
                .filter(message -> latestRiskByMessage.get(message.getId()) != RiskLevel.LOW)
                .forEach(message -> {
                    try {
                        String summary = supportContentProvider.createBufferedSummary(message.getContent());
                        if (summary != null && !summary.isBlank()) {
                            summaries.put(message.getId(), summary);
                        }
                    } catch (RuntimeException exception) {
                        log.warn("Buffered summary generation failed; returning the original message. complaintId={}, messageId={}",
                                complaintId, message.getId(), exception);
                    }
                });
        return summaries;
    }

    private void markLatestParentMessageRead(Long complaintId, User actor) {
        messageRepository.findFirstByComplaintIdAndTypeAndStatusNotOrderByIdDesc(
                complaintId,
                MessageType.PARENT_INQUIRY,
                MessageStatus.DRAFT
        ).filter(ComplaintMessage::markRead)
                .ifPresent(message -> eventRepository.save(ComplaintEvent.messageRead(message, actor)));
    }

    @Transactional
    public TeacherOriginalViewResponse viewOriginal(Long userId, Long complaintId, Long messageId) {
        User user = findUser(userId);
        Complaint complaint = findTeacherComplaint(user, complaintId);
        ComplaintMessage message = messageRepository.findById(messageId)
                .filter(candidate -> candidate.getComplaint().getId().equals(complaintId))
                .filter(candidate -> candidate.getType() == MessageType.PARENT_INQUIRY)
                .filter(candidate -> candidate.getStatus() != MessageStatus.DRAFT)
                .orElseThrow(() -> new ComplaintNotFoundException("전송된 메시지 원문을 찾을 수 없습니다."));
        return recordOriginalView(complaint, message, user);
    }

    private TeacherOriginalViewResponse recordOriginalView(
            Complaint complaint,
            ComplaintMessage message,
            User user
    ) {
        ComplaintEvent event = eventRepository.save(ComplaintEvent.originalViewed(
                complaint,
                user,
                message.getId()
        ));

        return new TeacherOriginalViewResponse(
                complaint.getId(),
                message.getId(),
                message.getContent(),
                event.getOccurredAt()
        );
    }

    public TeacherRiskResponse findRisk(Long userId, Long complaintId) {
        User user = findUser(userId);
        Complaint complaint = findTeacherComplaint(user, complaintId);
        RiskAnalysis analysis = latestAnalysis(complaint).orElse(null);
        boolean safetyLock = isSafetyLocked(complaintId);

        if (analysis == null) {
            return TeacherRiskResponse.unavailable(complaintId, safetyLock);
        }

        return TeacherRiskResponse.from(
                analysis,
                complaintRiskTagRepository.findByRiskAnalysisId(analysis.getId()),
                latestReview(analysis),
                teacherRiskScale.initialScoreFor(analysis.getRiskLevel()),
                safetyLock
        );
    }

    public TeacherHistoryResponse findHistory(Long userId, Long complaintId) {
        User user = findUser(userId);
        Complaint complaint = findTeacherComplaint(user, complaintId);

        return complaintHistoryService.findHistory(complaint.getId());
    }

    private Complaint findTeacherComplaint(User user, Long complaintId) {
        Complaint complaint = complaintRepository.findDetailById(complaintId)
                .orElseThrow(() -> new ComplaintNotFoundException("민원을 찾을 수 없습니다."));
        complaintAccessPolicy.validateAssignedTeacher(user, complaint);
        if (complaint.getStatus() == ComplaintStatus.DRAFT) {
            throw new ComplaintAccessDeniedException("제출되지 않은 민원은 교사가 조회할 수 없습니다.");
        }
        return complaint;
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("사용자를 찾을 수 없습니다."));
    }

    private LocalDateTime submittedAt(Complaint complaint) {
        return eventRepository
                .findFirstByComplaintIdAndEventTypeAndNewStatusOrderByOccurredAtDescIdDesc(
                        complaint.getId(),
                        MessageEventType.STATUS_CHANGED,
                        ComplaintStatus.OPEN
                )
                .map(ComplaintEvent::getOccurredAt)
                .orElseThrow(() -> new IllegalStateException("민원 제출 이력을 찾을 수 없습니다."));
    }

    private Optional<RiskAnalysis> latestAnalysis(Complaint complaint) {
        return riskAnalysisRepository
                .findFirstByComplaintIdAndCompletedTrueAndMessage_StatusNotOrderByIdDesc(
                        complaint.getId(),
                        MessageStatus.DRAFT
                );
    }

    private RiskReview latestReview(RiskAnalysis analysis) {
        return riskReviewRepository
                .findFirstByRiskAnalysisIdOrderByReviewedAtDescIdDesc(analysis.getId())
                .orElse(null);
    }

    private boolean isSafetyLocked(Long complaintId) {
        return emergencyReviewRequestRepository
                .findFirstByComplaintIdOrderByRequestedAtDescIdDesc(complaintId)
                .map(com.teacherhub.risk.entity.EmergencyRiskReviewRequest::isSafetyLock)
                .orElse(false);
    }

    private Map<Long, RiskLevel> latestRiskByMessage(Long complaintId) {
        Map<Long, RiskLevel> result = new HashMap<>();
        riskAnalysisRepository
                .findByComplaintIdAndCompletedTrueAndMessage_StatusNotOrderByIdAsc(
                        complaintId,
                        MessageStatus.DRAFT
                ).stream()
                .forEach(analysis -> result.put(
                        analysis.getMessage().getId(),
                        analysis.getRiskLevel()
                ));
        return result;
    }
}
