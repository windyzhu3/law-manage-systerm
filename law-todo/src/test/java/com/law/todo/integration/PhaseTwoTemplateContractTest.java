package com.law.todo.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class PhaseTwoTemplateContractTest
{
    @Test void migrationDefinesEveryStandardTemplateEventAndPermission() throws Exception
    {
        Path path=Path.of("..","ruoyi-admin","src","main","resources","db","migration","V0_17_3__todo_standard_templates.sql");
        String sql=Files.readString(path);
        for(String code:List.of("CONTRACT_REVIEW","CONTRACT_SIGN","PAYMENT_CONFIRM","INVOICE_HANDLE","CASE_CREATE_CHECK","CASE_ASSIGN","CASE_ACCEPT","CASE_REASSIGN","CASE_TRANSFER_REVIEW","MATTER_NODE_HANDLE","MATTER_EXPENSE_REVIEW","MATTER_DOCUMENT_SUPPLY","CASE_CLOSE_CONFIRM","CASE_ARCHIVE_CONFIRM"))assertTrue(sql.contains("'"+code+"'"),code);
        for(String event:List.of("CONTRACT_SUBMITTED","CONTRACT_APPROVED","CONTRACT_SIGNED","PAYMENT_CONFIRMED","INVOICE_HANDLED","CASE_CREATED","CASE_ASSIGNED","CASE_REJECTED","CASE_TRANSFER_REQUESTED","CASE_TRANSFER_APPROVED","MATTER_NODE_READY","MATTER_EXPENSE_SUBMITTED","MATTER_DOCUMENT_REQUIRED","ARCHIVE_APPLIED","CASE_CLOSED"))assertTrue(sql.contains("'"+event+"'"),event);
        for(String permission:List.of("todo:chain:query","todo:operations:list","todo:force:complete","todo:force:cancel","todo:batch:transfer","todo:sla:waive","todo:regenerate","todo:definition:list","todo:definition:edit","todo:definition:publish"))assertTrue(sql.contains("'"+permission+"'"),permission);
        assertTrue(sql.contains("role_key='case_manager'"));assertTrue(sql.contains("role_key='finance_manager'"));assertTrue(sql.contains("role_key='law_partner_manager'"));
    }
}
