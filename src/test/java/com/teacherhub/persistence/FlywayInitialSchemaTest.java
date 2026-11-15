package com.teacherhub.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

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
    void appliesAllMigrationsAndValidatesAllEntityMappings() {
        Integer appliedMigrationCount = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history "
                        + "where version in ('1', '2') and success = true",
                Integer.class
        );

        assertThat(appliedMigrationCount).isEqualTo(2);
    }

    @Test
    void idempotencyKeyUsesCaseSensitiveMySqlCollation() throws IOException {
        String migration = new ClassPathResource(
                "db/migration/V2__make_idempotency_key_case_sensitive.sql"
        )
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(migration)
                .contains("MODIFY COLUMN idempotency_key VARCHAR(100)")
                .contains("CHARACTER SET utf8mb4")
                .contains("COLLATE utf8mb4_bin");
    }
}
