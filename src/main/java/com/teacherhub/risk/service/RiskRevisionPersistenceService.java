package com.teacherhub.risk.service;

import com.teacherhub.ai.exception.AiResponseInvalidException;
import com.teacherhub.risk.analyzer.ComplaintRevisionGenerator.RevisionResult;
import com.teacherhub.risk.entity.RiskTagRevisionSuggestion;
import com.teacherhub.risk.masking.PiiMaskingService;
import com.teacherhub.risk.masking.PiiMaskingContext;
import com.teacherhub.risk.masking.PiiMaskingResult;
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
    public void complete(
            Long analysisId,
            RevisionResult revision,
            PiiMaskingContext maskingContext,
            PiiMaskingResult reversibleMasking
    ) {
        var analysis = analysisRepository.findForUpdate(analysisId).orElse(null);
        if (analysis == null) {
            return;
        }
        var finalRevision = revision.finalRevision();
        var suggestions = revision.tagSuggestions().stream()
                .map(suggestion -> new RiskTagRevisionSuggestion(
                        suggestion.code(),
                        restore(suggestion.originalExpression(), maskingContext, reversibleMasking),
                        restore(suggestion.suggestedExpression(), maskingContext, reversibleMasking),
                        restore(suggestion.reason(), maskingContext, reversibleMasking)
                ))
                .toList();
        analysis.completeRevision(
                restore(finalRevision.reason(), maskingContext, reversibleMasking),
                restore(finalRevision.content(), maskingContext, reversibleMasking),
                suggestions
        );
    }

    @Transactional
    public void markUnavailable(Long analysisId) {
        analysisRepository.findForUpdate(analysisId)
                .ifPresent(analysis -> analysis.markRevisionUnavailable());
    }

    private String restore(
            String value,
            PiiMaskingContext maskingContext,
            PiiMaskingResult reversibleMasking
    ) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (!piiMaskingService.mask(value, maskingContext).equals(value)) {
            throw new AiResponseInvalidException("AI 수정안에 원문 토큰이 아닌 개인정보가 포함되어 있습니다.");
        }
        try {
            return reversibleMasking.restore(value);
        } catch (IllegalArgumentException exception) {
            throw new AiResponseInvalidException("AI 수정안의 개인정보 토큰이 올바르지 않습니다.", exception);
        }
    }
}
