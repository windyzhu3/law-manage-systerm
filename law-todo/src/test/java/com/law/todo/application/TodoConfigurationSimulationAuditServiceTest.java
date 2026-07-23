package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import com.alibaba.fastjson2.JSON;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoConfigurationCommands.ConfigurationSimulationCommand;
import com.law.todo.mapper.TodoConfigurationMapper;
import com.law.todo.spi.TodoBusinessPayloadAccess.PayloadFieldSource;

@ExtendWith(MockitoExtension.class)
class TodoConfigurationSimulationAuditServiceTest
{
    @Mock private TodoConfigurationMapper mapper;

    @Test void inputSummaryContainsOnlyShapeAndNeverBusinessValues()
    {
        TodoConfigurationSimulationAuditService audit=new TodoConfigurationSimulationAuditService(mapper);
        audit.record(command(),actor(),4L,Map.of("state",Map.of("status","MATCHED")));

        ArgumentCaptor<Map<String,Object>> rows=ArgumentCaptor.forClass(Map.class);
        verify(mapper).insertSimulationRecord(rows.capture());
        String input=String.valueOf(rows.getValue().get("inputSummaryJson"));
        for(String value:List.of("private case facts","confidential description","Alice Example","1 Privacy Lane",
                "https://download.example.test/export.csv","unprefixed-secret-token")) assertFalse(input.contains(value));
        assertTrue(input.contains("caseFacts"));
        assertTrue(input.contains("description"));
        assertTrue(input.contains("STRING"));
        assertTrue(input.contains("size"));
    }

    @Test void resultRecordStillRecursivelyRedactsSensitiveContent()
    {
        TodoConfigurationSimulationAuditService audit=new TodoConfigurationSimulationAuditService(mapper);
        audit.record(command(),actor(),4L,Map.of("card",Map.of("customerEmail","alice@example.com",
                "nested",List.of(Map.of("fileUrl","file:///private/evidence.pdf","token","top-secret")))));

        ArgumentCaptor<Map<String,Object>> rows=ArgumentCaptor.forClass(Map.class);
        verify(mapper).insertSimulationRecord(rows.capture());
        String result=String.valueOf(rows.getValue().get("resultJson"));
        assertFalse(result.contains("alice@example.com"));
        assertFalse(result.contains("file:///private/evidence.pdf"));
        assertFalse(result.contains("top-secret"));
    }

    @Test void governedNeutralSensitiveNodesNeverReachPersistedResultJson()
    {
        Map<String,Object> raw=Map.of(
                "opaqueAlpha","neutral-secret",
                "opaqueBeta",94736251L,
                "opaqueGamma",true,
                "opaqueDelta",List.of("list-secret",73),
                "opaqueEpsilon",Map.of("inner","deep-secret","amount",91));
        List<PayloadFieldSource> fields=raw.keySet().stream()
                .map(path->new PayloadFieldSource(path,raw.get(path),"BUSINESS_OBJECT",true,false,null,true)).toList();
        TodoSensitiveDataPolicy policy=TodoSensitiveDataPolicy.from(raw,fields);
        TodoConfigurationSimulationAuditService audit=new TodoConfigurationSimulationAuditService(mapper);

        audit.record(command(),actor(),4L,Map.of("trace",Map.of(
                "aliasText","neutral-secret","aliasNumber",94736251L,"aliasBoolean",true,
                "aliasList",List.of("list-secret",73),
                "aliasObject",Map.of("inner","deep-secret","amount",91))),policy);

        ArgumentCaptor<Map<String,Object>> rows=ArgumentCaptor.forClass(Map.class);
        verify(mapper).insertSimulationRecord(rows.capture());
        Map<String,Object> result=JSON.parseObject(String.valueOf(rows.getValue().get("resultJson")));
        Map<?,?> trace=(Map<?,?>)result.get("trace");
        for(String key:List.of("aliasText","aliasNumber","aliasBoolean","aliasList","aliasObject"))
            assertTrue("[REDACTED]".equals(trace.get(key)));
        String persisted=String.valueOf(rows.getValue().get("resultJson"));
        for(String value:List.of("neutral-secret","94736251","list-secret","deep-secret"))
            assertFalse(persisted.contains(value));
    }

    @Test void recordUsesRequiresNewTransactionThroughASpringProxy()
    {
        PlatformTransactionManager transactions=org.mockito.Mockito.mock(PlatformTransactionManager.class);
        TransactionStatus status=org.mockito.Mockito.mock(TransactionStatus.class);
        org.mockito.Mockito.when(transactions.getTransaction(any())).thenReturn(status);
        TodoConfigurationSimulationAuditService target=new TodoConfigurationSimulationAuditService(mapper);
        ProxyFactory proxyFactory=new ProxyFactory(target);proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new TransactionInterceptor(transactions,new AnnotationTransactionAttributeSource()));
        TodoConfigurationSimulationAuditService proxy=(TodoConfigurationSimulationAuditService)proxyFactory.getProxy();

        proxy.record(command(),actor(),4L,Map.of("state",Map.of("status","MATCHED")));

        verify(transactions).getTransaction(org.mockito.ArgumentMatchers.argThat(definition ->
                definition.getPropagationBehavior()==TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        verify(transactions).commit(status);
    }

    private Actor actor(){return new Actor(7L,"operator",2L);}
    private ConfigurationSimulationCommand command()
    {
        return new ConfigurationSimulationCommand("request-audit",9L,"LEAD_CREATED","LEAD",3L,Map.of(
                "caseFacts",Map.of("description","confidential description","customerName","Alice Example",
                        "address","1 Privacy Lane"),"downloadUrl","https://download.example.test/export.csv",
                "token","unprefixed-secret-token","items",List.of("one","two"),"note","private case facts"),
                LocalDateTime.of(2026,7,21,9,0),List.of());
    }
}
