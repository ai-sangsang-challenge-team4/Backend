package com.teacherhub.risk.analyzer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.teacherhub.risk.config.RiskRuleCsvLoader;
import com.teacherhub.risk.dto.detection.RiskTagDetectionResult;
import com.teacherhub.risk.entity.RiskDetectionRule;
import com.teacherhub.risk.enums.RiskTagCode;
import com.teacherhub.risk.enums.RiskTagState;
import com.teacherhub.risk.repository.RiskDetectionRuleRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Offline-only entry point. It runs the exact production DictionaryRuleBasedRiskDetector
 * against caller-provided Golden Set complaint texts, without starting Spring, DB, or AI.
 * Only the existing 72-rule classpath dictionary is loaded; no scoring logic is copied.
 */
public final class RuleGoldensetExportMain {
    private static final ObjectMapper JSON = new ObjectMapper();

    private RuleGoldensetExportMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: <input-json> <new-output-json>");
        }
        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);
        if (Files.exists(output)) {
            throw new IllegalArgumentException("Refusing to overwrite existing Rule results");
        }

        JsonNode cases = JSON.readTree(input.toFile());
        if (!cases.isArray() || cases.size() != 200) {
            throw new IllegalArgumentException("Exactly 200 input cases are required");
        }
        List<RiskDetectionRule> rules = loadProductionRules();
        if (rules.size() != 72) {
            throw new IllegalStateException("Expected 72 original dictionary rules");
        }

        RiskDetectionRuleRepository repository = mock(RiskDetectionRuleRepository.class);
        when(repository.findAllByEnabledTrueOrderByIdAsc()).thenReturn(rules);
        DictionaryRuleBasedRiskDetector detector = new DictionaryRuleBasedRiskDetector(repository);
        detector.refresh();

        ObjectNode outputByCase = JSON.createObjectNode();
        Set<String> seenIds = new HashSet<>();
        for (JsonNode item : cases) {
            String caseId = item.path("case_id").asText("");
            String content = item.path("message_text").asText("");
            if (caseId.isBlank() || content.isBlank() || !seenIds.add(caseId)) {
                throw new IllegalArgumentException("Missing or duplicate case ID / complaint");
            }
            outputByCase.set(caseId, detectAllTags(detector, content));
        }
        Files.createDirectories(output.toAbsolutePath().getParent());
        JSON.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), outputByCase);
        System.out.println("Offline Rule Detector completed: 200 cases, 72 rules, no network calls");
    }

    static List<RiskDetectionRule> loadProductionRules() {
        return new RiskRuleCsvLoader().load().stream().map(seed ->
                new RiskDetectionRule(
                        seed.ruleId(), seed.sourceTagCode(), seed.tagCode(),
                        seed.expression(), seed.matchType(), seed.contextRequired(),
                        seed.contextSubtype(), seed.normalizationRule(), "v1.1"
                )).toList();
    }

    static ArrayNode detectAllTags(DictionaryRuleBasedRiskDetector detector, String content) {
        Map<RiskTagCode, RiskTagDetectionResult> detected = new EnumMap<>(RiskTagCode.class);
        for (RiskTagDetectionResult result : detector.detect(content)) {
            detected.put(result.code(), result);
        }

        ArrayNode tags = JSON.createArrayNode();
        for (RiskTagCode code : RiskTagCode.values()) {
            RiskTagDetectionResult result = detected.get(code);
            ObjectNode tag = JSON.createObjectNode();
            tag.put("code", code.name());
            tag.put("state", result == null ? RiskTagState.ABSENT.name() : result.state().name());
            tag.putNull("confidence");
            tag.putNull("severity");
            tag.putNull("repeatCount");
            ArrayNode spans = tag.putArray("evidenceSpans");
            if (result != null) {
                result.evidenceSpans().forEach(span -> {
                    ObjectNode node = spans.addObject();
                    node.put("text", span.text());
                    if (span.start() != null) node.put("start", span.start());
                    if (span.end() != null) node.put("end", span.end());
                });
            }
            tag.put("reason", result == null ? "No matching dictionary rule" : result.reason());
            tags.add(tag);
        }
        return tags;
    }
}
