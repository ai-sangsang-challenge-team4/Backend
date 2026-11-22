package com.teacherhub.ai.validation;

import com.teacherhub.ai.exception.AiResponseInvalidException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** 생성형 AI 출력을 저장하거나 노출하기 전에 적용하는 결정론적 검증입니다. */
public final class GenerationOutputValidator {

    private static final Pattern NUMBER = Pattern.compile("\\d+");
    private static final Pattern PERSON_NAME = Pattern.compile(
            "(?<![가-힣])([김이박최정강조윤장임한오서신권황안송류홍전고문양손배백허유남심노하곽성차주우구민진지엄채원천방공현함변염여추도소석선마길연위표명기반왕금옥육인맹제모탁국어은편용][가-힣]{1,3})"
                    + "(?=\\s*(?:학생|보호자|학부모|교사|선생님|교감|교장|님))"
    );
    private static final Set<String> NON_PERSON_NAMES = Set.of(
            "이름", "이상", "이번", "한명"
    );
    private static final Pattern LEGAL_CITATION = Pattern.compile(
            "(?:[가-힣A-Za-z][가-힣A-Za-z0-9·]{1,39}(?:시행규칙|시행령|법률|법)\\s*)?"
                    + "제\\s*\\d+(?:의\\d+)?\\s*조(?:의\\d+)?"
    );
    private GenerationOutputValidator() {
    }

    public static void validateNoNovelFacts(
            String source,
            String output,
            String capabilityName
    ) {
        if (!extract(NUMBER, output).stream().allMatch(extract(NUMBER, source)::contains)
                || !extractPersonNames(output).stream()
                .allMatch(extractPersonNames(source)::contains)) {
            throw new AiResponseInvalidException(
                    capabilityName + " 응답에 원문에서 확인할 수 없는 사실이 포함되어 있습니다."
            );
        }
    }

    public static boolean hasUnsupportedLegalCitation(
            String output,
            List<String> ragSources
    ) {
        Set<String> citations = extract(LEGAL_CITATION, output);
        if (citations.isEmpty()) {
            return false;
        }
        List<String> normalizedSources = ragSources == null
                ? List.of()
                : ragSources.stream()
                .filter(source -> source != null && !source.isBlank())
                .map(GenerationOutputValidator::normalize)
                .toList();
        return citations.stream()
                .map(GenerationOutputValidator::normalize)
                .anyMatch(citation -> normalizedSources.stream()
                        .noneMatch(source -> source.contains(citation)));
    }

    private static Set<String> extract(Pattern pattern, String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        Set<String> values = new LinkedHashSet<>();
        var matcher = pattern.matcher(value);
        while (matcher.find()) {
            values.add(matcher.groupCount() == 0 ? matcher.group() : matcher.group(1));
        }
        return Set.copyOf(values);
    }

    private static String normalize(String value) {
        return value.replaceAll("\\s+", "");
    }

    private static Set<String> extractPersonNames(String value) {
        return extract(PERSON_NAME, value).stream()
                .filter(name -> !NON_PERSON_NAMES.contains(name))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

}
