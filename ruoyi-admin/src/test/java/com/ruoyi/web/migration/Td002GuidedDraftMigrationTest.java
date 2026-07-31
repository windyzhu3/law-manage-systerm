package com.ruoyi.web.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class Td002GuidedDraftMigrationTest
{
    private static final Path MIGRATION=Path.of("src","main","java","db","migration",
            "V0_20_73__SeedTd002GuidedDraft.java");

    @Test void migrationIsForwardOnlyAndCreatesAnUnpublishedGovernedDraft() throws Exception
    {
        assertThat(MIGRATION).exists();
        String source=Files.readString(MIGRATION);
        assertThat(source)
                .contains("/todo-definitions/v0.2/TD-002.json")
                .contains("LEAD_INVALID_REVIEW_READY")
                .contains("t.template_code='TD-001'")
                .contains("v.status='PUBLISHED'")
                .contains("'DRAFT'")
                .contains("TD002_TRUE_INVALID")
                .contains("TD002_MISJUDGED_VALID")
                .contains("TD002_OVERDUE_DEFAULT")
                .contains("expectedEffect")
                .contains("requiredForPublish")
                .doesNotContain("insert into todo_trigger_rule")
                .doesNotContain("update todo_template\n");
    }
}
