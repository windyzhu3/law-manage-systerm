package com.law.todo.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import com.alibaba.fastjson2.JSON;
import com.law.todo.definition.catalog.TodoDecisionService;
import com.law.todo.definition.catalog.TodoEventCatalogService;
import com.law.todo.definition.codec.TodoDefinitionCodec;
import com.law.todo.definition.compiler.TodoDefinitionCompiler;
import com.law.todo.definition.model.TodoDefinitionDocument;

class ExistingTemplateParityTest
{
    private static final Path MIGRATION=Path.of("..","ruoyi-admin","src","main","resources","db","migration","V0_20_9__todo_existing_template_contracts.sql");
    private static final String GENERIC_SCHEMA="{\"additionalProperties\":true,\"type\":\"object\"}";

    @ParameterizedTest(name="{0}")
    @MethodSource("technicalTemplates")
    void migratedDefinitionCompilesAndKeepsEventUiDodAndSlaParity(TemplateContract contract) throws Exception
    {
        String sql=Files.readString(MIGRATION);
        assertTrue(sql.contains("('"+contract.templateCode()+"','"+contract.eventType()+"','"+contract.businessType()+"'"),contract+" migration row");
        assertTrue(sql.contains("'"+contract.dodConfig()+"',"+contract.slaMinutes()+",'"+contract.uiConfig()+"')"),contract+" DoD/UI/SLA row");

        TodoEventCatalogService events=mock(TodoEventCatalogService.class);TodoDecisionService decisions=mock(TodoDecisionService.class);
        when(events.payloadSchema(eq(contract.eventType()),eq(1))).thenReturn(GENERIC_SCHEMA);
        when(decisions.unresolvedBlockingDecisions(anyList())).thenReturn(List.of());
        TodoDefinitionDocument definition=new TodoDefinitionDocument(1,contract.templateCode(),
                new TodoDefinitionDocument.EventRule(contract.eventType(),1,Map.of()),
                new TodoDefinitionDocument.OwnerRule(Map.of("type","USER","operand",7)),
                new TodoDefinitionDocument.DodRule(json(contract.dodConfig())),
                new TodoDefinitionDocument.SlaRule(Map.of("calendarCode","DEFAULT","minutes",contract.slaMinutes())),
                new TodoDefinitionDocument.UiSchema(json(contract.uiConfig())),new TodoDefinitionDocument.RoutingGraph(Map.of()),
                List.of(),List.of(),List.of());
        var report=new TodoDefinitionCompiler(new TodoDefinitionCodec(),events,decisions).compile(definition);
        assertTrue(report.errors().isEmpty(),()->contract+" "+report.errors());
    }

    @Test void migrationCreatesImmutablePublishedSnapshotsAndRetiresOnlyOldTriggers() throws Exception
    {
        String sql=Files.readString(MIGRATION);
        for(String marker:List.of("definition_json","compiled_json","definition_hash","lower(sha2(x.definition_text,256))",
                "'PUBLISHED'","r.enabled='N'","r.template_version_id<>x.new_version_id","todo_event_catalog"))
            assertTrue(sql.contains(marker),marker);
        assertTrue(sql.contains("V0_20_9")||MIGRATION.getFileName().toString().startsWith("V0_20_9"));
    }

    @Test void knownContractMismatchesUseStableV02KeysAndFileRelations() throws Exception
    {
        String sql=Files.readString(MIGRATION);
        assertTrue(sql.contains("'LEAD_FIRST_CONTACT','LEAD_ASSIGNED','LEAD'")&&sql.contains("\"CONTACT_PROOF\"")&&sql.contains(",30,'"));
        assertTrue(sql.contains("{\"requiredFields\":[\"reviewAction\",\"opinion\"]}"));
        assertTrue(sql.contains("{\"requiredFields\":[\"transferId\",\"reviewAction\",\"opinion\"]}"));
        assertTrue(sql.contains("\"fileRelationType\":\"SIGNED_CONTRACT\"")&&sql.contains("\"type\":\"SIGNED_CONTRACT\""));
    }

    static Stream<TemplateContract> technicalTemplates()
    {
        return Stream.of(
            c("LEAD_FIRST_CONTACT","LEAD_ASSIGNED","LEAD","{\"materials\":[{\"minCount\":1,\"type\":\"CONTACT_PROOF\"}],\"requiredFields\":[\"contactResult\"]}",30,"{\"fields\":[{\"key\":\"contactResult\",\"type\":\"text\"},{\"key\":\"contactProof\",\"materialType\":\"CONTACT_PROOF\",\"type\":\"file\"}],\"fileRelationType\":\"CONTACT_PROOF\",\"formCode\":\"LEAD_FIRST_CONTACT\"}"),
            c("CONTRACT_REVIEW","CONTRACT_SUBMITTED","CONTRACT","{\"requiredFields\":[\"reviewAction\",\"opinion\"]}",240,"{\"fields\":[{\"key\":\"reviewAction\",\"type\":\"dict\"},{\"key\":\"opinion\",\"type\":\"textarea\"}],\"formCode\":\"CONTRACT_REVIEW\"}"),
            c("CONTRACT_SIGN","CONTRACT_APPROVED","CONTRACT","{\"materials\":[{\"minCount\":1,\"type\":\"SIGNED_CONTRACT\"}],\"requiredFields\":[\"signStatus\",\"signMethod\",\"signDate\"]}",960,"{\"fields\":[{\"key\":\"signStatus\",\"type\":\"dict\"},{\"key\":\"signMethod\",\"type\":\"dict\"},{\"key\":\"signDate\",\"type\":\"date\"},{\"key\":\"signedContract\",\"materialType\":\"SIGNED_CONTRACT\",\"type\":\"file\"}],\"fileRelationType\":\"SIGNED_CONTRACT\",\"formCode\":\"CONTRACT_SIGN\"}"),
            c("PAYMENT_CONFIRM","CONTRACT_SIGNED","CONTRACT","{\"requiredFields\":[\"planId\",\"receivedAmount\",\"voucherUrl\"]}",480,"{\"fields\":[{\"key\":\"planId\",\"type\":\"number\"},{\"key\":\"receivedAmount\",\"type\":\"number\"},{\"key\":\"voucherUrl\",\"type\":\"text\"}],\"formCode\":\"PAYMENT_CONFIRM\"}"),
            c("INVOICE_HANDLE","PAYMENT_CONFIRMED","CONTRACT","{\"requiredFields\":[\"planId\",\"action\"]}",960,"{\"fields\":[{\"key\":\"planId\",\"type\":\"number\"},{\"key\":\"action\",\"type\":\"dict\"},{\"key\":\"invoiceNo\",\"type\":\"text\"},{\"key\":\"invoiceFileUrl\",\"type\":\"text\"}],\"formCode\":\"INVOICE_HANDLE\"}"),
            c("CASE_CREATE_CHECK","INVOICE_HANDLED","CONTRACT","{\"requiredFields\":[\"materialsChecked\"]}",960,"{\"fields\":[{\"key\":\"materialsChecked\",\"type\":\"dict\"}],\"formCode\":\"CASE_CREATE_CHECK\"}"),
            c("CASE_ASSIGN","CASE_CREATED","CASE","{\"requiredFields\":[\"lawyerId\"]}",480,"{\"fields\":[{\"key\":\"lawyerId\",\"type\":\"user\"}],\"formCode\":\"CASE_ASSIGN\"}"),
            c("CASE_ACCEPT","CASE_ASSIGNED","CASE","{\"requiredFields\":[\"confirmId\",\"accepted\"]}",240,"{\"fields\":[{\"key\":\"confirmId\",\"type\":\"number\"},{\"key\":\"accepted\",\"type\":\"dict\"}],\"formCode\":\"CASE_ACCEPT\"}"),
            c("CASE_REASSIGN","CASE_REJECTED","CASE","{\"requiredFields\":[\"lawyerId\"]}",960,"{\"fields\":[{\"key\":\"lawyerId\",\"type\":\"user\"}],\"formCode\":\"CASE_REASSIGN\"}"),
            c("CASE_TRANSFER_REVIEW","CASE_TRANSFER_REQUESTED","CASE","{\"requiredFields\":[\"transferId\",\"reviewAction\",\"opinion\"]}",960,"{\"fields\":[{\"key\":\"transferId\",\"type\":\"number\"},{\"key\":\"reviewAction\",\"type\":\"dict\"},{\"key\":\"opinion\",\"type\":\"textarea\"}],\"formCode\":\"CASE_TRANSFER_REVIEW\"}"),
            c("MATTER_NODE_HANDLE","MATTER_NODE_READY","MATTER","{\"requiredFields\":[\"nodeId\",\"actualDate\"]}",960,"{\"fields\":[{\"key\":\"nodeId\",\"type\":\"number\"},{\"key\":\"actualDate\",\"type\":\"date\"}],\"formCode\":\"MATTER_NODE_HANDLE\"}"),
            c("MATTER_EXPENSE_REVIEW","MATTER_EXPENSE_SUBMITTED","MATTER","{\"requiredFields\":[\"expenseId\",\"result\"]}",960,"{\"fields\":[{\"key\":\"expenseId\",\"type\":\"number\"},{\"key\":\"result\",\"type\":\"dict\"}],\"formCode\":\"MATTER_EXPENSE_REVIEW\"}"),
            c("MATTER_DOCUMENT_SUPPLY","MATTER_DOCUMENT_REQUIRED","MATTER","{\"requiredFields\":[\"documentType\",\"fileName\",\"fileUrl\"]}",960,"{\"fields\":[{\"key\":\"documentType\",\"type\":\"dict\"},{\"key\":\"fileName\",\"type\":\"text\"},{\"key\":\"fileUrl\",\"type\":\"text\"}],\"formCode\":\"MATTER_DOCUMENT_SUPPLY\"}"),
            c("CASE_CLOSE_CONFIRM","ARCHIVE_APPLIED","MATTER","{\"requiredFields\":[\"action\",\"opinion\",\"feeClearStatus\"]}",960,"{\"fields\":[{\"key\":\"action\",\"type\":\"dict\"},{\"key\":\"opinion\",\"type\":\"textarea\"},{\"key\":\"feeClearStatus\",\"type\":\"dict\"}],\"formCode\":\"CASE_CLOSE_CONFIRM\"}"),
            c("CASE_ARCHIVE_CONFIRM","CASE_CLOSED","MATTER","{\"requiredFields\":[\"action\",\"opinion\",\"archiveNo\"]}",960,"{\"fields\":[{\"key\":\"action\",\"type\":\"dict\"},{\"key\":\"opinion\",\"type\":\"textarea\"},{\"key\":\"archiveNo\",\"type\":\"text\"}],\"formCode\":\"CASE_ARCHIVE_CONFIRM\"}"));
    }

    private static TemplateContract c(String code,String event,String type,String dod,int sla,String ui){return new TemplateContract(code,event,type,dod,sla,ui);}
    @SuppressWarnings("unchecked") private static Map<String,Object> json(String value){return JSON.parseObject(value,Map.class);}
    record TemplateContract(String templateCode,String eventType,String businessType,String dodConfig,int slaMinutes,String uiConfig)
    {@Override public String toString(){return templateCode;}}
}
