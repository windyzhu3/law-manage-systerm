package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoManagementCommands.CalendarCommand;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoMapper;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness=Strictness.LENIENT)
class TodoCalendarServiceTest
{
    @Mock TodoMapper mapper;
    private TodoCalendarService service;

    @BeforeEach void setUp(){service=new TodoCalendarService(mapper);}

    @Test void rejectsInvalidExceptionJson()
    {
        TodoException error=assertThrows(TodoException.class,
                ()->service.save(validationCommand("09:00:00","18:00:00","not-json")));
        assertEquals("TODO_CALENDAR_JSON_INVALID",error.getBusinessCode());
    }

    @Test void rejectsEndBeforeStart()
    {
        TodoException error=assertThrows(TodoException.class,
                ()->service.save(validationCommand("18:00:00","09:00:00","{}")));
        assertEquals("TODO_CALENDAR_TIME_INVALID",error.getBusinessCode());
    }

    @Test void calendarCreateReplaysAppliedActionWithoutDuplicateWrite()
    {
        Ledger ledger=ledger(true);
        when(mapper.insertCalendar(anyMap())).thenAnswer(invocation->{
            invocation.<Map<String,Object>>getArgument(0).put("calendarId",51L);return 1;
        });
        CalendarCommand command=command(null,"DEFAULT","calendar-create",0,"0");

        assertEquals(1,service.save(command,actor()));
        assertEquals(1,service.save(command,actor()));

        verify(mapper,times(1)).insertCalendar(anyMap());
        assertEquals(51L,ledger.entityId.get());
    }

    @Test void calendarUpdateReplaysAppliedActionWithoutDuplicateWrite()
    {
        ledger(true);when(mapper.updateCalendar(anyMap())).thenReturn(1);
        CalendarCommand command=command(51L,"DEFAULT","calendar-update",4,"1");

        assertEquals(1,service.save(command,actor()));
        assertEquals(1,service.save(command,actor()));

        verify(mapper,times(1)).updateCalendar(anyMap());
    }

    @Test void calendarActionIdCannotBeReusedForDifferentRequestOrActor()
    {
        ledger(true);when(mapper.insertCalendar(anyMap())).thenAnswer(invocation->{
            invocation.<Map<String,Object>>getArgument(0).put("calendarId",52L);return 1;
        });
        assertEquals(1,service.save(command(null,"DEFAULT","calendar-conflict",0,"0"),actor()));

        TodoException payload=assertThrows(TodoException.class,
                ()->service.save(command(null,"OTHER","calendar-conflict",0,"0"),actor()));
        assertEquals("TODO_CALENDAR_ACTION_CONFLICT",payload.getBusinessCode());
        TodoException operator=assertThrows(TodoException.class,
                ()->service.save(command(null,"DEFAULT","calendar-conflict",0,"0"),new Actor(8L,"bob",3L)));
        assertEquals("TODO_CALENDAR_ACTION_CONFLICT",operator.getBusinessCode());
    }

    @Test void calendarRejectsStaleVersionAndDoesNotCompleteAction()
    {
        ledger(true);when(mapper.updateCalendar(anyMap())).thenReturn(0);
        TodoException error=assertThrows(TodoException.class,
                ()->service.save(command(51L,"DEFAULT","calendar-stale",3,"0"),actor()));
        assertEquals("TODO_CALENDAR_VERSION_CONFLICT",error.getBusinessCode());
        verify(mapper,never()).completeDefinitionAction(anyString(),anyString(),anyLong());
    }

    @Test void calendarUpdateRequiresExplicitExpectedVersion()
    {
        CalendarCommand command=new CalendarCommand(51L,"DEFAULT","Default","Asia/Shanghai","1,2,3,4,5",
                "09:00","18:00","{}","0","calendar-no-version",null);
        TodoException error=assertThrows(TodoException.class,()->service.save(command,actor()));
        assertEquals("TODO_CALENDAR_VERSION_REQUIRED",error.getBusinessCode());
        verify(mapper,never()).insertDefinitionActionClaim(anyMap());
    }

    @Test void calendarRejectsPreviouslyIncompleteClaim()
    {
        ledger(false);
        TodoException error=assertThrows(TodoException.class,
                ()->service.save(command(null,"DEFAULT","calendar-incomplete",0,"0"),actor()));
        assertEquals("TODO_CALENDAR_ACTION_CONFLICT",error.getBusinessCode());
        verify(mapper,never()).insertCalendar(anyMap());
    }

    private CalendarCommand validationCommand(String start,String end,String exceptions)
    {return new CalendarCommand(null,"DEFAULT","Default","Asia/Shanghai","1,2,3,4,5",start,end,exceptions,"0");}
    private CalendarCommand command(Long id,String code,String actionId,int version,String status)
    {return new CalendarCommand(id,code,"Default","Asia/Shanghai","1,2,3,4,5","09:00","18:00","{}",status,actionId,version);}
    private Actor actor(){return new Actor(7L,"alice",2L);}

    private Ledger ledger(boolean insertFirst)
    {
        Ledger ledger=new Ledger();AtomicBoolean first=new AtomicBoolean(insertFirst);
        when(mapper.insertDefinitionActionClaim(anyMap())).thenAnswer(invocation->{
            Map<String,Object> action=new HashMap<>(invocation.getArgument(0));
            if(ledger.claim.get()==null)ledger.claim.set(action);
            return first.compareAndSet(true,false)?1:0;
        });
        when(mapper.selectDefinitionActionForUpdate(anyString())).thenAnswer(invocation->locked(ledger));
        when(mapper.completeDefinitionAction(anyString(),anyString(),anyLong())).thenAnswer(invocation->{
            ledger.applied.set(true);ledger.entityId.set(invocation.getArgument(2));return 1;
        });
        return ledger;
    }

    private Map<String,Object> locked(Ledger ledger)
    {
        Map<String,Object> action=ledger.claim.get();if(action==null)return null;
        Map<String,Object> row=new HashMap<>();row.put("action_type",action.get("actionType"));row.put("entity_type",action.get("entityType"));
        row.put("source_entity_id",action.get("sourceEntityId"));row.put("operator_id",action.get("operatorId"));
        row.put("operator_name",action.get("operatorName"));row.put("operator_dept_id",action.get("operatorDeptId"));
        row.put("request_fingerprint",action.get("requestFingerprint"));row.put("action_status",ledger.applied.get()?"APPLIED":"CLAIMED");
        if(ledger.entityId.get()!=null)row.put("entity_id",ledger.entityId.get());return row;
    }

    private static final class Ledger
    {private final AtomicReference<Map<String,Object>> claim=new AtomicReference<>();private final AtomicBoolean applied=new AtomicBoolean();private final AtomicReference<Long> entityId=new AtomicReference<>();}
}
