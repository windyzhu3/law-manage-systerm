package db.migration;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.law.todo.definition.codec.TodoDefinitionCodec;

/** Seeds the governed TD-003 retry-window configuration as a mutable draft only. */
public class V0_20_74__SeedTd003GuidedDraft extends BaseJavaMigration
{
    private static final String RESOURCE="/todo-definitions/v0.2/TD-003.json";
    private static final String MARKER="V0.20.74 TD-003 guided configuration draft";
    private static final String DOD_RECIPE="LEAD_RETRY_READY";

    @Override
    public void migrate(Context context) throws Exception
    {
        Source source=currentPublished(context,"TD-003");
        Source td004=currentPublished(context,"TD-004");
        JSONObject recipe=loadDodRecipe(context);
        requireNoIndependentTrigger(context);
        int versionNo=nextVersionNo(context,source.templateId());
        long draftVersionId=insertPlaceholder(context,source,versionNo);

        JSONObject definition=canonicalResource();
        applyDodRecipe(definition,recipe);
        bindVersionIdentities(definition,draftVersionId,td004.versionId());
        TodoDefinitionCodec codec=new TodoDefinitionCodec();
        String canonical=codec.canonicalJson(codec.read(definition.toJSONString()));
        String hash=sha256(canonical);
        JSONObject normalized=JSON.parseObject(canonical);
        finalizeDraft(context,draftVersionId,canonical,hash,normalized);
        upsertScenarios(context);
    }

    private Source currentPublished(Context context,String templateCode) throws Exception
    {
        try(PreparedStatement select=context.getConnection().prepareStatement("""
                select t.template_id,v.version_id
                from todo_template t
                join todo_template_version v
                  on v.template_id=t.template_id and v.version_no=t.current_version
                where t.template_code=? and t.status='0' and v.status='PUBLISHED'
                """))
        {
            select.setString(1,templateCode);
            try(ResultSet rows=select.executeQuery())
            {
                if(!rows.next())throw new IllegalStateException(
                        "The current published "+templateCode+" version is unavailable");
                Source result=new Source(rows.getLong(1),rows.getLong(2));
                if(rows.next())throw new IllegalStateException(
                        "More than one current published "+templateCode+" version was found");
                return result;
            }
        }
    }

    private JSONObject loadDodRecipe(Context context) throws Exception
    {
        try(PreparedStatement select=context.getConnection().prepareStatement("""
                select cast(value_json as char)
                from todo_configuration_resource_item
                where resource_type='DOD_RECIPE' and business_type='LEAD'
                  and resource_code=? and status='ACTIVE'
                """))
        {
            select.setString(1,DOD_RECIPE);
            try(ResultSet rows=select.executeQuery())
            {
                if(!rows.next())throw new IllegalStateException(
                        "The active "+DOD_RECIPE+" recipe is unavailable");
                JSONObject recipe;
                try
                {
                    recipe=JSON.parseObject(rows.getString(1));
                }
                catch(RuntimeException exception)
                {
                    throw invalidRecipe();
                }
                if(rows.next())throw new IllegalStateException(
                        "More than one active "+DOD_RECIPE+" recipe was found");
                validateRecipeContract(recipe);
                return recipe;
            }
        }
    }

    private void validateRecipeContract(JSONObject recipe)
    {
        if(recipe==null||recipe.size()!=4
                ||!exactStrings(recipe.getJSONArray("requiredFields"),Set.of("contactResult"))
                ||!exactStrings(recipe.getJSONArray("requiredAttachments"),Set.of("CONTACT_PROOF"))
                ||!exactStrings(recipe.getJSONArray("validatorRefs"),
                        Set.of("LeadFirstContactValidator")))throw invalidRecipe();

        JSONArray rules=recipe.getJSONArray("conditionalRules");
        if(rules==null||rules.size()!=1||!(rules.get(0) instanceof JSONObject rule)
                ||rule.size()!=2)throw invalidRecipe();
        JSONObject when=rule.getJSONObject("when");
        if(when==null||when.size()!=3
                ||!(when.get("field") instanceof String field)||!"contactResult".equals(field)
                ||!(when.get("operator") instanceof String operator)||!"EQ".equals(operator)
                ||!(when.get("value") instanceof String value)||!"CONNECTED".equals(value)
                ||!exactStrings(rule.getJSONArray("requiredFields"),
                        Set.of("name","city","demand","visited")))throw invalidRecipe();
    }

    private boolean exactStrings(JSONArray values,Set<String> expected)
    {
        if(values==null||values.size()!=expected.size())return false;
        Set<String> actual=new HashSet<>();
        for(Object raw:values)
        {
            if(!(raw instanceof String value)||value.isBlank()||!actual.add(value))return false;
        }
        return actual.equals(expected);
    }

    private IllegalStateException invalidRecipe()
    {
        return new IllegalStateException(
                DOD_RECIPE+" does not match the governed retry completion contract");
    }

    private void requireNoIndependentTrigger(Context context) throws Exception
    {
        try(PreparedStatement select=context.getConnection().prepareStatement("""
                select count(*)
                from todo_trigger_rule r
                join todo_template t on t.template_id=r.template_id
                where t.template_code='TD-003' and r.enabled='Y'
                """);ResultSet rows=select.executeQuery())
        {
            if(!rows.next())throw new IllegalStateException(
                    "TD-003 trigger invariant could not be evaluated");
            if(rows.getLong(1)!=0)throw new IllegalStateException(
                    "TD-003 must not have an enabled independent trigger");
        }
    }

    private void applyDodRecipe(JSONObject definition,JSONObject recipe)
    {
        JSONArray requiredFields=requiredArray(recipe,"requiredFields");
        JSONArray requiredAttachments=requiredArray(recipe,"requiredAttachments");
        JSONArray validatorRefs=requiredArray(recipe,"validatorRefs");
        JSONArray conditionalRules=requiredArray(recipe,"conditionalRules");
        if(!requiredFields.toJavaList(String.class).equals(java.util.List.of("contactResult"))
                ||!requiredAttachments.toJavaList(String.class).equals(
                        java.util.List.of("CONTACT_PROOF")))
            throw new IllegalStateException(DOD_RECIPE+" does not describe the governed retry proof");

        JSONObject dod=required(definition,"dod","config");
        dod.put("requiredFields",requiredFields);
        dod.put("validatorRefs",validatorRefs);

        JSONArray existingMaterials=dod.getJSONArray("materials");
        JSONArray materials=new JSONArray();
        for(String code:requiredAttachments.toJavaList(String.class))
        {
            JSONObject configured=existingMaterials==null?null:existingMaterials.stream()
                    .filter(JSONObject.class::isInstance).map(JSONObject.class::cast)
                    .filter(item->code.equals(item.getString("type"))).findFirst().orElse(null);
            JSONObject material=configured==null?new JSONObject():configured.clone();
            material.put("type",code);
            if(!material.containsKey("minCount"))material.put("minCount",1);
            materials.add(material);
        }
        dod.put("materials",materials);
        dod.put("conditionalRequired",runtimeConditionalRules(conditionalRules));
    }

    private JSONArray runtimeConditionalRules(JSONArray governed)
    {
        JSONArray result=new JSONArray();
        for(Object raw:governed)
        {
            if(!(raw instanceof JSONObject rule))throw new IllegalStateException(
                    DOD_RECIPE+" contains an invalid conditional rule");
            JSONObject when=rule.getJSONObject("when");
            JSONArray fields=rule.getJSONArray("requiredFields");
            if(when==null||fields==null||!"EQ".equals(when.getString("operator")))
                throw new IllegalStateException(DOD_RECIPE+" contains an unsupported conditional rule");
            for(String field:fields.toJavaList(String.class))
            {
                JSONObject runtimeWhen=new JSONObject();
                runtimeWhen.put("field",when.getString("field"));
                runtimeWhen.put("equals",when.get("value"));
                JSONObject runtime=new JSONObject();
                runtime.put("when",runtimeWhen);
                runtime.put("field",field);
                result.add(runtime);
            }
        }
        return result;
    }

    private int nextVersionNo(Context context,long templateId) throws Exception
    {
        try(PreparedStatement select=context.getConnection().prepareStatement("""
                select coalesce(max(version_no),0)+1
                from todo_template_version
                where template_id=?
                """))
        {
            select.setLong(1,templateId);
            try(ResultSet rows=select.executeQuery())
            {
                if(!rows.next())throw new IllegalStateException(
                        "TD-003 next draft version number could not be allocated");
                return rows.getInt(1);
            }
        }
    }

    private long insertPlaceholder(Context context,Source source,int versionNo) throws Exception
    {
        try(PreparedStatement insert=context.getConnection().prepareStatement("""
                insert into todo_template_version(
                  template_id,version_no,status,source_version_id,
                  owner_rule_json,dod_rule_json,sla_rule_json,next_rule_json,ui_schema_json,
                  change_summary,impact_scope,rollback_source_version_id,
                  definition_schema_version,definition_json,compiled_json,definition_hash,
                  validation_report_json,published_by,published_time,update_by,update_time
                )
                select template_id,?,'DRAFT',version_id,
                       owner_rule_json,dod_rule_json,sla_rule_json,next_rule_json,ui_schema_json,
                       ?,?,version_id,
                       definition_schema_version,definition_json,compiled_json,null,
                       json_object('errors',json_array(),'warnings',json_array()),
                       null,null,'migration',sysdate()
                from todo_template_version
                where version_id=? and status='PUBLISHED'
                """,Statement.RETURN_GENERATED_KEYS))
        {
            insert.setInt(1,versionNo);
            insert.setString(2,MARKER);
            insert.setString(3,"TD-003 governed retry event, owner, completion, schedule, outcomes and simulations");
            insert.setLong(4,source.versionId());
            if(insert.executeUpdate()!=1)throw new IllegalStateException(
                    "The TD-003 guided draft placeholder was not created");
            try(ResultSet keys=insert.getGeneratedKeys())
            {
                if(!keys.next())throw new IllegalStateException(
                        "The TD-003 guided draft version ID was not generated");
                return keys.getLong(1);
            }
        }
    }

    private JSONObject canonicalResource() throws Exception
    {
        try(InputStream input=getClass().getResourceAsStream(RESOURCE))
        {
            if(input==null)throw new IllegalStateException(
                    "The canonical TD-003 definition resource is missing");
            JSONObject packaged=JSON.parseObject(new String(input.readAllBytes(),StandardCharsets.UTF_8));
            JSONObject definition=packaged==null?null:packaged.getJSONObject("definition");
            if(definition==null)throw new IllegalStateException(
                    "The canonical TD-003 definition payload is missing");
            return definition;
        }
    }

    private void bindVersionIdentities(JSONObject definition,long draftVersionId,
            long publishedTd004VersionId)
    {
        JSONObject event=definition.getJSONObject("event");
        if(event==null||!"LEAD_RETRY_WINDOW_DUE".equals(event.getString("eventType")))
            throw new IllegalStateException("TD-003 must consume LEAD_RETRY_WINDOW_DUE");
        JSONObject owner=required(definition,"owner","config");
        if(!"BUSINESS_OWNER".equals(owner.getString("type"))
                ||!"LEAD".equals(owner.getString("businessType")))
            throw new IllegalStateException("TD-003 must resolve the current LEAD business owner");

        JSONObject routing=required(definition,"routing","config");
        JSONArray outcomes=routing.getJSONArray("businessOutcomes");
        if(outcomes==null||outcomes.size()!=4)throw new IllegalStateException(
                "TD-003 must contain exactly four governed business outcomes");
        int connected=0,retained=0,scheduled=0,ended=0;
        for(Object raw:outcomes)
        {
            if(!(raw instanceof JSONObject outcome))continue;
            String value=outcome.getString("value");
            String effect=outcome.getString("effectKind");
            if("CONNECTED".equals(value)&&"NEXT_TEMPLATE".equals(effect)
                    &&"TD-004".equals(outcome.getString("targetTemplateCode")))
            {
                outcome.put("targetVersionId",publishedTd004VersionId);
                connected++;
            }
            else if("CONTINUE_CURRENT_WINDOW".equals(value)&&"RETAIN_CURRENT".equals(effect))retained++;
            else if("NEXT_WINDOW".equals(value)&&"SCHEDULE_NEXT".equals(effect))scheduled++;
            else if("EXHAUSTED".equals(value)&&"END".equals(effect))ended++;
        }
        if(connected!=1||retained!=1||scheduled!=1||ended!=1)throw new IllegalStateException(
                "TD-003 governed business outcomes are incomplete");

        JSONArray nodes=routing.getJSONArray("nodes");
        if(nodes==null)throw new IllegalStateException("TD-003 route nodes are missing");
        int currentNodes=0,targetNodes=0;
        for(Object raw:nodes)
        {
            if(!(raw instanceof JSONObject node)||!"TASK".equals(node.getString("type")))continue;
            if("TD-003".equals(node.getString("templateCode")))
            {
                node.put("templateVersionId",draftVersionId);
                currentNodes++;
            }
            else if("TD-004".equals(node.getString("templateCode")))
            {
                node.put("templateVersionId",publishedTd004VersionId);
                targetNodes++;
            }
        }
        if(currentNodes!=1||targetNodes!=1)throw new IllegalStateException(
                "TD-003 route must contain one current task and one TD-004 task");

        JSONObject schedule=required(definition,"sla","config").getJSONObject("schedule");
        if(schedule==null||!"TD-003".equals(schedule.getString("targetTemplateCode")))
            throw new IllegalStateException("TD-003 retry schedule is missing");
        schedule.put("targetTemplateVersionId",draftVersionId);
    }

    private void finalizeDraft(Context context,long versionId,String canonical,String hash,
            JSONObject normalized) throws Exception
    {
        try(PreparedStatement update=context.getConnection().prepareStatement("""
                update todo_template_version
                set owner_rule_json=cast(? as json),
                    dod_rule_json=cast(? as json),
                    sla_rule_json=cast(? as json),
                    next_rule_json=cast(? as json),
                    ui_schema_json=cast(? as json),
                    definition_schema_version=?,
                    definition_json=cast(? as json),
                    compiled_json=cast(? as json),
                    definition_hash=?,
                    validation_report_json=json_object('errors',json_array(),'warnings',json_array()),
                    update_by='migration',update_time=sysdate()
                where version_id=? and status='DRAFT' and change_summary=?
                """))
        {
            update.setString(1,config(normalized,"owner"));
            update.setString(2,config(normalized,"dod"));
            update.setString(3,config(normalized,"sla"));
            update.setString(4,config(normalized,"routing"));
            update.setString(5,config(normalized,"ui"));
            update.setInt(6,normalized.getIntValue("schemaVersion"));
            update.setString(7,canonical);
            update.setString(8,canonical);
            update.setString(9,hash);
            update.setLong(10,versionId);
            update.setString(11,MARKER);
            if(update.executeUpdate()!=1)throw new IllegalStateException(
                    "The TD-003 guided draft could not be finalized");
        }
    }

    private void upsertScenarios(Context context) throws Exception
    {
        upsertScenario(context,"TD003_CONNECTED","联系成功","验证联系成功后进入五天实质进展",210,
                scenario("TD003_CONNECTED","NEXT_TEMPLATE","TD-004","CONNECTED","T0",1,true));
        upsertScenario(context,"TD003_CONTINUE_WINDOW","本窗口继续",
                "验证窗口仍有尝试次数时保留当前待办",220,
                scenario("TD003_CONTINUE_WINDOW","RETAIN_CURRENT",null,
                        "CONTINUE_CURRENT_WINDOW","T0",2,false));
        upsertScenario(context,"TD003_NEXT_WINDOW","进入下一窗口",
                "验证当前窗口完成后等待下一调度窗口",230,
                scenario("TD003_NEXT_WINDOW","SCHEDULE_NEXT",null,
                        "NEXT_WINDOW","T1_AM",1,false));
        upsertScenario(context,"TD003_EXHAUSTED","全部重试耗尽",
                "验证所有重试窗口耗尽后流程结束",240,
                scenario("TD003_EXHAUSTED","END",null,"EXHAUSTED","T2_PM",1,false));
    }

    private JSONObject scenario(String code,String effectKind,String targetTemplateCode,
            String contactResult,String attemptStage,int attemptCount,boolean connected)
    {
        JSONObject expectedEffect=new JSONObject();
        expectedEffect.put("kind",effectKind);
        if(targetTemplateCode!=null)expectedEffect.put("targetTemplateCode",targetTemplateCode);
        JSONObject payload=new JSONObject();
        payload.put("contactResult",contactResult);
        payload.put("attemptStage",attemptStage);
        payload.put("attemptCount",attemptCount);
        if(connected)
        {
            payload.put("name","张女士");
            payload.put("city","上海");
            payload.put("demand","劳动争议咨询");
            payload.put("visited","NO");
        }
        JSONObject value=new JSONObject();
        value.put("templateCode","TD-003");
        value.put("scenarioCode",code);
        value.put("scenarioVersion",1);
        value.put("completionPayload",payload);
        value.put("editableFields",JSON.parseArray("[\"contactResult\"]"));
        value.put("requiredMaterials",JSON.parseArray("[\"CONTACT_PROOF\"]"));
        value.put("completionNodeKey","td003");
        value.put("occurrence",1);
        value.put("expectedEffect",expectedEffect);
        value.put("requiredForPublish",true);
        return value;
    }

    private void upsertScenario(Context context,String code,String name,String description,
            int sortOrder,JSONObject value) throws Exception
    {
        try(PreparedStatement upsert=context.getConnection().prepareStatement("""
                insert into todo_configuration_resource_item(
                  resource_type,resource_code,resource_name,description,business_type,
                  value_json,status,sort_order,create_by
                ) values ('SIMULATION_SCENARIO',?,?,?,'LEAD',cast(? as json),'ACTIVE',?,'migration')
                on duplicate key update
                  resource_name=?,description=?,value_json=cast(? as json),status='ACTIVE',
                  sort_order=?,version=version+1,update_by='migration',update_time=sysdate()
                """))
        {
            String json=value.toJSONString();
            upsert.setString(1,code);
            upsert.setString(2,name);
            upsert.setString(3,description);
            upsert.setString(4,json);
            upsert.setInt(5,sortOrder);
            upsert.setString(6,name);
            upsert.setString(7,description);
            upsert.setString(8,json);
            upsert.setInt(9,sortOrder);
            if(upsert.executeUpdate()<1)throw new IllegalStateException(
                    "TD-003 scenario "+code+" was not stored");
        }
    }

    private JSONArray requiredArray(JSONObject root,String key)
    {
        JSONArray value=root==null?null:root.getJSONArray(key);
        if(value==null)throw new IllegalStateException(
                DOD_RECIPE+" is missing governed completion field "+key);
        return value;
    }

    private JSONObject required(JSONObject root,String section,String child)
    {
        JSONObject parent=root.getJSONObject(section);
        JSONObject value=parent==null?null:parent.getJSONObject(child);
        if(value==null)throw new IllegalStateException(
                "TD-003 definition is missing "+section+"."+child);
        return value;
    }

    private String config(JSONObject root,String section)
    {return required(root,section,"config").toJSONString();}

    private String sha256(String value) throws Exception
    {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private record Source(long templateId,long versionId) { }
}
