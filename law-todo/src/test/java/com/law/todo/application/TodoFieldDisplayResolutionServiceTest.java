package com.law.todo.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.law.todo.application.TodoConfigurationResourceCatalogService.FieldResource;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.spi.TodoFieldReferenceDirectory;
import com.law.todo.spi.TodoFieldReferenceDirectory.DisplayReference;
import com.law.todo.spi.TodoFieldReferenceDirectory.ReferencePage;

class TodoFieldDisplayResolutionServiceTest
{
    @Test void ignoresPlainAndTypedValuesThatDoNotUseAReferenceDirectory()
    {
        TodoFieldDisplayResolutionService service=new TodoFieldDisplayResolutionService(List.of());

        Map<String,DisplayReference> result=service.resolve(List.of(
                new TodoFieldDisplayResolutionService.FieldValue(field("contactedAt","DATE_TIME",null,null),
                        "2026-08-01T09:00:00"),
                new TodoFieldDisplayResolutionService.FieldValue(field("remark","PLAIN_VALUE",null,null),"已联系")),
                new Actor(1L,"admin",103L));

        assertThat(result).isEmpty();
    }

    @Test void batchesValuesBySemanticDirectoryAndNeverFallsBackToRawIds()
    {
        AtomicInteger calls=new AtomicInteger();
        TodoFieldReferenceDirectory directory=new TodoFieldReferenceDirectory()
        {
            @Override public boolean supports(String semanticType,String optionSource)
            {return "USER_ID".equals(semanticType)&&"SYSTEM_USER".equals(optionSource);}
            @Override public Map<Object,DisplayReference> resolve(String semanticType,String optionSource,String dictType,
                    Collection<?> rawValues,Actor actor)
            {
                calls.incrementAndGet();
                return Map.of(11L,new DisplayReference(11L,"张三",Map.of("deptName","销售一部"),true,false,null));
            }
            @Override public ReferencePage options(String semanticType,String optionSource,String dictType,String keyword,
                    int offset,int limit,Actor actor)
            {return new ReferencePage(List.of(),0);}
        };
        TodoFieldDisplayResolutionService service=new TodoFieldDisplayResolutionService(List.of(directory));
        FieldResource owner=field("ownerId","USER_ID","SYSTEM_USER",null);
        FieldResource operator=field("operatorId","USER_ID","SYSTEM_USER",null);

        Map<String,DisplayReference> result=service.resolve(List.of(
                new TodoFieldDisplayResolutionService.FieldValue(owner,11L),
                new TodoFieldDisplayResolutionService.FieldValue(operator,99L)),
                new Actor(1L,"admin",103L));

        assertThat(calls).hasValue(1);
        assertThat(result.get("ownerId").displayValue()).isEqualTo("张三");
        assertThat(result.get("ownerId").meta()).containsEntry("deptName","销售一部");
        assertThat(result.get("operatorId").displayValue()).isEqualTo("原配置对象已失效");
        assertThat(result.get("operatorId").invalidReason()).isEqualTo("原配置对象已失效");
    }

    private FieldResource field(String code,String semantic,String source,String dict)
    {
        return new FieldResource(code,code,"integer",false,null,false,List.of(),List.of(),List.of(),
                null,0,"EVENT_SCHEMA",null,"LEAD","ACTIVE",0,List.of(),semantic,source,dict,null);
    }
}
