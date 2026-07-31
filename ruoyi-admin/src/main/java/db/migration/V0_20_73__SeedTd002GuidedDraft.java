package db.migration;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HexFormat;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.law.todo.definition.codec.TodoDefinitionCodec;

/** Seeds the governed TD-002 configuration as a mutable draft only. */
public class V0_20_73__SeedTd002GuidedDraft extends BaseJavaMigration
{
    private static final String RESOURCE="/todo-definitions/v0.2/TD-002.json";
    private static final String MARKER="V0.20.73 TD-002 guided configuration draft";
    private static final String DOD_RECIPE="LEAD_INVALID_REVIEW_READY";

    @Override
    public void migrate(Context context) throws Exception
    {
        Source source=currentPublished(context,"TD-002");
        Source td001=currentPublished(context,"TD-001");
        int versionNo=nextVersionNo(context,source.templateId());
        long draftVersionId=insertPlaceholder(context,source,versionNo);

        JSONObject definition=canonicalResource();
        applyDodRecipe(definition,loadDodRecipe(context));
        bindVersionIdentities(definition,draftVersionId,td001.versionId());
        TodoDefinitionCodec codec=new TodoDefinitionCodec();
        String canonical=codec.canonicalJson(codec.read(definition.toJSONString()));
        String hash=sha256(canonical);
        JSONObject normalized=JSON.parseObject(canonical);
        finalizeDraft(context,draftVersionId,canonical,hash,normalized);
        upsertScenarios(context);
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
                JSONObject recipe=JSON.parseObject(rows.getString(1));
                if(rows.next())throw new IllegalStateException(
                        "More than one active "+DOD_RECIPE+" recipe was found");
                return recipe;
            }
        }
    }

    private void applyDodRecipe(JSONObject definition,JSONObject recipe)
    {
        JSONObject dod=required(definition,"dod","config");
        JSONArray requiredFields=recipe.getJSONArray("requiredFields");
        JSONArray requiredAttachments=recipe.getJSONArray("requiredAttachments");
        JSONArray validatorRefs=recipe.getJSONArray("validatorRefs");
        JSONArray conditionalRules=recipe.getJSONArray("conditionalRules");
        if(requiredFields==null||requiredAttachments==null||validatorRefs==null
                ||conditionalRules==null)throw new IllegalStateException(
                        DOD_RECIPE+" is missing governed completion fields");
        dod.put("requiredFields",requiredFields);
        dod.put("materials",requiredAttachments);
        dod.put("validatorRefs",validatorRefs);
        dod.put("conditionalRequired",conditionalRules);
    }

    private Source currentPublished(Context context,String templateCode) throws Exception
    {
        String sql="TD-001".equals(templateCode)?"""
                select t.template_id,v.version_id
                from todo_template t
                join todo_template_version v
                  on v.template_id=t.template_id and v.version_no=t.current_version
                where t.template_code='TD-001' and t.status='0' and v.status='PUBLISHED'
                """:"""
                select t.template_id,v.version_id
                from todo_template t
                join todo_template_version v
                  on v.template_id=t.template_id and v.version_no=t.current_version
                where t.template_code='TD-002' and t.status='0' and v.status='PUBLISHED'
                """;
        try(PreparedStatement select=context.getConnection().prepareStatement(sql);
                ResultSet rows=select.executeQuery())
        {
            if(!rows.next())throw new IllegalStateException(
                    "The current published "+templateCode+" version is unavailable");
            Source result=new Source(rows.getLong(1),rows.getLong(2));
            if(rows.next())throw new IllegalStateException(
                    "More than one current published "+templateCode+" version was found");
            return result;
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
                        "TD-002 next draft version number could not be allocated");
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
            insert.setString(3,"TD-002 governed event, owner, completion, SLA, outcomes and simulations");
            insert.setLong(4,source.versionId());
            if(insert.executeUpdate()!=1)throw new IllegalStateException(
                    "The TD-002 guided draft placeholder was not created");
            try(ResultSet keys=insert.getGeneratedKeys())
            {
                if(!keys.next())throw new IllegalStateException(
                        "The TD-002 guided draft version ID was not generated");
                return keys.getLong(1);
            }
        }
    }

    private JSONObject canonicalResource() throws Exception
    {
        try(InputStream input=getClass().getResourceAsStream(RESOURCE))
        {
            if(input==null)throw new IllegalStateException(
                    "The canonical TD-002 definition resource is missing");
            JSONObject packaged=JSON.parseObject(new String(input.readAllBytes(),StandardCharsets.UTF_8));
            JSONObject definition=packaged==null?null:packaged.getJSONObject("definition");
            if(definition==null)throw new IllegalStateException(
                    "The canonical TD-002 definition payload is missing");
            return definition;
        }
    }

    private void bindVersionIdentities(JSONObject definition,long draftVersionId,
            long publishedTd001VersionId)
    {
        JSONObject routing=required(definition,"routing","config");
        JSONArray outcomes=routing.getJSONArray("businessOutcomes");
        if(outcomes==null||outcomes.size()!=2)throw new IllegalStateException(
                "TD-002 must contain exactly two governed business outcomes");
        int terminal=0;
        int reopened=0;
        for(Object raw:outcomes)
        {
            if(!(raw instanceof JSONObject outcome))continue;
            if("TRUE_INVALID".equals(outcome.getString("value"))
                    &&"END".equals(outcome.getString("effectKind")))terminal++;
            if("MISJUDGED_VALID".equals(outcome.getString("value"))
                    &&"NEXT_TEMPLATE".equals(outcome.getString("effectKind"))
                    &&"TD-001".equals(outcome.getString("targetTemplateCode")))
            {
                outcome.put("targetVersionId",publishedTd001VersionId);
                reopened++;
            }
        }
        if(terminal!=1||reopened!=1)throw new IllegalStateException(
                "TD-002 governed business outcomes are incomplete");

        JSONArray nodes=routing.getJSONArray("nodes");
        if(nodes==null)throw new IllegalStateException("TD-002 route nodes are missing");
        int currentNodes=0;
        int reopenedNodes=0;
        for(Object raw:nodes)
        {
            if(!(raw instanceof JSONObject node)||!"TASK".equals(node.getString("type")))continue;
            if("TD-002".equals(node.getString("templateCode")))
            {
                node.put("templateVersionId",draftVersionId);
                currentNodes++;
            }
            else if("TD-001".equals(node.getString("templateCode")))
            {
                node.put("templateVersionId",publishedTd001VersionId);
                reopenedNodes++;
            }
        }
        if(currentNodes!=1||reopenedNodes!=1)throw new IllegalStateException(
                "TD-002 route must contain one current task and one TD-001 reopen task");
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
                    "The TD-002 guided draft could not be finalized");
        }
    }

    private void upsertScenarios(Context context) throws Exception
    {
        upsertScenario(context,"TD002_TRUE_INVALID","确认无效","验证确认无效后流程终止",110,
                scenario("TD002_TRUE_INVALID","END",null,"TRUE_INVALID","确认无效",false));
        upsertScenario(context,"TD002_MISJUDGED_VALID","误判有效",
                "验证误判有效后重新进入首联待办",120,
                scenario("TD002_MISJUDGED_VALID","NEXT_TEMPLATE","TD-001",
                        "MISJUDGED_VALID","复核为误判",false));
        upsertScenario(context,"TD002_OVERDUE_DEFAULT","超时默认确认",
                "验证二十四小时到期后系统按确认无效完成",130,
                scenario("TD002_OVERDUE_DEFAULT","END",null,"TRUE_INVALID",
                        "系统超时默认确认",true));
    }

    private JSONObject scenario(String code,String effectKind,String targetTemplateCode,
            String reviewResult,String reviewOpinion,boolean automatic)
    {
        JSONObject expectedEffect=new JSONObject();
        expectedEffect.put("kind",effectKind);
        if(targetTemplateCode!=null)expectedEffect.put("targetTemplateCode",targetTemplateCode);
        JSONObject payload=new JSONObject();
        payload.put("reviewResult",reviewResult);
        payload.put("reviewOpinion",reviewOpinion);
        JSONObject value=new JSONObject();
        value.put("templateCode","TD-002");
        value.put("scenarioCode",code);
        value.put("scenarioVersion",1);
        value.put("completionPayload",payload);
        value.put("editableFields",new JSONArray());
        value.put("requiredMaterials",new JSONArray());
        value.put("completionNodeKey","td002");
        value.put("occurrence",1);
        value.put("expectedEffect",expectedEffect);
        value.put("requiredForPublish",true);
        if(automatic)value.put("automatic",true);
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
                    "TD-002 scenario "+code+" was not stored");
        }
    }

    private JSONObject required(JSONObject root,String section,String child)
    {
        JSONObject parent=root.getJSONObject(section);
        JSONObject value=parent==null?null:parent.getJSONObject(child);
        if(value==null)throw new IllegalStateException(
                "TD-002 definition is missing "+section+"."+child);
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
