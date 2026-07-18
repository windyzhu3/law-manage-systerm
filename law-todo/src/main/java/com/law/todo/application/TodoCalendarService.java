package com.law.todo.application;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.law.todo.application.command.TodoManagementCommands.CalendarCommand;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoMapper;

@Service
public class TodoCalendarService
{
    private final TodoMapper mapper;public TodoCalendarService(TodoMapper mapper){this.mapper=mapper;}
    public List<Map<String,Object>> list(){return mapper.selectCalendars();}
    @Transactional public int save(CalendarCommand command){return save(command,new Actor(0L,"system",0L));}
    @Transactional public int save(CalendarCommand command,Actor actor){requireExpectedVersion(command.calendarId(),command.expectedVersion());validate(command);String type=command.calendarId()==null?"CREATE_CALENDAR":"UPDATE_CALENDAR";String fingerprint=fingerprint(type,command.calendarId(),command.expectedVersion(),command,actor);Long replay=claim(command.actionId(),type,command.calendarId(),fingerprint,actor,command);if(replay!=null)return 1;Map<String,Object> value=new java.util.HashMap<>();value.put("calendarId",command.calendarId());value.put("calendarCode",command.calendarCode());value.put("calendarName",command.calendarName());value.put("timezone",command.timezone());value.put("workDays",command.workDays());value.put("workStart",command.workStart());value.put("workEnd",command.workEnd());value.put("exceptionJson",command.exceptionJson());value.put("status",command.status()==null?"0":command.status());value.put("expectedVersion",command.expectedVersion()==null?0:command.expectedVersion());int saved=save(value);Long id=command.calendarId()==null?Long.valueOf(String.valueOf(value.get("calendarId"))):command.calendarId();complete(command.actionId(),fingerprint,id);return saved;}
    @Transactional int save(Map<String,Object> value){value.putIfAbsent("expectedVersion",0);int saved=value.get("calendarId")==null?mapper.insertCalendar(value):mapper.updateCalendar(value);if(saved<=0&&value.get("calendarId")!=null)throw new TodoException("TODO_CALENDAR_VERSION_CONFLICT","Calendar changed; refresh before retrying");return saved;}
    private void requireExpectedVersion(Long id,Integer version){if(id!=null&&version==null)throw new TodoException("TODO_CALENDAR_VERSION_REQUIRED","expectedVersion is required for calendar updates");}
    private void validate(CalendarCommand command){try{ZoneId.of(command.timezone());}catch(Exception e){throw new TodoException("TODO_CALENDAR_TIMEZONE_INVALID","Calendar timezone is invalid");}if(command.exceptionJson()!=null&&!command.exceptionJson().isBlank()){if(!JSON.isValidObject(command.exceptionJson()))throw new TodoException("TODO_CALENDAR_JSON_INVALID","Calendar exceptions must be a JSON object");JSONObject exceptions=JSON.parseObject(command.exceptionJson());for(String date:exceptions.keySet()){try{LocalDate.parse(date);}catch(Exception e){throw new TodoException("TODO_CALENDAR_DATE_INVALID","Calendar exception date is invalid");}if(!(exceptions.get(date) instanceof Boolean))throw new TodoException("TODO_CALENDAR_DATE_INVALID","Calendar exception must be boolean");}}LocalTime start=LocalTime.parse(command.workStart()),end=LocalTime.parse(command.workEnd());if(!end.isAfter(start))throw new TodoException("TODO_CALENDAR_TIME_INVALID","Calendar end time must be after start time");}
    private String fingerprint(String type,Long id,Integer version,Object command,Actor actor){Map<String,Object> values=new TreeMap<>();values.put("type",type);values.put("id",id);values.put("version",version);values.put("actor",actor.userId());values.put("request",JSON.parse(JSON.toJSONString(command)));return TodoDefinitionSimulationService.sha256(JSON.toJSONString(values));}
    private Long claim(String actionId,String type,Long source,String fingerprint,Actor actor,Object command)
    {
        if(actionId==null||actionId.isBlank())throw new TodoException("TODO_CALENDAR_ACTION_REQUIRED","actionId is required");
        Map<String,Object> action=new java.util.HashMap<>();action.put("actionId",actionId);action.put("actionType",type);action.put("entityType","CALENDAR");
        action.put("sourceEntityId",source);action.put("operatorId",actor.userId());action.put("operatorName",actor.userName());
        action.put("operatorDeptId",actor.deptId());action.put("requestFingerprint",fingerprint);action.put("payloadJson",JSON.toJSONString(command));
        int inserted=mapper.insertDefinitionActionClaim(action);
        Map<String,Object> locked=mapper.selectDefinitionActionForUpdate(actionId);
        if(locked==null||!type.equals(text(value(locked,"action_type","actionType")))
                ||!"CALENDAR".equals(text(value(locked,"entity_type","entityType")))
                ||!fingerprint.equals(text(value(locked,"request_fingerprint","requestFingerprint")))
                ||!Objects.equals(source,number(value(locked,"source_entity_id","sourceEntityId")))
                ||!Objects.equals(actor.userId(),number(value(locked,"operator_id","operatorId")))
                ||!Objects.equals(actor.userName(),text(value(locked,"operator_name","operatorName")))
                ||!Objects.equals(actor.deptId(),number(value(locked,"operator_dept_id","operatorDeptId"))))
            throw new TodoException("TODO_CALENDAR_ACTION_CONFLICT","action id conflicts");
        Long entity=number(value(locked,"entity_id","entityId"));
        String status=text(value(locked,"action_status","actionStatus"));
        if(entity!=null)
        {
            if(!"APPLIED".equals(status))throw new TodoException("TODO_CALENDAR_ACTION_CONFLICT","action incomplete");
            return entity;
        }
        if(inserted<=0||!"CLAIMED".equals(status))
            throw new TodoException("TODO_CALENDAR_ACTION_CONFLICT","recorded action is incomplete");
        return null;
    }
    private Object value(Map<String,Object> row,String snake,String camel){return row.containsKey(snake)?row.get(snake):row.get(camel);}
    private String text(Object value){return value==null?null:String.valueOf(value);}
    private Long number(Object value){return value==null?null:Long.valueOf(String.valueOf(value));}
    private void complete(String actionId,String fingerprint,Long id){if(id==null||mapper.completeDefinitionAction(actionId,fingerprint,id)<=0)throw new TodoException("TODO_CALENDAR_ACTION_CONFLICT","action completion failed");}
}
