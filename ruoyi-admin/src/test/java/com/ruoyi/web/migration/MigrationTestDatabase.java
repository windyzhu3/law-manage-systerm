package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.flywaydb.core.Flyway;

final class MigrationTestDatabase
{
    private MigrationTestDatabase() { }

    static String migrate()
    {
        String url = System.getenv("TODO_MIGRATION_DB_URL");
        assumeTrue(url != null && !url.isBlank(), "Migration database is provided by the CI quality gate");
        Flyway.configure()
            .dataSource(url, user(), password())
            .baselineOnMigrate(true)
            .baselineVersion("0.15.0")
            .locations("classpath:db/migration")
            .load()
            .migrate();
        return url;
    }

    static String user() { return System.getenv("TODO_MIGRATION_DB_USER"); }
    static String password() { return System.getenv("TODO_MIGRATION_DB_PASSWORD"); }
}
