package com.law.todo.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.law.todo.application.view.TodoDefinitionDiffView;
import com.law.todo.application.view.TodoDefinitionDiffView.Change;
import com.law.todo.application.view.TodoDefinitionDiffView.ChangeType;
import com.law.todo.application.view.TodoDefinitionDiffView.Risk;
import com.law.todo.definition.codec.LegacyDefinitionAdapter;
import com.law.todo.definition.codec.TodoDefinitionCodec;
import com.law.todo.definition.model.TodoDefinitionDocument;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoMapper;

/** Stable semantic section diff; JSON object member order is deliberately irrelevant. */
@Service
public class TodoDefinitionDiffService
{
    private static final List<String> SECTIONS=List.of("event","owner","dod","sla","ui","routing","autoActions","decisions");
    private final TodoMapper mapper;
    private final TodoDefinitionCodec codec=new TodoDefinitionCodec();
    private final LegacyDefinitionAdapter legacy=new LegacyDefinitionAdapter();

    public TodoDefinitionDiffService(TodoMapper mapper){this.mapper=mapper;}

    public TodoDefinitionDiffView diff(long leftVersionId,long rightVersionId)
    {
        TodoDefinitionDocument left=definition(require(leftVersionId));
        TodoDefinitionDocument right=definition(require(rightVersionId));
        Map<String,Object> before=sections(left),after=sections(right);
        List<Change> changes=new ArrayList<>();
        for(String section:SECTIONS)
        {
            Object oldValue=before.get(section),newValue=after.get(section);
            if(Objects.equals(oldValue,newValue))continue;
            ChangeType type=oldValue==null?ChangeType.ADDED:newValue==null?ChangeType.REMOVED:ChangeType.MODIFIED;
            changes.add(new Change(section,"$."+section,type,oldValue,newValue,risk(section),reason(section)));
        }
        changes.sort(Comparator.comparing(Change::section).thenComparing(Change::path));
        Risk overall=changes.stream().map(Change::risk).max(Comparator.comparingInt(Enum::ordinal)).orElse(Risk.NONE);
        return new TodoDefinitionDiffView(leftVersionId,rightVersionId,changes,overall);
    }

    private Map<String,Object> sections(TodoDefinitionDocument value)
    {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("event",value.event());result.put("owner",value.owner());result.put("dod",value.dod());
        result.put("sla",value.sla());result.put("ui",value.ui());result.put("routing",value.routing());
        result.put("autoActions",value.autoActions());result.put("decisions",value.decisionRefs());return result;
    }
    private Risk risk(String section){return switch(section){case "event","routing","decisions"->Risk.BLOCKING;case "owner","dod","autoActions"->Risk.HIGH;case "sla"->Risk.MEDIUM;default->Risk.LOW;};}
    private String reason(String section){return switch(section){case "event"->"Changes trigger eligibility";case "owner"->"Changes assignee resolution";case "dod"->"Changes completion contract";case "sla"->"Changes governed deadlines";case "ui"->"Changes rendered runtime form";case "routing"->"Changes executable route graph";case "autoActions"->"Changes controlled system actions";default->"Changes blocking decision dependencies";};}
    private Map<String,Object> require(long id){Map<String,Object> row=mapper.selectTemplateVersionById(id);if(row==null||row.isEmpty())throw new TodoException("TODO_TEMPLATE_VERSION_NOT_FOUND","Template version not found");return row;}
    private TodoDefinitionDocument definition(Map<String,Object> row){String json=text(value(row,"definition_json","definitionJson"));return json==null||json.isBlank()?legacy.fromLegacy(row):codec.read(json);}
    private Object value(Map<String,Object> row,String snake,String camel){return row.containsKey(snake)?row.get(snake):row.get(camel);}
    private String text(Object value){return value==null?null:String.valueOf(value);}
}
