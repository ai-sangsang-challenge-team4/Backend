package com.teacherhub.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

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
                "select count(*) from flyway_schema_history where version in ('1', '2') and success = true",
                Integer.class
        );

        assertThat(appliedMigrationCount).isEqualTo(2);

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
    void idempotencyKeyUsesCaseSensitiveMySqlCollation() throws IOException {
        String migration = new ClassPathResource("db/migration/V1__init_schema.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(migration)
                .containsPattern("idempotency_key\\s+VARCHAR\\(100\\)\\s+COLLATE\\s+utf8mb4_bin");
    }
}
