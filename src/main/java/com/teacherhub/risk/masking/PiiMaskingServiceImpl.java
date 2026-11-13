package com.teacherhub.risk.masking;

import org.springframework.stereotype.Service;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class PiiMaskingServiceImpl implements PiiMaskingService {

    private static final Pattern RESIDENT_ID = Pattern.compile("(?<![0-9])[0-9]{6}[-.\\s]?[1-8][0-9]{6}(?![0-9])");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<![0-9])(?:\\+82[-.\\s]?(?:10|2|50[1-8]?|[3-6][1-5]|70)|0(?:1[016789]|2|50[1-8]?|[3-6][1-5]|70))[-.\\s]?[0-9]{3,4}[-.\\s]?[0-9]{4}(?![0-9])");
    private static final Pattern CLASS_STUDENT = Pattern.compile(
            "(?<![0-9가-힣])(?:[0-9]{1,2}\\s*학년\\s*)?[0-9]{1,2}\\s*반\\s*[가-힣]{2,10}\\s*학생"
                    + "(?=$|\\s|[,.!?]|과|와|은|는|이|가|을|를|의|에게|께|에게서)"
    );

    @Override
    public String mask(String content) {
        return mask(content, PiiMaskingContext.empty());
    }

    @Override
    public String mask(String content, PiiMaskingContext context) {

        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("마스킹할 텍스트가 없습니다.");
        }

        PiiMaskingContext safeContext = context == null ? PiiMaskingContext.empty() : context;
        String masked = RESIDENT_ID.matcher(content).replaceAll("[주민등록번호]");
        masked = EMAIL.matcher(masked).replaceAll("[이메일]");
        masked = PHONE.matcher(masked).replaceAll("[전화번호]");
        masked = CLASS_STUDENT.matcher(masked).replaceAll("[학생정보]");
        masked = replaceAll(masked, safeContext.studentIdentifiers(), "[학번]");
        masked = replaceAll(masked, safeContext.teacherIdentifiers(), "[교직원번호]");
        masked = replaceAll(masked, safeContext.studentNames(), "[학생명]");
        masked = replaceAll(masked, safeContext.teacherNames(), "[교사명]");

        ensureNoSupportedPiiRemains(masked, safeContext);
        return masked;
    }

    private String replaceAll(String content, List<String> values, String replacement) {
        String masked = content;
        for (String value : values) {
            masked = masked.replace(value, replacement);
        }
        return masked;
    }

    private void ensureNoSupportedPiiRemains(String masked, PiiMaskingContext context) {
        if (RESIDENT_ID.matcher(masked).find()
                || EMAIL.matcher(masked).find()
                || PHONE.matcher(masked).find()
                || CLASS_STUDENT.matcher(masked).find()
                || containsAny(withoutPlaceholders(masked), context.studentNames())
                || containsAny(withoutPlaceholders(masked), context.teacherNames())
                || containsAny(withoutPlaceholders(masked), context.studentIdentifiers())
                || containsAny(withoutPlaceholders(masked), context.teacherIdentifiers())) {
            throw new IllegalStateException("지원되는 개인정보 형식이 마스킹 후에도 남아 있습니다.");
        }
    }

    private String withoutPlaceholders(String value) {
        return value
                .replace("[학생정보]", "")
                .replace("[학생명]", "")
                .replace("[교사명]", "")
                .replace("[학번]", "")
                .replace("[교직원번호]", "")
                .replace("[전화번호]", "")
                .replace("[이메일]", "")
                .replace("[주민등록번호]", "");
    }

    private boolean containsAny(String content, List<String> values) {
        return values.stream().anyMatch(content::contains);
    }
}
