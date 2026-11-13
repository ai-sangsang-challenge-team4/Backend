package com.teacherhub.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

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
                "select count(*) from flyway_schema_history where version = '1' and success = true",
                Integer.class
        );

        assertThat(appliedMigrationCount).isEqualTo(1);
    }
}
