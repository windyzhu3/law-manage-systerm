package com.law.todo.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FinanceReadinessMigrationContractTest
{
    @Test void checklist_does_not_create_finance_facts_or_formulas() throws Exception
    {String sql=Files.readString(Path.of("..","ruoyi-admin","src","main","resources","db","migration","V0_20_20__foundation_finance_readiness.sql")).toLowerCase().replaceAll("\\s+"," ");assertEquals(9,sql.split("\\('g-06'",-1).length-1);assertTrue(sql.contains("'q009_node_collection_policy'"));assertTrue(sql.contains("'q012_risk_formula_policy'"));assertFalse(sql.contains("alter table biz_contract_fee_plan"));assertFalse(sql.contains("create table biz_risk_fee_calculation"));assertFalse(sql.contains("update biz_"));}
}
