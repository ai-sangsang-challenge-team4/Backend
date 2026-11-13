package com.teacherhub.risk.service;

import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.enums.RevisionStatus;
import com.teacherhub.risk.repository.ComplaintRiskTagRepository;
import com.teacherhub.risk.repository.RiskAnalysisRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RiskAnalysisQueryServiceTest {

    @Test
    void exposesPendingRevisionWithoutHidingCompletedRiskFindings() {
        RiskAnalysisRepository analyses = mock(RiskAnalysisRepository.class);
        ComplaintRiskTagRepository tags = mock(ComplaintRiskTagRepository.class);
        RiskAnalysis analysis = mock(RiskAnalysis.class, RETURNS_DEEP_STUBS);
        when(analyses.findById(10L)).thenReturn(Optional.of(analysis));
        when(analysis.isCompleted()).thenReturn(true);
        when(analysis.getComplaint().getParent().getUser().getId()).thenReturn(1L);
        when(analysis.getRevisionStatus()).thenReturn(RevisionStatus.PENDING);
        when(analysis.getTagRevisionSuggestions()).thenReturn(List.of());
        when(tags.findByRiskAnalysisId(10L)).thenReturn(List.of());

        var response = new RiskAnalysisQueryService(analyses, tags).findFindings(1L, 10L);

        assertThat(response.revisionStatus()).isEqualTo(RevisionStatus.PENDING);
        assertThat(response.findings()).isEmpty();
        assertThat(response.finalRevision()).isNull();
    }
}
