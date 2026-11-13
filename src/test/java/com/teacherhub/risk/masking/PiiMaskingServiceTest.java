package com.teacherhub.risk.masking;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class PiiMaskingServiceTest {
    private final PiiMaskingService service = new PiiMaskingServiceImpl();

    @Test
    void masksSupportedPatternsAndPreservesSurroundingText() {
        assertThat(service.mask("연락: 010-1234-5678, 02 123 4567, +82 10 1234 5678, a.b+test@example.com, 900101-1234567"))
                .isEqualTo("연락: [전화번호], [전화번호], [전화번호], [이메일], [주민등록번호]");
        assertThat(service.mask("01012345678 / 9001011234567"))
                .isEqualTo("[전화번호] / [주민등록번호]");
        assertThat(service.mask("0501-2345-6789 / 0504-1234-5678 / 010.1234.5678 / 900101.1234567"))
                .isEqualTo("[전화번호] / [전화번호] / [전화번호] / [주민등록번호]");
    }

    @Test
    void preservesOrdinaryTextAndDoesNotClaimToMaskNames() {
        String text = "김민수 학생의 상황을 확인해 주세요. [전화번호]";
        assertThat(service.mask(text)).isEqualTo(text);
        assertThat(service.mask(service.mask("010-1234-5678"))).isEqualTo("[전화번호]");
    }

    @Test
    void masksKnownStudentTeacherNamesAndIdentifiersWithOneContext() {
        PiiMaskingContext context = new PiiMaskingContext(
                List.of("김민수"),
                List.of("이서아"),
                List.of("S-2026-001"),
                List.of("T-1042")
        );

        assertThat(service.mask(
                "김민수 학생과 이서아 선생님, 학번 S-2026-001, 교번 T-1042",
                context
        )).isEqualTo("[학생명] 학생과 [교사명] 선생님, 학번 [학번], 교번 [교직원번호]");
    }

    @Test
    void masksClassStudentPatternsWithoutCompilingPerNameRegex() {
        assertThat(service.mask(
                "3학년 2반 김민수학생과 0반 박서준 학생을 확인하고, "
                        + "1반 이서준 학생도 4반 최유리 학생(피해자)과 확인해 주세요."
        )).isEqualTo(
                "[학생정보]과 [학생정보]을 확인하고, "
                        + "[학생정보]도 [학생정보](피해자)과 확인해 주세요."
        );
    }

    @Test
    void masksShortIdentifiersOnlyAsWholeTokens() {
        PiiMaskingContext context = new PiiMaskingContext(
                List.of(),
                List.of(),
                List.of("7"),
                List.of("12")
        );

        assertThat(service.mask("학번7과 학번 7, 2017, 교번 12와 A12", context))
                .isEqualTo("학번[학번]과 학번 [학번], 2017, 교번 [교직원번호]와 A12");
    }

    @Test
    void replacesLongerKnownNamesFirstAndRemainsIdempotent() {
        PiiMaskingContext context = new PiiMaskingContext(
                List.of("김민수", "김민수아"),
                List.of(),
                List.of(),
                List.of()
        );
        String masked = service.mask("김민수아와 김민수", context);

        assertThat(masked).isEqualTo("[학생명]와 [학생명]");
        assertThat(service.mask(masked, context)).isEqualTo(masked);
    }

    @Test
    void rejectsEmptyText() {
        assertThatThrownBy(() -> service.mask(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.mask("  ")).isInstanceOf(IllegalArgumentException.class);
    }
}
