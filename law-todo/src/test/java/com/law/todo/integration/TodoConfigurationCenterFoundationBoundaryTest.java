package com.law.todo.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.law.todo.application.TodoAdmissionEvidenceService;
import com.law.todo.application.TodoFoundationAdmissionReadinessService;

class TodoConfigurationCenterFoundationBoundaryTest
{
    private static final Path REPOSITORY_ROOT = locateRepositoryRoot();

    @Test void rebuildDoesNotRemoveFoundationPublishGate() throws IOException
    {
        assertNotNull(TodoFoundationAdmissionReadinessService.class);
        assertNotNull(TodoAdmissionEvidenceService.class);
        String definition = source("law-todo/src/main/java/com/law/todo/application/TodoDefinitionService.java");
        assertTrue(definition.contains("TODO_PRD_TEMPLATE_BLOCKED"));
    }

    @Test void legacyTabbedPageIsRemovedWhileNewPagesAndGovernanceComponentsRemain()
    {
        assertFalse(Files.exists(REPOSITORY_ROOT.resolve("ruoyi-ui/src/views/todo/config/index.vue")));
        for (String page : List.of("template", "sla", "dod", "trigger", "simulation", "release"))
            assertTrue(Files.exists(REPOSITORY_ROOT.resolve(
                    "ruoyi-ui/src/views/todo/config/" + page + "/index.vue")), page);
        for (String component : List.of("FoundationAdmissionOverview.vue", "FoundationResourceReadiness.vue",
                "HistoricalMigrationReadiness.vue", "FileSecurityReadiness.vue", "FinanceReadiness.vue",
                "AcceptanceReadiness.vue"))
            assertTrue(Files.exists(REPOSITORY_ROOT.resolve(
                    "ruoyi-ui/src/views/todo/config/components/" + component)), component);
    }

    @Test void prdCatalogAndFoundationMigrationsRemainAvailable() throws IOException
    {
        String manifest = source("law-todo/src/main/resources/todo-definitions/v0.2/manifest.json");
        assertTrue(manifest.contains("TD-001"));
        assertTrue(manifest.contains("TD-025"));
        assertTrue(Files.exists(REPOSITORY_ROOT.resolve(
                "ruoyi-admin/src/main/resources/db/migration/V0_20_10__v02_prd_definition_catalog.sql")));
    }

    private static String source(String relativePath) throws IOException
    {
        return Files.readString(REPOSITORY_ROOT.resolve(relativePath));
    }

    private static Path locateRepositoryRoot()
    {
        Path current = Path.of("").toAbsolutePath();
        while (current != null)
        {
            if (Files.isDirectory(current.resolve("law-todo")) && Files.isDirectory(current.resolve("ruoyi-ui")))
                return current;
            current = current.getParent();
        }
        throw new IllegalStateException("Repository root not found from " + Path.of("").toAbsolutePath());
    }
}
