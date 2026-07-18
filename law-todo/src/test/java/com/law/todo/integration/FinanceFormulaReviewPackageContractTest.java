package com.law.todo.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class FinanceFormulaReviewPackageContractTest
{
    private static final Path PACKAGE=Path.of("..","doc","reviews",
            "v0.2-foundation-g06-finance-formula-review-package.md");
    private static final Path MIGRATION=Path.of("..","ruoyi-admin","src","main","resources","db","migration",
            "V0_20_26__foundation_g06_finance_review_package.sql");
    private static final List<String> REQUIREMENTS=List.of("FEE_PLAN_CORE_PRECISION",
            "PAYMENT_CONFIRMATION_SERVICE","PAYMENT_IDEMPOTENT_EVENT","NODE_FEE_SCHEMA",
            "RECEIVABLE_COLLECTION_REFUND_TABLES","RISK_FEE_SCHEMA","Q009_NODE_COLLECTION_POLICY",
            "Q012_RISK_FORMULA_POLICY","FINANCE_BUSINESS_SIGNOFF");

    @Test void packageDefinesDecisionAndReconciliationInputsWithoutChoosingFormulaOrApproving() throws Exception
    {
        String document=Files.readString(PACKAGE);

        assertTrue(document.contains("PENDING_FINANCE_BUSINESS_ARCHITECTURE_REVIEW"));
        assertTrue(document.contains("Q-009：未决"));
        assertTrue(document.contains("Q-012：未决"));
        assertTrue(document.contains("不得由开发人员代签"));
        for(String code:REQUIREMENTS)assertTrue(document.contains(code),"Missing G-06 requirement "+code);
        for(String object:List.of("biz_contract_fee_plan","biz_receivable_trigger","biz_collection_record",
                "biz_refund","biz_risk_fee_calculation","PAYMENT_CONFIRMED","RECEIVABLE_DUE",
                "RISK_FEE_CONFIRMED"))assertTrue(document.contains(object),"Missing finance boundary "+object);
        for(String input:List.of("公式版本","金额精度","舍入","税费","退款","重算","催收 Owner"))
            assertTrue(document.contains(input),"Missing review input "+input);
        assertTrue(document.contains("FINANCE_BUSINESS_SIGNOFF=NEEDS_REVIEW"));
        assertTrue(document.contains("G06-FINANCE-FORMULA=OPEN"));
        assertFalse(document.contains("APPROVED_BY_DEVELOPMENT"));
    }

    @Test void migrationLinksOnlySignoffAndNeverChangesPolicyOrFinanceFacts() throws Exception
    {
        String sql=Files.readString(MIGRATION).toLowerCase().replaceAll("\\s+"," ");

        assertEquals(1,count(sql,"requirement_code='finance_business_signoff'"));
        assertTrue(sql.contains("source_status='needs_review'"));
        assertTrue(sql.contains("v0.2-foundation-g06-finance-formula-review-package.md"));
        assertFalse(sql.contains("set source_status"));
        assertFalse(sql.contains("update todo_decision"));
        assertFalse(sql.contains("todo_admission_evidence"));
        assertFalse(sql.contains("alter table biz_contract"));
        assertFalse(sql.contains("alter table biz_contract_fee_plan"));
        assertFalse(sql.contains("create table biz_receivable"));
        assertFalse(sql.contains("create table biz_collection"));
        assertFalse(sql.contains("create table biz_refund"));
        assertFalse(sql.contains("create table biz_risk"));
        assertFalse(sql.contains("'approved'"));
    }

    private int count(String text,String token){return text.split(java.util.regex.Pattern.quote(token),-1).length-1;}
}
