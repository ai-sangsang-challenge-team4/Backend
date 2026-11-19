package com.teacherhub.escalation.service;

import com.teacherhub.complaint.access.ComplaintAccessPolicy;
import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintEvent;
import com.teacherhub.escalation.dto.TeacherEscalationListQuery;
import com.teacherhub.escalation.dto.ShareEscalationRequest;
import com.teacherhub.escalation.entity.ComplaintEscalation;
import com.teacherhub.escalation.enums.EscalationStatus;
import com.teacherhub.escalation.enums.EscalationScope;
import com.teacherhub.escalation.exception.AdminNotFoundException;
import com.teacherhub.escalation.exception.EscalationReasonRequiredException;
import com.teacherhub.escalation.exception.EscalationAccessDeniedException;
import com.teacherhub.escalation.repository.ComplaintEscalationRepository;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.complaint.repository.ComplaintEventRepository;
import com.teacherhub.escalation.service.TeacherEscalationService;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.repository.RiskAnalysisRepository;
import com.teacherhub.risk.repository.RiskReviewRepository;
import com.teacherhub.risk.repository.EmergencyRiskReviewRequestRepository;
import com.teacherhub.user.entity.Teacher;
import com.teacherhub.user.entity.Student;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.enums.UserRole;
import com.teacherhub.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TeacherEscalationServiceTest {

    private final ComplaintRepository complaintRepository = mock(ComplaintRepository.class);
    private final ComplaintEscalationRepository escalationRepository = mock(ComplaintEscalationRepository.class);
    private final RiskAnalysisRepository riskAnalysisRepository = mock(RiskAnalysisRepository.class);
    private final RiskReviewRepository riskReviewRepository = mock(RiskReviewRepository.class);
    private final EmergencyRiskReviewRequestRepository emergencyReviewRequestRepository =
            mock(EmergencyRiskReviewRequestRepository.class);
    private final ComplaintEventRepository eventRepository = mock(ComplaintEventRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ComplaintAccessPolicy accessPolicy = mock(ComplaintAccessPolicy.class);
    private final TeacherEscalationService service = new TeacherEscalationService(
            complaintRepository,
            escalationRepository,
            riskAnalysisRepository,
            riskReviewRepository,
            emergencyReviewRequestRepository,
            eventRepository,
            userRepository,
            accessPolicy
    );

    private User teacherUser;
    private User admin;
    private Teacher teacher;
    private Complaint complaint;
    private RiskAnalysis analysis;
    private ShareEscalationRequest request;

    @BeforeEach
    void setUp() {
        teacherUser = mock(User.class);
        admin = mock(User.class);
        teacher = mock(Teacher.class);
        complaint = mock(Complaint.class);
        analysis = mock(RiskAnalysis.class);
        request = new ShareEscalationRequest(
                20L,
                Set.of(EscalationScope.MASKING_MESSAGES, EscalationScope.ORIGINAL_MESSAGES),
                "긴급 대응 지원이 필요합니다."
        );

        when(teacherUser.getId()).thenReturn(10L);
        when(teacherUser.getName()).thenReturn("담임 교사");
        when(teacherUser.getRole()).thenReturn(UserRole.TEACHER);
        when(admin.getId()).thenReturn(20L);
        when(admin.getName()).thenReturn("관리자");
        when(admin.getRole()).thenReturn(UserRole.ADMIN);
        when(teacher.getUser()).thenReturn(teacherUser);
        when(complaint.getId()).thenReturn(42L);
        when(complaint.getTeacher()).thenReturn(teacher);
        when(analysis.getId()).thenReturn(100L);
        when(analysis.getRiskLevel()).thenReturn(RiskLevel.EMERGENCY);
        when(analysis.getRiskScore()).thenReturn(70);

        when(userRepository.findById(10L)).thenReturn(Optional.of(teacherUser));
        when(userRepository.findById(20L)).thenReturn(Optional.of(admin));
        when(complaintRepository.findForUpdate(42L)).thenReturn(Optional.of(complaint));
        when(riskAnalysisRepository.findFirstByComplaintIdAndCompletedTrueOrderByIdDesc(42L))
                .thenReturn(Optional.of(analysis));
        when(riskReviewRepository.findFirstByRiskAnalysisIdOrderByReviewedAtDescIdDesc(100L))
                .thenReturn(Optional.empty());
        when(emergencyReviewRequestRepository
                .findFirstByComplaintIdOrderByRequestedAtDescIdDesc(42L))
                .thenReturn(Optional.empty());
        when(escalationRepository.saveAndFlush(any(ComplaintEscalation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void sharesEmergencyComplaintWhenTargetAndReasonExist() {
        when(escalationRepository.findByComplaintId(42L)).thenReturn(Optional.empty());

        var response = service.share(10L, 42L, request);

        assertThat(response.escalationStatus()).isEqualTo(EscalationStatus.SHARED);
        assertThat(response.currentRiskLevel()).isEqualTo(RiskLevel.EMERGENCY);
        verify(escalationRepository).saveAndFlush(any(ComplaintEscalation.class));
        verify(eventRepository).save(any(ComplaintEvent.class));
    }

    @Test
    void duplicateShareReturnsSameCaseAndCreatesOnlyOne() {
        ComplaintEscalation existing = new ComplaintEscalation(
                complaint,
                analysis,
                teacher,
                admin,
                Set.of(EscalationScope.MASKING_MESSAGES, EscalationScope.ORIGINAL_MESSAGES),
                "긴급 대응 지원이 필요합니다."
        );
        when(escalationRepository.findByComplaintId(42L)).thenReturn(Optional.of(existing));

        var response = service.share(10L, 42L, request);

        assertThat(response.escalationStatus()).isEqualTo(EscalationStatus.SHARED);
        verify(escalationRepository, never()).saveAndFlush(any());
    }

    @Test
    void closingConfirmationWithoutSharingKeepsNotShared() {
        when(complaintRepository.findDetailById(42L)).thenReturn(Optional.of(complaint));
        when(escalationRepository.findByComplaintId(42L)).thenReturn(Optional.empty());

        var response = service.findStatus(10L, 42L);

        assertThat(response.escalationStatus()).isEqualTo(EscalationStatus.NOT_SHARED);
        assertThat(response.currentRiskLevel()).isEqualTo(RiskLevel.EMERGENCY);
        assertThat(response.currentRiskScore()).isEqualTo(70);
        verify(escalationRepository, never()).saveAndFlush(any());
    }

    @Test
    void shareReasonIsRequired() {
        var requestWithoutReason = new ShareEscalationRequest(
                20L,
                Set.of(EscalationScope.MASKING_MESSAGES),
                "  "
        );

        assertThatThrownBy(() -> service.share(10L, 42L, requestWithoutReason))
                .isInstanceOf(EscalationReasonRequiredException.class);
    }

    @Test
    void selectedAdminMustExist() {
        var requestWithUnknownAdmin = new ShareEscalationRequest(
                99L,
                Set.of(EscalationScope.MASKING_MESSAGES),
                "긴급 대응 지원이 필요합니다."
        );

        assertThatThrownBy(() -> service.share(10L, 42L, requestWithUnknownAdmin))
                .isInstanceOf(AdminNotFoundException.class);
    }

    @Test
    void findsOnlyAuthenticatedTeachersSharedEscalationsAsPage() {
        ComplaintEscalation escalation = mock(ComplaintEscalation.class);
        Student student = mock(Student.class);
        var pageable = PageRequest.of(
                0,
                TeacherEscalationListQuery.PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "sharedAt")
                        .and(Sort.by(Sort.Direction.DESC, "id"))
        );
        var query = new TeacherEscalationListQuery(
                0,
                "  생활지도  ",
                EscalationStatus.UNDER_REVIEW,
                RiskLevel.EMERGENCY
        );

        when(escalation.getId()).thenReturn(1L);
        when(escalation.getComplaint()).thenReturn(complaint);
        when(escalation.getRiskAnalysis()).thenReturn(analysis);
        when(escalation.getSharedBy()).thenReturn(teacher);
        when(escalation.getAssignedAdmin()).thenReturn(admin);
        when(escalation.getScopes()).thenReturn(Set.of(EscalationScope.MASKING_MESSAGES));
        when(escalation.getReason()).thenReturn("공동 검토가 필요합니다.");
        when(escalation.getStatus()).thenReturn(EscalationStatus.UNDER_REVIEW);
        when(complaint.getTitle()).thenReturn("학생 생활지도 관련 문의");
        when(complaint.getStudent()).thenReturn(student);
        when(student.getName()).thenReturn("김학생");
        when(escalationRepository.searchSharedByTeacher(
                10L,
                "생활지도",
                EscalationStatus.UNDER_REVIEW,
                RiskLevel.EMERGENCY,
                pageable
        )).thenReturn(new PageImpl<>(List.of(escalation), pageable, 1));

        var response = service.findAll(10L, query);

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content()).singleElement().satisfies(item -> {
            assertThat(item.complaintId()).isEqualTo(42L);
            assertThat(item.complaintTitle()).isEqualTo("학생 생활지도 관련 문의");
            assertThat(item.assignedAdminName()).isEqualTo("관리자");
            assertThat(item.currentRiskLevel()).isEqualTo(RiskLevel.EMERGENCY);
        });
        verify(escalationRepository).searchSharedByTeacher(
                10L,
                "생활지도",
                EscalationStatus.UNDER_REVIEW,
                RiskLevel.EMERGENCY,
                pageable
        );
    }

    @Test
    void rejectsAnotherTeachersSharedEscalationDetail() {
        ComplaintEscalation escalation = mock(ComplaintEscalation.class);
        Teacher anotherTeacher = mock(Teacher.class);
        User anotherTeacherUser = mock(User.class);

        when(escalationRepository.findDetailById(1L)).thenReturn(Optional.of(escalation));
        when(escalation.getSharedBy()).thenReturn(anotherTeacher);
        when(anotherTeacher.getUser()).thenReturn(anotherTeacherUser);
        when(anotherTeacherUser.getId()).thenReturn(99L);

        assertThatThrownBy(() -> service.findOne(10L, 1L))
                .isInstanceOf(EscalationAccessDeniedException.class);
    }
}
