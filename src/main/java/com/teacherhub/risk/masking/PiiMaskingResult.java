package com.teacherhub.risk.masking;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 외부 AI 경계에서만 사용하는 가역 마스킹 결과입니다.
 * 원문 값은 메모리에만 유지하며 로그나 외부 요청에 포함하지 않습니다.
 */
public final class PiiMaskingResult {

    private static final Pattern PII_TOKEN = Pattern.compile(
            "\\[(?:개인정보:[^\\r\\n]+?|전화번호|이메일|주민등록번호|학생정보|학생명|교사명|제3자 학생명|사용자명|학번|교직원번호)]"
    );

    private final String maskedContent;
    private final Map<String, Restoration> restorations;

    PiiMaskingResult(String maskedContent, Map<String, Restoration> restorations) {
        this.maskedContent = maskedContent;
        this.restorations = Map.copyOf(new LinkedHashMap<>(restorations));
    }

    public String maskedContent() {
        return maskedContent;
    }

    public String mask(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        String masked = content;
        for (Map.Entry<String, Restoration> entry : restorations.entrySet().stream()
                .sorted(Comparator.comparingInt(
                        (Map.Entry<String, Restoration> entry) -> entry.getValue().originalValue().length()
                ).reversed())
                .toList()) {
            masked = masked.replace(entry.getValue().originalValue(), entry.getKey());
        }
        return masked;
    }

    public String restore(String generatedContent) {
        if (generatedContent == null || generatedContent.isBlank()) {
            return generatedContent;
        }

        Matcher matcher = PII_TOKEN.matcher(generatedContent);
        Map<String, Integer> occurrences = new LinkedHashMap<>();
        while (matcher.find()) {
            String token = matcher.group();
            Restoration restoration = restorations.get(token);
            if (restoration == null) {
                throw new IllegalArgumentException("원문에 없는 개인정보 토큰이 포함되어 있습니다.");
            }
            int count = occurrences.merge(token, 1, Integer::sum);
            if (count > restoration.maxOccurrences()) {
                throw new IllegalArgumentException("개인정보 토큰이 원문보다 많이 반복되었습니다.");
            }
        }
        if (PII_TOKEN.matcher(generatedContent).replaceAll("").contains("[개인정보:")) {
            throw new IllegalArgumentException("완전하지 않은 개인정보 토큰이 포함되어 있습니다.");
        }

        String restored = generatedContent;
        for (Map.Entry<String, Restoration> entry : restorations.entrySet()) {
            restored = restored.replace(entry.getKey(), entry.getValue().originalValue());
        }
        return restored;
    }

    record Restoration(String originalValue, int maxOccurrences) {
    }
}
