package com.ruoyi.web.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

class FoundationCollationMigrationTest
{
    @Test
    void normalizesAllRuntimeTablesToTheBaselineCollation() throws Exception
    {
        String url = MigrationTestDatabase.migrate();
        try (Connection connection = DriverManager.getConnection(url, MigrationTestDatabase.user(),
            MigrationTestDatabase.password()); Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery("select count(*) from information_schema.tables "
                + "where table_schema=database() and table_collation<>'utf8mb4_unicode_ci'"))
        {
            result.next();
            assertEquals(0, result.getInt(1),
                "Every v0.2 runtime table must use the v0.15 baseline collation");
        }
    }
}
