package com.law.todo.application;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson2.JSON;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoConfigurationCommands.SlaRuleCommand;
import com.law.todo.application.view.TodoConfigurationViews.SlaCalculationResult;
import com.law.todo.application.view.TodoConfigurationViews.SlaRuleDetail;
import com.law.todo.application.view.TodoConfigurationViews.SlaRuleListItem;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.service.WorkingTimeCalculator;
import com.law.todo.domain.service.WorkingTimeCalculator.WorkCalendar;
import com.law.todo.mapper.TodoConfigurationMapper;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.spi.TodoDictionaryValidationPort;

/** Manages reusable SLA rules; published template snapshots are deliberately outside this service's write scope. */
@Service
public class TodoSlaRuleManagementService
{
    private final TodoConfigurationMapper mapper;
    private final TodoMapper todoMapper;
    private final TodoDictionaryValidationPort dictionaries;
    private final WorkingTimeCalculator calculator=new WorkingTimeCalculator();

    public TodoSlaRuleManagementService(TodoConfigurationMapper mapper,TodoMapper todoMapper,
            TodoDictionaryValidationPort dictionaries)
    {this.mapper=mapper;this.todoMapper=todoMapper;this.dictionaries=dictionaries;}

    @Transactional(readOnly=true)
    public List<SlaRuleListItem> list(Map<String,Object> query)
    {return mapper.selectSlaRules(query==null?Map.of():query).stream().map(this::listItem).toList();}

    @Transactional(readOnly=true)
    public SlaRuleDetail detail(long slaRuleId)
    {return detail(require(slaRuleId));}

    @Transactional
    public long save(SlaRuleCommand command,Actor actor)
    {
        validate(command);requireCalendar(command.calendarCode());Map<String,Object> row=row(command,actor);
        int changed=command.slaRuleId()==null?mapper.insertSlaRule(row):mapper.updateSlaRuleConditionally(row);
        if(changed<=0)throw new TodoException("TODO_SLA_RULE_VERSION_CONFLICT","SLA rule changed; refresh before retrying");
        Long id=command.slaRuleId()==null?number(row.get("slaRuleId")):command.slaRuleId();
        if(id==null)throw new TodoException("TODO_SLA_RULE_VERSION_CONFLICT","SLA rule identifier was not generated");
        return id;
    }

    @Transactional
    public long copy(long sourceSlaRuleId,String newRuleCode,String actionId,Actor actor)
    {
        Map<String,Object> source=require(sourceSlaRuleId);
        if(newRuleCode==null||newRuleCode.isBlank())throw new TodoException("TODO_SLA_RULE_CODE_REQUIRED","Copied SLA rule code is required");
        SlaRuleCommand copy=new SlaRuleCommand(null,newRuleCode,text(source,"rule_code","ruleCode"),text(source,"sla_type","slaType"),
                integer(source,"duration_value","durationValue"),text(source,"duration_unit","durationUnit"),
                text(source,"calendar_code","calendarCode"),text(source,"start_strategy","startStrategy"),
                integer(source,"soft_remind_percent","softRemindPercent"),integer(source,"hard_remind_percent","hardRemindPercent"),
                integer(source,"escalate_percent","escalatePercent"),text(source,"pause_policy_json","pausePolicyJson"),
                text(source,"escalation_policy_json","escalationPolicyJson"),text(source,"auto_action_json","autoActionJson"),
                text(source,"status","status"),actionId,0);
        return save(copy,actor);
    }

    @Transactional
    public void toggle(long slaRuleId,String status,String actionId,int expectedVersion,Actor actor)
    {
        if(!"0".equals(status)&&!"1".equals(status))throw new TodoException("TODO_SLA_RULE_STATUS_INVALID","SLA rule status is invalid");
        mapper.countSlaRuleReferences(slaRuleId); // Reporting only: existing draft references remain intact; published snapshots are never touched.
        Map<String,Object> row=new HashMap<>();row.put("slaRuleId",slaRuleId);row.put("status",status);row.put("actionId",actionId);
        row.put("expectedVersion",expectedVersion);row.put("updateBy",actor.userName());
        if(mapper.updateSlaRuleConditionally(row)<=0)
            throw new TodoException("TODO_SLA_RULE_VERSION_CONFLICT","SLA rule changed; refresh before retrying");
    }

    @Transactional(readOnly=true)
    public SlaCalculationResult testCalculation(long slaRuleId,LocalDateTime createdAt)
    {
        if(createdAt==null)throw new TodoException("TODO_SLA_RULE_CALCULATION_START_REQUIRED","SLA calculation start is required");
        Map<String,Object> rule=require(slaRuleId);WorkCalendar calendar=calendar(requireCalendar(text(rule,"calendar_code","calendarCode")));
        long minutes=durationMinutes(rule,calendar);LocalDateTime due=calculator.addWorkingMinutes(createdAt,minutes,calendar);
        return new SlaCalculationResult(createdAt,calculator.addWorkingMinutes(createdAt,threshold(minutes,
                integer(rule,"soft_remind_percent","softRemindPercent")),calendar),due,
                calculator.addWorkingMinutes(createdAt,threshold(minutes,integer(rule,"escalate_percent","escalatePercent")),calendar));
    }

    private void validate(SlaRuleCommand command)
    {
        if(command==null||!dictionaries.isEnabled("law_todo_sla_type",command.slaType())
                ||!dictionaries.isEnabled("law_todo_sla_unit",command.durationUnit())
                ||!dictionaries.isEnabled("law_todo_sla_start_strategy",command.startStrategy()))
            throw new TodoException("TODO_SLA_RULE_DICTIONARY_INVALID","SLA rule dictionary value is unknown or disabled");
    }
    private Map<String,Object> requireCalendar(String calendarCode)
    {Map<String,Object> calendar=todoMapper.selectCalendarByCode(calendarCode);if(calendar==null||calendar.isEmpty())throw new TodoException("TODO_SLA_RULE_CALENDAR_NOT_FOUND","Working calendar is unknown or disabled");return calendar;}
    private Map<String,Object> require(long id)
    {Map<String,Object> rule=mapper.selectSlaRule(id);if(rule==null||rule.isEmpty())throw new TodoException("TODO_SLA_RULE_NOT_FOUND","SLA rule not found");return rule;}
    private Map<String,Object> row(SlaRuleCommand command,Actor actor)
    {
        Map<String,Object> row=new HashMap<>();row.put("slaRuleId",command.slaRuleId());row.put("ruleCode",command.ruleCode());
        row.put("ruleName",command.ruleName());row.put("slaType",command.slaType());row.put("durationValue",command.durationValue());
        row.put("durationUnit",command.durationUnit());row.put("calendarCode",command.calendarCode());row.put("startStrategy",command.startStrategy());
        row.put("softRemindPercent",command.softRemindPercent());row.put("hardRemindPercent",command.hardRemindPercent());
        row.put("escalatePercent",command.escalatePercent());row.put("pausePolicyJson",command.pausePolicyJson());
        row.put("escalationPolicyJson",command.escalationPolicyJson());row.put("autoActionJson",command.autoActionJson());
        row.put("status",command.status());row.put("actionId",command.actionId());row.put("expectedVersion",command.expectedVersion());
        row.put("updateAllFields",true);row.put("createBy",actor.userName());row.put("updateBy",actor.userName());return row;
    }
    private SlaRuleListItem listItem(Map<String,Object> row)
    {return new SlaRuleListItem(number(value(row,"sla_rule_id","slaRuleId")),text(row,"rule_code","ruleCode"),text(row,"rule_name","ruleName"),
            text(row,"sla_type","slaType"),integer(row,"duration_value","durationValue"),text(row,"duration_unit","durationUnit"),
            text(row,"calendar_code","calendarCode"),text(row,"start_strategy","startStrategy"),text(row,"status","status"),
            integer(row,"version","version"),longNumber(value(row,"reference_count","referenceCount")),date(value(row,"update_time","updateTime")));}
    private SlaRuleDetail detail(Map<String,Object> row)
    {return new SlaRuleDetail(number(value(row,"sla_rule_id","slaRuleId")),text(row,"rule_code","ruleCode"),text(row,"rule_name","ruleName"),
            text(row,"sla_type","slaType"),integer(row,"duration_value","durationValue"),text(row,"duration_unit","durationUnit"),
            text(row,"calendar_code","calendarCode"),text(row,"start_strategy","startStrategy"),integer(row,"soft_remind_percent","softRemindPercent"),
            integer(row,"hard_remind_percent","hardRemindPercent"),integer(row,"escalate_percent","escalatePercent"),
            text(row,"pause_policy_json","pausePolicyJson"),text(row,"escalation_policy_json","escalationPolicyJson"),
            text(row,"auto_action_json","autoActionJson"),text(row,"status","status"),integer(row,"version","version"),
            longNumber(value(row,"reference_count","referenceCount")),text(row,"create_by","createBy"),date(value(row,"create_time","createTime")),
            text(row,"update_by","updateBy"),date(value(row,"update_time","updateTime")));}
    private WorkCalendar calendar(Map<String,Object> row)
    {
        Set<DayOfWeek> days=EnumSet.noneOf(DayOfWeek.class);for(String day:text(row,"work_days","workDays").split(","))days.add(DayOfWeek.of(Integer.parseInt(day.trim())));
        Map<LocalDate,Boolean> exceptions=new HashMap<>();String json=text(row,"exception_json","exceptionJson");
        if(json!=null&&!json.isBlank())JSON.parseObject(json).forEach((date,working)->exceptions.put(LocalDate.parse(date),Boolean.valueOf(String.valueOf(working))));
        return new WorkCalendar(days,time(value(row,"work_start","workStart")),time(value(row,"work_end","workEnd")),exceptions);
    }
    private long durationMinutes(Map<String,Object> rule,WorkCalendar calendar)
    {long value=integer(rule,"duration_value","durationValue");return switch(text(rule,"duration_unit","durationUnit"))
        {case "MINUTE"->value;case "HOUR"->value*60;case "DAY"->value*Duration.between(calendar.workStart(),calendar.workEnd()).toMinutes();
            default->throw new TodoException("TODO_SLA_RULE_DURATION_UNIT_INVALID","SLA duration unit is unsupported");};}
    private long threshold(long minutes,int percent){return (minutes*percent+99)/100;}
    private Object value(Map<String,Object> row,String snake,String camel){return row.containsKey(snake)?row.get(snake):row.get(camel);}
    private String text(Map<String,Object> row,String snake,String camel){Object value=value(row,snake,camel);return value==null?null:String.valueOf(value);}
    private Long number(Object value){return value==null?null:Long.valueOf(String.valueOf(value));}
    private long longNumber(Object value){Long number=number(value);return number==null?0:number;}
    private Integer integer(Map<String,Object> row,String snake,String camel){Object value=value(row,snake,camel);return value==null?null:Integer.valueOf(String.valueOf(value));}
    private LocalTime time(Object value){String text=String.valueOf(value);return LocalTime.parse(text.length()>8?text.substring(0,8):text);}
    private LocalDateTime date(Object value){if(value instanceof LocalDateTime time)return time;if(value instanceof Timestamp timestamp)return timestamp.toLocalDateTime();return null;}
}
