package db.migration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.law.todo.definition.codec.TodoDefinitionCodec;

/**
 * Keeps the mutable TD-001 usability-repair draft aligned with the current
 * published targets after a downstream template publishes a replacement
 * version. Published TD-001 history and simulation evidence are never changed.
 */
public class V0_20_68__RepointTd001DraftToCurrentTargets extends BaseJavaMigration
{
    private static final String MARKER="V0.20.65 TD-001 配置易用性修复草稿";

    @Override
    public void migrate(Context context) throws Exception
    {
        Draft draft=loadDraft(context);
        Map<String,Long> currentTargets=loadCurrentTargets(context);
        TodoDefinitionCodec codec=new TodoDefinitionCodec();
        JSONObject root=JSONObject.parseObject(draft.definitionJson());
        JSONObject routing=required(root,"routing","config");

        JSONArray outcomes=routing.getJSONArray("businessOutcomes");
        if(outcomes==null||outcomes.size()!=3)throw new IllegalStateException(
                "The TD-001 repair draft must contain three governed business outcomes");
        int outcomeUpdates=0;
        for(Object raw:outcomes)
        {
            if(!(raw instanceof JSONObject outcome))continue;
            String templateCode=outcome.getString("targetTemplateCode");
            Long targetVersionId=currentTargets.get(templateCode);
            if(targetVersionId==null)throw new IllegalStateException(
                    "TD-001 outcome targets an unsupported template: "+templateCode);
            outcome.put("targetVersionId",targetVersionId);
            outcomeUpdates++;
        }
        if(outcomeUpdates!=3)throw new IllegalStateException(
                "All TD-001 business outcomes must be structured objects");

        JSONArray nodes=routing.getJSONArray("nodes");
        if(nodes==null)throw new IllegalStateException("The TD-001 repair route nodes are missing");
        Map<String,Integer> nodeCounts=new HashMap<>();
        for(Object raw:nodes)
        {
            if(!(raw instanceof JSONObject node))continue;
            String templateCode=node.getString("templateCode");
            Long targetVersionId=currentTargets.get(templateCode);
            if(targetVersionId==null)continue;
            node.put("templateVersionId",targetVersionId);
            nodeCounts.merge(templateCode,1,Integer::sum);
        }
        for(String templateCode:currentTargets.keySet())
        {
            if(nodeCounts.getOrDefault(templateCode,0)!=1)throw new IllegalStateException(
                    "TD-001 must contain exactly one route node for "+templateCode);
        }

        String canonical=codec.canonicalJson(codec.read(root.toJSONString()));
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        JSONObject normalized=JSONObject.parseObject(canonical);
        try(PreparedStatement update=context.getConnection().prepareStatement("""
                update todo_template_version
                set next_rule_json=cast(? as json),
                    definition_json=cast(? as json),
                    compiled_json=cast(? as json),
                    definition_hash=?,
                    validation_report_json=json_object('errors',json_array(),'warnings',json_array()),
                    update_by='migration',
                    update_time=sysdate()
                where version_id=?
                  and status=?
                  and status in ('DRAFT','BLOCKED')
                  and change_summary=?
                """))
        {
            update.setString(1,required(normalized,"routing","config").toJSONString());
            update.setString(2,canonical);
            update.setString(3,canonical);
            update.setString(4,hash);
            update.setLong(5,draft.versionId());
            update.setString(6,draft.status());
            update.setString(7,MARKER);
            if(update.executeUpdate()!=1)throw new IllegalStateException(
                    "The mutable TD-001 repair draft changed while its targets were repointed");
        }
    }

    private Draft loadDraft(Context context) throws Exception
    {
        try(PreparedStatement select=context.getConnection().prepareStatement("""
                select v.version_id,v.status,cast(v.definition_json as char)
                from todo_template t
                join todo_template_version v on v.template_id=t.template_id
                where t.template_code='TD-001'
                  and v.status in ('DRAFT','BLOCKED')
                  and v.change_summary=?
                """))
        {
            select.setString(1,MARKER);
            try(ResultSet rows=select.executeQuery())
            {
                if(!rows.next())throw new IllegalStateException(
                        "The mutable TD-001 repair draft is missing");
                Draft result=new Draft(rows.getLong(1),rows.getString(2),rows.getString(3));
                if(rows.next())throw new IllegalStateException(
                        "More than one mutable TD-001 repair draft uses the migration marker");
                return result;
            }
        }
    }

    private Map<String,Long> loadCurrentTargets(Context context) throws Exception
    {
        Map<String,Long> targets=new HashMap<>();
        try(PreparedStatement select=context.getConnection().prepareStatement("""
                select t.template_code,v.version_id
                from todo_template t
                join todo_template_version v
                  on v.template_id=t.template_id and v.version_no=t.current_version
                where t.template_code in ('TD-002','TD-003','TD-004')
                  and t.status='0'
                  and v.status='PUBLISHED'
                """);
                ResultSet rows=select.executeQuery())
        {
            while(rows.next())targets.put(rows.getString(1),rows.getLong(2));
        }
        if(targets.size()!=3)throw new IllegalStateException(
                "TD-001 requires current published TD-002, TD-003 and TD-004 targets");
        return targets;
    }

    private JSONObject required(JSONObject root,String section,String child)
    {
        JSONObject parent=root.getJSONObject(section);
        JSONObject value=parent==null?null:parent.getJSONObject(child);
        if(value==null)throw new IllegalStateException(
                "TD-001 definition is missing "+section+"."+child);
        return value;
    }

    private record Draft(long versionId,String status,String definitionJson) { }
}
