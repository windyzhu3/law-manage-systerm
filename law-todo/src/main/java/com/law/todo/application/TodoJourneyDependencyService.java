package com.law.todo.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.law.todo.application.view.TodoConfigurationViews.JourneyImpact;
import com.law.todo.definition.model.TodoDefinitionDocument;

/**
 * Compares two canonical journey definitions and projects the downstream
 * configuration and evidence that must be reconsidered.
 */
@Service
public class TodoJourneyDependencyService
{
    private static final List<String> STEP_ORDER=List.of(
            "EVENT","TRIGGER","OWNER","DOD","SLA","ROUTING","SIMULATION_PUBLISH");
    private static final Set<String> PRESENTATION_KEYS=Set.of(
            "title","subtitle","description","helpText","panels","layout","businessStage");

    public JourneyImpact analyze(TodoDefinitionDocument before,TodoDefinitionDocument after)
    {
        List<String> changedPaths=new ArrayList<>();
        compare("",document(before),document(after),changedPaths);
        if(changedPaths.isEmpty())return JourneyImpact.none();

        LinkedHashSet<String> affected=new LinkedHashSet<>();
        boolean executable=false;
        boolean completionResult=false;
        boolean routeOnly=true;
        for(String path:changedPaths)
        {
            boolean presentation=presentationOnly(path);
            completionResult|=path.contains("contactResult");
            if(path.startsWith("event.eventType")||path.startsWith("event.payloadVersion"))
            {
                add(affected,"EVENT","TRIGGER","OWNER","DOD","ROUTING","SIMULATION_PUBLISH");
                executable=true;routeOnly=false;
            }
            else if(path.startsWith("event.condition"))
            {
                add(affected,"TRIGGER","SIMULATION_PUBLISH");
                executable=true;routeOnly=false;
            }
            else if(path.startsWith("owner."))
            {
                add(affected,"OWNER","SIMULATION_PUBLISH");
                executable=true;routeOnly=false;
            }
            else if(path.startsWith("dod."))
            {
                add(affected,"DOD","ROUTING","SIMULATION_PUBLISH");
                executable=true;routeOnly=false;
            }
            else if(path.startsWith("sla."))
            {
                add(affected,"SLA","SIMULATION_PUBLISH");
                executable=true;routeOnly=false;
            }
            else if(path.startsWith("routing."))
            {
                add(affected,"ROUTING","SIMULATION_PUBLISH");
                executable=true;
            }
            else if(path.startsWith("ui."))
            {
                add(affected,"SIMULATION_PUBLISH");
                if(!presentation){executable=true;routeOnly=false;}
            }
            else
            {
                add(affected,"SIMULATION_PUBLISH");
                executable=true;routeOnly=false;
            }
            if(path.contains("contactResult"))
                add(affected,"DOD","ROUTING","SIMULATION_PUBLISH");
        }
        List<String> ordered=STEP_ORDER.stream().filter(affected::contains).toList();
        List<String> invalidated=executable?List.of("SIMULATION_SCENARIOS"):List.of();
        return new JourneyImpact(changedPaths,ordered,invalidated,
                message(executable,completionResult,routeOnly));
    }

    private Map<String,Object> document(TodoDefinitionDocument value)
    {
        if(value==null)return Map.of();
        Map<String,Object> result=new LinkedHashMap<>();
        Map<String,Object> event=new LinkedHashMap<>();
        if(value.event()!=null)
        {
            event.put("eventType",value.event().eventType());
            event.put("payloadVersion",value.event().payloadVersion());
            event.put("condition",value.event().condition());
        }
        result.put("event",event);
        result.put("owner",section(value.owner()==null?null:value.owner().config()));
        result.put("dod",section(value.dod()==null?null:value.dod().config()));
        result.put("sla",section(value.sla()==null?null:value.sla().config()));
        result.put("routing",section(value.routing()==null?null:value.routing().config()));
        result.put("ui",section(value.ui()==null?null:value.ui().config()));
        result.put("autoActions",value.autoActions());
        result.put("decisionRefs",value.decisionRefs());
        result.put("acceptanceRefs",value.acceptanceRefs());
        return result;
    }

    private Map<String,Object> section(Map<String,Object> config)
    {
        Map<String,Object> section=new LinkedHashMap<>();
        section.put("config",config==null?Map.of():config);
        return section;
    }

    private void compare(String path,Object before,Object after,List<String> changed)
    {
        if(Objects.deepEquals(before,after))return;
        if(before instanceof Map<?,?> left&&after instanceof Map<?,?> right)
        {
            LinkedHashSet<String> keys=new LinkedHashSet<>();
            left.keySet().forEach(key->keys.add(String.valueOf(key)));
            right.keySet().forEach(key->keys.add(String.valueOf(key)));
            for(String key:keys)
                compare(child(path,key),left.get(key),right.get(key),changed);
            return;
        }
        if(before instanceof List<?> left&&after instanceof List<?> right)
        {
            int size=Math.max(left.size(),right.size());
            for(int index=0;index<size;index++)
                compare(path+"["+index+"]",index<left.size()?left.get(index):null,
                        index<right.size()?right.get(index):null,changed);
            return;
        }
        changed.add(path);
    }

    private String child(String parent,String name)
    {return parent==null||parent.isBlank()?name:parent+"."+name;}

    private boolean presentationOnly(String path)
    {
        if(!path.startsWith("ui.config."))return false;
        String key=path.substring("ui.config.".length());
        int separator=key.indexOf('.');
        int array=key.indexOf('[');
        int end=separator<0?key.length():separator;
        if(array>=0)end=Math.min(end,array);
        return PRESENTATION_KEYS.contains(key.substring(0,end));
    }

    private void add(Collection<String> target,String... values)
    {for(String value:values)target.add(value);}

    private String message(boolean executable,boolean completionResult,boolean routeOnly)
    {
        if(!executable)return "展示信息已更新，不影响现有模拟证据。";
        if(completionResult)
            return "完成结果发生变化，已标记完成标准和后续路由为受影响；发布前需要重新验证模拟场景。";
        if(routeOnly)return "后续路由发生变化，发布前需要重新验证模拟场景。";
        return "可执行配置发生变化，相关步骤需要复核，发布前需要重新验证模拟场景。";
    }
}
