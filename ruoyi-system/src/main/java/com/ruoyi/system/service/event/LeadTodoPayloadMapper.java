package com.ruoyi.system.service.event;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import com.law.business.lead.dto.LeadCallRecordCommand;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;

/** Package-owned conversion helpers; no persistence or authorization decisions belong here. */
final class LeadTodoPayloadMapper
{
    private LeadTodoPayloadMapper(){ }

    static Map<String,Object> values(Map<String,Object> payload)
    {
        return payload==null?Map.of():payload;
    }

    static String text(Map<String,Object> values,String... keys)
    {
        for(String key:keys)
        {
            Object value=values.get(key);
            if(value!=null&&!String.valueOf(value).isBlank())return String.valueOf(value).trim();
        }
        return null;
    }

    static Long number(Map<String,Object> values,String key)
    {
        Object value=values.get(key);
        if(value==null)return null;
        try{return Long.valueOf(String.valueOf(value));}
        catch(NumberFormatException invalid)
        {throw new TodoException("TODO_HANDLER_PAYLOAD_INVALID",key+" must be an integer");}
    }

    static LeadCallRecordCommand manualCall(TodoInstance todo,Map<String,Object> payload)
    {
        Map<String,Object> call=new LinkedHashMap<>(flatCall(payload));
        call.putAll(map(payload.get("callRecord")));
        LeadCallRecordCommand command=new LeadCallRecordCommand();
        command.setLeadId(todo.getBusinessId());
        command.setTodoId(todo.getTodoId());
        command.setCallChannel(defaultText(text(call,"callChannel"),"MANUAL"));
        command.setExternalCallId(text(call,"externalCallId"));
        command.setBusinessOccurrenceKey(defaultText(text(call,"businessOccurrenceKey"),
                derivedOccurrenceKey(todo,call)));
        Object startedAt=call.containsKey("startedAt")?call.get("startedAt"):call.get("contactedAt");
        command.setStartedAt(dateTime(startedAt,"startedAt"));
        command.setEndedAt(dateTime(call.get("endedAt"),"endedAt"));
        command.setDurationSeconds(integer(call.get("durationSeconds"),"durationSeconds"));
        command.setCallResult(text(call,"callResult","contactResult","result"));
        command.setRecordingFileObjectId(number(call,"recordingFileObjectId"));
        command.setManualNotes(text(call,"manualNotes","content"));
        // Provider summaries are admitted only by LeadCallRecordService.recordTrustedCallback.
        return command;
    }

    private static Map<String,Object> flatCall(Map<String,Object> payload)
    {
        Map<String,Object> result=new LinkedHashMap<>();
        for(String key:new String[]{"callChannel","externalCallId","businessOccurrenceKey","startedAt",
                "contactedAt","endedAt","durationSeconds","callResult","contactResult","result",
                "attemptCount","recordingFileObjectId","manualNotes","content"})
            if(payload.containsKey(key))result.put(key,payload.get(key));
        return result;
    }

    private static Map<String,Object> map(Object value)
    {
        if(!(value instanceof Map<?,?> source))return Map.of();
        Map<String,Object> result=new LinkedHashMap<>();
        source.forEach((key,item)->result.put(String.valueOf(key),item));
        return result;
    }

    private static Integer integer(Object value,String field)
    {
        if(value==null)return null;
        try{return Integer.valueOf(String.valueOf(value));}
        catch(NumberFormatException invalid)
        {throw new TodoException("TODO_HANDLER_PAYLOAD_INVALID",field+" must be an integer");}
    }

    private static LocalDateTime dateTime(Object value,String field)
    {
        if(value==null||String.valueOf(value).isBlank())return null;
        if(value instanceof LocalDateTime supplied)return supplied;
        String text=String.valueOf(value).trim();
        try{return LocalDateTime.parse(text.replace(' ','T'));}
        catch(RuntimeException invalid)
        {
            try{return LocalDateTime.parse(text,DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));}
            catch(RuntimeException ignored)
            {throw new TodoException("TODO_HANDLER_PAYLOAD_INVALID",field+" must be an ISO local date-time");}
        }
    }

    private static String derivedOccurrenceKey(TodoInstance todo,Map<String,Object> call)
    {
        if("TD-001".equals(todo.getTemplateCode())||"LEAD_FIRST_CONTACT".equals(todo.getTemplateCode()))
            return "TD-001:"+todo.getTodoId()+":FIRST_CONTACT";
        if("TD-003".equals(todo.getTemplateCode()))
        {
            Integer attempt=integer(call.get("attemptCount"),"attemptCount");
            if(todo.getOccurrenceKey()!=null&&attempt!=null&&attempt>0)
                return "TD-003:"+todo.getTodoId()+":"+todo.getOccurrenceKey()+":ATTEMPT:"+attempt;
        }
        return null;
    }

    private static String defaultText(String value,String fallback){return value==null?fallback:value;}
}
