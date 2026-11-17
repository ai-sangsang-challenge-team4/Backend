package com.teacherhub.complaint.teacher.service;

import com.teacherhub.complaint.teacher.provider.TeacherSupportContentProvider;
import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintMessage;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.enums.RiskLevel;
import com.teacherhub.risk.masking.PiiMaskingContext;
import com.teacherhub.risk.masking.PiiMaskingService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class BufferedSummaryServiceTest {

    private final BufferedSummaryPersistenceService persistenceService =
            mock(BufferedSummaryPersistenceService.class);
    private final TeacherSupportContentProvider contentProvider =
            mock(TeacherSupportContentProvider.class);
    private final PiiMaskingService maskingService = mock(PiiMaskingService.class);
    private final BufferedSummaryService service = new BufferedSummaryService(
            persistenceService,
            contentProvider,
            maskingService
    );
    private final PiiMaskingContext maskingContext = mock(PiiMaskingContext.class);
    private final RiskAnalysis analysis = analysis();

    @Test
    void reusesCompletedSummaryWithoutCallingAiAgain() {
        when(persistenceService.claim(100L)).thenReturn(
                BufferedSummaryPersistenceService.Claim.completed("저장된 완충 요약")
        );
        when(maskingService.mask("저장된 완충 요약", maskingContext))
                .thenReturn("저장된 완충 요약");

        Optional<String> result = service.findOrGenerate(
                42L, 11L, analysis, "민원 원문", maskingContext
        );

        assertThat(result).contains("저장된 완충 요약");
        verify(contentProvider, never()).createBufferedSummary(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void generatesAndStoresMaskedSummaryOnlyAfterClaimingAnalysis() {
        when(persistenceService.claim(100L)).thenReturn(
                BufferedSummaryPersistenceService.Claim.claimed("claim-token")
        );
        when(maskingService.mask("민원 원문", maskingContext)).thenReturn("마스킹된 원문");
        when(contentProvider.createBufferedSummary("마스킹된 원문")).thenReturn("생성된 요약");
        when(maskingService.mask("생성된 요약", maskingContext)).thenReturn("안전한 생성 요약");

        Optional<String> result = service.findOrGenerate(
                42L, 11L, analysis, "민원 원문", maskingContext
        );

        assertThat(result).contains("안전한 생성 요약");
        verify(persistenceService).complete(100L, "claim-token", "안전한 생성 요약");
    }

    @Test
    void doesNotCallAiWhenAnotherRequestIsGeneratingOrFailureWasStored() {
        when(persistenceService.claim(100L)).thenReturn(
                BufferedSummaryPersistenceService.Claim.unavailable()
        );

        Optional<String> result = service.findOrGenerate(
                42L, 11L, analysis, "민원 원문", maskingContext
        );

        assertThat(result).isEmpty();
        verify(contentProvider, never()).createBufferedSummary(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void storesUnavailableStateWhenGenerationFails() {
        when(persistenceService.claim(100L)).thenReturn(
                BufferedSummaryPersistenceService.Claim.claimed("claim-token")
        );
        when(maskingService.mask("민원 원문", maskingContext)).thenReturn("마스킹된 원문");
        when(contentProvider.createBufferedSummary("마스킹된 원문"))
                .thenThrow(new IllegalStateException("AI unavailable"));

        Optional<String> result = service.findOrGenerate(
                42L, 11L, analysis, "민원 원문", maskingContext
        );

        assertThat(result).isEmpty();
        verify(persistenceService).markUnavailable(100L, "claim-token");
    }

    @Test
    void reusesSummaryCreatedInsideAnUncommittedCallerTransaction() {
        ComplaintMessage message = mock(ComplaintMessage.class);
        when(message.getComplaint()).thenReturn(mock(Complaint.class));
        when(message.getContent()).thenReturn("민원 원문");
        when(message.getContentVersion()).thenReturn(1L);
        RiskAnalysis uncommitted = new RiskAnalysis(
                message, 10, RiskLevel.LOW, "model", 0.0
        );
        org.springframework.test.util.ReflectionTestUtils.setField(uncommitted, "id", 101L);
        when(persistenceService.claim(101L)).thenReturn(
                BufferedSummaryPersistenceService.Claim.notVisible()
        );
        when(maskingService.mask("민원 원문", maskingContext)).thenReturn("마스킹된 원문");
        when(contentProvider.createBufferedSummary("마스킹된 원문")).thenReturn("생성된 요약");
        when(maskingService.mask("생성된 요약", maskingContext)).thenReturn("안전한 생성 요약");
        when(maskingService.mask("안전한 생성 요약", maskingContext)).thenReturn("안전한 생성 요약");

        assertThat(service.findOrGenerate(
                42L, 11L, uncommitted, "민원 원문", maskingContext
        )).contains("안전한 생성 요약");
        assertThat(service.findOrGenerate(
                42L, 11L, uncommitted, "민원 원문", maskingContext
        )).contains("안전한 생성 요약");

        verify(contentProvider, times(1)).createBufferedSummary("마스킹된 원문");
    }

    private RiskAnalysis analysis() {
        RiskAnalysis result = mock(RiskAnalysis.class);
        when(result.getId()).thenReturn(100L);
        return result;
    }
}
