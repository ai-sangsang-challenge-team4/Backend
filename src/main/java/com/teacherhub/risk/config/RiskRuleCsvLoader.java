package com.teacherhub.risk.config;

import com.teacherhub.risk.enums.RiskRuleMatchType;
import com.teacherhub.risk.enums.RiskTagCode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class RiskRuleCsvLoader {

    static final String DATASET_VERSION = "v1.1";
    private static final String RESOURCE =
            "risk/teachercare_rule_dictionary_v1.1_extended.csv";
    private static final int EXPECTED_RULE_COUNT = 72;

    public List<RiskRuleSeed> load() {
        ClassPathResource resource = new ClassPathResource(RESOURCE);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        resource.getInputStream(),
                        StandardCharsets.UTF_8
                )
        )) {
            String headerLine = reader.readLine();

            if (headerLine == null) {
                throw new IllegalStateException("룰 사전 CSV 헤더가 없습니다.");
            }

            Map<String, Integer> columns = indexColumns(parseLine(headerLine));
            List<RiskRuleSeed> rules = new ArrayList<>();
            String line;
            int lineNumber = 1;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                if (line.isBlank()) {
                    continue;
                }

                List<String> values = parseLine(line);

                try {
                    String sourceTagCode = value(values, columns, "risk_tag_code");

                    rules.add(new RiskRuleSeed(
                            value(values, columns, "rule_id"),
                            sourceTagCode,
                            mapTagCode(sourceTagCode),
                            value(values, columns, "keyword"),
                            RiskRuleMatchType.valueOf(
                                    value(values, columns, "match_type")
                            ),
                            Boolean.parseBoolean(
                                    value(values, columns, "context_required")
                            ),
                            value(values, columns, "context_subtype"),
                            value(values, columns, "normalization_rule")
                    ));

                } catch (RuntimeException exception) {
                    throw new IllegalStateException(
                            "룰 사전 CSV를 읽을 수 없습니다. line=" + lineNumber,
                            exception
                    );
                }
            }

            if (rules.size() != EXPECTED_RULE_COUNT) {
                throw new IllegalStateException(
                        "룰 사전 행 수가 올바르지 않습니다. expected="
                                + EXPECTED_RULE_COUNT
                                + ", actual="
                                + rules.size()
                );
            }

            long uniqueRuleIds = rules.stream()
                    .map(RiskRuleSeed::ruleId)
                    .distinct()
                    .count();

            if (uniqueRuleIds != rules.size()) {
                throw new IllegalStateException("룰 사전에 중복 rule_id가 있습니다.");
            }

            return List.copyOf(rules);

        } catch (IOException exception) {
            throw new IllegalStateException("룰 사전 CSV를 불러올 수 없습니다.", exception);
        }
    }

    static List<String> parseLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;

        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);

            if (character == '"') {
                if (quoted
                        && index + 1 < line.length()
                        && line.charAt(index + 1) == '"') {
                    value.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                values.add(value.toString());
                value.setLength(0);
            } else {
                value.append(character);
            }
        }

        if (quoted) {
            throw new IllegalArgumentException("닫히지 않은 CSV 따옴표가 있습니다.");
        }

        values.add(value.toString());
        return values;
    }

    private Map<String, Integer> indexColumns(List<String> headers) {
        Map<String, Integer> columns = new HashMap<>();

        for (int index = 0; index < headers.size(); index++) {
            columns.put(headers.get(index), index);
        }

        for (String required : List.of(
                "rule_id",
                "risk_tag_code",
                "keyword",
                "match_type",
                "context_required",
                "context_subtype",
                "normalization_rule"
        )) {
            if (!columns.containsKey(required)) {
                throw new IllegalStateException(
                        "룰 사전 CSV 필수 컬럼이 없습니다: " + required
                );
            }
        }

        return columns;
    }

    private String value(
            List<String> values,
            Map<String, Integer> columns,
            String column
    ) {
        int index = columns.get(column);

        if (index >= values.size()) {
            throw new IllegalArgumentException("CSV 컬럼 값이 없습니다: " + column);
        }

        return values.get(index).trim();
    }

    private RiskTagCode mapTagCode(String sourceTagCode) {
        try {
            return RiskTagCode.valueOf(sourceTagCode);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "백엔드 RiskTagCode와 일치하지 않는 위험 태그입니다: "
                            + sourceTagCode,
                    exception
            );
        }
    }

    public record RiskRuleSeed(
            String ruleId,
            String sourceTagCode,
            RiskTagCode tagCode,
            String expression,
            RiskRuleMatchType matchType,
            boolean contextRequired,
            String contextSubtype,
            String normalizationRule
    ) {
    }
}
