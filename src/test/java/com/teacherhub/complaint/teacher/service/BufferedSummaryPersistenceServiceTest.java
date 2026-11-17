package com.teacherhub.complaint.teacher.service;

import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintMessage;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.repository.RiskAnalysisRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BufferedSummaryPersistenceServiceTest {

    @Test
    void allowsOnlyTheFirstRequestToGenerateAndThenReturnsStoredResult() {
        RiskAnalysisRepository repository = mock(RiskAnalysisRepository.class);
        RiskAnalysis analysis = analysis();
        when(repository.findForUpdate(100L)).thenReturn(Optional.of(analysis));
        BufferedSummaryPersistenceService service = new BufferedSummaryPersistenceService(repository);

        var firstClaim = service.claim(100L);
        assertThat(firstClaim.state()).isEqualTo(BufferedSummaryPersistenceService.State.CLAIMED);
        assertThat(firstClaim.claimToken()).isNotBlank();
        assertThat(service.claim(100L).state())
                .isEqualTo(BufferedSummaryPersistenceService.State.UNAVAILABLE);

        assertThat(service.complete(100L, firstClaim.claimToken(), "마스킹된 완충 요약"))
                .isTrue();

        var reused = service.claim(100L);
        assertThat(reused.state()).isEqualTo(BufferedSummaryPersistenceService.State.COMPLETED);
        assertThat(reused.summary()).isEqualTo("마스킹된 완충 요약");
    }

    @Test
    void reclaimsGenerationThatExceededTheAiTimeoutBuffer() {
        RiskAnalysisRepository repository = mock(RiskAnalysisRepository.class);
        RiskAnalysis analysis = analysis();
        analysis.startBufferedSummary(
                java.time.LocalDateTime.now().minusMinutes(3),
                "80d96ccf-013e-4416-b3f0-c4d4db27907b"
        );
        when(repository.findForUpdate(100L)).thenReturn(Optional.of(analysis));
        BufferedSummaryPersistenceService service = new BufferedSummaryPersistenceService(repository);

        assertThat(service.claim(100L).state())
                .isEqualTo(BufferedSummaryPersistenceService.State.CLAIMED);
    }

    @Test
    void rejectsLateWritesAfterAStaleGenerationWasReclaimed() {
        RiskAnalysisRepository repository = mock(RiskAnalysisRepository.class);
        RiskAnalysis analysis = analysis();
        when(repository.findForUpdate(100L)).thenReturn(Optional.of(analysis));
        BufferedSummaryPersistenceService service = new BufferedSummaryPersistenceService(repository);

        var oldClaim = service.claim(100L);
        org.springframework.test.util.ReflectionTestUtils.setField(
                analysis,
                "bufferedSummaryStartedAt",
                java.time.LocalDateTime.now().minusMinutes(3)
        );
        var currentClaim = service.claim(100L);

        assertThat(currentClaim.claimToken()).isNotEqualTo(oldClaim.claimToken());
        assertThat(service.complete(100L, oldClaim.claimToken(), "오래된 요약")).isFalse();
        assertThat(service.markUnavailable(100L, oldClaim.claimToken())).isFalse();
        assertThat(analysis.getBufferedSummaryStatus())
                .isEqualTo(com.teacherhub.risk.enums.BufferedSummaryStatus.GENERATING);
        assertThat(analysis.getBufferedSummaryClaimToken()).isEqualTo(currentClaim.claimToken());

        assertThat(service.complete(100L, currentClaim.claimToken(), "최신 요약")).isTrue();
        assertThat(analysis.getBufferedSummary()).isEqualTo("최신 요약");
    }

    private RiskAnalysis analysis() {
        ComplaintMessage message = mock(ComplaintMessage.class);
        when(message.getComplaint()).thenReturn(mock(Complaint.class));
        when(message.getContent()).thenReturn("저장하지 않을 원문");
        when(message.getContentVersion()).thenReturn(1L);
        return new RiskAnalysis(message, 10, RiskLevel.LOW, "model", 0.0);
    }
}
