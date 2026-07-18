package com.law.todo.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class HistoricalMigrationReviewPackageContractTest
{
    private static final Path PACKAGE=Path.of("..","doc","reviews",
            "v0.2-foundation-g04-historical-migration-review-package.md");
    private static final Path MIGRATION=Path.of("..","ruoyi-admin","src","main","resources","db","migration",
            "V0_20_25__foundation_g04_migration_review_package.sql");
    private static final List<String> REVIEW_CODES=List.of("UNCLASSIFIED_CASE_EXCEPTION_LIST",
            "BACKFILL_BATCH_IDEMPOTENCY","BACKFILL_VALIDATION_SQL","BACKFILL_ROLLBACK_SQL");

    @Test void reviewPackageDefinesAnExactGuardedPlanWithoutChoosingOrApproving() throws Exception
    {
        String document=Files.readString(PACKAGE);

        assertTrue(document.contains("PENDING_ARCHITECTURE_CASE_DBA_REVIEW"));
        assertTrue(document.contains("Reviewer：未指定"));
        assertTrue(document.contains("默认业务线：未决定"));
        assertTrue(document.contains("不得由开发人员代签"));
        for(String code:List.of("CASE_BUSINESS_LINE_SCHEMA","HISTORICAL_CASE_DEFAULT",
                "UNCLASSIFIED_CASE_EXCEPTION_LIST","BACKFILL_BATCH_IDEMPOTENCY","BACKFILL_VALIDATION_SQL",
                "BACKFILL_ROLLBACK_SQL","TODO_VERSION_REFERENCE","TODO_VERSION_IMMUTABILITY"))
            assertTrue(document.contains(code),"Missing G-04 requirement "+code);
        for(String value:List.of("NON_LITIGATION","COMPREHENSIVE","EXECUTION"))
            assertTrue(document.contains(value),"Missing business-line value "+value);
        assertTrue(document.contains("biz_case_business_line_exception"));
        assertTrue(document.contains("biz_case_business_line_migration_batch"));
        assertTrue(document.contains("biz_case_business_line_migration_audit"));
        assertTrue(document.contains("unique key uk_case_line_audit_batch_case"));
        assertTrue(document.contains("orphan_todo_version_count"));
        assertTrue(document.contains("c.business_line=a.new_business_line"));
        assertTrue(document.contains("c.update_time=a.migrated_at"));
        assertTrue(document.contains("签字/审批记录：未提供"));
        assertFalse(document.contains("APPROVED_BY_DEVELOPMENT"));
    }

    @Test void forwardMigrationLinksOnlyTheFourEvidenceItemsAndNeverApprovesOrMutatesBusinessData() throws Exception
    {
        String sql=Files.readString(MIGRATION).toLowerCase().replaceAll("\\s+"," ");

        for(String code:REVIEW_CODES)assertTrue(sql.contains("'"+code.toLowerCase()+"'"));
        assertEquals(4,count(sql,"requirement_code='"));
        assertTrue(sql.contains("source_status='needs_evidence'"));
        assertTrue(sql.contains("v0.2-foundation-g04-historical-migration-review-package.md"));
        assertFalse(sql.contains("set source_status"));
        assertFalse(sql.contains("todo_admission_evidence"));
        assertFalse(sql.contains("alter table biz_case"));
        assertFalse(sql.contains("update biz_case"));
        assertFalse(sql.contains("update todo_instance"));
        assertFalse(sql.contains("reviewer_user_id"));
        assertFalse(sql.contains("owner_user_id"));
        assertFalse(sql.contains("'approved'"));
    }

    private int count(String text,String token){return text.split(java.util.regex.Pattern.quote(token),-1).length-1;}
}
