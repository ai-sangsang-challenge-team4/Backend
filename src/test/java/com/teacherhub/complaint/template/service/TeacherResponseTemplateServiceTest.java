package com.teacherhub.complaint.template.service;

import com.teacherhub.complaint.access.ComplaintAccessPolicy;
import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.enums.MessageStatus;
import com.teacherhub.complaint.enums.WaitingFor;
import com.teacherhub.complaint.repository.ComplaintEventRepository;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.complaint.template.entity.ResponseTemplate;
import com.teacherhub.complaint.template.entity.ResponseTemplateVersion;
import com.teacherhub.complaint.template.repository.ResponseTemplateAdoptionRepository;
import com.teacherhub.complaint.template.repository.ResponseTemplateVersionRepository;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.repository.EmergencyRiskReviewRequestRepository;
import com.teacherhub.risk.repository.RiskAnalysisRepository;
import com.teacherhub.risk.repository.RiskReviewRepository;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TeacherResponseTemplateServiceTest {
    private final ComplaintRepository complaints = mock(ComplaintRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ComplaintAccessPolicy accessPolicy = mock(ComplaintAccessPolicy.class);
    private final RiskAnalysisRepository analyses = mock(RiskAnalysisRepository.class);
    private final RiskReviewRepository reviews = mock(RiskReviewRepository.class);
    private final EmergencyRiskReviewRequestRepository emergencyReviews =
            mock(EmergencyRiskReviewRequestRepository.class);
    private final ResponseTemplateVersionRepository versions = mock(ResponseTemplateVersionRepository.class);
    private final ResponseTemplateAdoptionRepository adoptions = mock(ResponseTemplateAdoptionRepository.class);
    private final ComplaintEventRepository events = mock(ComplaintEventRepository.class);
    private final TeacherResponseTemplateService service = new TeacherResponseTemplateService(
            complaints,
            users,
            accessPolicy,
            analyses,
            reviews,
            emergencyReviews,
            versions,
            adoptions,
            events
    );

    @Test
    void returnsLatestActiveHighTemplateWithRequiredVariables() {
        User teacher = mock(User.class);
        Complaint complaint = mock(Complaint.class);
        RiskAnalysis analysis = mock(RiskAnalysis.class);
        when(users.findById(7L)).thenReturn(Optional.of(teacher));
        when(complaints.findDetailById(42L)).thenReturn(Optional.of(complaint));
        when(complaint.getStatus()).thenReturn(ComplaintStatus.OPEN);
        when(complaint.getWaitingFor()).thenReturn(WaitingFor.TEACHER);
        when(emergencyReviews.findFirstByComplaintIdOrderByRequestedAtDescIdDesc(42L))
                .thenReturn(Optional.empty());
        when(analyses.findFirstByComplaintIdAndCompletedTrueAndMessage_StatusNotOrderByIdDesc(
                42L,
                MessageStatus.DRAFT
        )).thenReturn(Optional.of(analysis));
        when(analysis.getId()).thenReturn(100L);
        when(analysis.getRiskLevel()).thenReturn(RiskLevel.HIGH);
        when(reviews.findFirstByRiskAnalysisIdOrderByReviewedAtDescIdDesc(100L)).thenReturn(Optional.empty());

        ResponseTemplate template = new ResponseTemplate("HIGH_ACK", "High 확인 안내", RiskLevel.HIGH);
        ReflectionTestUtils.setField(template, "id", 10L);
        ResponseTemplateVersion oldVersion = new ResponseTemplateVersion(template, 1, "이전 {{parentName}}", true);
        ResponseTemplateVersion latestVersion = new ResponseTemplateVersion(
                template,
                2,
                "{{parentName}} 보호자님, {{nextAction}} 진행 중입니다.",
                true
        );
        ReflectionTestUtils.setField(oldVersion, "id", 11L);
        ReflectionTestUtils.setField(latestVersion, "id", 12L);
        when(versions.findByActiveTrueAndTemplate_RiskLevelOrderByTemplate_NameAscVersionDesc(RiskLevel.HIGH))
                .thenReturn(List.of(latestVersion, oldVersion));

        var result = service.findAvailable(7L, 42L);

        assertThat(result).singleElement().satisfies(response -> {
            assertThat(response.version()).isEqualTo(2);
            assertThat(response.requiredVariables()).containsExactly("parentName", "nextAction");
        });
        verifyNoInteractions(adoptions, events);
    }

    @Test
    void returnsEmptyTemplatesWhenSubmissionHasNoCompletedAnalysis() {
        User teacher = mock(User.class);
        Complaint complaint = mock(Complaint.class);
        when(users.findById(7L)).thenReturn(Optional.of(teacher));
        when(complaints.findDetailById(42L)).thenReturn(Optional.of(complaint));
        when(complaint.getStatus()).thenReturn(ComplaintStatus.OPEN);
        when(complaint.getWaitingFor()).thenReturn(WaitingFor.TEACHER);
        when(emergencyReviews.findFirstByComplaintIdOrderByRequestedAtDescIdDesc(42L))
                .thenReturn(Optional.empty());
        when(analyses.findFirstByComplaintIdAndCompletedTrueAndMessage_StatusNotOrderByIdDesc(
                42L,
                MessageStatus.DRAFT
        )).thenReturn(Optional.empty());

        assertThat(service.findAvailable(7L, 42L)).isEmpty();
        verifyNoInteractions(versions, reviews, adoptions, events);
    }
}
