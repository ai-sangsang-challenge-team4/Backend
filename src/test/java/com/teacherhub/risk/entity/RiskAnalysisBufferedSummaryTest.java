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

        analysis.startBufferedSummary(startedAt);
        analysis.completeBufferedSummary("마스킹된 완충 요약");

        assertThat(analysis.getBufferedSummaryStatus()).isEqualTo(BufferedSummaryStatus.COMPLETED);
        assertThat(analysis.getBufferedSummary()).isEqualTo("마스킹된 완충 요약");
        assertThat(analysis.getBufferedSummaryStartedAt()).isNull();
    }

    @Test
    void rejectsInvalidGeneratedSummary() {
        RiskAnalysis analysis = new RiskAnalysis(message(), 10, RiskLevel.LOW, "model", 0.0);
        analysis.startBufferedSummary(LocalDateTime.now());

        assertThatThrownBy(() -> analysis.completeBufferedSummary(" "))
                .isInstanceOf(IllegalArgumentException.class);
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
