package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.todo.domain.TodoAccessPolicy;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.TodoStatus;
import com.law.todo.domain.TodoStatusTransitions;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.mapper.TodoMapper;
import com.law.file.application.FileMaterialQueryService;
import com.law.file.application.FileMaterialQueryService.FileMaterialView;
import com.law.file.domain.FileObject.FileActor;

@ExtendWith(MockitoExtension.class)
class TodoQueryServiceTest
{
    @Mock TodoMapper mapper;@Mock TodoAccessPolicy access;
    @Test void rejectsInvisibleDetail(){TodoInstance t=new TodoInstance();t.setTodoId(1L);when(mapper.selectById(1L)).thenReturn(t);when(access.canView(t,7L,3L)).thenReturn(false);TodoException e=assertThrows(TodoException.class,()->new TodoQueryService(mapper,access).detail(1L,7L,3L));assertEquals("TODO_ACCESS_DENIED",e.getBusinessCode());}
    @Test void dashboardReturnsMapperMetrics(){when(mapper.selectDashboard(7L,3L)).thenReturn(Map.of("mine",4));assertEquals(4,new TodoQueryService(mapper,access).dashboard(7L,3L).get("mine"));}
    @Test void detailViewContainsAuditAndAttachments(){TodoInstance t=new TodoInstance();t.setTodoId(1L);when(mapper.selectById(1L)).thenReturn(t);when(access.canView(t,7L,3L)).thenReturn(true);when(mapper.selectActionTimeline(1L)).thenReturn(java.util.List.of(Map.of("action_type","CLAIM")));when(mapper.selectAttachments(1L)).thenReturn(java.util.List.of(Map.of("file_name","a.pdf")));Map<String,Object> view=new TodoQueryService(mapper,access).detailView(1L,7L,3L);assertEquals(1,((java.util.List<?>)view.get("actions")).size());assertEquals(1,((java.util.List<?>)view.get("attachments")).size());}
    @Test void formUsesPublishedDefinitionSnapshot()
    {
        TodoInstance t=new TodoInstance();t.setTodoId(2L);t.setStatus("SUBMITTED");t.setTemplateVersionId(9L);
        when(mapper.selectById(2L)).thenReturn(t);when(access.canView(t,7L,3L)).thenReturn(true);
        when(mapper.selectTemplateVersionById(9L)).thenReturn(Map.of(
                "compiled_json", "{\"schemaVersion\":1,\"templateCode\":\"T\",\"dod\":{\"config\":{\"requiredFields\":[\"result\"]}},\"ui\":{\"config\":{\"fields\":[\"result\"],\"defaults\":{\"result\":\"OK\"}}},\"autoActions\":[],\"decisionRefs\":[],\"acceptanceRefs\":[]}",
                "definition_hash", "abc"));

        var form=new TodoQueryService(mapper,access).form(2L,new Actor(7L,"alice",3L));

        assertEquals("COMPLETE",form.action());assertEquals("abc",form.definitionHash());
        assertEquals("OK",form.defaults().get("result"));
    }

    @Test void form_exposes_authoritative_business_material_and_extension_policy_context()
    {
        TodoInstance todo=new TodoInstance();todo.setTodoId(2L);todo.setStatus("SUBMITTED");todo.setTemplateVersionId(9L);
        todo.setBusinessType("CONTRACT");todo.setBusinessId(77L);
        when(mapper.selectById(2L)).thenReturn(todo);when(access.canView(todo,7L,3L)).thenReturn(true);
        when(mapper.selectTemplateVersionById(9L)).thenReturn(Map.of("dod_rule_json","{}","ui_schema_json","{}"));
        FileMaterialQueryService files=org.mockito.Mockito.mock(FileMaterialQueryService.class);
        when(files.list("CONTRACT",77L,new FileActor(7L,"alice",3L))).thenReturn(List.of(
            new FileMaterialView(12L,41L,"SIGNED_CONTRACT","signed.pdf","BUSINESS")));
        when(mapper.selectExtensionContext(2L)).thenReturn(new java.util.HashMap<>(Map.ofEntries(
            Map.entry("policy_version_id",101L),Map.entry("max_extension_count",3),
            Map.entry("max_extension_value",2L),Map.entry("max_extension_unit","WORKING_DAYS"),
            Map.entry("proof_required",true),Map.entry("pending_sla_mode","CONTINUE"),
            Map.entry("due_at",java.time.LocalDateTime.of(2026,7,20,18,0)))));
        when(mapper.countApprovedExtensions(2L,101L)).thenReturn(1);

        var form=new TodoQueryService(mapper,access,files).form(2L,new Actor(7L,"alice",3L));

        assertEquals("CONTRACT",form.businessType());assertEquals(77L,form.businessId());
        assertEquals(12L,form.materials().get(0).relationId());
        assertEquals(41L,form.materials().get(0).fileObjectId());
        assertEquals(2,form.extensionPolicy().remainingRequestCount());
        assertEquals(true,form.extensionPolicy().proofRequired());
    }

    @ParameterizedTest
    @CsvSource({
        "CREATED,CLAIM,CLAIMED",
        "CLAIMED,START,IN_PROGRESS",
        "RETURNED,START,IN_PROGRESS",
        "IN_PROGRESS,SUBMIT,SUBMITTED",
        "SUBMITTED,COMPLETE,COMPLETED"
    })
    void formAdvertisesOnlyLegalNextAction(String currentStatus,String expectedAction,
            String targetStatus)
    {
        TodoInstance todo=new TodoInstance();todo.setTodoId(3L);todo.setStatus(currentStatus);
        todo.setTemplateVersionId(9L);
        when(mapper.selectById(3L)).thenReturn(todo);when(access.canView(todo,7L,3L)).thenReturn(true);
        when(mapper.selectTemplateVersionById(9L)).thenReturn(Map.of("dod_rule_json","{}","ui_schema_json","{}"));

        var form=new TodoQueryService(mapper,access).form(3L,new Actor(7L,"alice",3L));

        assertEquals(expectedAction,form.action());
        assertEquals(true,TodoStatusTransitions.canTransition(TodoStatus.fromCode(currentStatus),
                TodoStatus.fromCode(targetStatus)));
    }
}
