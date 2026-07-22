package com.law.todo.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class TodoConfigurableClosureMigrationContractTest
{
    private static final Path MIGRATION=Path.of("..","ruoyi-admin","src","main","resources","db","migration",
            "V0_20_36__todo_configuration_resources.sql");
    private static final Set<String> ACTIVE_EVENTS=Set.of(
            "ARCHIVE_APPLIED","CASE_ASSIGNED","CASE_CLASSIFIED_COMPREHENSIVE","CASE_CLASSIFIED_ENFORCEMENT",
            "CASE_CLASSIFIED_NON_LITIGATION","CASE_CLOSED","CASE_CREATED","CASE_HANDOFF_ACCEPTED",
            "CASE_HANDOFF_SUBMITTED","CASE_REJECTED","CASE_TRANSFER_APPROVED","CASE_TRANSFER_REQUESTED",
            "CONFLICT_SCREENING_FAILED","CONTRACT_APPROVED","CONTRACT_SIGNED","CONTRACT_SUBMITTED",
            "CUSTOMER_PROGRESS_STALE","DEAL_CONFIRMED","ENFORCEMENT_ORDER_ACCEPTED",
            "ENFORCEMENT_SERVICE_NODE_READY","INVOICE_HANDLED","LEAD_ASSIGNED",
            "LEAD_FIRST_CONTACT_UNREACHABLE","LEAD_FIRST_CONTACT_VALID","LEAD_SUSPECT_INVALID_MARKED",
            "MATTER_DOCUMENT_REQUIRED","MATTER_EXPENSE_SUBMITTED","MATTER_HEARING_COMPLETED",
            "MATTER_NODE_READY","NON_LITIGATION_WORK_COMPLETED","PAYMENT_CONFIRMED","PAYMENT_FULLY_RECEIVED",
            "QUOTE_ACCEPTED_CONFLICT_CLEARED","QUOTE_DISCOUNT_APPROVAL_REQUESTED","RECEIVABLE_DUE",
            "RISK_FEE_CONFIRMED");

    @Test void migrationCreatesGovernedConfigurationResources() throws Exception
    {
        String sql=normalized();

        for(String column:Set.of("event_name","description","source_module","schema_status","version"))
            assertTrue(sql.contains("add column "+column),"missing event catalogue column "+column);
        assertTrue(sql.contains("create table todo_validator_metadata"));
        for(String column:Set.of("validator_code","validator_name","business_types_json","parameter_schema_json",
                "example_parameters_json","status","version"))
            assertTrue(sql.contains(column),"missing validator metadata column "+column);
        assertTrue(sql.contains("'todo:resource:list'"));
        assertTrue(sql.contains("'todo:resource:edit'"));
    }

    @Test void migrationMakesEveryActiveEventSchemaSelectable() throws Exception
    {
        String sql=normalized();
        Pattern eventUpdate=Pattern.compile("event_type='([a-z0-9_]+)'\\s+and payload_version=1");
        Set<String> updated=eventUpdate.matcher(sql).results().map(match->match.group(1)).collect(Collectors.toSet());
        Set<String> expected=ACTIVE_EVENTS.stream().map(String::toLowerCase).collect(Collectors.toSet());
        String eventSql=sql.substring(0,sql.indexOf("insert into todo_validator_metadata"));

        assertEquals(expected,updated);
        assertEquals(ACTIVE_EVENTS.size(),count(eventSql,"schema_status='ready'"));
        assertEquals(ACTIVE_EVENTS.size(),count(eventSql,"'properties',json_object("));
        assertEquals(ACTIVE_EVENTS.size(),count(eventSql,"sample_payload_json=json_object("));
    }

    private String normalized() throws Exception
    {return Files.readString(MIGRATION).toLowerCase().replaceAll("--[^\\r\\n]*","").replaceAll("\\s+"," ");}

    private int count(String input,String token)
    {int count=0;for(int index=0;(index=input.indexOf(token,index))>=0;index+=token.length())count++;return count;}
}
