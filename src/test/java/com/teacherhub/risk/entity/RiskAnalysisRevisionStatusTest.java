package com.teacherhub.risk.entity;

import com.teacherhub.complaint.entity.Complaint;
import com.teacherhub.complaint.entity.ComplaintMessage;
import com.teacherhub.risk.enums.RevisionStatus;
import com.teacherhub.risk.enums.RiskAnalysisMode;
import com.teacherhub.risk.enums.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RiskAnalysisRevisionStatusTest {

    @Test
    void completesRevisionWithoutChangingCompletedRiskResult() {
        RiskAnalysis analysis = pendingAnalysis();

        analysis.completeRevision("완화 이유", "완화 문장", List.of());

        assertThat(analysis.isCompleted()).isTrue();
        assertThat(analysis.getRevisionStatus()).isEqualTo(RevisionStatus.COMPLETED);
        assertThat(analysis.getRiskScore()).isEqualTo(25);
        assertThat(analysis.getRiskLevel()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(analysis.getAiRevision()).isEqualTo("완화 문장");
    }

    @Test
    void marksOnlyPendingRevisionUnavailable() {
        RiskAnalysis analysis = pendingAnalysis();

        analysis.markRevisionUnavailable();

        assertThat(analysis.isCompleted()).isTrue();
        assertThat(analysis.getRevisionStatus()).isEqualTo(RevisionStatus.UNAVAILABLE);
        assertThat(analysis.getAnalysisMode()).isEqualTo(RiskAnalysisMode.HYBRID);
    }

    private RiskAnalysis pendingAnalysis() {
        ComplaintMessage message = mock(ComplaintMessage.class);
        when(message.getComplaint()).thenReturn(mock(Complaint.class));
        when(message.getContent()).thenReturn("masked content");
        when(message.getContentVersion()).thenReturn(1L);
        RiskAnalysis analysis = new RiskAnalysis(
                message,
                25,
                RiskLevel.MEDIUM,
                "model",
                0.0,
                RiskAnalysisMode.HYBRID
        );
        analysis.completeAnalysis(RevisionStatus.PENDING);
        return analysis;
    }
}
