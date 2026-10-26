package com.teacherhub.complaint.escalation.service;

import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintEvent;
import com.teacherhub.complaint.entity.ComplaintMessage;
import com.teacherhub.complaint.enums.MessageStatus;
import com.teacherhub.complaint.enums.MessageType;
import com.teacherhub.complaint.repository.ComplaintEventRepository;
import com.teacherhub.complaint.repository.ComplaintMessageRepository;
import com.teacherhub.complaint.teacher.policy.TeacherRiskScale;
import com.teacherhub.escalation.dto.CompleteEscalationRequest;
import com.teacherhub.escalation.dto.StartEscalationReviewRequest;
import com.teacherhub.escalation.entity.ComplaintEscalation;
import com.teacherhub.escalation.enums.EscalationStatus;
import com.teacherhub.escalation.enums.EscalationScope;
import com.teacherhub.escalation.exception.EscalationAccessDeniedException;
import com.teacherhub.escalation.exception.EscalationStateException;
import com.teacherhub.escalation.exception.EscalationVersionConflictException;
import com.teacherhub.escalation.repository.ComplaintEscalationRepository;
import com.teacherhub.escalation.service.AdminEscalationService;
import com.teacherhub.user.entity.Teacher;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.enums.UserRole;
import com.teacherhub.user.repository.UserRepository;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.masking.PiiMaskingService;
import com.teacherhub.risk.repository.ComplaintRiskTagRepository;
import com.teacherhub.risk.repository.RiskReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    private final TeacherRiskScale teacherRiskScale = mock(TeacherRiskScale.class);
    private final PiiMaskingService piiMaskingService = mock(PiiMaskingService.class);
    private final AdminEscalationService service = new AdminEscalationService(
            escalationRepository,
            userRepository,
            messageRepository,
            eventRepository,
            riskTagRepository,
            riskReviewRepository,
            teacherRiskScale,
            piiMaskingService
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
    }

    @Test
    void sharedCaseMovesToUnderReview() {
        var response = service.startReview(20L, 1L, new StartEscalationReviewRequest(0L));

        assertThat(response.escalationStatus()).isEqualTo(EscalationStatus.UNDER_REVIEW);
        verify(eventRepository).save(any(ComplaintEvent.class));
    }

    @Test
    void unassignedAdminCannotReadSharedCase() {
        assertThatThrownBy(() -> service.findOne(21L, 1L))
                .isInstanceOf(EscalationAccessDeniedException.class);
    }

    @Test
    void detailContainsShareContentAndReusesTeacherRiskAndHistoryShapes() {
        ComplaintMessage message = mock(ComplaintMessage.class);
        when(message.getId()).thenReturn(11L);
        when(message.getType()).thenReturn(MessageType.PARENT_INQUIRY);
        when(message.getSender()).thenReturn(teacherUser);
        when(message.getContent()).thenReturn("010-1234-5678로 연락해 주세요.");
        when(message.getMaskedContent()).thenReturn("[전화번호]로 연락해 주세요.");
        when(messageRepository.findByComplaintIdAndStatusNotOrderByIdAsc(42L, MessageStatus.DRAFT))
                .thenReturn(List.of(message));
        when(riskTagRepository.findByRiskAnalysisId(100L)).thenReturn(List.of());
        when(riskReviewRepository.findFirstByRiskAnalysisIdOrderByReviewedAtDescIdDesc(100L))
                .thenReturn(Optional.empty());
        when(teacherRiskScale.initialScoreFor(com.teacherhub.risk.enums.RiskLevel.EMERGENCY))
                .thenReturn(7);
        when(eventRepository.findByComplaintIdOrderByOccurredAtDescIdDesc(42L))
                .thenReturn(List.of());

        var response = service.findOne(20L, 1L);

        assertThat(response.shareContent().reason()).isEqualTo("긴급 대응 지원이 필요합니다.");
        assertThat(response.shareContent().maskedMessages()).hasSize(1);
        assertThat(response.shareContent().maskedMessages().get(0).content())
                .isEqualTo("[전화번호]로 연락해 주세요.");
        assertThat(response.shareContent().originalMessages()).isEmpty();
        assertThat(response.riskAnalysis().riskLevel())
                .isEqualTo(com.teacherhub.risk.enums.RiskLevel.EMERGENCY);
        assertThat(response.history().complaintId()).isEqualTo(42L);
    }

    @Test
    void stateOrderCannotBeSkipped() {
        assertThatThrownBy(() -> service.complete(
                20L,
                1L,
                new CompleteEscalationRequest(0L, "승인합니다.")
        )).isInstanceOf(EscalationStateException.class);
    }

    @Test
    void staleVersionReturnsConflict() {
        assertThatThrownBy(() -> service.startReview(
                20L,
                1L,
                new StartEscalationReviewRequest(3L)
        )).isInstanceOf(EscalationVersionConflictException.class);
    }

    @Test
    void underReviewCaseCanBeCompletedWithResult() {
        service.startReview(20L, 1L, new StartEscalationReviewRequest(0L));

        var response = service.complete(
                20L,
                1L,
                new CompleteEscalationRequest(0L, "위험도 하향 조정을 승인합니다.")
        );

        assertThat(response.escalationStatus()).isEqualTo(EscalationStatus.COMPLETED);
        assertThat(response.approved()).isTrue();
        assertThat(response.result()).isEqualTo("위험도 하향 조정을 승인합니다.");
    }
}
