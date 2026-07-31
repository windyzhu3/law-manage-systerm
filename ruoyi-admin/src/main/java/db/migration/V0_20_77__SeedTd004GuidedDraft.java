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
import com.law.todo.spi.TodoCompletionHandler;

/** Seeds the governed recurring TD-004 configuration as a mutable draft only. */
public class V0_20_77__SeedTd004GuidedDraft extends BaseJavaMigration
{
    private static final String RESOURCE="/todo-definitions/v0.2/TD-004.json";
    private static final String MARKER="V0.20.77 TD-004 guided configuration draft";
    private static final String DOD_RECIPE="LEAD_PROGRESS_READY";
    private static final String HANDLER_CLASS=
            "com.ruoyi.system.service.event.LeadProgressHandoffTodoHandler";

    @Override
    public void migrate(Context context) throws Exception
    {
        Source source=currentPublished(context);
        JSONObject recipe=loadDodRecipe(context);
        requireNoIndependentTrigger(context);
        int versionNo=nextVersionNo(context,source.templateId());
        long draftVersionId=insertPlaceholder(context,source,versionNo);

        JSONObject definition=canonicalDefinition();
        applyDodRecipe(definition,recipe);
        bindVersionIdentity(definition,draftVersionId);
        TodoDefinitionCodec codec=new TodoDefinitionCodec();
        String canonical=codec.canonicalJson(codec.read(definition.toJSONString()));
        String hash=sha256(canonical);
        JSONObject normalized=JSON.parseObject(canonical);
        finalizeDraft(context,draftVersionId,canonical,hash,normalized);
        upsertScenarios(context);
    }

    private Source currentPublished(Context context) throws Exception
    {
        try(PreparedStatement select=context.getConnection().prepareStatement("""
                select t.template_id,v.version_id
                from todo_template t
                join todo_template_version v
                  on v.template_id=t.template_id and v.version_no=t.current_version
                where t.template_code='TD-004' and t.status='0' and v.status='PUBLISHED'
                """))
        {
            try(ResultSet rows=select.executeQuery())
            {
                if(!rows.next())throw new IllegalStateException(
                        "The current published TD-004 version is unavailable");
                Source source=new Source(rows.getLong(1),rows.getLong(2));
                if(rows.next())throw new IllegalStateException(
                        "More than one current published TD-004 version was found");
                return source;
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
                catch(RuntimeException invalid)
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
                ||!exactStrings(recipe.getJSONArray("requiredFields"),
                        Set.of("progressType","progressAt"))
                ||!exactStrings(recipe.getJSONArray("requiredAttachments"),
                        Set.of("FOLLOWUP_PROOF"))
                ||!exactStrings(recipe.getJSONArray("validatorRefs"),Set.of())
                ||!exactArray(recipe.getJSONArray("conditionalRules")))throw invalidRecipe();
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

    private boolean exactArray(JSONArray values)
    {return values!=null&&values.isEmpty();}

    private IllegalStateException invalidRecipe()
    {
        return new IllegalStateException(
                DOD_RECIPE+" does not match the governed progress completion contract");
    }

    private void requireNoIndependentTrigger(Context context) throws Exception
    {
        try(PreparedStatement select=context.getConnection().prepareStatement("""
                select count(*)
                from todo_trigger_rule r
                join todo_template t on t.template_id=r.template_id
                where t.template_code='TD-004' and r.enabled='Y'
                """);ResultSet rows=select.executeQuery())
        {
            if(!rows.next())throw new IllegalStateException(
                    "TD-004 trigger invariant could not be evaluated");
            if(rows.getLong(1)!=0)throw new IllegalStateException(
                    "TD-004 must not have an enabled independent trigger");
        }
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
                        "TD-004 next draft version number could not be allocated");
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
            insert.setString(3,"TD-004 governed event, owner, completion proof, recurring outcome and simulations");
            insert.setLong(4,source.versionId());
            if(insert.executeUpdate()!=1)throw new IllegalStateException(
                    "The TD-004 guided draft placeholder was not created");
            try(ResultSet keys=insert.getGeneratedKeys())
            {
                if(!keys.next())throw new IllegalStateException(
                        "The TD-004 guided draft version ID was not generated");
                return keys.getLong(1);
            }
        }
    }

    private JSONObject canonicalDefinition() throws Exception
    {
        JSONObject packaged;
        try(InputStream input=getClass().getResourceAsStream(RESOURCE))
        {
            if(input==null)throw new IllegalStateException(
                    "The canonical TD-004 definition resource is missing");
            packaged=JSON.parseObject(new String(input.readAllBytes(),StandardCharsets.UTF_8));
        }
        if(packaged==null)throw new IllegalStateException(
                "The canonical TD-004 definition package is invalid");
        requireRuntimeCapability(packaged);
        JSONObject definition=packaged.getJSONObject("definition");
        if(definition==null)throw new IllegalStateException(
                "The canonical TD-004 definition payload is missing");
        return definition;
    }

    private void requireRuntimeCapability(JSONObject packaged)
    {
        JSONObject capability=packaged.getJSONObject("handlerCapability");
        if(capability==null||!"TD-004_COMPLETE".equals(capability.getString("requiredCode"))
                ||!"PRESENT".equals(capability.getString("repositoryStatus")))
            throw new IllegalStateException("The TD-004 completion capability is not declared executable");
        try
        {
            Class<?> handler=Class.forName(HANDLER_CLASS);
            if(!TodoCompletionHandler.class.isAssignableFrom(handler)
                    ||handler.getMethod("catalogCode").getDeclaringClass()==TodoCompletionHandler.class)
                throw new IllegalStateException("The TD-004 completion handler is not registered");
        }
        catch(ReflectiveOperationException missing)
        {
            throw new IllegalStateException("The TD-004 completion handler is not registered",missing);
        }
    }

    private void applyDodRecipe(JSONObject definition,JSONObject recipe)
    {
        JSONArray requiredFields=requiredArray(recipe,"requiredFields");
        JSONArray requiredAttachments=requiredArray(recipe,"requiredAttachments");
        JSONArray validatorRefs=requiredArray(recipe,"validatorRefs");
        JSONArray conditionalRules=requiredArray(recipe,"conditionalRules");
        JSONObject dod=required(definition,"dod","config");
        dod.put("requiredFields",requiredFields);
        dod.put("validatorRefs",validatorRefs);
        dod.put("conditionalRequired",conditionalRules);

        JSONArray configuredMaterials=dod.getJSONArray("materials");
        JSONArray materials=new JSONArray();
        for(String code:requiredAttachments.toJavaList(String.class))
        {
            JSONObject configured=configuredMaterials==null?null:configuredMaterials.stream()
                    .filter(JSONObject.class::isInstance).map(JSONObject.class::cast)
                    .filter(item->code.equals(item.getString("type"))).findFirst().orElse(null);
            JSONObject material=configured==null?new JSONObject():configured.clone();
            material.put("type",code);
            if(!material.containsKey("minCount"))material.put("minCount",1);
            materials.add(material);
        }
        dod.put("materials",materials);
    }

    private void bindVersionIdentity(JSONObject definition,long draftVersionId)
    {
        JSONObject event=definition.getJSONObject("event");
        if(event==null||!"LEAD_FIRST_CONTACT_VALID".equals(event.getString("eventType")))
            throw new IllegalStateException("TD-004 must consume LEAD_FIRST_CONTACT_VALID");
        JSONObject owner=required(definition,"owner","config");
        if(!"BUSINESS_OWNER".equals(owner.getString("type"))
                ||!"LEAD".equals(owner.getString("businessType")))
            throw new IllegalStateException("TD-004 must resolve the current LEAD business owner");

        JSONObject routing=required(definition,"routing","config");
        JSONArray outcomes=routing.getJSONArray("businessOutcomes");
        if(outcomes==null||outcomes.size()!=1||!(outcomes.get(0) instanceof JSONObject outcome)
                ||!Set.of("field","value","label","effectKind","targetTemplateCode","businessAction")
                        .equals(outcome.keySet())
                ||!"result".equals(outcome.getString("field"))
                ||!"PROGRESS_RECORDED".equals(outcome.getString("value"))
                ||!"已记录实质进展".equals(outcome.getString("label"))
                ||!"SCHEDULE_SELF".equals(outcome.getString("effectKind"))
                ||!"TD-004".equals(outcome.getString("targetTemplateCode"))
                ||!"REFRESH_FIVE_DAY_WINDOW".equals(outcome.getString("businessAction")))
            throw new IllegalStateException("TD-004 governed recurring outcome is invalid");
        outcome.put("targetVersionId",draftVersionId);

        JSONArray nodes=routing.getJSONArray("nodes");
        if(nodes==null||nodes.size()!=2)throw new IllegalStateException(
                "TD-004 route must contain only the current task and terminal node");
        int currentTasks=0,terminalNodes=0;
        for(Object raw:nodes)
        {
            if(!(raw instanceof JSONObject node))continue;
            if("TASK".equals(node.getString("type"))&&"td004".equals(node.getString("key"))
                    &&"TD-004".equals(node.getString("templateCode")))
            {
                node.put("templateVersionId",draftVersionId);
                currentTasks++;
            }
            else if("END".equals(node.getString("type"))&&"end".equals(node.getString("key")))
                terminalNodes++;
        }
        JSONArray edges=routing.getJSONArray("edges");
        if(currentTasks!=1||terminalNodes!=1||edges==null||edges.size()!=1
                ||!(edges.get(0) instanceof JSONObject edge)
                ||!"td004".equals(edge.getString("from"))||!"end".equals(edge.getString("to")))
            throw new IllegalStateException("TD-004 ordinary routing must terminate without a self-loop");
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
                    "The TD-004 guided draft could not be finalized");
        }
    }

    private void upsertScenarios(Context context) throws Exception
    {
        upsertScenario(context,"TD004_PROGRESS_RECORDED","记录实质进展",
                "验证登记实质进展后刷新五天跟进窗口",310,
                progressScenario("TD004_PROGRESS_RECORDED","PHONE","2026-07-31T10:00:00",
                        "完成有效沟通",false));
        upsertScenario(context,"TD004_IDEMPOTENT_REPLAY","幂等重放",
                "验证重复完成请求仍只刷新一次五天跟进窗口",320,
                progressScenario("TD004_IDEMPOTENT_REPLAY","WECHAT","2026-07-31T11:00:00",
                        null,true));
        upsertScenario(context,"TD004_PROOF_REQUIRED","缺少进展凭证",
                "验证未提交跟进凭证时按预期拒绝完成",330,missingProofScenario());
    }

    private JSONObject progressScenario(String code,String progressType,String progressAt,
            String remark,boolean replay)
    {
        JSONObject payload=new JSONObject();
        payload.put("progressType",progressType);
        payload.put("progressAt",progressAt);
        if(remark!=null)payload.put("remark",remark);
        JSONObject value=baseScenario(code,payload,JSON.parseArray("[\"FOLLOWUP_PROOF\"]"));
        JSONObject effect=new JSONObject();
        effect.put("kind","SCHEDULE_SELF");
        effect.put("targetTemplateCode","TD-004");
        value.put("expectedEffect",effect);
        if(replay)value.put("replay",true);
        return value;
    }

    private JSONObject missingProofScenario()
    {
        JSONObject payload=new JSONObject();
        payload.put("progressType","PHONE");
        payload.put("progressAt","2026-07-31T10:00:00");
        JSONObject value=baseScenario("TD004_PROOF_REQUIRED",payload,new JSONArray());
        JSONObject effect=new JSONObject();
        effect.put("kind","EXPECTED_VALIDATION_FAILURE");
        value.put("expectedEffect",effect);
        value.put("expectedErrorCode","TODO_DOD_ATTACHMENT_MISSING");
        return value;
    }

    private JSONObject baseScenario(String code,JSONObject payload,JSONArray materials)
    {
        JSONObject value=new JSONObject();
        value.put("templateCode","TD-004");
        value.put("scenarioCode",code);
        value.put("scenarioVersion",1);
        value.put("completionPayload",payload);
        value.put("editableFields",JSON.parseArray("[\"progressType\",\"progressAt\",\"remark\"]"));
        value.put("requiredMaterials",materials);
        value.put("completionNodeKey","td004");
        value.put("occurrence",1);
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
                    "TD-004 scenario "+code+" was not stored");
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
                "TD-004 definition is missing "+section+"."+child);
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
