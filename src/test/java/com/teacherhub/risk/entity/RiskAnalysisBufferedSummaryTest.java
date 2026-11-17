package com.teacherhub.risk.entity;

import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintMessage;
import com.teacherhub.risk.enums.BufferedSummaryStatus;
import com.teacherhub.risk.enums.RiskLevel;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RiskAnalysisBufferedSummaryTest {

    @Test
    void transitionsFromNotRequestedToCompletedWithoutStoringOriginalContent() {
        ComplaintMessage message = message();
        RiskAnalysis analysis = new RiskAnalysis(message, 10, RiskLevel.LOW, "model", 0.0);
        LocalDateTime startedAt = LocalDateTime.of(2026, 11, 17, 10, 0);
        String claimToken = "c4d27fc1-5be7-4a45-b982-7e86615fd83b";

        analysis.startBufferedSummary(startedAt, claimToken);
        analysis.completeBufferedSummary(claimToken, "마스킹된 완충 요약");

        assertThat(analysis.getBufferedSummaryStatus()).isEqualTo(BufferedSummaryStatus.COMPLETED);
        assertThat(analysis.getBufferedSummary()).isEqualTo("마스킹된 완충 요약");
        assertThat(analysis.getBufferedSummaryStartedAt()).isNull();
        assertThat(analysis.getBufferedSummaryClaimToken()).isNull();
    }

    @Test
    void rejectsInvalidGeneratedSummary() {
        RiskAnalysis analysis = new RiskAnalysis(message(), 10, RiskLevel.LOW, "model", 0.0);
        String claimToken = "c4d27fc1-5be7-4a45-b982-7e86615fd83b";
        analysis.startBufferedSummary(LocalDateTime.now(), claimToken);

        assertThatThrownBy(() -> analysis.completeBufferedSummary(claimToken, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsCompletionAndFailureFromAnOlderClaim() {
        RiskAnalysis analysis = new RiskAnalysis(message(), 10, RiskLevel.LOW, "model", 0.0);
        String oldToken = "80d96ccf-013e-4416-b3f0-c4d4db27907b";
        String currentToken = "ca085fe3-f415-47b0-a90d-aa07f0208106";
        analysis.startBufferedSummary(LocalDateTime.now().minusMinutes(3), oldToken);
        analysis.startBufferedSummary(LocalDateTime.now(), currentToken);

        assertThat(analysis.completeBufferedSummary(oldToken, "오래된 요약")).isFalse();
        assertThat(analysis.markBufferedSummaryUnavailable(oldToken)).isFalse();
        assertThat(analysis.getBufferedSummaryStatus()).isEqualTo(BufferedSummaryStatus.GENERATING);
        assertThat(analysis.getBufferedSummaryClaimToken()).isEqualTo(currentToken);

        assertThat(analysis.completeBufferedSummary(currentToken, "최신 요약")).isTrue();
        assertThat(analysis.getBufferedSummary()).isEqualTo("최신 요약");
    }

    private ComplaintMessage message() {
        Complaint complaint = mock(Complaint.class);
        ComplaintMessage message = mock(ComplaintMessage.class);
        when(message.getComplaint()).thenReturn(complaint);
        when(message.getContent()).thenReturn("저장하지 않을 민원 원문");
        when(message.getContentVersion()).thenReturn(1L);
        return message;
    }
}
