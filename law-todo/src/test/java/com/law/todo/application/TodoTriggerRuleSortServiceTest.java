package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoManagementCommands.TriggerSortCommand;
import com.law.todo.application.command.TodoManagementCommands.TriggerSortItem;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoMapper;

class TodoTriggerRuleSortServiceTest
{
    @Test
    void sortsEveryRuleWithItsExpectedVersionUnderOneAction() {
        TodoMapper mapper=mapperForClaim();when(mapper.updateTriggerRuleSortConditionally(any())).thenReturn(1);
        TodoTemplateService service=new TodoTemplateService(mapper);
        service.sortTriggers(new TriggerSortCommand("sort-triggers",java.util.List.of(new TriggerSortItem(8L,0,4),new TriggerSortItem(3L,1,9))),actor());
        verify(mapper,times(2)).updateTriggerRuleSortConditionally(any());verify(mapper).completeDefinitionAction(Mockito.eq("sort-triggers"),any(),Mockito.eq(8L));
    }

    @Test
    void rejectsDuplicateRuleOrSortOrderBeforeAnySortMutation() {
        TodoMapper mapper=mapperForClaim();TodoTemplateService service=new TodoTemplateService(mapper);
        assertThrows(TodoException.class,()->service.sortTriggers(new TriggerSortCommand("duplicate-sort",java.util.List.of(new TriggerSortItem(8L,0,4),new TriggerSortItem(8L,0,9))),actor()));
        verify(mapper,Mockito.never()).updateTriggerRuleSortConditionally(any());
    }

    private TodoMapper mapperForClaim()
    {
        TodoMapper mapper=Mockito.mock(TodoMapper.class);AtomicReference<Map<String,Object>> claimed=new AtomicReference<>();
        when(mapper.insertDefinitionActionClaim(any())).thenAnswer(invocation -> {claimed.set(new HashMap<>(invocation.getArgument(0)));return 1;});
        when(mapper.selectDefinitionActionForUpdate(any())).thenAnswer(invocation -> {
            Map<String,Object> action=claimed.get();Map<String,Object> locked=new HashMap<>();
            locked.put("action_type",action.get("actionType"));locked.put("entity_type",action.get("entityType"));
            locked.put("request_fingerprint",action.get("requestFingerprint"));locked.put("source_entity_id",action.get("sourceEntityId"));
            locked.put("operator_id",action.get("operatorId"));locked.put("operator_name",action.get("operatorName"));locked.put("operator_dept_id",action.get("operatorDeptId"));locked.put("action_status","CLAIMED");return locked;
        });when(mapper.completeDefinitionAction(any(),any(),any())).thenReturn(1);return mapper;
    }

    private Actor actor(){return new Actor(7L,"configuration-admin",9L);}
}
