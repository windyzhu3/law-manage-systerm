package com.law.todo.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.law.todo.definition.catalog.TodoDecisionService;
import com.law.todo.definition.catalog.TodoEventCatalogService;
import com.law.todo.definition.codec.TodoDefinitionCodec;
import com.law.todo.definition.compiler.TodoDefinitionCompiler;
import com.law.todo.definition.model.TodoDefinitionDocument;

class ExistingTemplateParityTest
{
    private static final Path MIGRATION=Path.of("..","ruoyi-admin","src","main","resources","db","migration","V0_20_9__todo_existing_template_contracts.sql");
    private static final String GENERIC_SCHEMA="{\"additionalProperties\":true,\"type\":\"object\"}";
    private static final Map<String,Long> ROLE_VARIABLES=Map.of(
            "case_manager_role_id",101L,"finance_role_id",102L,"partner_role_id",103L);

    @ParameterizedTest(name="{0}")
    @MethodSource("migrationContracts")
    void migratedDefinitionIsTheExactCanonicalCompilerOutput(MigrationContract contract) throws Exception
    {
        String definitionText=evaluateDefinitionExpression(sql(),contract.values());
        TodoDefinitionCodec codec=new TodoDefinitionCodec();
        TodoDefinitionDocument definition=codec.read(definitionText);

        assertEquals(contract.templateCode(),definition.templateCode());
        assertEquals(contract.eventType(),definition.event().eventType());
        assertEquals(contract.businessType(),contract.values().get("business_type"));
        assertEquals(contract.values().get("owner_config"),
                JSON.toJSONString(definition.owner().config(),JSONWriter.Feature.SortMapEntriesByKeys));

        TodoEventCatalogService events=mock(TodoEventCatalogService.class);
        TodoDecisionService decisions=mock(TodoDecisionService.class);
        when(events.payloadSchema(eq(contract.eventType()),eq(1))).thenReturn(GENERIC_SCHEMA);
        when(decisions.unresolvedBlockingDecisions(anyList())).thenReturn(List.of());
        var report=new TodoDefinitionCompiler(codec,events,decisions).compile(definition);

        assertTrue(report.errors().isEmpty(),()->contract+" "+report.errors());
        assertEquals(codec.canonicalJson(definition),definitionText,contract+" definition_json canonical bytes");
        assertEquals(definitionText,report.compiledJson(),contract+" compiled_json canonical bytes");
        assertEquals(sha256(definitionText),report.definitionHash(),contract+" definition_hash");
    }

    @Test void migrationRowsResolveRoleOwnersAndCoverTheCatalogAndTriggerContracts() throws Exception
    {
        Map<String,MigrationContract> contracts=contractsByCode();
        assertEquals(15,contracts.size());
        assertEquals(103,((Number)contracts.get("CONTRACT_REVIEW").definition().owner().config().get("operand")).intValue());
        assertEquals(102,((Number)contracts.get("PAYMENT_CONFIRM").definition().owner().config().get("operand")).intValue());
        assertEquals(101,((Number)contracts.get("CASE_ASSIGN").definition().owner().config().get("operand")).intValue());

        String sql=sql();
        for(String marker:List.of("select distinct x.event_type,1,x.business_type","select x.event_type,t.template_id,x.new_version_id,x.business_type",
                "'CASE_TRANSFER_APPROVED',t.template_id,x.new_version_id,'CASE'","json_object('requiresAcceptance',true)"))
            assertTrue(sql.contains(marker),marker);
        assertEquals("CASE_ACCEPT",contracts.get("CASE_ACCEPT").templateCode());
    }

    @Test void migrationFailsBeforeMutationUnlessAllRolesTemplatesAndSourceVersionsExist() throws Exception
    {
        String sql=sql().toLowerCase();
        assertTrue(sql.contains("ck_task11_required_roles_present"));
        assertTrue(sql.contains("ck_task11_all_15_templates_present"));
        assertTrue(sql.contains("ck_task11_all_15_source_versions_present"));
        int guard=sql.indexOf("ck_task11_all_15_source_versions_present");
        assertTrue(guard>=0&&guard<sql.indexOf("insert into todo_event_catalog"),"guards must precede persistent writes");
    }

    @Test void migrationReusesAnAlreadyPublishedCanonicalVersionOnPartialRetry() throws Exception
    {
        String sql=sql().toLowerCase();
        assertTrue(sql.contains("v.definition_hash=lower(sha2(x.definition_text,256))"));
        assertTrue(sql.contains("v.status='published'"));
        assertTrue(sql.contains("set x.new_version_id=v.version_id,x.new_version_no=v.version_no"));
        assertTrue(sql.contains("where x.new_version_id is null"));
        assertEquals(1,count(sql,"insert into todo_template_version"),"migration has one guarded version insert");
    }

    @Test void knownContractCorrectionsAndOldHistoryImmutabilityRemainEncoded() throws Exception
    {
        Map<String,MigrationContract> contracts=contractsByCode();
        TodoDefinitionDocument lead=contracts.get("LEAD_FIRST_CONTACT").definition();
        assertEquals(30,lead.sla().config().get("minutes"));
        assertEquals("CONTACT_PROOF",lead.ui().config().get("fileRelationType"));
        assertEquals("SIGNED_CONTRACT",contracts.get("CONTRACT_SIGN").definition().ui().config().get("fileRelationType"));
        assertEquals(List.of("reviewAction","opinion"),contracts.get("CONTRACT_REVIEW").definition().dod().config().get("requiredFields"));
        assertEquals(List.of("transferId","reviewAction","opinion"),contracts.get("CASE_TRANSFER_REVIEW").definition().dod().config().get("requiredFields"));

        String sql=sql().toLowerCase();
        assertFalse(sql.contains("update todo_template_version set"),"published history must not be mutated");
        assertTrue(sql.contains("r.template_version_id<>x.new_version_id"));
    }

    static Stream<MigrationContract> migrationContracts() throws Exception
    {
        return parseContracts(sql()).stream();
    }

    private static Map<String,MigrationContract> contractsByCode() throws Exception
    {
        Map<String,MigrationContract> result=new LinkedHashMap<>();
        for(MigrationContract contract:parseContracts(sql()))result.put(contract.templateCode(),contract);
        return result;
    }

    private static List<MigrationContract> parseContracts(String sql)
    {
        String marker="insert into tmp_todo_template_contract(template_code,event_type,business_type,owner_legacy,owner_config,dod_config,sla_minutes,ui_config) values";
        int start=sql.indexOf(marker)+marker.length();
        String values=sql.substring(start,sql.indexOf(';',start));
        List<MigrationContract> result=new ArrayList<>();
        for(String tuple:splitTopLevel(values,','))
        {
            String trimmed=tuple.trim();
            if(trimmed.isEmpty())continue;
            assertTrue(trimmed.startsWith("(")&&trimmed.endsWith(")"),trimmed);
            List<String> columns=splitTopLevel(trimmed.substring(1,trimmed.length()-1),',');
            assertEquals(8,columns.size(),trimmed);
            Map<String,String> row=new LinkedHashMap<>();
            String[] names={"template_code","event_type","business_type","owner_legacy","owner_config","dod_config","sla_minutes","ui_config"};
            for(int i=0;i<names.length;i++)row.put(names[i],evaluateScalar(columns.get(i).trim()));
            result.add(new MigrationContract(row));
        }
        return result;
    }

    private static String evaluateDefinitionExpression(String sql,Map<String,String> row)
    {
        Matcher matcher=Pattern.compile("set\\s+definition_text=concat\\((.*?)\\);",Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher(sql);
        assertTrue(matcher.find(),"definition_text concat expression");
        StringBuilder value=new StringBuilder();
        for(String token:splitTopLevel(matcher.group(1),','))
        {
            String expression=token.trim();
            value.append(expression.startsWith("'")?unquote(expression):row.get(expression));
        }
        return value.toString();
    }

    private static String evaluateScalar(String expression)
    {
        if(expression.startsWith("'"))return unquote(expression);
        if(expression.startsWith("concat("))
        {
            StringBuilder value=new StringBuilder();
            for(String token:splitTopLevel(expression.substring(7,expression.length()-1),','))
            {
                String part=token.trim();
                value.append(part.startsWith("'")?unquote(part):ROLE_VARIABLES.get(part.substring(1)));
            }
            return value.toString();
        }
        return expression;
    }

    private static List<String> splitTopLevel(String value,char delimiter)
    {
        List<String> result=new ArrayList<>();int depth=0,start=0;boolean quoted=false;
        for(int i=0;i<value.length();i++)
        {
            char current=value.charAt(i);
            if(current=='\''&&(i+1>=value.length()||value.charAt(i+1)!='\''))quoted=!quoted;
            else if(current=='\''&&quoted)i++;
            else if(!quoted&&current=='(')depth++;
            else if(!quoted&&current==')')depth--;
            else if(!quoted&&depth==0&&current==delimiter){result.add(value.substring(start,i));start=i+1;}
        }
        result.add(value.substring(start));return result;
    }

    private static String unquote(String value)
    {
        return value.substring(1,value.length()-1).replace("''","'");
    }

    private static int count(String value,String marker)
    {
        int count=0,index=0;while((index=value.indexOf(marker,index))>=0){count++;index+=marker.length();}return count;
    }

    private static String sha256(String value) throws Exception
    {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static String sql() throws Exception{return Files.readString(MIGRATION);}

    record MigrationContract(Map<String,String> values)
    {
        String templateCode(){return values.get("template_code");}
        String eventType(){return values.get("event_type");}
        String businessType(){return values.get("business_type");}
        TodoDefinitionDocument definition() throws Exception{return new TodoDefinitionCodec().read(evaluateDefinitionExpression(sql(),values));}
        @Override public String toString(){return templateCode();}
    }
}
