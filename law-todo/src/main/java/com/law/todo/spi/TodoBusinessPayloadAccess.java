package com.law.todo.spi;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.law.todo.application.command.TodoActionCommands.Actor;

/** Actor-scoped business payload projection used only by read-only configuration simulation. */
public interface TodoBusinessPayloadAccess
{
    boolean supports(String businessType);
    PayloadHydration hydrate(String eventType,int payloadVersion,String businessType,long businessId,Actor actor);

    record PayloadHydration(Map<String,Object> payload,List<PayloadFieldSource> fields,boolean sample)
    {
        public PayloadHydration
        {
            payload=payload==null?Map.of():Collections.unmodifiableMap(new LinkedHashMap<>(payload));
            fields=fields==null?List.of():List.copyOf(fields);
        }

        public int coveragePercent()
        {
            long required=fields.stream().filter(PayloadFieldSource::required).count();
            if(required==0)return 100;
            long present=fields.stream().filter(PayloadFieldSource::required).filter(field->!field.missing()).count();
            return (int)Math.round(present*100.0/required);
        }
    }

    record PayloadFieldSource(String path,Object value,String source,boolean required,
            boolean missing,String missingReason,boolean sensitive) { }

    record DataSourceStatus(String businessType,boolean directoryAvailable,boolean payloadAvailable,
            boolean sampleAvailable,String status,String message) { }
}
