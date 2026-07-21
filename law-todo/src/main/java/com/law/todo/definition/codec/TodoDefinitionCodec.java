package com.law.todo.definition.codec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.law.todo.definition.model.TodoDefinitionDocument;
import com.law.todo.definition.model.TodoDefinitionDocument.AutoActionRule;
import com.law.todo.definition.model.TodoDefinitionDocument.DodRule;
import com.law.todo.definition.model.TodoDefinitionDocument.EventRule;
import com.law.todo.definition.model.TodoDefinitionDocument.OwnerRule;
import com.law.todo.definition.model.TodoDefinitionDocument.RoutingGraph;
import com.law.todo.definition.model.TodoDefinitionDocument.SlaRule;
import com.law.todo.definition.model.TodoDefinitionDocument.UiSchema;

/** Serializes definition documents into a repeatable representation suitable for hashing. */
public final class TodoDefinitionCodec
{
    public TodoDefinitionDocument read(String json)
    {
        Objects.requireNonNull(json, "json");
        JSONObject value = JSON.parseObject(json);
        JSONObject event = value.getJSONObject("event");
        return new TodoDefinitionDocument(
                value.getIntValue("schemaVersion"),
                value.getString("templateCode"),
                event == null ? null : new EventRule(event.getString("eventType"),
                        event.getIntValue("payloadVersion"), map(event.getJSONObject("condition"))),
                section(value, "owner", OwnerRule::new),
                section(value, "dod", DodRule::new),
                section(value, "sla", SlaRule::new),
                section(value, "ui", UiSchema::new),
                section(value, "routing", RoutingGraph::new),
                autoActions(value.getJSONArray("autoActions")),
                strings(value.getJSONArray("decisionRefs")),
                strings(value.getJSONArray("acceptanceRefs")));
    }

    public String canonicalJson(TodoDefinitionDocument value)
    {
        Objects.requireNonNull(value, "value");
        return JSON.toJSONString(value, JSONWriter.Feature.SortMapEntriesByKeys);
    }

    private <T> T section(JSONObject root, String name,
            java.util.function.Function<Map<String, Object>, T> constructor)
    {
        JSONObject section = root.getJSONObject(name);
        return section == null ? null : constructor.apply(map(section.getJSONObject("config")));
    }

    private List<AutoActionRule> autoActions(JSONArray values)
    {
        if (values == null || values.isEmpty()) return List.of();
        List<AutoActionRule> result = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++)
        {
            JSONObject value = values.getJSONObject(index);
            result.add(new AutoActionRule(map(value == null ? null : value.getJSONObject("config"))));
        }
        return result;
    }

    private List<String> strings(JSONArray values)
    {
        if (values == null || values.isEmpty()) return List.of();
        List<String> result = new ArrayList<>(values.size());
        for (Object value : values) result.add(value == null ? null : String.valueOf(value));
        return result;
    }

    private Map<String, Object> map(JSONObject value)
    {
        return value == null || value.isEmpty() ? Map.of() : value;
    }
}
