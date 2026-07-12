package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;

class FlywayMigrationTest
{
    @Test
    void migratesV015BaselineToTodoPhaseOneSchema()
    {
        String url = System.getenv("TODO_MIGRATION_DB_URL");
        assumeTrue(url != null && !url.isBlank(), "Migration database is provided by the CI quality gate");
        Flyway flyway = Flyway.configure()
            .dataSource(url, System.getenv("TODO_MIGRATION_DB_USER"), System.getenv("TODO_MIGRATION_DB_PASSWORD"))
            .baselineOnMigrate(true)
            .baselineVersion("0.15.0")
            .locations("classpath:db/migration")
            .load();

        MigrateResult result = flyway.migrate();
        MigrationInfo current = flyway.info().current();

        assertTrue(result.success);
        assertEquals("0.16.5", current.getVersion().getVersion());
    }
}
