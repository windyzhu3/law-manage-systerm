package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.todo.application.command.TodoActionCommands.ActionCommand;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.domain.TodoAccessPolicy;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.spi.TodoCompletionHandler;

@ExtendWith(MockitoExtension.class)
class TodoCommandServiceTest
{
    @Mock TodoMapper mapper;
    @Mock TodoAccessPolicy access;
    @Mock TodoCompletionHandler completionHandler;
    @Mock TodoRoutingService routing;
    @Mock TodoDodService dod;
    TodoCommandService service;

    @BeforeEach void setUp(){service=new TodoCommandService(mapper,access);}

    @Test void claimsCreatedTodoAndWritesAudit()
    {
        TodoInstance todo=todo(1L,"CREATED",null);
        when(mapper.selectById(1L)).thenReturn(todo);
        when(access.canClaim(todo,7L,3L)).thenReturn(true);
        when(mapper.updateStatusConditionally(1L,"CREATED","CLAIMED",7L,"alice")).thenReturn(1);
        when(mapper.insertActionIfAbsent(anyMap())).thenReturn(1);
        service.claim(1L,new ActionCommand("a-1",null,Map.of()),new Actor(7L,"alice",3L));
        verify(mapper).insertActionIfAbsent(anyMap());
    }

    @Test void repeatedActionReturnsWithoutChangingState()
    {
        when(mapper.selectActionById("same")).thenReturn(Map.of("todo_id",1L));
        service.claim(1L,new ActionCommand("same",null,Map.of()),new Actor(7L,"alice",3L));
        verify(mapper,never()).updateStatusConditionally(org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString());
    }

    @Test void rejectsActionIdReusedForAnotherTodo()
    {
        when(mapper.selectActionById("same")).thenReturn(Map.of("todo_id",99L));
        TodoException error=assertThrows(TodoException.class,()->service.claim(1L,new ActionCommand("same",null,Map.of()),new Actor(7L,"alice",3L)));
        assertEquals("TODO_ACTION_ID_CONFLICT",error.getBusinessCode());
    }

    @Test void nonOwnerCannotStartTodo()
    {
        TodoInstance todo=todo(1L,"CLAIMED",8L);
        when(mapper.selectById(1L)).thenReturn(todo);
        TodoException error=assertThrows(TodoException.class,()->service.start(1L,new ActionCommand("a-2",null,Map.of()),new Actor(7L,"alice",3L)));
        assertEquals("TODO_ACCESS_DENIED",error.getBusinessCode());
    }

    @Test void completeRejectsMissingDodField()
    {
        TodoInstance todo=todo(2L,"SUBMITTED",7L);todo.setTemplateVersionId(9L);todo.setBusinessType("LEAD");
        when(mapper.selectById(2L)).thenReturn(todo);when(access.canOperate(todo,7L)).thenReturn(true);
        when(mapper.selectTemplateVersionById(9L)).thenReturn(Map.of("dod_rule_json","{\"requiredFields\":[\"contactResult\"]}"));
        TodoCommandService guarded=new TodoCommandService(mapper,access,new TodoDodService(List.of()),List.of(),null);
        TodoException error=assertThrows(TodoException.class,()->guarded.complete(2L,new ActionCommand("done-1",null,Map.of()),new Actor(7L,"alice",3L)));
        assertEquals("TODO_DOD_FIELD_MISSING",error.getBusinessCode());
        verify(mapper,never()).updateStatusConditionally(2L,"SUBMITTED","COMPLETED",null,"alice");
    }

    @Test void humanCommandsCannotClaimReservedAutoActionNamespace()
    {
        TodoException error=assertThrows(TodoException.class,()->service.claim(1L,new ActionCommand("AUTO:1:rule",null,Map.of()),new Actor(7L,"alice",3L)));
        assertEquals("TODO_ACTION_ID_RESERVED",error.getBusinessCode());verify(mapper,never()).selectActionById("AUTO:1:rule");
    }

    @Test void autoReplayRejectsActionTypeOrSourceCollision()
    {
        when(mapper.selectAutoActionExecutionForUpdate("AUTO:1:rule")).thenReturn(execution("AUTO:1:rule",1L,"COMPLETE_DEFAULT","CLAIMED"));
        when(mapper.selectActionById("AUTO:1:rule")).thenReturn(Map.of("todo_id",1L,"action_type","COMPLETE","action_source","HUMAN","operator_id",7L,"operator_name","alice"));
        TodoException error=assertThrows(TodoException.class,()->service.autoComplete(1L,new ActionCommand("AUTO:1:rule",null,Map.of()),TodoAutoActionService.SERVICE_ACTOR));
        assertEquals("TODO_AUTO_ACTION_REPLAY_CONFLICT",error.getBusinessCode());verify(mapper,never()).selectById(1L);
    }

    @Test void autoReplayAcceptsOnlyMatchingSystemAction()
    {
        TodoInstance done=todo(1L,"COMPLETED",7L);when(mapper.selectAutoActionExecutionForUpdate("AUTO:1:rule")).thenReturn(execution("AUTO:1:rule",1L,"COMPLETE_DEFAULT","CLAIMED"));when(mapper.selectActionById("AUTO:1:rule")).thenReturn(Map.of("todo_id",1L,"action_type","COMPLETE_DEFAULT","action_source","SYSTEM","operator_id",-1L,"operator_name","TODO_AUTO_ACTION"));when(mapper.selectById(1L)).thenReturn(done);
        assertEquals(done,service.autoComplete(1L,new ActionCommand("AUTO:1:rule",null,Map.of()),TodoAutoActionService.SERVICE_ACTOR));verify(mapper,never()).updateStatusConditionally(any(),any(),any(),any(),any());
        InOrder order=org.mockito.Mockito.inOrder(mapper);order.verify(mapper).selectAutoActionExecutionForUpdate("AUTO:1:rule");order.verify(mapper).selectActionById("AUTO:1:rule");
    }

    @Test void deadExecutionFenceRejectsAutoCommandBeforeTodoMutationOrReplay()
    {
        when(mapper.selectAutoActionExecutionForUpdate("AUTO:1:rule")).thenReturn(execution("AUTO:1:rule",1L,"COMPLETE_DEFAULT","DEAD"));
        TodoException error=assertThrows(TodoException.class,()->service.autoComplete(1L,new ActionCommand("AUTO:1:rule",null,Map.of()),TodoAutoActionService.SERVICE_ACTOR));
        assertEquals("TODO_AUTO_ACTION_FENCE_REJECTED",error.getBusinessCode());verify(mapper,never()).selectActionById(any());verify(mapper,never()).selectById(any());verify(mapper,never()).updateStatusConditionally(any(),any(),any(),any(),any());
    }

    @Test void strongActionCommandPreservesPresentNullAndLegacyPayloadAlias()
    {
        Map<String,Object> fields=new java.util.HashMap<>();fields.put("result",null);
        ActionCommand command=new ActionCommand("a",null,fields,List.of(11L));
        assertEquals(true,command.fields().containsKey("result"));
        assertEquals(command.fields(),command.payload());
        assertEquals(List.of(11L),command.fileObjectIds());
    }

    @Test void completionValidatesStrongFieldsFromVersionedDefinition()
    {
        TodoInstance todo=todo(6L,"SUBMITTED",7L);todo.setTemplateVersionId(10L);todo.setBusinessType("LEAD");
        when(mapper.selectById(6L)).thenReturn(todo);when(access.canOperate(todo,7L)).thenReturn(true);
        when(mapper.selectTemplateVersionById(10L)).thenReturn(Map.of("compiled_json",
                "{\"schemaVersion\":1,\"templateCode\":\"T\",\"dod\":{\"config\":{\"requiredFields\":[\"contactResult\"]}},\"ui\":{\"config\":{\"fields\":[\"contactResult\"]}},\"autoActions\":[],\"decisionRefs\":[],\"acceptanceRefs\":[]}"));
        TodoCommandService guarded=new TodoCommandService(mapper,access,new TodoDodService(List.of()),List.of(),null);

        TodoException error=assertThrows(TodoException.class,()->guarded.complete(6L,
                new ActionCommand("done-2",null,Map.of(),List.of()),new Actor(7L,"alice",3L)));

        assertEquals("TODO_DOD_FIELD_MISSING",error.getBusinessCode());
    }

    @Test void controlledCompletionStillRunsNonWaivableDodValidation()
    {
        TodoInstance todo=todo(16L,"SUBMITTED",7L);todo.setTemplateVersionId(10L);todo.setBusinessType("LEAD");
        when(mapper.selectAutoActionExecutionForUpdate("AUTO:16:r")).thenReturn(execution("AUTO:16:r",16L,"COMPLETE_DEFAULT","CLAIMED"));
        when(mapper.selectById(16L)).thenReturn(todo);
        when(mapper.selectTemplateVersionById(10L)).thenReturn(Map.of("compiled_json","{\"schemaVersion\":1,\"templateCode\":\"T\",\"dod\":{\"config\":{\"requiredFields\":[\"proof\"]}},\"ui\":{\"config\":{\"fields\":[\"proof\"]}},\"autoActions\":[],\"decisionRefs\":[],\"acceptanceRefs\":[]}"));
        TodoCommandService guarded=new TodoCommandService(mapper,access,new TodoDodService(List.of()),List.of(),null);

        TodoException error=assertThrows(TodoException.class,()->guarded.autoComplete(16L,new ActionCommand("AUTO:16:r",null,Map.of()),TodoAutoActionService.SERVICE_ACTOR));

        assertEquals("TODO_DOD_FIELD_MISSING",error.getBusinessCode());
        verify(mapper,never()).updateStatusConditionally(org.mockito.ArgumentMatchers.eq(16L),any(),any(),any(),any());
    }

    @Test void controlledEntryPointRejectsSpoofedHumanActor()
    {
        TodoException error=assertThrows(TodoException.class,()->service.autoComplete(1L,new ActionCommand("AUTO:1:r",null,Map.of()),new Actor(7L,"alice",3L)));
        assertEquals("TODO_AUTO_ACTION_ACTOR_REQUIRED",error.getBusinessCode());
        verify(mapper,never()).selectById(1L);
    }

    @Test void nonReviewerCannotReturnTodo(){TodoInstance todo=todo(3L,"SUBMITTED",8L);when(mapper.selectById(3L)).thenReturn(todo);when(access.canReview(todo,7L)).thenReturn(false);TodoException e=assertThrows(TodoException.class,()->service.returnTodo(3L,new ActionCommand("back-1",null,Map.of()),new Actor(7L,"alice",3L)));assertEquals("TODO_ACCESS_DENIED",e.getBusinessCode());}
    @Test void returnValidatesActionSpecificRule()
    {
        TodoInstance todo=todo(7L,"SUBMITTED",8L);todo.setTemplateVersionId(11L);
        when(mapper.selectById(7L)).thenReturn(todo);when(access.canReview(todo,7L)).thenReturn(true);
        when(mapper.selectTemplateVersionById(11L)).thenReturn(Map.of("compiled_json",
                "{\"schemaVersion\":1,\"templateCode\":\"T\",\"dod\":{\"config\":{\"actions\":{\"RETURN\":{\"requiredFields\":[\"reason\"]}}}},\"ui\":{\"config\":{\"fields\":[\"reason\"]}},\"autoActions\":[],\"decisionRefs\":[],\"acceptanceRefs\":[]}"));

        TodoException error=assertThrows(TodoException.class,()->service.returnTodo(7L,
                new ActionCommand("back-2",null,Map.of(),List.of()),new Actor(7L,"alice",3L)));

        assertEquals("TODO_DOD_FIELD_MISSING",error.getBusinessCode());
    }
    @Test void terminalTodoCannotTransfer(){TodoInstance todo=todo(4L,"COMPLETED",7L);when(mapper.selectById(4L)).thenReturn(todo);when(access.canOperate(todo,7L)).thenReturn(true);TodoException e=assertThrows(TodoException.class,()->service.transfer(4L,new ActionCommand("move-1",null,Map.of("targetOwnerId",9L)),new Actor(7L,"alice",3L)));assertEquals("TODO_TERMINAL",e.getBusinessCode());}

    @Test void writesValidJsonPayloadToAuditLog()
    {
        TodoInstance todo=todo(5L,"IN_PROGRESS",7L);when(mapper.selectById(5L)).thenReturn(todo);when(access.canOperate(todo,7L)).thenReturn(true);when(mapper.updateStatusConditionally(5L,"IN_PROGRESS","IN_PROGRESS",9L,"alice")).thenReturn(1);when(mapper.insertActionIfAbsent(anyMap())).thenReturn(1);
        service.transfer(5L,new ActionCommand("move-2",null,Map.of("targetOwnerId",9L)),new Actor(7L,"alice",3L));
        ArgumentCaptor<Map<String,Object>> log=ArgumentCaptor.forClass(Map.class);verify(mapper).insertActionIfAbsent(log.capture());assertEquals("{\"targetOwnerId\":9}",log.getValue().get("payloadJson"));
    }

    @Test void completionHandlerAndGraphRoutingShareCompletionFlowInThatOrder()
    {
        TodoInstance todo=todo(8L,"SUBMITTED",7L);todo.setTemplateVersionId(12L);
        when(mapper.selectById(8L)).thenReturn(todo);when(access.canOperate(todo,7L)).thenReturn(true);
        when(mapper.selectTemplateVersionById(12L)).thenReturn(Map.of());
        when(mapper.updateStatusConditionally(8L,"SUBMITTED","COMPLETED",null,"alice")).thenReturn(1);
        when(mapper.insertActionIfAbsent(anyMap())).thenReturn(1);when(completionHandler.supports(todo)).thenReturn(true);
        TodoCommandService guarded=new TodoCommandService(mapper,access,new TodoDodService(List.of()),List.of(completionHandler),routing);

        guarded.complete(8L,new ActionCommand("done-8",null,Map.of("approved",true)),new Actor(7L,"alice",3L));

        InOrder order=org.mockito.Mockito.inOrder(completionHandler,routing);
        order.verify(completionHandler).complete(todo,Map.of("approved",true),7L,"alice");
        order.verify(routing).advance(todo,Map.of("approved",true));
    }

    @Test void completion_passes_the_authenticated_actor_to_dod_material_validation()
    {
        TodoInstance todo=todo(18L,"SUBMITTED",7L);todo.setTemplateVersionId(18L);todo.setBusinessType("CASE");todo.setBusinessId(9L);
        when(mapper.selectById(18L)).thenReturn(todo);when(access.canOperate(todo,7L)).thenReturn(true);
        when(mapper.selectTemplateVersionById(18L)).thenReturn(Map.of("compiled_json",
            "{\"schemaVersion\":1,\"templateCode\":\"T\",\"dod\":{\"config\":{}},\"ui\":{\"config\":{}},\"autoActions\":[],\"decisionRefs\":[],\"acceptanceRefs\":[]}"));
        when(mapper.updateStatusConditionally(18L,"SUBMITTED","COMPLETED",null,"alice")).thenReturn(1);
        when(mapper.insertActionIfAbsent(anyMap())).thenReturn(1);
        Actor actor=new Actor(7L,"alice",3L);
        TodoCommandService guarded=new TodoCommandService(mapper,access,dod,List.of(),null);

        guarded.complete(18L,new ActionCommand("done-18",null,Map.of(),List.of(11L)),actor);

        verify(dod).validate(eq(todo),any(),eq("COMPLETE"),eq(Map.of()),eq(List.of(11L)),eq(actor));
    }

    private TodoInstance todo(Long id,String status,Long owner){TodoInstance t=new TodoInstance();t.setTodoId(id);t.setStatus(status);t.setOwnerId(owner);return t;}
    private Map<String,Object> execution(String key,Long todoId,String type,String status){return Map.of("execution_key",key,"todo_id",todoId,"action_type",type,"status",status);}
}
