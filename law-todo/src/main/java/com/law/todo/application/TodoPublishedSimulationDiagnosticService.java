package com.law.todo.application;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson2.JSON;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoDefinitionCommands.SimulateDefinitionCommand;
import com.law.todo.application.view.TodoConfigurationViews.PublishedSimulationDiagnostic;
import com.law.todo.application.view.TodoConfigurationViews.PublishedSimulationDiagnosticSummary;
import com.law.todo.application.view.TodoSimulationView;
import com.law.todo.mapper.TodoConfigurationMapper;

/** Read-only health check for every currently published configuration snapshot. */
@Service
public class TodoPublishedSimulationDiagnosticService
{
    private final TodoConfigurationMapper mapper;
    private final TodoDefinitionSimulationService simulations;

    public TodoPublishedSimulationDiagnosticService(TodoConfigurationMapper mapper,
            TodoDefinitionSimulationService simulations)
    {this.mapper=mapper;this.simulations=simulations;}

    @Transactional(readOnly=true)
    public PublishedSimulationDiagnosticSummary diagnose(Actor actor)
    {
        LocalDateTime generatedAt=LocalDateTime.now();
        List<PublishedSimulationDiagnostic> items=new ArrayList<>();
        for(Map<String,Object> row:mapper.selectPublishedSimulationCandidates())
            items.add(diagnose(row,generatedAt));
        int passed=(int)items.stream().filter(item->"PASSED".equals(item.status())).count();
        int warning=(int)items.stream().filter(item->"WARNING".equals(item.status())).count();
        int failed=(int)items.stream().filter(item->"FAILED".equals(item.status())).count();
        return new PublishedSimulationDiagnosticSummary(items.size(),passed,warning,failed,items,generatedAt);
    }

    private PublishedSimulationDiagnostic diagnose(Map<String,Object> row,LocalDateTime effectiveAt)
    {
        long versionId=number(row,"version_id","versionId");long templateId=number(row,"template_id","templateId");
        String templateCode=text(row,"template_code","templateCode"),templateName=text(row,"template_name","templateName");
        String eventType=text(row,"event_type","eventType"),businessType=text(row,"business_type","businessType");
        Integer payloadVersion=integer(row,"payload_version","payloadVersion");
        try
        {
            Map<String,Object> payload=payload(value(row,"sample_payload_json","samplePayloadJson"));
            if(eventType==null||payloadVersion==null||payload.isEmpty())
                throw new IllegalStateException("Active event sample is unavailable");
            TodoSimulationView simulation=simulations.simulate(versionId,
                new SimulateDefinitionCommand(payload,businessType,businessId(payload),effectiveAt,List.of()),
                eventType,payloadVersion,businessType);
            List<String> issueCodes=simulation.issues().stream().map(TodoSimulationView.SimulationIssue::code).distinct().toList();
            boolean errors=simulation.issues().stream().anyMatch(issue->"ERROR".equals(issue.severity()));
            boolean warnings=simulation.issues().stream().anyMatch(issue->"WARNING".equals(issue.severity()));
            String status=errors?"FAILED":(!"MATCHED".equals(simulation.trigger().status())
                ||!"RESOLVED".equals(simulation.owner().status())||warnings?"WARNING":"PASSED");
            String message="PASSED".equals(status)?"模板、事件、负责人、SLA 与完成条件均可解析"
                : summary(simulation,status);
            return new PublishedSimulationDiagnostic(versionId,templateId,templateCode,templateName,eventType,
                payloadVersion,businessType,status,simulation.owner().status(),issueCodes,message,simulation);
        }
        catch(RuntimeException failure)
        {
            return new PublishedSimulationDiagnostic(versionId,templateId,templateCode,templateName,eventType,
                payloadVersion,businessType,"FAILED","UNKNOWN",List.of(code(failure)),safeMessage(failure),null);
        }
    }

    private String summary(TodoSimulationView simulation,String status)
    {
        if("UNRESOLVED".equals(simulation.owner().status()))return "负责人无法由事件样例解析";
        if(!"MATCHED".equals(simulation.trigger().status()))return "事件样例未命中触发条件";
        return "FAILED".equals(status)?"定义模拟存在阻断错误":"定义模拟存在需确认的警告";
    }

    private Map<String,Object> payload(Object raw)
    {
        if(raw instanceof Map<?,?> values)
        {Map<String,Object> result=new LinkedHashMap<>();values.forEach((key,item)->result.put(String.valueOf(key),item));return result;}
        if(raw==null)return Map.of();
        try{Map<String,Object> result=new LinkedHashMap<>();JSON.parseObject(String.valueOf(raw)).forEach(result::put);return result;}
        catch(RuntimeException invalid){return Map.of();}
    }

    private long businessId(Map<String,Object> payload)
    {
        for(String key:List.of("businessId","leadId","contractId","caseId","matterId","assignmentId",
                "approvalId","planId","invoiceId","transferId","nodeId","expenseId","archiveId"))
        {Object raw=payload.get(key);if(raw instanceof Number number&&number.longValue()>0)return number.longValue();}
        return 1L;
    }

    private String code(RuntimeException failure)
    {return failure instanceof com.law.todo.domain.TodoException todo?todo.getBusinessCode():"TODO_PUBLISHED_DIAGNOSTIC_FAILED";}
    private String safeMessage(RuntimeException failure)
    {return failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage();}
    private Object value(Map<String,Object> row,String snake,String camel){return row.containsKey(snake)?row.get(snake):row.get(camel);}
    private String text(Map<String,Object> row,String snake,String camel){Object raw=value(row,snake,camel);return raw==null?null:String.valueOf(raw);}
    private long number(Map<String,Object> row,String snake,String camel){Object raw=value(row,snake,camel);return raw==null?0L:Long.parseLong(String.valueOf(raw));}
    private Integer integer(Map<String,Object> row,String snake,String camel){Object raw=value(row,snake,camel);return raw==null?null:Integer.valueOf(String.valueOf(raw));}
}
