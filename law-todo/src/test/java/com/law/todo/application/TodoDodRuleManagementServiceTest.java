package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.law.todo.application.TodoDodRuleManagementService.DodTestResult;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoConfigurationCommands.DodRuleCommand;
import com.law.todo.application.view.TodoConfigurationViews.DodRuleDetail;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoConfigurationMapper;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.spi.TodoBusinessValidator;
import com.law.todo.spi.TodoDictionaryValidationPort;

@ExtendWith(MockitoExtension.class)
class TodoDodRuleManagementServiceTest
{
    @Mock TodoConfigurationMapper mapper;
    @Mock TodoMapper todoMapper;
    @Mock TodoDictionaryValidationPort dictionaries;
    @Mock TodoBusinessValidator validator;
    private TodoDodRuleManagementService service;
    private final Actor actor=new Actor(7L,"alice",2L);

    @BeforeEach void setUp()
    {
        service=new TodoDodRuleManagementService(mapper,todoMapper,dictionaries,List.of(validator));
    }

    @Test void rejectsUnknownExternalValidator()
    {
        ledger(true);enabledDictionaries();when(validator.catalogCode()).thenReturn("BUSINESS_STATE");

        TodoException error=assertThrows(TodoException.class,()->service.save(command("[\"MISSING\"]"),actor));

        assertEquals("TODO_DOD_VALIDATOR_NOT_FOUND",error.getBusinessCode());
    }

    @Test void rejectsUnknownOrDisabledDictionaryValues()
    {
        ledger(true);when(dictionaries.isEnabled("law_todo_dod_rule_type","TASK")).thenReturn(true);
        when(dictionaries.isEnabled("law_todo_rule_status","0")).thenReturn(false);

        TodoException error=assertThrows(TodoException.class,()->service.save(command("[]"),actor));

        assertEquals("TODO_DOD_RULE_DICTIONARY_INVALID",error.getBusinessCode());
    }

    @Test void sampleTestReturnsEveryFailedRequirement()
    {
        when(mapper.selectDodRule(7L)).thenReturn(rule(7L,"DOD-7","0",2,"[\"result\"]","[\"CALL_RECORD\"]","[]"));

        DodTestResult result=service.test(7L,Map.of(),List.of(),actor);

        assertEquals(List.of("result"),result.missingFields());
        assertEquals(List.of("CALL_RECORD"),result.missingAttachments());
        assertEquals(List.of(),result.validatorIssues());
        assertFalse(result.passed());
    }

    @Test void sampleTestTreatsBlankValuesAsMissing()
    {
        when(mapper.selectDodRule(7L)).thenReturn(rule(7L,"DOD-7","0",2,"[\"result\"]","[]","[]"));

        DodTestResult result=service.test(7L,Map.of("result"," "),List.of(),actor);

        assertEquals(List.of("result"),result.missingFields());
    }

    @Test void staleUpdateReturnsStableConflictCode()
    {
        ledger(true);enabledDictionaries();when(mapper.updateDodRuleConditionally(anyMap())).thenReturn(0);

        TodoException error=assertThrows(TodoException.class,()->service.save(existingCommand(4),actor));

        assertEquals("TODO_DOD_RULE_VERSION_CONFLICT",error.getBusinessCode());
    }

    @Test void createReplaysRecordedActionWithoutDuplicateInsert()
    {
        Ledger ledger=ledger(true);enabledDictionaries();when(mapper.insertDodRule(anyMap())).thenAnswer(invocation->{
            invocation.<Map<String,Object>>getArgument(0).put("dodRuleId",10L);return 1;
        });

        assertEquals(10L,service.save(command("[]"),actor));
        assertEquals(10L,service.save(command("[]"),actor));

        verify(mapper,times(1)).insertDodRule(anyMap());
        verify(todoMapper,times(1)).completeDefinitionAction(anyString(),anyString(),anyLong());
        assertEquals(10L,ledger.entityId.get());
    }

    @Test void createReplayReturnsStoredIdAfterDictionaryAndValidatorCatalogueChange()
    {
        ledger(true);enabledDictionaries();when(validator.catalogCode()).thenReturn("BUSINESS_STATE");
        DodRuleCommand command=command("[\"BUSINESS_STATE\"]");
        when(mapper.insertDodRule(anyMap())).thenAnswer(invocation->{invocation.<Map<String,Object>>getArgument(0).put("dodRuleId",10L);return 1;});
        assertEquals(10L,service.save(command,actor));
        lenient().when(dictionaries.isEnabled("law_todo_dod_rule_type","TASK")).thenReturn(false);
        lenient().when(validator.catalogCode()).thenReturn("OTHER");

        assertEquals(10L,service.save(command,actor));

        verify(mapper,times(1)).insertDodRule(anyMap());
    }

    @Test void sameActionIdWithDifferentSaveRequestReturnsActionConflict()
    {
        ledger(true);enabledDictionaries();when(mapper.insertDodRule(anyMap())).thenAnswer(invocation->{invocation.<Map<String,Object>>getArgument(0).put("dodRuleId",10L);return 1;});
        assertEquals(10L,service.save(command("[]"),actor));
        DodRuleCommand different=new DodRuleCommand(null,"DOD-OTHER","Done","TASK","[\"other\"]","[]","[]","[]","{}","0","save-new",0);

        TodoException error=assertThrows(TodoException.class,()->service.save(different,actor));

        assertEquals("TODO_DOD_RULE_ACTION_CONFLICT",error.getBusinessCode());
        verify(mapper,times(1)).insertDodRule(anyMap());
    }

    @Test void copiesWithCallerSuppliedUniqueCodeAndActorAudit()
    {
        ledger(true);enabledDictionaries();when(mapper.selectDodRule(9L)).thenReturn(rule(9L,"DOD-9","0",2,"[\"result\"]","[\"CALL_RECORD\"]","[]"));
        when(mapper.insertDodRule(anyMap())).thenAnswer(invocation->{invocation.<Map<String,Object>>getArgument(0).put("dodRuleId",10L);return 1;});

        assertEquals(10L,service.copy(9L,"DOD-9-COPY","copy-9",actor));

        verify(mapper).insertDodRule(argThat(row->"DOD-9-COPY".equals(row.get("ruleCode"))
                &&"alice".equals(row.get("createBy"))&&"copy-9".equals(row.get("actionId"))));
    }

    @Test void disabledReferencedRuleKeepsPublishedSnapshotsUntouched()
    {
        ledger(true);when(mapper.updateDodRuleConditionally(anyMap())).thenReturn(1);

        service.toggle(9L,"1","toggle-9",2,actor);

        verify(mapper).updateDodRuleConditionally(argThat(row->"1".equals(row.get("status"))
                &&Long.valueOf(9L).equals(row.get("dodRuleId"))&&Integer.valueOf(2).equals(row.get("expectedVersion"))));
        verify(mapper,never()).countDodRuleReferences(9L);
    }

    @Test void copyReplaysRecordedActionWithoutReadingOrDuplicatingTheSource()
    {
        ledger(true);enabledDictionaries();when(mapper.selectDodRule(9L)).thenReturn(rule(9L,"DOD-9","0",2,"[]","[]","[]"));
        when(mapper.insertDodRule(anyMap())).thenAnswer(invocation->{invocation.<Map<String,Object>>getArgument(0).put("dodRuleId",10L);return 1;});

        assertEquals(10L,service.copy(9L,"DOD-9-COPY","copy-9",actor));
        assertEquals(10L,service.copy(9L,"DOD-9-COPY","copy-9",actor));

        verify(mapper,times(1)).selectDodRule(9L);
        verify(mapper,times(1)).insertDodRule(anyMap());
    }

    @Test void toggleReplaysRecordedActionWithoutDuplicateWrite()
    {
        Ledger ledger=ledger(true);when(mapper.updateDodRuleConditionally(anyMap())).thenReturn(1);

        service.toggle(9L,"1","toggle-9",2,actor);
        service.toggle(9L,"1","toggle-9",2,actor);

        verify(mapper,times(1)).updateDodRuleConditionally(anyMap());
        verify(todoMapper,times(1)).completeDefinitionAction(anyString(),anyString(),anyLong());
        assertEquals(9L,ledger.entityId.get());
    }

    @Test void listsAndReadsTypedRuleViews()
    {
        when(mapper.selectDodRules(Map.of("status","0"))).thenReturn(List.of(rule(9L,"DOD-9","0",2,"[]","[]","[]")));
        when(mapper.selectDodRule(9L)).thenReturn(rule(9L,"DOD-9","0",2,"[]","[]","[]"));

        assertEquals("DOD-9",service.list(Map.of("status","0")).get(0).ruleCode());
        DodRuleDetail detail=service.detail(9L);
        assertEquals("TASK",detail.ruleType());
        assertEquals(2,detail.version());
    }

    private void enabledDictionaries()
    {
        when(dictionaries.isEnabled("law_todo_dod_rule_type","TASK")).thenReturn(true);
        when(dictionaries.isEnabled("law_todo_rule_status","0")).thenReturn(true);
    }
    private DodRuleCommand command(String validators)
    {return new DodRuleCommand(null,"DOD-NEW","Done","TASK","[\"result\"]","[\"CALL_RECORD\"]","[]",validators,"{}","0","save-new",0);}
    private DodRuleCommand existingCommand(int version)
    {return new DodRuleCommand(9L,"DOD-9","Done","TASK","[\"result\"]","[\"CALL_RECORD\"]","[]","[]","{}","0","save-9",version);}
    private Map<String,Object> rule(long id,String code,String status,int version,String fields,String attachments,String validators)
    {return Map.ofEntries(Map.entry("dod_rule_id",id),Map.entry("rule_code",code),Map.entry("rule_name","Done"),Map.entry("rule_type","TASK"),
            Map.entry("required_fields_json",fields),Map.entry("required_attachments_json",attachments),Map.entry("conditional_rules_json","[]"),
            Map.entry("validator_refs_json",validators),Map.entry("error_messages_json","{}"),Map.entry("status",status),Map.entry("version",version),
            Map.entry("reference_count",4L),Map.entry("create_by","alice"));}

    private Ledger ledger(boolean insertFirst)
    {
        Ledger ledger=new Ledger();AtomicBoolean first=new AtomicBoolean(insertFirst);
        lenient().when(todoMapper.insertDefinitionActionClaim(anyMap())).thenAnswer(invocation->{
            Map<String,Object> action=new HashMap<>(invocation.getArgument(0));if(ledger.claim.get()==null)ledger.claim.set(action);
            return first.compareAndSet(true,false)?1:0;
        });
        lenient().when(todoMapper.selectDefinitionActionForUpdate(anyString())).thenAnswer(invocation->locked(ledger));
        lenient().when(todoMapper.completeDefinitionAction(anyString(),anyString(),anyLong())).thenAnswer(invocation->{
            ledger.applied.set(true);ledger.entityId.set(invocation.getArgument(2));return 1;
        });
        return ledger;
    }
    private Map<String,Object> locked(Ledger ledger)
    {
        Map<String,Object> action=ledger.claim.get();if(action==null)return null;Map<String,Object> row=new HashMap<>();
        row.put("action_type",action.get("actionType"));row.put("entity_type",action.get("entityType"));row.put("source_entity_id",action.get("sourceEntityId"));
        row.put("operator_id",action.get("operatorId"));row.put("operator_name",action.get("operatorName"));row.put("operator_dept_id",action.get("operatorDeptId"));
        row.put("request_fingerprint",action.get("requestFingerprint"));row.put("action_status",ledger.applied.get()?"APPLIED":"CLAIMED");
        if(ledger.entityId.get()!=null)row.put("entity_id",ledger.entityId.get());return row;
    }
    private static final class Ledger
    {private final AtomicReference<Map<String,Object>> claim=new AtomicReference<>();private final AtomicBoolean applied=new AtomicBoolean();
        private final AtomicReference<Long> entityId=new AtomicReference<>();}
}
