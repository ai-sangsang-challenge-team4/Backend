package com.teacherhub.ai.validation;

import com.teacherhub.ai.exception.AiResponseInvalidException;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    private static final Pattern REQUIREMENT_MARKER = Pattern.compile(
            "요청|요구|바랍|원합|부탁|촉구|해\\s*주|하여\\s*주|해야\\s*합|해라|하십시오|바라겠"
    );
    private static final Map<String, Pattern> REQUIREMENT_INTENTS = requirementIntents();

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

    public static void validateRequirementIntentPreserved(
            String source,
            String revision
    ) {
        for (String clause : source.split("[.!?。！？\\n]+")) {
            if (!REQUIREMENT_MARKER.matcher(clause).find()) {
                continue;
            }
            for (Map.Entry<String, Pattern> intent : REQUIREMENT_INTENTS.entrySet()) {
                if (intent.getValue().matcher(clause).find()
                        && !intent.getValue().matcher(revision).find()) {
                    throw new AiResponseInvalidException(
                            "AI 수정안이 원문의 요구사항을 보존하지 않았습니다."
                    );
                }
            }
        }
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

    private static Map<String, Pattern> requirementIntents() {
        Map<String, Pattern> intents = new LinkedHashMap<>();
        intents.put("CONFIRM", Pattern.compile("확인|검토|사실관계"));
        intents.put("ANSWER", Pattern.compile("답변|회신|응답|안내"));
        intents.put("APOLOGY", Pattern.compile("사과|유감"));
        intents.put("EXPLANATION", Pattern.compile("해명|설명"));
        intents.put("ACTION", Pattern.compile("조치|처리|해결|개선|시정|징계|처벌"));
        intents.put("CANCEL", Pattern.compile("취소|철회|중단"));
        intents.put("CORRECT", Pattern.compile("수정|정정"));
        intents.put("DELETE", Pattern.compile("삭제|제거"));
        intents.put("REFUND", Pattern.compile("환불|반환"));
        intents.put("MEETING", Pattern.compile("면담|상담|통화"));
        intents.put("DISCLOSE", Pattern.compile("공개|제공|열람"));
        intents.put("REPLACE", Pattern.compile("교체|변경"));
        return Map.copyOf(intents);
    }
}
