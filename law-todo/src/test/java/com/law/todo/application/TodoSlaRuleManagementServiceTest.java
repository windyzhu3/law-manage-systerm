package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoConfigurationCommands.SlaRuleCommand;
import com.law.todo.application.view.TodoConfigurationViews.SlaCalculationResult;
import com.law.todo.application.view.TodoConfigurationViews.SlaRuleDetail;
import com.law.todo.application.view.TodoConfigurationViews.SlaRuleListItem;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoConfigurationMapper;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.spi.TodoDictionaryValidationPort;

@ExtendWith(MockitoExtension.class)
class TodoSlaRuleManagementServiceTest
{
    @Mock TodoConfigurationMapper mapper;
    @Mock TodoMapper todoMapper;
    @Mock TodoDictionaryValidationPort dictionaries;
    private TodoSlaRuleManagementService service;
    private final Actor actor=new Actor(7L,"alice",2L);

    @BeforeEach void setUp()
    {
        service=new TodoSlaRuleManagementService(mapper,todoMapper,dictionaries);
    }

    @Test void rejectsUnknownCalendar()
    {
        enabledDictionaries();
        when(todoMapper.selectCalendarByCode("UNKNOWN")).thenReturn(null);

        TodoException error=assertThrows(TodoException.class,()->service.save(command("UNKNOWN"),actor));

        assertEquals("TODO_SLA_RULE_CALENDAR_NOT_FOUND",error.getBusinessCode());
    }

    @Test void rejectsUnknownOrDisabledDictionaryValues()
    {
        when(dictionaries.isEnabled("law_todo_sla_type","RESPONSE")).thenReturn(true);
        when(dictionaries.isEnabled("law_todo_sla_unit","MINUTE")).thenReturn(false);

        TodoException error=assertThrows(TodoException.class,()->service.save(command("DEFAULT"),actor));

        assertEquals("TODO_SLA_RULE_DICTIONARY_INVALID",error.getBusinessCode());
        verify(todoMapper,never()).selectCalendarByCode(anyString());
    }

    @Test void staleUpdateReturnsStableConflictCode()
    {
        enabledDictionaries();
        when(todoMapper.selectCalendarByCode("DEFAULT")).thenReturn(calendar());
        when(mapper.updateSlaRuleConditionally(anyMap())).thenReturn(0);

        TodoException error=assertThrows(TodoException.class,()->service.save(existingCommand(4),actor));

        assertEquals("TODO_SLA_RULE_VERSION_CONFLICT",error.getBusinessCode());
    }

    @Test void disabledReferencedRuleKeepsPublishedSnapshotsUntouched()
    {
        when(mapper.countSlaRuleReferences(9L)).thenReturn(3);
        when(mapper.updateSlaRuleConditionally(anyMap())).thenReturn(1);

        service.toggle(9L,"1","toggle-9",2,actor);

        verify(mapper).updateSlaRuleConditionally(argThat(row->"1".equals(row.get("status"))
                &&Long.valueOf(9L).equals(row.get("slaRuleId"))&&Integer.valueOf(2).equals(row.get("expectedVersion"))));
        verifyNoInteractions(todoMapper);
    }

    @Test void listsAndReadsTypedRuleViews()
    {
        when(mapper.selectSlaRules(Map.of("status","0"))).thenReturn(List.of(rule(9L,"SLA-9","0",2)));
        when(mapper.selectSlaRule(9L)).thenReturn(rule(9L,"SLA-9","0",2));

        List<SlaRuleListItem> listed=service.list(Map.of("status","0"));
        SlaRuleDetail detail=service.detail(9L);

        assertEquals("SLA-9",listed.get(0).ruleCode());
        assertEquals(4L,listed.get(0).referenceCount());
        assertEquals("DEFAULT",detail.calendarCode());
        assertEquals(2,detail.version());
    }

    @Test void copiesWithCallerSuppliedUniqueCodeAndActorAudit()
    {
        enabledDictionaries();
        when(mapper.selectSlaRule(9L)).thenReturn(rule(9L,"SLA-9","0",2));
        when(todoMapper.selectCalendarByCode("DEFAULT")).thenReturn(calendar());
        when(mapper.insertSlaRule(anyMap())).thenAnswer(invocation->{
            invocation.<Map<String,Object>>getArgument(0).put("slaRuleId",10L);return 1;
        });

        assertEquals(10L,service.copy(9L,"SLA-9-COPY","copy-9",actor));

        verify(mapper).insertSlaRule(argThat(row->"SLA-9-COPY".equals(row.get("ruleCode"))
                &&"alice".equals(row.get("createBy"))&&"copy-9".equals(row.get("actionId"))));
    }

    @Test void calculatesWorkingCalendarThresholdsWithoutMutatingRule()
    {
        when(mapper.selectSlaRule(9L)).thenReturn(rule(9L,"SLA-9","0",2));
        when(todoMapper.selectCalendarByCode("DEFAULT")).thenReturn(calendar());
        LocalDateTime createdAt=LocalDateTime.of(2026,7,10,17,0);

        SlaCalculationResult result=service.testCalculation(9L,createdAt);

        assertEquals(createdAt,result.createdAt());
        assertEquals(LocalDateTime.of(2026,7,13,9,36),result.remind80At());
        assertEquals(LocalDateTime.of(2026,7,13,10,0),result.overdue100At());
        assertEquals(LocalDateTime.of(2026,7,13,11,0),result.escalate150At());
        verify(mapper,never()).updateSlaRuleConditionally(anyMap());
    }

    private void enabledDictionaries()
    {
        when(dictionaries.isEnabled("law_todo_sla_type","RESPONSE")).thenReturn(true);
        when(dictionaries.isEnabled("law_todo_sla_unit","MINUTE")).thenReturn(true);
        when(dictionaries.isEnabled("law_todo_sla_start_strategy","TODO_CREATED")).thenReturn(true);
    }

    private SlaRuleCommand command(String calendarCode)
    {return new SlaRuleCommand(null,"SLA-NEW","Response", "RESPONSE",120,"MINUTE",calendarCode,"TODO_CREATED",
            80,100,150,"{}","{}","{}","0","save-new",0);}
    private SlaRuleCommand existingCommand(int version)
    {return new SlaRuleCommand(9L,"SLA-9","Response", "RESPONSE",120,"MINUTE","DEFAULT","TODO_CREATED",
            80,100,150,"{}","{}","{}","0","save-9",version);}
    private Map<String,Object> calendar()
    {return Map.of("calendar_id",1L,"work_days","1,2,3,4,5","work_start","09:00:00","work_end","18:00:00","exception_json","{}");}
    private Map<String,Object> rule(long id,String code,String status,int version)
    {return Map.ofEntries(Map.entry("sla_rule_id",id),Map.entry("rule_code",code),Map.entry("rule_name","Response"),
            Map.entry("sla_type","RESPONSE"),Map.entry("duration_value",120),Map.entry("duration_unit","MINUTE"),
            Map.entry("calendar_code","DEFAULT"),Map.entry("start_strategy","TODO_CREATED"),Map.entry("soft_remind_percent",80),
            Map.entry("hard_remind_percent",100),Map.entry("escalate_percent",150),Map.entry("pause_policy_json","{}"),
            Map.entry("escalation_policy_json","{}"),Map.entry("auto_action_json","{}"),Map.entry("status",status),
            Map.entry("version",version),Map.entry("reference_count",4L),Map.entry("create_by","alice"));}
}
