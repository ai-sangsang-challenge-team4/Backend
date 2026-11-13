package com.teacherhub.risk.service;

import com.teacherhub.risk.analyzer.ComplaintRevisionGenerator.RevisionResult;
import com.teacherhub.risk.entity.RiskTagRevisionSuggestion;
import com.teacherhub.risk.masking.PiiMaskingService;
import com.teacherhub.risk.repository.RiskAnalysisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RiskRevisionPersistenceService {

    private final RiskAnalysisRepository analysisRepository;
    private final PiiMaskingService piiMaskingService;

    @Transactional
    public void complete(Long analysisId, RevisionResult revision) {
        var analysis = analysisRepository.findForUpdate(analysisId).orElse(null);
        if (analysis == null) {
            return;
        }
        var finalRevision = revision.finalRevision();
        var suggestions = revision.tagSuggestions().stream()
                .map(suggestion -> new RiskTagRevisionSuggestion(
                        suggestion.code(),
                        mask(suggestion.originalExpression()),
                        mask(suggestion.suggestedExpression()),
                        mask(suggestion.reason())
                ))
                .toList();
        analysis.completeRevision(
                mask(finalRevision.reason()),
                mask(finalRevision.content()),
                suggestions
        );
    }

    @Transactional
    public void markUnavailable(Long analysisId) {
        analysisRepository.findForUpdate(analysisId)
                .ifPresent(analysis -> analysis.markRevisionUnavailable());
    }

    private String mask(String value) {
        return value == null || value.isBlank() ? value : piiMaskingService.mask(value);
    }
}
