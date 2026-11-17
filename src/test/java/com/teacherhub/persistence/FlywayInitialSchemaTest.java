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
                "select count(*) from flyway_schema_history where version in ('1', '2', '3') and success = true",
                Integer.class
        );

        assertThat(appliedMigrationCount).isEqualTo(3);

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
                "PROFANITY", 4,
                "INSULT", 6,
                "THREAT", 22,
                "LEGAL", 4,
                "EXPOSURE", 6,
                "REPEAT", 6,
                "UNFAIR_REQUEST", 6,
                "PRIVACY", 6
        ));
    }

    @Test
    void v2RebalancesPreExistingRiskTagScoresWithoutDataInitializer() {
        String databaseUrl = "jdbc:h2:mem:flyway-v2-isolated-" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        var dataSource = new DriverManagerDataSource(databaseUrl, "sa", "");

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("1"))
                .load()
                .migrate();

        var isolatedJdbcTemplate = new JdbcTemplate(dataSource);
        isolatedJdbcTemplate.batchUpdate(
                "insert into risk_tags (code, name, default_score) values (?, ?, ?)",
                List.of(
                        new Object[]{"PROFANITY", "욕설", 6},
                        new Object[]{"INSULT", "인격 모욕", 10},
                        new Object[]{"THREAT", "위험·협박", 25},
                        new Object[]{"LEGAL", "법적 조치 언급", 4},
                        new Object[]{"EXPOSURE", "공개·유포 위험", 12},
                        new Object[]{"REPEAT", "반복 민원", 6},
                        new Object[]{"UNFAIR_REQUEST", "부당 요구", 8},
                        new Object[]{"PRIVACY", "개인정보 위험", 15}
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
                "PROFANITY", 4,
                "INSULT", 6,
                "THREAT", 22,
                "LEGAL", 4,
                "EXPOSURE", 6,
                "REPEAT", 6,
                "UNFAIR_REQUEST", 6,
                "PRIVACY", 6
        ));
    }

    @Test
    void idempotencyKeyUsesCaseSensitiveMySqlCollation() throws IOException {
        String migration = new ClassPathResource("db/migration/V1__init_schema.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(migration)
                .containsPattern("idempotency_key\\s+VARCHAR\\(100\\)\\s+COLLATE\\s+utf8mb4_bin");
    }
}
