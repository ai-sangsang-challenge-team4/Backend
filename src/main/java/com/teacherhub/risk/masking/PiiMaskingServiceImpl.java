package com.teacherhub.risk.masking;

import org.springframework.stereotype.Service;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PiiMaskingServiceImpl implements PiiMaskingService {

    private static final Pattern RESIDENT_ID = Pattern.compile("(?<![0-9])[0-9]{6}[-.\\s]?[1-8][0-9]{6}(?![0-9])");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<![0-9])(?:\\+82[-.\\s]?(?:10|2|50[1-8]?|[3-6][1-5]|70)|0(?:1[016789]|2|50[1-8]?|[3-6][1-5]|70))[-.\\s]?[0-9]{3,4}[-.\\s]?[0-9]{4}(?![0-9])");
    private static final Pattern CLASS_STUDENT = Pattern.compile(
            "(?<![0-9가-힣])(?:[0-9]{1,2}\\s*학년\\s*)?[0-9]{1,2}\\s*반\\s*[가-힣]{2,10}\\s*학생"
                    + "(?=$|\\s|[\\p{P}\\p{S}]|과|와|은|는|이|가|을|를|의|도|만|에게|께|에게서|부터|까지|처럼|보다|으로|로)"
    );
    private static final Pattern KOREAN_PERSON_NAME = Pattern.compile("[가-힣]{3,5}");
    private static final Set<String> COMPOUND_KOREAN_SURNAMES = Set.of(
            "남궁", "독고", "동방", "망절", "사공", "서문", "선우", "제갈", "황보"
    );

    @Override
    public String mask(String content) {
        return mask(content, PiiMaskingContext.empty());
    }

    @Override
    public String mask(String content, PiiMaskingContext context) {
        validateContent(content);
        PiiMaskingContext safeContext = context == null ? PiiMaskingContext.empty() : context;
        String masked = RESIDENT_ID.matcher(content).replaceAll("[주민등록번호]");
        masked = EMAIL.matcher(masked).replaceAll("[이메일]");
        masked = PHONE.matcher(masked).replaceAll("[전화번호]");
        masked = replaceWholeTokens(masked, safeContext.studentIdentifiers(), "[학번]");
        masked = replaceWholeTokens(masked, safeContext.teacherIdentifiers(), "[교직원번호]");
        masked = replaceKnownNames(masked, safeContext);
        masked = CLASS_STUDENT.matcher(masked).replaceAll("[학생정보]");

        ensureNoSupportedPiiRemains(masked, safeContext);
        return masked;
    }

    @Override
    public PiiMaskingResult maskReversibly(String content, PiiMaskingContext context) {
        validateContent(content);
        PiiMaskingContext safeContext = context == null ? PiiMaskingContext.empty() : context;
        ReversibleMasker masker = new ReversibleMasker(content);
        masker.replacePattern(RESIDENT_ID, "RESIDENT_ID");
        masker.replacePattern(EMAIL, "EMAIL");
        masker.replacePattern(PHONE, "PHONE");
        masker.replaceWholeTokens(safeContext.studentIdentifiers(), "STUDENT_ID");
        masker.replaceWholeTokens(safeContext.teacherIdentifiers(), "TEACHER_ID");
        for (NamedValue value : knownNames(safeContext)) {
            masker.replaceLiteral(value.value(), value.category());
        }
        masker.replacePattern(CLASS_STUDENT, "CLASS_STUDENT");

        ensureNoSupportedPiiRemains(masker.valueWithoutTokens(), safeContext);
        return masker.result();
    }

    private void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("마스킹할 텍스트가 없습니다.");
        }
    }

    private String replaceKnownNames(String content, PiiMaskingContext context) {
        String masked = content;
        for (NamedValue value : knownNames(context)) {
            masked = masked.replace(value.value(), value.replacement());
        }
        return masked;
    }

    private List<NamedValue> knownNames(PiiMaskingContext context) {
        Map<String, NamedValue> values = new LinkedHashMap<>();
        addStudentNames(values, context.studentNames(), "STUDENT_NAME", "[학생명]");
        addNames(values, context.teacherNames(), "TEACHER_NAME", "[교사명]");
        addStudentNames(
                values,
                context.thirdPartyStudentNames(),
                "THIRD_PARTY_STUDENT_NAME",
                "[제3자 학생명]"
        );
        addNames(values, context.additionalNames(), "ADDITIONAL_NAME", "[사용자명]");
        return values.values().stream()
                .sorted(Comparator.comparingInt((NamedValue value) -> value.value().length())
                        .reversed()
                        .thenComparing(NamedValue::value))
                .toList();
    }

    private void addNames(
            Map<String, NamedValue> target,
            List<String> names,
            String category,
            String replacement
    ) {
        for (String name : names) {
            target.putIfAbsent(name, new NamedValue(name, category, replacement));
        }
    }

    private void addStudentNames(
            Map<String, NamedValue> target,
            List<String> names,
            String category,
            String replacement
    ) {
        addNames(target, names, category, replacement);
        for (String name : names) {
            String givenName = koreanGivenName(name);
            if (givenName != null) {
                target.putIfAbsent(givenName, new NamedValue(givenName, category, replacement));
            }
        }
    }

    private String koreanGivenName(String name) {
        if (!KOREAN_PERSON_NAME.matcher(name).matches()) {
            return null;
        }
        int surnameLength = COMPOUND_KOREAN_SURNAMES.stream().anyMatch(name::startsWith) ? 2 : 1;
        String givenName = name.substring(surnameLength);
        return givenName.length() >= 2 ? givenName : null;
    }

    private String replaceWholeTokens(String content, List<String> values, String replacement) {
        String masked = content;
        for (String value : values) {
            masked = replaceWholeToken(masked, value, replacement);
        }
        return masked;
    }

    // 특정 토큰을 전체적으로 교체하는 메서드
    private String replaceWholeToken(String content, String value, String replacement) {
        StringBuilder result = new StringBuilder(content.length());
        int cursor = 0;
        int match;
        while ((match = content.indexOf(value, cursor)) >= 0) {
            int end = match + value.length();
            boolean startsAtBoundary = match == 0 || !isIdentifierCharacter(content.charAt(match - 1));
            boolean endsAtBoundary = end == content.length() || !isIdentifierCharacter(content.charAt(end));
            if (startsAtBoundary && endsAtBoundary) {
                result.append(content, cursor, match).append(replacement);
                cursor = end;
            } else {
                result.append(content, cursor, match + 1);
                cursor = match + 1;
            }
        }
        return result.append(content, cursor, content.length()).toString();
    }

    private boolean isIdentifierCharacter(char value) {
        return value >= '0' && value <= '9'
                || value >= 'A' && value <= 'Z'
                || value >= 'a' && value <= 'z';
    }

    private void ensureNoSupportedPiiRemains(String masked, PiiMaskingContext context) {
        String withoutPlaceholders = withoutPlaceholders(masked);
        if (RESIDENT_ID.matcher(masked).find()
                || EMAIL.matcher(masked).find()
                || PHONE.matcher(masked).find()
                || CLASS_STUDENT.matcher(masked).find()
                || knownNames(context).stream().anyMatch(value -> withoutPlaceholders.contains(value.value()))
                || containsWholeToken(withoutPlaceholders, context.studentIdentifiers())
                || containsWholeToken(withoutPlaceholders, context.teacherIdentifiers())) {
            throw new IllegalStateException("지원되는 개인정보 형식이 마스킹 후에도 남아 있습니다.");
        }
    }

    private String withoutPlaceholders(String value) {
        return value
                .replace("[학생정보]", "")
                .replace("[학생명]", "")
                .replace("[교사명]", "")
                .replace("[제3자 학생명]", "")
                .replace("[사용자명]", "")
                .replace("[학번]", "")
                .replace("[교직원번호]", "")
                .replace("[전화번호]", "")
                .replace("[이메일]", "")
                .replace("[주민등록번호]", "");
    }

    private boolean containsWholeToken(String content, List<String> values) {
        return values.stream().anyMatch(value -> !replaceWholeToken(content, value, "").equals(content));
    }

    private record NamedValue(String value, String category, String replacement) {
    }

    private final class ReversibleMasker {
        private String value;
        private final Map<String, PiiMaskingResult.Restoration> restorations = new LinkedHashMap<>();
        private final Map<String, String> tokenByValue = new LinkedHashMap<>();
        private final Map<String, Integer> sequenceByCategory = new LinkedHashMap<>();

        private ReversibleMasker(String value) {
            this.value = value;
        }

        private String value() {
            return value;
        }

        private void replacePattern(Pattern pattern, String category) {
            Matcher matcher = pattern.matcher(value);
            StringBuffer result = new StringBuffer(value.length());
            while (matcher.find()) {
                matcher.appendReplacement(
                        result,
                        Matcher.quoteReplacement(token(category, matcher.group(), 1))
                );
            }
            matcher.appendTail(result);
            value = result.toString();
        }

        private void replaceLiteral(String original, String category) {
            Set<String> protectedTokens = Set.copyOf(restorations.keySet());
            int occurrences = countOutsideTokens(
                    protectedTokens,
                    content -> countOccurrences(content, original)
            );
            if (occurrences == 0) {
                return;
            }
            String replacement = token(category, original, occurrences);
            value = replaceOutsideTokens(
                    protectedTokens,
                    content -> content.replace(original, replacement)
            );
        }

        private void replaceWholeTokens(List<String> originals, String category) {
            for (String original : originals) {
                Set<String> protectedTokens = Set.copyOf(restorations.keySet());
                int occurrences = countOutsideTokens(
                        protectedTokens,
                        content -> wholeTokenOccurrences(content, original)
                );
                if (occurrences == 0) {
                    continue;
                }
                String replacement = token(category, original, occurrences);
                value = replaceOutsideTokens(
                        protectedTokens,
                        content -> replaceWholeToken(content, original, replacement)
                );
            }
        }

        private int countOutsideTokens(Set<String> protectedTokens, ToIntFunction<String> counter) {
            return transformOutsideTokens(
                    protectedTokens,
                    content -> content,
                    counter
            ).count();
        }

        private String replaceOutsideTokens(
                Set<String> protectedTokens,
                UnaryOperator<String> replacement
        ) {
            return transformOutsideTokens(protectedTokens, replacement, content -> 0).value();
        }

        private TransformedValue transformOutsideTokens(
                Set<String> protectedTokens,
                UnaryOperator<String> replacement,
                ToIntFunction<String> counter
        ) {
            if (protectedTokens.isEmpty()) {
                return new TransformedValue(replacement.apply(value), counter.applyAsInt(value));
            }

            StringBuilder transformed = new StringBuilder(value.length());
            int count = 0;
            int cursor = 0;
            while (cursor < value.length()) {
                TokenPosition nextToken = nextProtectedToken(protectedTokens, cursor);
                if (nextToken == null) {
                    String remainder = value.substring(cursor);
                    transformed.append(replacement.apply(remainder));
                    count += counter.applyAsInt(remainder);
                    break;
                }
                String unprotected = value.substring(cursor, nextToken.start());
                transformed.append(replacement.apply(unprotected));
                transformed.append(nextToken.token());
                count += counter.applyAsInt(unprotected);
                cursor = nextToken.start() + nextToken.token().length();
            }
            return new TransformedValue(transformed.toString(), count);
        }

        private TokenPosition nextProtectedToken(Set<String> protectedTokens, int cursor) {
            TokenPosition next = null;
            for (String token : protectedTokens) {
                int position = value.indexOf(token, cursor);
                if (position >= 0 && (next == null || position < next.start())) {
                    next = new TokenPosition(position, token);
                }
            }
            return next;
        }

        private String token(String category, String original, int occurrences) {
            String valueKey = category + '\u0000' + original;
            String existing = tokenByValue.get(valueKey);
            if (existing != null) {
                PiiMaskingResult.Restoration current = restorations.get(existing);
                restorations.put(existing, new PiiMaskingResult.Restoration(
                        current.originalValue(),
                        current.maxOccurrences() + occurrences
                ));
                return existing;
            }
            int sequence = sequenceByCategory.merge(category, 1, Integer::sum);
            String created = "[개인정보:" + category + ":" + sequence + "]";
            tokenByValue.put(valueKey, created);
            restorations.put(created, new PiiMaskingResult.Restoration(original, occurrences));
            return created;
        }

        private int countOccurrences(String content, String target) {
            int count = 0;
            int cursor = 0;
            while ((cursor = content.indexOf(target, cursor)) >= 0) {
                count++;
                cursor += target.length();
            }
            return count;
        }

        private int wholeTokenOccurrences(String content, String target) {
            int count = 0;
            int cursor = 0;
            int match;
            while ((match = content.indexOf(target, cursor)) >= 0) {
                int end = match + target.length();
                boolean startsAtBoundary = match == 0 || !isIdentifierCharacter(content.charAt(match - 1));
                boolean endsAtBoundary = end == content.length() || !isIdentifierCharacter(content.charAt(end));
                if (startsAtBoundary && endsAtBoundary) {
                    count++;
                    cursor = end;
                } else {
                    cursor = match + 1;
                }
            }
            return count;
        }

        private PiiMaskingResult result() {
            return new PiiMaskingResult(value, restorations);
        }

        private String valueWithoutTokens() {
            String withoutTokens = value;
            for (String token : restorations.keySet()) {
                withoutTokens = withoutTokens.replace(token, "");
            }
            return withoutTokens;
        }

        private record TransformedValue(String value, int count) {
        }

        private record TokenPosition(int start, String token) {
        }
    }
}
