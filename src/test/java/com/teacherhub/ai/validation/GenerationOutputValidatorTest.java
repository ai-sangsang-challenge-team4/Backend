package com.teacherhub.ai.validation;

import com.teacherhub.ai.exception.AiResponseInvalidException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenerationOutputValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "민원이 총 3건 접수되었습니다.",
            "2027년 1월 1일에 처리하겠습니다.",
            "김민수 학생의 요청입니다."
    })
    void rejectsNumbersDatesAndNamesNotFoundInSource(String output) {
        assertThatThrownBy(() -> GenerationOutputValidator.validateNoNovelFacts(
                "확인과 답변을 요청드립니다.",
                output,
                "AI 생성"
        )).isInstanceOf(AiResponseInvalidException.class);
    }

    @Test
    void flagsOnlyLegalCitationsMissingFromRagSources() {
        assertThat(GenerationOutputValidator.hasUnsupportedLegalCitation(
                "초중등교육법 제20조를 확인했습니다.",
                List.of("초중등교육법 제20조 교원의 임무")
        )).isFalse();
        assertThat(GenerationOutputValidator.hasUnsupportedLegalCitation(
                "초중등교육법 제20조를 확인했습니다.",
                List.of("초중등교육법 제21조 교원의 자격")
        )).isTrue();
    }

}
