package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;

import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoManagementCommands.TriggerSortCommand;
import com.law.todo.application.command.TodoManagementCommands.TriggerSortItem;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoMapper;

class TodoTriggerSortTransactionTest
{
    @Test void transactionProxyRollsBackBothSortsWhenTheSecondOptimisticUpdateConflicts()
    {
        TodoMapper mapper=Mockito.mock(TodoMapper.class);RecordingTransactionManager transactions=new RecordingTransactionManager();
        AtomicReference<Map<String,Object>> claim=new AtomicReference<>();
        when(mapper.insertDefinitionActionClaim(any())).thenAnswer(invocation->{claim.set(new HashMap<>(invocation.getArgument(0)));return 1;});
        when(mapper.selectDefinitionActionForUpdate(anyString())).thenAnswer(invocation->{Map<String,Object> row=new HashMap<>(claim.get());row.put("action_status","CLAIMED");return snake(row);});
        when(mapper.updateTriggerRuleSortConditionally(any())).thenAnswer(invocation->{
            int call=transactions.sortCalls++;transactions.stage(()->transactions.persistedSorts++);return call==0?1:0;
        });
        TodoTemplateService service=transactional(new TodoTemplateService(mapper),transactions);

        TodoException error=assertThrows(TodoException.class,()->service.sortTriggers(new TriggerSortCommand("sort-rollback",
                List.of(new TriggerSortItem(8L,0,1),new TriggerSortItem(9L,1,1))),new Actor(7L,"alice",2L)));

        assertEquals("TODO_TRIGGER_VERSION_CONFLICT",error.getBusinessCode());assertEquals(1,transactions.rollbacks);assertEquals(0,transactions.commits);assertEquals(0,transactions.persistedSorts);
        InOrder order=Mockito.inOrder(mapper);order.verify(mapper).insertDefinitionActionClaim(any());order.verify(mapper).selectDefinitionActionForUpdate("sort-rollback");
        verify(mapper,never()).completeDefinitionAction(anyString(),anyString(),any());
    }

    private TodoTemplateService transactional(TodoTemplateService target,RecordingTransactionManager transactions)
    {ProxyFactory proxy=new ProxyFactory(target);proxy.addAdvice(new TransactionInterceptor(transactions,new AnnotationTransactionAttributeSource()));return (TodoTemplateService)proxy.getProxy();}
    private Map<String,Object> snake(Map<String,Object> claim)
    {Map<String,Object> row=new HashMap<>();row.put("action_type",claim.get("actionType"));row.put("entity_type",claim.get("entityType"));row.put("request_fingerprint",claim.get("requestFingerprint"));row.put("source_entity_id",claim.get("sourceEntityId"));row.put("operator_id",claim.get("operatorId"));row.put("operator_name",claim.get("operatorName"));row.put("operator_dept_id",claim.get("operatorDeptId"));row.put("action_status","CLAIMED");return row;}

    static final class RecordingTransactionManager extends AbstractPlatformTransactionManager
    {
        private final ThreadLocal<List<Runnable>> writes=new ThreadLocal<>();int sortCalls;int persistedSorts;int commits;int rollbacks;
        void stage(Runnable write){writes.get().add(write);}
        @Override protected Object doGetTransaction(){return new Object();}
        @Override protected void doBegin(Object transaction,TransactionDefinition definition){writes.set(new ArrayList<>());}
        @Override protected void doCommit(org.springframework.transaction.support.DefaultTransactionStatus status){writes.get().forEach(Runnable::run);commits++;writes.remove();}
        @Override protected void doRollback(org.springframework.transaction.support.DefaultTransactionStatus status){rollbacks++;writes.remove();}
    }
}
