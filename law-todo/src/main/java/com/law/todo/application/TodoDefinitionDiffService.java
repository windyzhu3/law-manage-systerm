package com.law.todo.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.springframework.stereotype.Service;

import com.alibaba.fastjson2.JSON;
import com.law.todo.application.view.TodoDefinitionDiffView;
import com.law.todo.application.view.TodoDefinitionDiffView.Change;
import com.law.todo.application.view.TodoDefinitionDiffView.ChangeType;
import com.law.todo.application.view.TodoDefinitionDiffView.Risk;
import com.law.todo.definition.codec.LegacyDefinitionAdapter;
import com.law.todo.definition.codec.TodoDefinitionCodec;
import com.law.todo.definition.model.TodoDefinitionDocument;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoMapper;

/** Stable semantic leaf diff with business-key normalization for executable collections. */
@Service
public class TodoDefinitionDiffService
{
    private static final Set<String> KEYED_LISTS=Set.of(
            "$.routing.config.nodes","$.routing.config.edges","$.autoActions",
            "$.decisionRefs","$.acceptanceRefs");
    private final TodoMapper mapper;
    private final TodoDefinitionCodec codec=new TodoDefinitionCodec();
    private final LegacyDefinitionAdapter legacy=new LegacyDefinitionAdapter();

    public TodoDefinitionDiffService(TodoMapper mapper){this.mapper=mapper;}

    public TodoDefinitionDiffView diff(long leftVersionId,long rightVersionId)
    {
        Map<String,Object> left=require(leftVersionId),right=require(rightVersionId);
        Long leftTemplate=longValue(value(left,"template_id","templateId")),rightTemplate=longValue(value(right,"template_id","templateId"));
        if(leftTemplate==null||rightTemplate==null)throw new TodoException("TODO_TEMPLATE_VERSION_TEMPLATE_MISSING","Template version is missing its template identity");
        if(!leftTemplate.equals(rightTemplate))throw new TodoException("TODO_TEMPLATE_VERSION_DIFF_TEMPLATE_MISMATCH","Definition versions must belong to the same template");
        Object before=JSON.parse(codec.canonicalJson(definition(left)));
        Object after=JSON.parse(codec.canonicalJson(definition(right)));
        List<Change> changes=new ArrayList<>();
        compare("$",before,after,changes);
        changes.sort(Comparator.comparing(Change::path).thenComparing(change->change.type().name()));
        Risk overall=changes.stream().map(Change::risk).max(Comparator.comparingInt(Enum::ordinal)).orElse(Risk.NONE);
        return new TodoDefinitionDiffView(leftVersionId,rightVersionId,changes,overall);
    }

    private void compare(String path,Object before,Object after,List<Change> changes)
    {
        if(Objects.equals(before,after))return;
        if(before instanceof Map<?,?> left&&after instanceof Map<?,?> right)
        {
            Map<String,Object> leftMap=stringMap(left),rightMap=stringMap(right);
            Set<String> keys=new TreeSet<>();keys.addAll(leftMap.keySet());keys.addAll(rightMap.keySet());
            for(String key:keys)compare(path+"."+key,leftMap.get(key),rightMap.get(key),changes);
            return;
        }
        if(before instanceof List<?> left&&after instanceof List<?> right&&KEYED_LISTS.contains(path))
        {
            Map<String,Object> leftMap=keyed(path,left),rightMap=keyed(path,right);
            Set<String> keys=new TreeSet<>();keys.addAll(leftMap.keySet());keys.addAll(rightMap.keySet());
            for(String key:keys)compare(path+"["+key+"]",leftMap.get(key),rightMap.get(key),changes);
            return;
        }
        if(before instanceof List<?> left&&after instanceof List<?> right)
        {
            int size=Math.max(left.size(),right.size());
            for(int index=0;index<size;index++)compare(path+"["+index+"]",
                    index<left.size()?left.get(index):null,index<right.size()?right.get(index):null,changes);
            return;
        }
        ChangeType type=before==null?ChangeType.ADDED:after==null?ChangeType.REMOVED:ChangeType.MODIFIED;
        String section=section(path);changes.add(new Change(section,path,type,before,after,risk(section),reason(section)));
    }

    private Map<String,Object> keyed(String path,List<?> values)
    {
        Map<String,Object> result=new TreeMap<>();int anonymous=0;
        for(Object value:values)
        {
            String key;
            if("$.decisionRefs".equals(path)||"$.acceptanceRefs".equals(path))key=String.valueOf(value);
            else if(value instanceof Map<?,?> map)
            {
                Map<String,Object> item=stringMap(map);
                if("$.autoActions".equals(path)&&item.get("config") instanceof Map<?,?> config)
                    key=text(stringMap(config).get("ruleKey"));
                else key=text(item.get("key"));
            }
            else key=null;
            if(key==null||key.isBlank())key="#"+(anonymous++);
            while(result.containsKey(key))key=key+"#"+(anonymous++);
            result.put(key,value);
        }
        return result;
    }

    private Map<String,Object> stringMap(Map<?,?> raw)
    {
        Map<String,Object> result=new LinkedHashMap<>();raw.forEach((key,value)->result.put(String.valueOf(key),value));return result;
    }

    private String section(String path)
    {
        if(path==null||!path.startsWith("$.")||path.length()<3)return "definition";
        String rest=path.substring(2);int dot=rest.indexOf('.'),bracket=rest.indexOf('[');int end=rest.length();
        if(dot>=0)end=Math.min(end,dot);if(bracket>=0)end=Math.min(end,bracket);return rest.substring(0,end);
    }

    private Risk risk(String section){return switch(section){case "schemaVersion","templateCode","event","routing","decisionRefs","acceptanceRefs"->Risk.BLOCKING;case "owner","dod","autoActions"->Risk.HIGH;case "sla"->Risk.MEDIUM;default->Risk.LOW;};}
    private String reason(String section){return switch(section){case "schemaVersion"->"Changes the definition contract version";case "templateCode"->"Changes the stable definition identity";case "event"->"Changes trigger eligibility";case "owner"->"Changes assignee resolution";case "dod"->"Changes completion contract";case "sla"->"Changes governed deadlines";case "ui"->"Changes rendered runtime form";case "routing"->"Changes executable route graph";case "autoActions"->"Changes controlled system actions";case "acceptanceRefs"->"Changes acceptance dependencies";default->"Changes blocking decision dependencies";};}
    private Map<String,Object> require(long id){Map<String,Object> row=mapper.selectTemplateVersionById(id);if(row==null||row.isEmpty())throw new TodoException("TODO_TEMPLATE_VERSION_NOT_FOUND","Template version not found");return row;}
    private TodoDefinitionDocument definition(Map<String,Object> row){String json=text(value(row,"definition_json","definitionJson"));return json==null||json.isBlank()?legacy.fromLegacy(row):codec.read(json);}
    private Object value(Map<String,Object> row,String snake,String camel){return row.containsKey(snake)?row.get(snake):row.get(camel);}
    private Long longValue(Object value){try{return value==null?null:Long.valueOf(String.valueOf(value));}catch(NumberFormatException invalid){return null;}}
    private String text(Object value){return value==null?null:String.valueOf(value);}
}
