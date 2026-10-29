package com.teacherhub.complaint.escalation.service;

import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintEvent;
import com.teacherhub.complaint.entity.ComplaintMessage;
import com.teacherhub.complaint.enums.MessageStatus;
import com.teacherhub.complaint.enums.MessageType;
import com.teacherhub.complaint.repository.ComplaintEventRepository;
import com.teacherhub.complaint.repository.ComplaintMessageRepository;
import com.teacherhub.complaint.history.service.ComplaintHistoryService;
import com.teacherhub.complaint.teacher.dto.query.TeacherHistoryResponse;
import com.teacherhub.complaint.teacher.policy.TeacherRiskScale;
import com.teacherhub.escalation.dto.EscalationMessageRequest;
import com.teacherhub.escalation.entity.ComplaintEscalation;
import com.teacherhub.escalation.enums.EscalationStatus;
import com.teacherhub.escalation.enums.EscalationScope;
import com.teacherhub.risk.enums.RiskReviewStatus;
import com.teacherhub.escalation.exception.EscalationAccessDeniedException;
import com.teacherhub.escalation.exception.EscalationStateException;
import com.teacherhub.escalation.repository.ComplaintEscalationRepository;
import com.teacherhub.escalation.service.AdminEscalationService;
import com.teacherhub.user.entity.Teacher;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.enums.UserRole;
import com.teacherhub.user.repository.UserRepository;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.entity.EmergencyRiskReviewRequest;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.masking.PiiMaskingService;
import com.teacherhub.risk.repository.ComplaintRiskTagRepository;
import com.teacherhub.risk.repository.RiskReviewRepository;
import com.teacherhub.risk.repository.EmergencyRiskReviewRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminEscalationServiceTest {

    private final ComplaintEscalationRepository escalationRepository = mock(ComplaintEscalationRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ComplaintMessageRepository messageRepository = mock(ComplaintMessageRepository.class);
    private final ComplaintEventRepository eventRepository = mock(ComplaintEventRepository.class);
    private final ComplaintRiskTagRepository riskTagRepository = mock(ComplaintRiskTagRepository.class);
    private final RiskReviewRepository riskReviewRepository = mock(RiskReviewRepository.class);
    private final EmergencyRiskReviewRequestRepository emergencyReviewRequestRepository =
            mock(EmergencyRiskReviewRequestRepository.class);
    private final TeacherRiskScale teacherRiskScale = mock(TeacherRiskScale.class);
    private final PiiMaskingService piiMaskingService = mock(PiiMaskingService.class);
    private final ComplaintHistoryService complaintHistoryService = mock(ComplaintHistoryService.class);
    private final AdminEscalationService service = new AdminEscalationService(
            escalationRepository,
            userRepository,
            messageRepository,
            eventRepository,
            riskTagRepository,
            riskReviewRepository,
            emergencyReviewRequestRepository,
            teacherRiskScale,
            piiMaskingService,
            complaintHistoryService
    );

    private User admin;
    private User teacherUser;
    private ComplaintEscalation escalation;

    @BeforeEach
    void setUp() {
        admin = mock(User.class);
        User otherAdmin = mock(User.class);
        teacherUser = mock(User.class);
        Teacher teacher = mock(Teacher.class);
        Complaint complaint = mock(Complaint.class);
        RiskAnalysis analysis = mock(RiskAnalysis.class);

        when(admin.getId()).thenReturn(20L);
        when(admin.getName()).thenReturn("관리자");
        when(admin.getRole()).thenReturn(UserRole.ADMIN);
        when(otherAdmin.getId()).thenReturn(21L);
        when(otherAdmin.getRole()).thenReturn(UserRole.ADMIN);
        when(teacherUser.getName()).thenReturn("담임 교사");
        when(teacher.getUser()).thenReturn(teacherUser);
        when(complaint.getId()).thenReturn(42L);
        when(analysis.getId()).thenReturn(100L);
        when(analysis.getRiskScore()).thenReturn(7);
        when(analysis.getRiskLevel()).thenReturn(com.teacherhub.risk.enums.RiskLevel.EMERGENCY);
        when(analysis.getComplaint()).thenReturn(complaint);

        escalation = new ComplaintEscalation(
                complaint,
                analysis,
                teacher,
                admin,
                Set.of(EscalationScope.MASKING_MESSAGES),
                "긴급 대응 지원이 필요합니다."
        );

        when(userRepository.findById(20L)).thenReturn(Optional.of(admin));
        when(userRepository.findById(21L)).thenReturn(Optional.of(otherAdmin));
        when(escalationRepository.findDetailById(1L)).thenReturn(Optional.of(escalation));
        when(escalationRepository.saveAndFlush(any(ComplaintEscalation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(emergencyReviewRequestRepository
                .findFirstByComplaintIdOrderByRequestedAtDescIdDesc(42L))
                .thenReturn(Optional.empty());
        when(emergencyReviewRequestRepository
                .findFirstByComplaintIdOrderByRequestedAtDescIdDesc(42L))
                .thenReturn(Optional.empty());
        when(complaintHistoryService.findHistory(42L))
                .thenReturn(new TeacherHistoryResponse(42L, List.of()));
    }

    @Test
    void sharedCaseMovesToUnderReview() {
        var response = service.startReview(
                20L,
                1L,
                new EscalationMessageRequest("  학생 안전 조치를 먼저 확인합니다.  ")
        );

        assertThat(response.escalationStatus()).isEqualTo(EscalationStatus.UNDER_REVIEW);
        assertThat(escalation.getReviewMemo()).isEqualTo("학생 안전 조치를 먼저 확인합니다.");
        verify(eventRepository).save(any(ComplaintEvent.class));
    }

    @Test
    void unassignedAdminCannotReadSharedCase() {
        assertThatThrownBy(() -> service.findOne(21L, 1L))
                .isInstanceOf(EscalationAccessDeniedException.class);
    }

    @Test
    void summaryContainsEmergencyReviewStatusAndSafetyLock() {
        EmergencyRiskReviewRequest emergencyReview = new EmergencyRiskReviewRequest(
                escalation.getComplaint(),
                escalation.getRiskAnalysis(),
                escalation.getSharedBy(),
                admin,
                7,
                RiskLevel.EMERGENCY,
                5,
                RiskLevel.HIGH,
                "Emergency 재검토 요청"
        );
        when(emergencyReviewRequestRepository
                .findFirstByComplaintIdOrderByRequestedAtDescIdDesc(42L))
                .thenReturn(Optional.of(emergencyReview));
        when(riskReviewRepository.findFirstByRiskAnalysisIdOrderByReviewedAtDescIdDesc(100L))
                .thenReturn(Optional.empty());

        var response = service.findOne(20L, 1L);

        assertThat(response.riskReviewStatus())
                .isEqualTo(RiskReviewStatus.PENDING_ADMIN_APPROVAL);
        assertThat(response.safetyLock()).isTrue();
    }

    @Test
    void detailContainsShareContentAndReusesTeacherRiskAndHistoryShapes() {
        ComplaintMessage message = mock(ComplaintMessage.class);
        ComplaintEvent submittedEvent = mock(ComplaintEvent.class);
        LocalDateTime submittedAt = LocalDateTime.of(2026, 10, 27, 10, 0);
        when(message.getId()).thenReturn(11L);
        when(message.getType()).thenReturn(MessageType.PARENT_INQUIRY);
        when(message.getSender()).thenReturn(teacherUser);
        when(message.getContent()).thenReturn("010-1234-5678로 연락해 주세요.");
        when(message.getMaskedContent()).thenReturn("[전화번호]로 연락해 주세요.");
        when(messageRepository.findByComplaintIdAndStatusNotOrderByIdAsc(42L, MessageStatus.DRAFT))
                .thenReturn(List.of(message));
        when(eventRepository.findFirstByComplaintIdAndEventTypeAndNewStatusOrderByOccurredAtDescIdDesc(
                42L,
                com.teacherhub.complaint.enums.MessageEventType.STATUS_CHANGED,
                com.teacherhub.complaint.enums.ComplaintStatus.OPEN
        )).thenReturn(Optional.of(submittedEvent));
        when(submittedEvent.getOccurredAt()).thenReturn(submittedAt);
        when(riskTagRepository.findByRiskAnalysisId(100L)).thenReturn(List.of());
        when(riskReviewRepository.findFirstByRiskAnalysisIdOrderByReviewedAtDescIdDesc(100L))
                .thenReturn(Optional.empty());
        when(teacherRiskScale.initialScoreFor(com.teacherhub.risk.enums.RiskLevel.EMERGENCY))
                .thenReturn(7);
        when(eventRepository.findByComplaintIdOrderByOccurredAtDescIdDesc(42L))
                .thenReturn(List.of());

        var messages = service.findMessages(20L, 1L);
        var risk = service.findRisk(20L, 1L);
        var history = service.findHistory(20L, 1L);

        assertThat(messages.reason()).isEqualTo("긴급 대응 지원이 필요합니다.");
        assertThat(messages.maskedMessages()).hasSize(1);
        assertThat(messages.maskedMessages().get(0).content())
                .isEqualTo("[전화번호]로 연락해 주세요.");
        assertThat(messages.originalMessages()).isEmpty();
        assertThat(messages.mediationPackageIncluded()).isFalse();
        assertThat(risk.riskLevel())
                .isEqualTo(com.teacherhub.risk.enums.RiskLevel.EMERGENCY);
        assertThat(history.complaintId()).isEqualTo(42L);
    }

    @Test
    void shareContentFollowsSelectedScopes() {
        escalation.getScopes().add(EscalationScope.ORIGINAL_MESSAGES);
        escalation.getScopes().add(EscalationScope.MEDIATION_PACKAGE);
        ComplaintMessage message = mock(ComplaintMessage.class);
        ComplaintEvent submittedEvent = mock(ComplaintEvent.class);
        LocalDateTime submittedAt = LocalDateTime.of(2026, 10, 27, 10, 0);
        when(message.getId()).thenReturn(11L);
        when(message.getType()).thenReturn(MessageType.PARENT_INQUIRY);
        when(message.getSender()).thenReturn(teacherUser);
        when(message.getContent()).thenReturn("010-1234-5678로 연락해 주세요.");
        when(message.getMaskedContent()).thenReturn("[전화번호]로 연락해 주세요.");
        when(messageRepository.findByComplaintIdAndStatusNotOrderByIdAsc(42L, MessageStatus.DRAFT))
                .thenReturn(List.of(message));
        when(eventRepository.findFirstByComplaintIdAndEventTypeAndNewStatusOrderByOccurredAtDescIdDesc(
                42L,
                com.teacherhub.complaint.enums.MessageEventType.STATUS_CHANGED,
                com.teacherhub.complaint.enums.ComplaintStatus.OPEN
        )).thenReturn(Optional.of(submittedEvent));
        when(submittedEvent.getOccurredAt()).thenReturn(submittedAt);

        var response = service.findMessages(20L, 1L);

        assertThat(response.maskedMessages().get(0).content())
                .isEqualTo("[전화번호]로 연락해 주세요.");
        assertThat(response.originalMessages().get(0).content())
                .isEqualTo("010-1234-5678로 연락해 주세요.");
        assertThat(response.mediationPackageIncluded()).isTrue();
    }

    @Test
    void stateOrderCannotBeSkipped() {
        assertThatThrownBy(() -> service.complete(
                20L,
                1L,
                new EscalationMessageRequest("승인합니다.")
        )).isInstanceOf(EscalationStateException.class);
    }

    @Test
    void underReviewCaseCanBeCompletedWithResult() {
        service.startReview(20L, 1L, new EscalationMessageRequest("검토를 시작합니다."));

        var response = service.complete(
                20L,
                1L,
                new EscalationMessageRequest("관리자 검토를 완료합니다.")
        );

        assertThat(response.escalationStatus()).isEqualTo(EscalationStatus.COMPLETED);
        assertThat(response.riskReviewStatus()).isNull();
        assertThat(escalation.getResult()).isEqualTo("관리자 검토를 완료합니다.");
    }

    @Test
    void pendingSeparatedEmergencyReviewMustBeDecidedBeforeCompletion() {
        EmergencyRiskReviewRequest emergencyReview = new EmergencyRiskReviewRequest(
                escalation.getComplaint(),
                escalation.getRiskAnalysis(),
                escalation.getSharedBy(),
                admin,
                7,
                RiskLevel.EMERGENCY,
                5,
                RiskLevel.HIGH,
                "Emergency 재검토 요청"
        );
        when(emergencyReviewRequestRepository
                .findFirstByComplaintIdOrderByRequestedAtDescIdDesc(42L))
                .thenReturn(Optional.of(emergencyReview));
        when(riskReviewRepository.findFirstByRiskAnalysisIdOrderByReviewedAtDescIdDesc(100L))
                .thenReturn(Optional.empty());

        service.startReview(20L, 1L, new EscalationMessageRequest("검토를 시작합니다."));

        assertThatThrownBy(() -> service.complete(
                20L,
                1L,
                new EscalationMessageRequest("처리 완료")
        )).isInstanceOf(EscalationStateException.class);
    }
}
