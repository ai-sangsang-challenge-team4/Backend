package com.teacherhub.complaint.teacher.service;

import com.teacherhub.complaint.access.ComplaintAccessPolicy;
import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintEvent;
import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.enums.WaitingFor;
import com.teacherhub.escalation.entity.ComplaintEscalation;
import com.teacherhub.escalation.exception.AdminApprovalRequiredException;
import com.teacherhub.escalation.repository.ComplaintEscalationRepository;
import com.teacherhub.complaint.repository.ComplaintEventRepository;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.complaint.teacher.dto.risk.TeacherRiskReviewRequest;
import com.teacherhub.complaint.teacher.policy.TeacherRiskScale;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.escalation.enums.RiskReviewStatus;
import com.teacherhub.risk.repository.RiskAnalysisRepository;
import com.teacherhub.risk.repository.RiskReviewRepository;
import com.teacherhub.user.entity.Teacher;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TeacherRiskReviewServiceTest {

    private final ComplaintRepository complaintRepository = mock(ComplaintRepository.class);
    private final RiskAnalysisRepository riskAnalysisRepository = mock(RiskAnalysisRepository.class);
    private final RiskReviewRepository riskReviewRepository = mock(RiskReviewRepository.class);
    private final ComplaintEscalationRepository escalationRepository = mock(ComplaintEscalationRepository.class);
    private final ComplaintEventRepository eventRepository = mock(ComplaintEventRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ComplaintAccessPolicy accessPolicy = mock(ComplaintAccessPolicy.class);
    private final TeacherRiskScale riskScale = mock(TeacherRiskScale.class);
    private final TeacherRiskReviewService service = new TeacherRiskReviewService(
            complaintRepository,
            riskAnalysisRepository,
            riskReviewRepository,
            escalationRepository,
            eventRepository,
            userRepository,
            accessPolicy,
            riskScale
    );

    private User teacherUser;
    private Teacher teacher;
    private Complaint complaint;
    private RiskAnalysis analysis;

    @BeforeEach
    void setUp() {
        teacherUser = mock(User.class);
        teacher = mock(Teacher.class);
        complaint = mock(Complaint.class);
        analysis = mock(RiskAnalysis.class);

        when(teacherUser.getName()).thenReturn("담임 교사");
        when(teacher.getUser()).thenReturn(teacherUser);
        when(complaint.getId()).thenReturn(42L);
        when(complaint.getTeacher()).thenReturn(teacher);
        when(complaint.getStatus()).thenReturn(ComplaintStatus.OPEN);
        when(complaint.getWaitingFor()).thenReturn(WaitingFor.TEACHER);
        when(analysis.getId()).thenReturn(100L);
        when(analysis.getRiskLevel()).thenReturn(RiskLevel.EMERGENCY);
        when(analysis.getComplaint()).thenReturn(complaint);

        when(userRepository.findById(10L)).thenReturn(Optional.of(teacherUser));
        when(complaintRepository.findForUpdate(42L)).thenReturn(Optional.of(complaint));
        when(riskAnalysisRepository.findFirstByComplaintIdAndCompletedTrueOrderByIdDesc(42L))
                .thenReturn(Optional.of(analysis));
        when(riskReviewRepository.findFirstByRiskAnalysisIdOrderByReviewedAtDescIdDesc(100L))
                .thenReturn(Optional.empty());
        when(riskScale.initialScoreFor(RiskLevel.EMERGENCY)).thenReturn(7);
        when(riskScale.levelFor(5)).thenReturn(RiskLevel.HIGH);
    }

    @Test
    void emergencyRiskCannotChangeWithoutAdminApproval() {
        when(escalationRepository.findByComplaintId(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.review(
                10L,
                42L,
                new TeacherRiskReviewRequest(5, "위험도 하향 검토")
        )).isInstanceOf(AdminApprovalRequiredException.class);

        verify(riskReviewRepository, never()).save(any());
    }

    @Test
    void emergencyRiskChangeCreatesPendingAdminApprovalRequest() {
        ComplaintEscalation escalation = mock(ComplaintEscalation.class);
        when(escalation.getRiskAnalysis()).thenReturn(analysis);
        when(escalation.getId()).thenReturn(200L);
        when(escalation.getRiskChangeRequestedAt())
                .thenReturn(LocalDateTime.of(2026, 10, 27, 14, 0));
        when(escalationRepository.findByComplaintId(42L)).thenReturn(Optional.of(escalation));
        when(escalationRepository.saveAndFlush(escalation))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(eventRepository.save(any(ComplaintEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.review(
                10L,
                42L,
                new TeacherRiskReviewRequest(5, "위험도 하향 요청")
        );

        assertThat(response.previousLevel()).isEqualTo(RiskLevel.EMERGENCY);
        assertThat(response.riskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(response.status()).isEqualTo(RiskReviewStatus.PENDING_ADMIN_APPROVAL);
        assertThat(response.riskReviewId()).isNull();
        verify(escalation).requestRiskChange(
                7,
                RiskLevel.EMERGENCY,
                5,
                RiskLevel.HIGH,
                "위험도 하향 요청"
        );
        verify(riskReviewRepository, never()).save(any());
    }
}
