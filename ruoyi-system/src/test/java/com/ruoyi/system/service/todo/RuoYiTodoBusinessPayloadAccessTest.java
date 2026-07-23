package com.ruoyi.system.service.todo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.domain.TodoException;
import com.ruoyi.system.mapper.TodoBusinessDirectoryMapper;

@ExtendWith(MockitoExtension.class)
class RuoYiTodoBusinessPayloadAccessTest
{
    @Mock TodoBusinessDirectoryMapper mapper;
    @Captor ArgumentCaptor<Map<String,Object>> query;
    private final Actor actor=new Actor(7L,"operator",3L);

    @Test void mapsAllFiveBusinessFamiliesWithStableEventPayloads()
    {
        record Fixture(String type,String event,Map<String,Object> row,String expectedKey,Object expectedValue) { }
        List<Fixture> fixtures=List.of(
                new Fixture("LEAD","LEAD_ASSIGNED",row("business_id",81L,"owner_id",7L,"dept_id",3L),
                        "leadId",81L),
                new Fixture("CUSTOMER","CUSTOMER_PROGRESS_STALE",
                        row("business_id",82L,"owner_id",7L,"last_follow_time","2026-07-22T09:00:00"),
                        "customerId",82L),
                new Fixture("CONTRACT","CONTRACT_SUBMITTED",
                        row("business_id",83L,"owner_id",7L,"sign_amount",new BigDecimal("100000")),
                        "amount",new BigDecimal("100000")),
                new Fixture("CASE","CASE_CREATED",
                        row("business_id",84L,"contract_id",83L,"owner_id",7L),
                        "contractId",83L),
                new Fixture("MATTER","MATTER_NODE_READY",
                        row("business_id",85L,"current_node","HEARING","owner_id",7L),
                        "nodeCode","HEARING"));

        RuoYiTodoBusinessPayloadAccess access=new RuoYiTodoBusinessPayloadAccess(mapper);
        for(Fixture fixture:fixtures)
        {
            when(mapper.selectVisibleBusinessPayload(anyMap())).thenReturn(fixture.row());
            var result=access.hydrate(fixture.event(),1,fixture.type(),fixture.type().equals("LEAD")?81L:
                    fixture.type().equals("CUSTOMER")?82L:fixture.type().equals("CONTRACT")?83L:
                    fixture.type().equals("CASE")?84L:85L,actor);
            assertThat(result.payload()).containsEntry(fixture.expectedKey(),fixture.expectedValue());
            assertThat(result.fields()).allMatch(field->List.of("BUSINESS_OBJECT","SYSTEM_DEFAULT").contains(field.source()));
            assertThat(result.fields()).extracting(com.law.todo.spi.TodoBusinessPayloadAccess.PayloadFieldSource::source)
                    .contains("BUSINESS_OBJECT");
        }
    }

    @Test void reusesTheActorScopedDirectoryQueryContract()
    {
        when(mapper.selectVisibleBusinessPayload(anyMap()))
                .thenReturn(row("business_id",81L,"owner_id",7L,"dept_id",3L));

        new RuoYiTodoBusinessPayloadAccess(mapper).hydrate("LEAD_ASSIGNED",1,"LEAD",81L,actor);

        verify(mapper).selectVisibleBusinessPayload(query.capture());
        assertThat(query.getValue()).containsEntry("businessType","LEAD")
                .containsEntry("businessId",81L)
                .containsEntry("currentUserId",7L)
                .containsEntry("currentDeptId",3L)
                .containsEntry("dataScope",true)
                .containsEntry("permissions","lead:query,lead:mine:query");
    }

    @Test void inaccessibleObjectFailsWithStableBusinessCode()
    {
        when(mapper.selectVisibleBusinessPayload(anyMap())).thenReturn(Map.of());

        assertThatThrownBy(()->new RuoYiTodoBusinessPayloadAccess(mapper)
                .hydrate("LEAD_ASSIGNED",1,"LEAD",81L,actor))
                .isInstanceOf(TodoException.class)
                .extracting(error->((TodoException)error).getBusinessCode())
                .isEqualTo("TODO_SIMULATION_BUSINESS_OBJECT_NOT_FOUND");
    }

    @Test void rejectsUnsupportedEventTypeVersionTupleWithExactCode()
    {
        when(mapper.selectVisibleBusinessPayload(anyMap()))
                .thenReturn(row("business_id",81L,"owner_id",7L,"dept_id",3L));

        assertThatThrownBy(()->new RuoYiTodoBusinessPayloadAccess(mapper)
                .hydrate("CONTRACT_SIGNED",1,"LEAD",81L,actor))
                .isInstanceOf(TodoException.class)
                .extracting(error->((TodoException)error).getBusinessCode())
                .isEqualTo("TODO_SIMULATION_PAYLOAD_MAPPING_UNSUPPORTED");
        assertThatThrownBy(()->new RuoYiTodoBusinessPayloadAccess(mapper)
                .hydrate("LEAD_ASSIGNED",2,"LEAD",81L,actor))
                .isInstanceOf(TodoException.class)
                .extracting(error->((TodoException)error).getBusinessCode())
                .isEqualTo("TODO_SIMULATION_PAYLOAD_MAPPING_UNSUPPORTED");
    }

    private Map<String,Object> row(Object... values)
    {
        Map<String,Object> result=new LinkedHashMap<>();
        for(int index=0;index<values.length;index+=2)result.put(String.valueOf(values[index]),values[index+1]);
        return result;
    }
}
