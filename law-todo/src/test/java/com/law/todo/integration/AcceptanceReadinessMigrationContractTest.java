package com.law.todo.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class AcceptanceReadinessMigrationContractTest
{
    @Test
    void migration_creates_unmapped_slots_without_fabricating_acceptance_evidence() throws Exception
    {
        String sql = Files.readString(Path.of("..", "ruoyi-admin", "src", "main", "resources", "db",
                "migration", "V0_20_21__foundation_phase_one_acceptance.sql"))
                .toLowerCase().replaceAll("\\s+", " ");

        assertTrue(sql.contains("create table todo_foundation_acceptance_requirement"));
        assertTrue(sql.contains("create table todo_acceptance_scenario"));
        assertTrue(sql.contains("create table todo_acceptance_ref_mapping"));
        assertTrue(sql.contains("create table todo_acceptance_action"));
        assertTrue(sql.contains("json_table"));
        assertTrue(sql.contains("unique key uk_todo_acceptance_ref (acceptance_ref)"));
        assertEquals(8, sql.split("\\('g-07'", -1).length - 1);

        List<String> phaseOneTemplates = List.of("td-001", "td-002", "td-003", "td-004", "td-005",
                "td-006", "td-007", "td-008", "td-009", "td-010", "td-011", "td-012", "td-013",
                "td-014", "td-015", "td-016", "td-022", "td-023", "td-025");
        phaseOneTemplates.forEach(code -> assertTrue(sql.contains("'" + code + "'"), code));
        assertTrue(sql.contains("'unmapped','mapped','in_review','approved','rejected'"));
        assertTrue(sql.contains("'draft','in_review','approved','rejected'"));

        assertFalse(sql.contains("insert into todo_acceptance_scenario"));
        assertFalse(sql.contains("insert into todo_acceptance_action"));
        assertFalse(sql.contains("insert into sys_user"));
        assertFalse(sql.contains("insert into sys_role"));
        assertFalse(sql.contains("insert into sys_role_menu"));
        assertFalse(sql.contains("insert into todo_trigger_rule"));
        assertFalse(sql.contains("update todo_template"));
        assertFalse(sql.contains("update todo_prd_definition_catalog"));
    }
}
