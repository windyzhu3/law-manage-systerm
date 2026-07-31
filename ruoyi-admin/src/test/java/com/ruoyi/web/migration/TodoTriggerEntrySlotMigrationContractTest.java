package com.ruoyi.web.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TodoTriggerEntrySlotMigrationContractTest
{
    @Test
    void migrationDefinesOneEnabledBindingPerEntrySlot()
    {
        String sql = migration("V0_20_70__todo_trigger_entry_slots.sql");
        assertThat(sql).contains("entry_slot_code", "active_entry_slot_code",
                "uk_todo_trigger_active_entry_slot", "LEAD_FIRST_CONTACT_ENTRY");
        assertThat(sql).contains("replacement_template_code", "TD-001");
    }

    @Test
    void followUpMigrationRepairsAndGuardsTheLeadIngressSlot()
    {
        String sql=migration("V0_20_71__todo_lead_ingress_hardening.sql");
        assertThat(sql).contains("LEAD_FIRST_CONTACT_ENTRY", "TD-001",
                "ck_todo_trigger_lead_ingress_slot", "enabled='Y'",
                "coalesce(@lead_entry_target,0)", "trigger_rule_id>0");
    }

    private String migration(String name)
    {
        try (InputStream resource = getClass().getResourceAsStream("/db/migration/" + name))
        {
            assertThat(resource).as("Migration resource %s", name).isNotNull();
            return new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException exception)
        {
            throw new AssertionError("Unable to read migration " + name, exception);
        }
    }
}
