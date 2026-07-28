package com.law.todo.application;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.law.todo.application.view.TodoSimulationScenarioViews.SimulationScenario;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoConfigurationMapper;

@Service
public class TodoSimulationScenarioCatalog
{
    private final TodoConfigurationMapper mapper;

    public TodoSimulationScenarioCatalog(TodoConfigurationMapper mapper){this.mapper=mapper;}

    @Transactional(readOnly=true)
    public List<SimulationScenario> scenarios(String templateCode)
    {return scenarios(templateCode,"LEAD");}

    @Transactional(readOnly=true)
    public List<SimulationScenario> scenarios(String templateCode,String businessType)
    {
        List<Map<String,Object>> rows=mapper.selectConfigurationResourceItems("SIMULATION_SCENARIO",businessType);
        List<SimulationScenario> result=new ArrayList<>();Set<String> codes=new HashSet<>();
        for(Map<String,Object> row:rows==null?List.<Map<String,Object>>of():rows)
        {
            JSONObject value=parse(value(row,"value_json","valueJson"));
            if(!templateCode.equals(value.getString("templateCode")))continue;
            String code=text(row,"resource_code","resourceCode");
            if(!codes.add(code))
                throw new TodoException("TODO_SIMULATION_SCENARIO_DUPLICATE",
                        "TODO_SIMULATION_SCENARIO_DUPLICATE: Duplicate active scenario "+code);
            rejectUnsupportedPlaceholder(value);
            result.add(new SimulationScenario(number(row,"resource_item_id","resourceItemId"),code,
                    value.getString("templateCode"),text(row,"resource_name","resourceName"),
                    integer(value.get("scenarioVersion")),map(value.get("completionPayload")),
                    strings(value,"editableFields"),strings(value,"requiredMaterials"),
                    value.getString("completionNodeKey"),integer(value.get("occurrence")),
                    value.getString("expectedNextTemplateCode"),
                    Boolean.TRUE.equals(value.getBoolean("requiredForPublish")),
                    text(row,"status","status"),integer(value(row,"sort_order","sortOrder"))));
        }
        return List.copyOf(result);
    }

    private void rejectUnsupportedPlaceholder(Object value)
    {
        if(value instanceof Map<?,?> map)map.values().forEach(this::rejectUnsupportedPlaceholder);
        else if(value instanceof List<?> list)list.forEach(this::rejectUnsupportedPlaceholder);
        else if(value instanceof String text&&text.contains("${")&&!"${SIMULATION_NOW}".equals(text))
            throw new TodoException("TODO_SIMULATION_SCENARIO_PLACEHOLDER_UNSUPPORTED",
                    "TODO_SIMULATION_SCENARIO_PLACEHOLDER_UNSUPPORTED: Unsupported placeholder "+text);
    }

    private JSONObject parse(Object value)
    {
        JSONObject result=value instanceof JSONObject object?object:JSON.parseObject(String.valueOf(value));
        if(result==null)throw new TodoException("TODO_SIMULATION_SCENARIO_INVALID","Scenario value is invalid");
        return result;
    }
    @SuppressWarnings("unchecked")
    private Map<String,Object> map(Object value)
    {return value instanceof Map<?,?> values?(Map<String,Object>)values:Map.of();}
    private List<String> strings(JSONObject value,String key)
    {return value.getJSONArray(key)==null?List.of():value.getJSONArray(key).toJavaList(String.class);}
    private Object value(Map<String,Object> row,String snake,String camel)
    {return row.containsKey(snake)?row.get(snake):row.get(camel);}
    private String text(Map<String,Object> row,String snake,String camel)
    {Object value=value(row,snake,camel);return value==null?null:String.valueOf(value);}
    private long number(Map<String,Object> row,String snake,String camel)
    {return Long.parseLong(String.valueOf(value(row,snake,camel)));}
    private int integer(Object value){return value==null?0:Integer.parseInt(String.valueOf(value));}
}
