package com.teacherhub.risk.service;

import com.teacherhub.complaint.access.ComplaintAccessPolicy;
import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintEvent;
import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.enums.WaitingFor;
import com.teacherhub.complaint.repository.ComplaintEventRepository;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.complaint.teacher.policy.TeacherRiskScale;
import com.teacherhub.risk.enums.RiskReviewStatus;
import com.teacherhub.escalation.repository.ComplaintEscalationRepository;
import com.teacherhub.risk.dto.EmergencyReviewCreateRequest;
import com.teacherhub.risk.dto.EmergencyReviewUpdateRequest;
import com.teacherhub.risk.entity.EmergencyRiskReviewRequest;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.entity.RiskReview;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.enums.RiskReviewDirection;
import com.teacherhub.risk.exception.EmergencyReviewAccessDeniedException;
import com.teacherhub.risk.exception.EmergencyReviewDuplicateException;
import com.teacherhub.risk.repository.EmergencyRiskReviewRequestRepository;
import com.teacherhub.risk.repository.RiskAnalysisRepository;
import com.teacherhub.risk.repository.RiskReviewRepository;
import com.teacherhub.user.entity.Teacher;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.enums.UserRole;
import com.teacherhub.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmergencyReviewServiceTest {

    private final ComplaintRepository complaintRepository = mock(ComplaintRepository.class);
    private final RiskAnalysisRepository analysisRepository = mock(RiskAnalysisRepository.class);
    private final RiskReviewRepository reviewRepository = mock(RiskReviewRepository.class);
    private final EmergencyRiskReviewRequestRepository requestRepository =
            mock(EmergencyRiskReviewRequestRepository.class);
    private final ComplaintEscalationRepository escalationRepository =
            mock(ComplaintEscalationRepository.class);
    private final ComplaintEventRepository eventRepository = mock(ComplaintEventRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ComplaintAccessPolicy accessPolicy = mock(ComplaintAccessPolicy.class);
    private final TeacherRiskScale riskScale = mock(TeacherRiskScale.class);
    private final EmergencyReviewService service = new EmergencyReviewService(
            complaintRepository,
            analysisRepository,
            reviewRepository,
            requestRepository,
            escalationRepository,
            eventRepository,
            userRepository,
            accessPolicy,
            riskScale
    );

    private User teacherUser;
    private User reviewer;
    private Teacher teacher;
    private Complaint complaint;
    private RiskAnalysis analysis;

    @BeforeEach
    void setUp() {
        teacherUser = mock(User.class);
        reviewer = mock(User.class);
        teacher = mock(Teacher.class);
        complaint = mock(Complaint.class);
        analysis = mock(RiskAnalysis.class);

        when(teacherUser.getId()).thenReturn(10L);
        when(teacherUser.getName()).thenReturn("담임 교사");
        when(teacherUser.getRole()).thenReturn(UserRole.TEACHER);
        when(reviewer.getId()).thenReturn(20L);
        when(reviewer.getName()).thenReturn("지정 검토자");
        when(reviewer.getRole()).thenReturn(UserRole.ADMIN);
        when(teacher.getUser()).thenReturn(teacherUser);
        when(complaint.getId()).thenReturn(42L);
        when(complaint.getTeacher()).thenReturn(teacher);
        when(complaint.getStatus()).thenReturn(ComplaintStatus.OPEN);
        when(complaint.getWaitingFor()).thenReturn(WaitingFor.TEACHER);
        when(analysis.getId()).thenReturn(100L);
        when(analysis.getRiskLevel()).thenReturn(RiskLevel.EMERGENCY);

        when(userRepository.findById(10L)).thenReturn(Optional.of(teacherUser));
        when(userRepository.findById(20L)).thenReturn(Optional.of(reviewer));
        when(userRepository.findFirstByRoleOrderByIdAsc(UserRole.ADMIN))
                .thenReturn(Optional.of(reviewer));
        when(complaintRepository.findForUpdate(42L)).thenReturn(Optional.of(complaint));
        when(analysisRepository.findFirstByComplaintIdAndCompletedTrueOrderByIdDesc(42L))
                .thenReturn(Optional.of(analysis));
        when(reviewRepository.findFirstByRiskAnalysisIdOrderByReviewedAtDescIdDesc(100L))
                .thenReturn(Optional.empty());
        when(requestRepository.findActiveByComplaintId(
                42L,
                RiskReviewStatus.PENDING_ADMIN_APPROVAL
        )).thenReturn(Optional.empty());
        when(riskScale.initialScoreFor(RiskLevel.EMERGENCY)).thenReturn(7);
        when(riskScale.levelFor(5)).thenReturn(RiskLevel.HIGH);
        when(requestRepository.saveAndFlush(any(EmergencyRiskReviewRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(reviewRepository.save(any(RiskReview.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(eventRepository.save(any(ComplaintEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsRequestWithoutAdminShareAndEnablesSafetyLock() {
        when(escalationRepository.findByComplaintId(42L)).thenReturn(Optional.empty());

        var response = service.request(
                10L,
                42L,
                new EmergencyReviewCreateRequest(5, "즉시 위해 가능성이 낮습니다.", null)
        );

        assertThat(response.status()).isEqualTo(RiskReviewStatus.PENDING_ADMIN_APPROVAL);
        assertThat(response.safetyLock()).isTrue();
        assertThat(response.reviewerId()).isEqualTo(20L);
        verify(requestRepository).saveAndFlush(any(EmergencyRiskReviewRequest.class));
    }

    @Test
    void raisingRiskToEmergencyAppliesAndEnablesSafetyLock() {
        when(escalationRepository.findByComplaintId(42L)).thenReturn(Optional.empty());
        RiskReview emergencyReview = new RiskReview(
                analysis,
                teacher,
                6,
                RiskLevel.HIGH,
                7,
                RiskLevel.EMERGENCY,
                RiskReviewDirection.RAISED,
                "Emergency 상향 검토"
        );

        var response = service.activateSafetyLock(teacherUser, complaint, emergencyReview);

        assertThat(response.status()).isEqualTo(RiskReviewStatus.APPLIED);
        assertThat(response.safetyLock()).isTrue();
        assertThat(response.requestedRiskLevel()).isEqualTo(RiskLevel.EMERGENCY);
        verify(requestRepository).saveAndFlush(any(EmergencyRiskReviewRequest.class));
        verify(eventRepository).save(any(ComplaintEvent.class));
    }

    @Test
    void rejectsDuplicateWhilePendingOrSafetyLockIsActive() {
        when(requestRepository.findActiveByComplaintId(
                42L,
                RiskReviewStatus.PENDING_ADMIN_APPROVAL
        )).thenReturn(Optional.of(mock(EmergencyRiskReviewRequest.class)));

        assertThatThrownBy(() -> service.request(
                10L,
                42L,
                new EmergencyReviewCreateRequest(5, "재검토 사유", null)
        )).isInstanceOf(EmergencyReviewDuplicateException.class);

        verify(requestRepository, never()).saveAndFlush(any());
    }

    @Test
    void approvalLowersRiskButKeepsSafetyLock() {
        EmergencyRiskReviewRequest request = request();
        when(requestRepository.findForUpdate(300L)).thenReturn(Optional.of(request));

        var response = service.update(
                20L,
                300L,
                new EmergencyReviewUpdateRequest(
                        RiskReviewStatus.APPLIED,
                        null,
                        null
                )
        );

        assertThat(response.status()).isEqualTo(RiskReviewStatus.APPLIED);
        assertThat(response.safetyLock()).isTrue();
        verify(reviewRepository).save(any(RiskReview.class));
    }

    @Test
    void onlyDesignatedReviewerCanReleaseSafetyLock() {
        EmergencyRiskReviewRequest request = request();
        User otherAdmin = mock(User.class);
        when(otherAdmin.getId()).thenReturn(21L);
        when(otherAdmin.getRole()).thenReturn(UserRole.ADMIN);
        when(userRepository.findById(21L)).thenReturn(Optional.of(otherAdmin));
        when(requestRepository.findForUpdate(300L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.update(
                21L,
                300L,
                new EmergencyReviewUpdateRequest(null, false, null)
        )).isInstanceOf(EmergencyReviewAccessDeniedException.class);

        assertThatThrownBy(() -> service.update(
                20L,
                300L,
                new EmergencyReviewUpdateRequest(null, false, "승인 전 해제 시도")
        )).isInstanceOf(com.teacherhub.escalation.exception.EscalationStateException.class)
                .hasMessageContaining("승인 전");

        var response = service.update(
                20L,
                300L,
                new EmergencyReviewUpdateRequest(RiskReviewStatus.APPLIED, false, "하향 검토 승인 및 잠금 해제")
        );
        assertThat(response.safetyLock()).isFalse();
    }

    private EmergencyRiskReviewRequest request() {
        return new EmergencyRiskReviewRequest(
                complaint,
                analysis,
                teacher,
                reviewer,
                7,
                RiskLevel.EMERGENCY,
                5,
                RiskLevel.HIGH,
                "즉시 위해 가능성이 낮습니다."
        );
    }
}
