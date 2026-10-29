package com.teacherhub.complaint.teacher.service;

import com.teacherhub.complaint.access.ComplaintAccessPolicy;
import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintEvent;
import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.enums.WaitingFor;
import com.teacherhub.complaint.repository.ComplaintEventRepository;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.complaint.teacher.dto.risk.TeacherRiskReviewRequest;
import com.teacherhub.complaint.teacher.policy.TeacherRiskScale;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.entity.RiskReview;
import com.teacherhub.risk.dto.EmergencyReviewResponse;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.enums.RiskReviewStatus;
import com.teacherhub.risk.repository.RiskAnalysisRepository;
import com.teacherhub.risk.repository.RiskReviewRepository;
import com.teacherhub.risk.service.EmergencyReviewService;
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
    private final ComplaintEventRepository eventRepository = mock(ComplaintEventRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ComplaintAccessPolicy accessPolicy = mock(ComplaintAccessPolicy.class);
    private final TeacherRiskScale riskScale = mock(TeacherRiskScale.class);
    private final EmergencyReviewService emergencyReviewService = mock(EmergencyReviewService.class);
    private final TeacherRiskReviewService service = new TeacherRiskReviewService(
            complaintRepository,
            riskAnalysisRepository,
            riskReviewRepository,
            eventRepository,
            userRepository,
            accessPolicy,
            riskScale,
            emergencyReviewService
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
    void emergencyRiskCanBeRequestedBeforeAdminShare() {
        when(emergencyReviewService.request(eq(10L), eq(42L), any())).thenReturn(
                emergencyReviewResponse()
        );

        var response = service.review(
                10L,
                42L,
                new TeacherRiskReviewRequest(5, "위험도 하향 검토")
        );

        assertThat(response.status()).isEqualTo(RiskReviewStatus.PENDING_ADMIN_APPROVAL);
        assertThat(response.safetyLock()).isTrue();
        assertThat(response.emergencyReviewRequestId()).isEqualTo(300L);
        verify(riskReviewRepository, never()).save(any());
    }

    @Test
    void emergencyRiskChangeCreatesPendingAdminApprovalRequest() {
        when(emergencyReviewService.request(eq(10L), eq(42L), any())).thenReturn(
                emergencyReviewResponse()
        );

        var response = service.review(
                10L,
                42L,
                new TeacherRiskReviewRequest(5, "위험도 하향 요청")
        );

        assertThat(response.previousLevel()).isEqualTo(RiskLevel.EMERGENCY);
        assertThat(response.riskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(response.status()).isEqualTo(RiskReviewStatus.PENDING_ADMIN_APPROVAL);
        assertThat(response.safetyLock()).isTrue();
        assertThat(response.riskReviewId()).isNull();
        verify(riskReviewRepository, never()).save(any());
    }

    @Test
    void raisingRiskToEmergencyActivatesSafetyLockImmediately() {
        when(analysis.getRiskLevel()).thenReturn(RiskLevel.HIGH);
        when(riskScale.initialScoreFor(RiskLevel.HIGH)).thenReturn(6);
        when(riskScale.levelFor(7)).thenReturn(RiskLevel.EMERGENCY);
        when(riskReviewRepository.save(any(RiskReview.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(emergencyReviewService.activateSafetyLock(
                eq(teacherUser),
                eq(complaint),
                any(RiskReview.class)
        )).thenReturn(new EmergencyReviewResponse(
                301L,
                42L,
                100L,
                6,
                RiskLevel.HIGH,
                7,
                RiskLevel.EMERGENCY,
                "Emergency 상향 검토",
                RiskReviewStatus.APPLIED,
                true,
                20L,
                "관리자",
                LocalDateTime.of(2026, 10, 29, 14, 0),
                LocalDateTime.of(2026, 10, 29, 14, 0),
                LocalDateTime.of(2026, 10, 29, 14, 0)
        ));

        var response = service.review(
                10L,
                42L,
                new TeacherRiskReviewRequest(7, "Emergency 상향 검토")
        );

        assertThat(response.riskLevel()).isEqualTo(RiskLevel.EMERGENCY);
        assertThat(response.status()).isEqualTo(RiskReviewStatus.APPLIED);
        assertThat(response.safetyLock()).isTrue();
        verify(emergencyReviewService).activateSafetyLock(
                eq(teacherUser),
                eq(complaint),
                any(RiskReview.class)
        );
    }

    private EmergencyReviewResponse emergencyReviewResponse() {
        return new EmergencyReviewResponse(
                300L,
                42L,
                100L,
                7,
                RiskLevel.EMERGENCY,
                5,
                RiskLevel.HIGH,
                "위험도 하향 요청",
                RiskReviewStatus.PENDING_ADMIN_APPROVAL,
                true,
                20L,
                "관리자",
                LocalDateTime.of(2026, 10, 27, 14, 0),
                null,
                LocalDateTime.of(2026, 10, 27, 14, 0)
        );
    }
}
