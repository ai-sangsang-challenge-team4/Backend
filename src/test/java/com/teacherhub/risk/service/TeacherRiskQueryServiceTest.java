package com.teacherhub.risk.service;

import com.teacherhub.complaint.access.ComplaintAccessPolicy;
import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.enums.ComplaintStatus;
import com.teacherhub.complaint.enums.MessageStatus;
import com.teacherhub.complaint.repository.ComplaintRepository;
import com.teacherhub.risk.dto.llm.SafetyFlags;
import com.teacherhub.risk.entity.ComplaintRiskTag;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.entity.RiskTag;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.enums.RiskTagCode;
import com.teacherhub.risk.enums.RiskTagState;
import com.teacherhub.risk.repository.ComplaintRiskTagRepository;
import com.teacherhub.risk.repository.EmergencyRiskReviewRequestRepository;
import com.teacherhub.risk.repository.RiskAnalysisRepository;
import com.teacherhub.risk.repository.RiskReviewRepository;
import com.teacherhub.user.entity.User;
import com.teacherhub.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TeacherRiskQueryServiceTest {

    @Test
    void returnsUnavailableResponseWhenCompletedAnalysisDoesNotExist() {
        Fixture fixture = new Fixture();
        when(fixture.analyses.findFirstByComplaintIdAndCompletedTrueAndMessage_StatusNotOrderByIdDesc(
                42L,
                MessageStatus.DRAFT
        )).thenReturn(Optional.empty());

        var response = fixture.service.findRisk(7L, 42L);

        assertThat(response.complaintId()).isEqualTo(42L);
        assertThat(response.riskAnalysisId()).isNull();
        assertThat(response.riskTagResults()).isEmpty();
        assertThat(response.emergencyOverride()).isNull();
    }

    @Test
    void returnsDetectedFactorsWithoutEmergencyOverrideForStudentSafetyConcern() {
        Fixture fixture = new Fixture();
        RiskAnalysis analysis = mock(RiskAnalysis.class);
        ComplaintRiskTag result = mock(ComplaintRiskTag.class);
        RiskTag definition = mock(RiskTag.class);
        when(fixture.analyses.findFirstByComplaintIdAndCompletedTrueAndMessage_StatusNotOrderByIdDesc(
                42L,
                MessageStatus.DRAFT
        )).thenReturn(Optional.of(analysis));
        when(analysis.getComplaint()).thenReturn(fixture.complaint);
        when(analysis.getId()).thenReturn(100L);
        when(analysis.getRiskScore()).thenReturn(12);
        when(analysis.getRiskLevel()).thenReturn(RiskLevel.LOW);
        when(analysis.safetyFlags()).thenReturn(new SafetyFlags(false, true, false));
        when(fixture.tags.findByRiskAnalysisId(100L)).thenReturn(List.of(result));
        when(fixture.reviews.findFirstByRiskAnalysisIdOrderByReviewedAtDescIdDesc(100L))
                .thenReturn(Optional.empty());
        when(result.getRiskTag()).thenReturn(definition);
        when(result.effectiveFinalState()).thenReturn(RiskTagState.PRESENT);
        when(result.getEvidenceSpans()).thenReturn(List.of());
        when(result.getEvidence()).thenReturn("첫 번째 근거\n두 번째 근거");
        when(definition.getCode()).thenReturn(RiskTagCode.THREAT);
        when(definition.getName()).thenReturn("위협");

        var response = fixture.service.findRisk(7L, 42L);

        assertThat(response.riskTagResults()).singleElement().satisfies(factor ->
                assertThat(factor.evidence())
                        .extracting(com.teacherhub.risk.dto.detection.EvidenceSpan::text)
                        .containsExactly("첫 번째 근거", "두 번째 근거"));
        assertThat(response.emergencyOverride().applied()).isFalse();
        assertThat(response.emergencyOverride().safetySignals()).isEmpty();
    }

    private static class Fixture {
        private final ComplaintRepository complaints = mock(ComplaintRepository.class);
        private final RiskAnalysisRepository analyses = mock(RiskAnalysisRepository.class);
        private final RiskReviewRepository reviews = mock(RiskReviewRepository.class);
        private final EmergencyRiskReviewRequestRepository emergencyReviews =
                mock(EmergencyRiskReviewRequestRepository.class);
        private final ComplaintRiskTagRepository tags = mock(ComplaintRiskTagRepository.class);
        private final UserRepository users = mock(UserRepository.class);
        private final ComplaintAccessPolicy accessPolicy = mock(ComplaintAccessPolicy.class);
        private final User teacher = mock(User.class);
        private final Complaint complaint = mock(Complaint.class);
        private final TeacherRiskQueryService service = new TeacherRiskQueryService(
                complaints,
                analyses,
                reviews,
                emergencyReviews,
                tags,
                users,
                accessPolicy
        );

        private Fixture() {
            when(users.findById(7L)).thenReturn(Optional.of(teacher));
            when(complaints.findDetailById(42L)).thenReturn(Optional.of(complaint));
            when(complaint.getId()).thenReturn(42L);
            when(complaint.getStatus()).thenReturn(ComplaintStatus.OPEN);
            when(emergencyReviews.findFirstByComplaintIdOrderByRequestedAtDescIdDesc(42L))
                    .thenReturn(Optional.empty());
        }
    }
}
