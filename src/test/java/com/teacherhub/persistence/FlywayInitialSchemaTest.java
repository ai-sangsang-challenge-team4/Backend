package com.teacherhub.persistence;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
                "spring.datasource.url=jdbc:h2:mem:flyway-initial;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.flyway.enabled=true",
                "spring.flyway.locations=classpath:db/migration"
        }
)
class FlywayInitialSchemaTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appliesInitialSchemaAndValidatesAllEntityMappings() {
        Integer appliedMigrationCount = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version in ('1', '2', '3', '4', '5', '6', '7', '8') and success = true",
                Integer.class
        );

        assertThat(appliedMigrationCount).isEqualTo(8);

        Map<String, Integer> configuredScores = jdbcTemplate.query(
                "select code, default_score from risk_tags",
                resultSet -> {
                    Map<String, Integer> scores = new HashMap<>();
                    while (resultSet.next()) {
                        scores.put(
                                resultSet.getString("code"),
                                resultSet.getInt("default_score")
                        );
                    }
                    return scores;
                }
        );

        assertThat(configuredScores).containsExactlyInAnyOrderEntriesOf(Map.of(
                "PROFANITY", 7,
                "INSULT", 8,
                "THREAT", 12,
                "LEGAL", 6,
                "EXPOSURE", 6,
                "REPEAT", 4,
                "UNFAIR_REQUEST", 2,
                "PRIVACY", 5
        ));
    }

    @Test
    void v7RebalancesPreExistingRiskTagScoresWithoutDataInitializer() {
        String databaseUrl = "jdbc:h2:mem:flyway-v7-isolated-" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        var dataSource = new DriverManagerDataSource(databaseUrl, "sa", "");

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("6"))
                .load()
                .migrate();

        var isolatedJdbcTemplate = new JdbcTemplate(dataSource);
        isolatedJdbcTemplate.batchUpdate(
                "insert into risk_tags (code, name, default_score) values (?, ?, ?)",
                List.of(
                        new Object[]{"PROFANITY", "욕설", 4},
                        new Object[]{"INSULT", "인격 모욕", 6},
                        new Object[]{"THREAT", "위험·협박", 22},
                        new Object[]{"LEGAL", "법적 조치 언급", 4},
                        new Object[]{"EXPOSURE", "공개·유포 위험", 6},
                        new Object[]{"REPEAT", "반복 민원", 6},
                        new Object[]{"UNFAIR_REQUEST", "부당 요구", 6},
                        new Object[]{"PRIVACY", "개인정보 위험", 6}
                )
        );

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        Map<String, Integer> migratedScores = isolatedJdbcTemplate.query(
                "select code, default_score from risk_tags",
                resultSet -> {
                    Map<String, Integer> scores = new HashMap<>();
                    while (resultSet.next()) {
                        scores.put(
                                resultSet.getString("code"),
                                resultSet.getInt("default_score")
                        );
                    }
                    return scores;
                }
        );

        assertThat(migratedScores).containsExactlyInAnyOrderEntriesOf(Map.of(
                "PROFANITY", 7,
                "INSULT", 8,
                "THREAT", 12,
                "LEGAL", 6,
                "EXPOSURE", 6,
                "REPEAT", 4,
                "UNFAIR_REQUEST", 2,
                "PRIVACY", 5
        ));
    }

    @Test
    void v8RecalculatesStoredStudentSafetyOnlyEmergencyAnalysis() {
        String databaseUrl = "jdbc:h2:mem:flyway-v8-isolated-" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        var dataSource = new DriverManagerDataSource(databaseUrl, "sa", "");

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("7"))
                .load()
                .migrate();

        var isolatedJdbcTemplate = new JdbcTemplate(dataSource);
        isolatedJdbcTemplate.execute("set referential_integrity false");
        isolatedJdbcTemplate.update(
                "insert into risk_tags (id, code, name, default_score) values (1, 'THREAT', '위험·협박', 12)"
        );
        isolatedJdbcTemplate.update("""
                insert into risk_analyses (
                    id, complaint_id, message_id, content_version, risk_score, risk_level,
                    model_name, temperature, credible_threat, student_safety_concern,
                    privacy_exposure, analysis_mode, analyzed_at, content_hash, completed,
                    revision_status
                ) values (
                    1, 1, 1, 0, 100, 'EMERGENCY', 'old-policy-model', 0.0,
                    false, true, false, 'HYBRID', current_timestamp,
                    'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                    true, 'COMPLETED'
                )
                """);
        isolatedJdbcTemplate.update("""
                insert into risk_analyses (
                    id, complaint_id, message_id, content_version, risk_score, risk_level,
                    model_name, temperature, credible_threat, student_safety_concern,
                    privacy_exposure, analysis_mode, analyzed_at, content_hash, completed,
                    revision_status
                ) values
                    (
                        2, 1, 2, 0, 100, 'EMERGENCY', 'old-policy-model', 0.0,
                        true, true, false, 'HYBRID', current_timestamp,
                        'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
                        true, 'COMPLETED'
                    ),
                    (
                        3, 1, 3, 0, 100, 'EMERGENCY', 'old-policy-model', 0.0,
                        false, true, true, 'HYBRID', current_timestamp,
                        'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc',
                        true, 'COMPLETED'
                    )
                """);
        isolatedJdbcTemplate.update("""
                insert into complaint_risk_tags (
                    id, risk_analysis_id, risk_tag_id, rule_detected, llm_detected,
                    final_detected, rule_state, llm_state, final_state, severity, repeat_count
                ) values (
                    1, 1, 1, false, true, true, 'ABSENT', 'PRESENT', 'PRESENT', 2, 1
                )
                """);
        isolatedJdbcTemplate.execute("set referential_integrity true");

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        Map<String, Object> recalculated = isolatedJdbcTemplate.queryForMap(
                "select risk_score, risk_level from risk_analyses where id = 1"
        );

        assertThat(recalculated)
                .containsEntry("risk_score", 31)
                .containsEntry("risk_level", "MEDIUM");

        List<Map<String, Object>> emergencySignals = isolatedJdbcTemplate.queryForList(
                "select risk_score, risk_level from risk_analyses where id in (2, 3) order by id"
        );
        assertThat(emergencySignals)
                .allSatisfy(analysis -> assertThat(analysis)
                        .containsEntry("risk_score", 100)
                        .containsEntry("risk_level", "EMERGENCY"));
    }

    @Test
    void v9ReclassifiesStoredAnalysesWithCurrentRiskLevelBoundaries() {
        String databaseUrl = "jdbc:h2:mem:flyway-v9-isolated-" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        var dataSource = new DriverManagerDataSource(databaseUrl, "sa", "");

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("8"))
                .load()
                .migrate();

        var isolatedJdbcTemplate = new JdbcTemplate(dataSource);
        isolatedJdbcTemplate.execute("set referential_integrity false");
        isolatedJdbcTemplate.update("""
                insert into risk_analyses (
                    id, complaint_id, message_id, content_version, risk_score, risk_level,
                    model_name, temperature, analysis_mode, analyzed_at, content_hash,
                    completed, revision_status
                ) values
                    (
                        1, 1, 1, 0, 51, 'MEDIUM', 'old-boundary-model', 0.0,
                        'HYBRID', current_timestamp,
                        'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                        true, 'COMPLETED'
                    ),
                    (
                        2, 1, 2, 0, 80, 'EMERGENCY', 'old-boundary-model', 0.0,
                        'HYBRID', current_timestamp,
                        'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
                        true, 'COMPLETED'
                    ),
                    (
                        3, 1, 3, 0, 81, 'EMERGENCY', 'old-boundary-model', 0.0,
                        'HYBRID', current_timestamp,
                        'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc',
                        true, 'COMPLETED'
                    )
                """);
        isolatedJdbcTemplate.execute("set referential_integrity true");

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        List<Map<String, Object>> reclassified = isolatedJdbcTemplate.queryForList(
                "select risk_score, risk_level from risk_analyses order by id"
        );

        assertThat(reclassified)
                .containsExactly(
                        Map.of("risk_score", 51, "risk_level", "HIGH"),
                        Map.of("risk_score", 80, "risk_level", "HIGH"),
                        Map.of("risk_score", 81, "risk_level", "EMERGENCY")
                );
    }

    @Test
    void idempotencyKeyUsesCaseSensitiveMySqlCollation() throws IOException {
        String migration = new ClassPathResource("db/migration/V1__init_schema.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(migration)
                .containsPattern("idempotency_key\\s+VARCHAR\\(100\\)\\s+COLLATE\\s+utf8mb4_bin");
    }
}
