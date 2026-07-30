package com.law.todo.application.view;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.law.todo.application.view.TodoConfigurationJourneyView.EmployeeTodoPreview;
import com.law.todo.application.view.TodoConfigurationJourneyView.JourneyIssue;
import com.law.todo.spi.TodoBusinessPayloadAccess.PayloadFieldSource;

/** Page-oriented, redacted view of one read-only journey simulation. */
public record TodoJourneySimulationResult(
        HydratedPayload payload,
        TodoSimulationView engine,
        List<TraceSection> trace,
        EmployeeTodoPreview employeePreview,
        List<JourneyIssue> issues,
        boolean publishEligible,
        TodoSimulationReadinessView readiness)
{
    public TodoJourneySimulationResult
    {
        trace=trace==null?List.of():List.copyOf(trace);
        issues=issues==null?List.of():List.copyOf(issues);
    }

    public TodoJourneySimulationResult(HydratedPayload payload,TodoSimulationView engine,
            List<TraceSection> trace,EmployeeTodoPreview employeeTodoPreview,
            List<JourneyIssue> issues,boolean publishEligible)
    {
        this(payload,engine,trace,employeeTodoPreview,issues,publishEligible,null);
    }

    public record HydratedPayload(Map<String,Object> values,List<PayloadFieldSource> fields,int coveragePercent)
    {
        public HydratedPayload
        {
            values=values==null?Map.of():Collections.unmodifiableMap(new LinkedHashMap<>(values));
            fields=fields==null?List.of():List.copyOf(fields);
        }
    }

    public record TraceSection(String code,String title,String status,String summary,List<TraceDetail> details)
    {
        public TraceSection { details=details==null?List.of():List.copyOf(details); }
    }

    public record TraceDetail(String label,String value,String source,String status) { }
}
