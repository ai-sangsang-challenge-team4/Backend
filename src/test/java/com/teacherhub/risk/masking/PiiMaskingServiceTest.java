package com.teacherhub.risk.masking;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PiiMaskingServiceTest {
    private final PiiMaskingService service = new PiiMaskingServiceImpl();

    @Test
    void masksSupportedPatternsAndPreservesSurroundingText() {
        assertThat(service.mask("연락: 010-1234-5678, 02 123 4567, +82 10 1234 5678, a.b+test@example.com, 900101-1234567"))
                .isEqualTo("연락: [전화번호], [전화번호], [전화번호], [이메일], [주민등록번호]");
        assertThat(service.mask("01012345678 / 9001011234567"))
                .isEqualTo("[전화번호] / [주민등록번호]");
    }

    @Test
    void preservesOrdinaryTextAndDoesNotClaimToMaskNames() {
        String text = "김민수 학생의 상황을 확인해 주세요. [전화번호]";
        assertThat(service.mask(text)).isEqualTo(text);
        assertThat(service.mask(service.mask("010-1234-5678"))).isEqualTo("[전화번호]");
    }

    @Test
    void rejectsEmptyText() {
        assertThatThrownBy(() -> service.mask(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.mask("  ")).isInstanceOf(IllegalArgumentException.class);
    }
}
