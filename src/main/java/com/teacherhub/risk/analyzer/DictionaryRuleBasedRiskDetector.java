package com.teacherhub.risk.analyzer;

import com.teacherhub.risk.dto.detection.EvidenceSpan;
import com.teacherhub.risk.dto.detection.RiskTagDetectionResult;
import com.teacherhub.risk.entity.RiskDetectionRule;
import com.teacherhub.risk.enums.RiskRuleMatchType;
import com.teacherhub.risk.enums.RiskTagCode;
import com.teacherhub.risk.enums.RiskTagState;
import com.teacherhub.risk.repository.RiskDetectionRuleRepository;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Component
public class DictionaryRuleBasedRiskDetector
        implements RuleBasedRiskDetector {

    private final RiskDetectionRuleRepository repository;
    private volatile List<CompiledRule> cachedRules = List.of();

    public DictionaryRuleBasedRiskDetector(
            RiskDetectionRuleRepository repository
    ) {
        this.repository = repository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void refresh() {
        cachedRules = repository.findAllByEnabledTrueOrderByIdAsc()
                .stream()
                .map(CompiledRule::from)
                .toList();
    }

    @Override
    public List<RiskTagDetectionResult> detect(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        List<CompiledRule> rules = rules();
        NormalizedContent normalized = NormalizedContent.from(content);
        Map<RiskTagCode, List<RuleMatch>> matches =
                new EnumMap<>(RiskTagCode.class);

        for (CompiledRule rule : rules) {
            rule.find(normalized)
                    .filter(span -> !isExcluded(rule.ruleId(), normalized.nfc(), span))
                    .ifPresent(span ->
                            matches.computeIfAbsent(
                                            rule.tagCode(),
                                            ignored -> new ArrayList<>()
                                    )
                                    .add(new RuleMatch(rule, span))
                    );
        }

        return matches.entrySet()
                .stream()
                .map(entry -> aggregate(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<CompiledRule> rules() {
        List<CompiledRule> rules = cachedRules;

        if (!rules.isEmpty()) {
            return rules;
        }

        synchronized (this) {
            if (cachedRules.isEmpty()) {
                refresh();
            }

            return cachedRules;
        }
    }

    private RiskTagDetectionResult aggregate(
            RiskTagCode code,
            List<RuleMatch> matches
    ) {
        boolean hintOnly = matches.stream()
                .allMatch(match -> match.rule().matchType().isHintOnly());

        List<EvidenceSpan> evidenceSpans = matches.stream()
                .map(RuleMatch::evidence)
                .distinct()
                .toList();

        Set<String> ruleIds = new LinkedHashSet<>();
        Set<String> matchTypes = new LinkedHashSet<>();
        Set<String> contextChecks = new LinkedHashSet<>();

        matches.forEach(match -> {
            ruleIds.add(match.rule().ruleId());
            matchTypes.add(match.rule().matchType().name());
            contextChecks.add(match.rule().contextSubtype());
        });

        String reason = "ruleCandidates="
                + String.join(",", ruleIds)
                + "; matchTypes="
                + String.join(",", matchTypes)
                + "; contextChecks="
                + String.join(",", contextChecks);

        return new RiskTagDetectionResult(
                code,
                hintOnly ? RiskTagState.UNKNOWN : RiskTagState.BOUNDARY,
                null,
                evidenceSpans,
                reason
        );
    }

    private boolean isExcluded(
            String ruleId,
            String normalizedContent,
            EvidenceSpan span
    ) {
        return switch (ruleId) {
            case "RD-PROF-09" -> overlaps(
                    normalizedContent,
                    span,
                    "시발점"
            );
            case "RD-THREAT-09" -> overlaps(
                    normalizedContent,
                    span,
                    "죽여주는"
            );
            default -> false;
        };
    }

    private boolean overlaps(
            String content,
            EvidenceSpan span,
            String excludedExpression
    ) {
        if (span.start() == null || span.end() == null) {
            return false;
        }

        int fromIndex = 0;
        int excludedStart;

        while ((excludedStart = content.indexOf(excludedExpression, fromIndex)) >= 0) {
            int excludedEnd = excludedStart + excludedExpression.length();

            if (span.start() < excludedEnd && span.end() > excludedStart) {
                return true;
            }

            fromIndex = excludedStart + 1;
        }

        return false;
    }

    private record RuleMatch(
            CompiledRule rule,
            EvidenceSpan evidence
    ) {
    }

    private record CompiledRule(
            String ruleId,
            RiskTagCode tagCode,
            String expression,
            RiskRuleMatchType matchType,
            String contextSubtype,
            boolean ignoreCase,
            Pattern pattern
    ) {
        static CompiledRule from(RiskDetectionRule rule) {
            Pattern pattern = null;

            if (rule.getMatchType() == RiskRuleMatchType.PATTERN) {
                try {
                    pattern = Pattern.compile(rule.getExpression());
                } catch (PatternSyntaxException exception) {
                    throw new IllegalStateException(
                            "Java와 호환되지 않는 위험 탐지 정규식입니다: "
                                    + rule.getRuleId(),
                            exception
                    );
                }
            }

            return new CompiledRule(
                    rule.getRuleId(),
                    rule.getTagCode(),
                    rule.getExpression(),
                    rule.getMatchType(),
                    rule.getContextSubtype(),
                    rule.getNormalizationRule().contains("대소문자 무시"),
                    pattern
            );
        }

        Optional<EvidenceSpan> find(NormalizedContent content) {
            if (matchType == RiskRuleMatchType.PATTERN) {
                Matcher matcher = pattern.matcher(content.nfc());

                if (!matcher.find()) {
                    return Optional.empty();
                }

                return Optional.of(content.spanFromNfc(
                        matcher.start(),
                        matcher.end()
                ));
            }

            String normalizedExpression = Normalizer.normalize(
                            expression,
                            Normalizer.Form.NFC
                    )
                    .replaceAll("\\s+", "");

            String haystack = content.compact();

            if (ignoreCase) {
                normalizedExpression = normalizedExpression.toLowerCase(Locale.ROOT);
                haystack = haystack.toLowerCase(Locale.ROOT);
            }

            int index = haystack.indexOf(normalizedExpression);

            if (index < 0) {
                return Optional.empty();
            }

            return Optional.of(content.spanFromCompact(
                    index,
                    index + normalizedExpression.length()
            ));
        }
    }

    private record NormalizedContent(
            String original,
            String nfc,
            String compact,
            int[] compactToNfcIndex
    ) {
        static NormalizedContent from(String original) {
            String nfc = Normalizer.normalize(original, Normalizer.Form.NFC);
            StringBuilder compact = new StringBuilder();
            List<Integer> mapping = new ArrayList<>();

            for (int index = 0; index < nfc.length(); index++) {
                char character = nfc.charAt(index);

                if (!Character.isWhitespace(character)) {
                    compact.append(character);
                    mapping.add(index);
                }
            }

            int[] compactToNfcIndex = mapping.stream()
                    .mapToInt(Integer::intValue)
                    .toArray();

            return new NormalizedContent(
                    original,
                    nfc,
                    compact.toString(),
                    compactToNfcIndex
            );
        }

        EvidenceSpan spanFromCompact(int start, int end) {
            int nfcStart = compactToNfcIndex[start];
            int nfcEnd = compactToNfcIndex[end - 1] + 1;
            return spanFromNfc(nfcStart, nfcEnd);
        }

        EvidenceSpan spanFromNfc(int start, int end) {
            String evidence = nfc.substring(start, end);

            if (!original.equals(nfc)) {
                return EvidenceSpan.unlocated(evidence);
            }

            return new EvidenceSpan(evidence, start, end);
        }
    }
}
