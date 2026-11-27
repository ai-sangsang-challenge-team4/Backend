package com.teacherhub.risk.service;

import com.teacherhub.ai.exception.AiResponseInvalidException;
import com.teacherhub.risk.analyzer.ComplaintRevisionGenerator.FinalRevision;
import com.teacherhub.risk.analyzer.ComplaintRevisionGenerator.RevisionResult;
import com.teacherhub.risk.analyzer.ComplaintRevisionGenerator.TagRevisionSuggestion;
import com.teacherhub.risk.entity.RiskAnalysis;
import com.teacherhub.risk.enums.RiskTagCode;
import com.teacherhub.risk.masking.PiiMaskingContext;
import com.teacherhub.risk.masking.PiiMaskingResult;
import com.teacherhub.risk.masking.PiiMaskingServiceImpl;
import com.teacherhub.risk.repository.RiskAnalysisRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RiskRevisionPersistenceServiceTest {

    private final RiskAnalysisRepository analyses = mock(RiskAnalysisRepository.class);
    private final PiiMaskingServiceImpl masking = new PiiMaskingServiceImpl();
    private final RiskRevisionPersistenceService service =
            new RiskRevisionPersistenceService(analyses, masking);
    private final RiskAnalysis analysis = mock(RiskAnalysis.class);
    private final PiiMaskingContext context = new PiiMaskingContext(
            List.of("김서윤"),
            List.of("김민지"),
            List.of("S-2026-001"),
            List.of("T-2026-001")
    ).withThirdPartyStudentNames(List.of("박민수"));

    @Test
    void restoresAllOriginalPersonalInformationBeforeSavingRevision() {
        String original = "김서윤과 박민수의 연락처는 010-3143-1234이고 "
                + "이메일은 parent@example.com, 주민번호는 900101-1234567입니다.";
        PiiMaskingResult reversible = masking.maskReversibly(original, context);
        RevisionResult revision = new RevisionResult(
                new FinalRevision("개인정보 토큰을 유지해 표현을 완화했습니다.", reversible.maskedContent()),
                List.of(new TagRevisionSuggestion(
                        RiskTagCode.PRIVACY,
                        reversible.maskedContent(),
                        reversible.maskedContent(),
                        "개인정보 표현을 확인했습니다."
                ))
        );
        when(analyses.findForUpdate(10L)).thenReturn(Optional.of(analysis));

        service.complete(10L, revision, context, reversible);

        verify(analysis).completeRevision(
                eq("개인정보 토큰을 유지해 표현을 완화했습니다."),
                eq(original),
                argThat(suggestions -> suggestions.size() == 1
                        && suggestions.get(0).getOriginalExpression().equals(original)
                        && suggestions.get(0).getSuggestedExpression().equals(original))
        );
    }

    @Test
    void rejectsNewPersonalInformationThatWasNotRepresentedByAnOriginalToken() {
        PiiMaskingResult reversible = masking.maskReversibly("김서윤 학생을 확인해 주세요.", context);
        RevisionResult revision = new RevisionResult(
                new FinalRevision("수정 이유", "새 연락처는 010-9999-8888입니다."),
                List.of()
        );
        when(analyses.findForUpdate(10L)).thenReturn(Optional.of(analysis));

        assertThatThrownBy(() -> service.complete(10L, revision, context, reversible))
                .isInstanceOf(AiResponseInvalidException.class);
        verify(analysis, never()).completeRevision(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                anyList()
        );
    }
}
