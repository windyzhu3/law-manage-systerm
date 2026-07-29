package db.migration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HexFormat;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.law.todo.definition.codec.TodoDefinitionCodec;

/**
 * V0.20.59 published TD-003's contacted-at contract by copying the previous
 * immutable definition, but its recursive retry target still referenced that
 * previous version. Publish a new immutable version whose self references,
 * policy pointer and catalog snapshot all agree.
 */
public class V0_20_67__PublishCanonicalTd003SelfReference extends BaseJavaMigration
{
    @Override
    public void migrate(Context context) throws Exception
    {
        Source source=source(context);
        TodoDefinitionCodec codec=new TodoDefinitionCodec();
        JSONObject root=JSONObject.parseObject(source.definitionJson());
        JSONObject schedule=required(root,"sla","config").getJSONObject("schedule");
        if(schedule==null)throw new IllegalStateException("TD-003 schedule configuration is missing");
        long existingTarget=schedule.getLongValue("targetTemplateVersionId");
        if(existingTarget==source.versionId())return;

        long newVersionId=insertPlaceholder(context,source);
        schedule.put("targetTemplateVersionId",newVersionId);
        JSONArray nodes=required(root,"routing","config").getJSONArray("nodes");
        if(nodes==null)throw new IllegalStateException("TD-003 route nodes are missing");
        int selfReferences=0;
        for(Object raw:nodes)
        {
            if(!(raw instanceof JSONObject node))continue;
            if("TASK".equals(node.getString("type"))
                    &&"TD-003".equals(node.getString("templateCode")))
            {
                node.put("templateVersionId",newVersionId);
                selfReferences++;
            }
        }
        if(selfReferences!=1)throw new IllegalStateException(
                "TD-003 must contain exactly one self task node");

        String canonical=codec.canonicalJson(codec.read(root.toJSONString()));
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        JSONObject normalized=JSONObject.parseObject(canonical);
        updateNewVersion(context,newVersionId,canonical,hash,normalized);
        updatePointers(context,source.templateId(),source.versionNo()+1,
                source.versionId(),newVersionId,canonical);
    }

    private Source source(Context context) throws Exception
    {
        try(PreparedStatement select=context.getConnection().prepareStatement("""
                select t.template_id,v.version_id,v.version_no,cast(v.definition_json as char),
                       cast(v.owner_rule_json as char),cast(v.dod_rule_json as char),
                       cast(v.sla_rule_json as char),cast(v.next_rule_json as char),
                       cast(v.ui_schema_json as char),v.definition_schema_version
                from todo_template t
                join todo_template_version v
                  on v.template_id=t.template_id and v.version_no=t.current_version
                where t.template_code='TD-003' and v.status='PUBLISHED'
                """);
                ResultSet rows=select.executeQuery())
        {
            if(!rows.next())throw new IllegalStateException(
                    "The current published TD-003 definition is missing");
            Source result=new Source(rows.getLong(1),rows.getLong(2),rows.getInt(3),
                    rows.getString(4),rows.getString(5),rows.getString(6),rows.getString(7),
                    rows.getString(8),rows.getString(9),rows.getInt(10));
            if(rows.next())throw new IllegalStateException(
                    "More than one current TD-003 definition was found");
            return result;
        }
    }

    private long insertPlaceholder(Context context,Source source) throws Exception
    {
        try(PreparedStatement insert=context.getConnection().prepareStatement("""
                insert into todo_template_version(
                  template_id,version_no,status,source_version_id,
                  owner_rule_json,dod_rule_json,sla_rule_json,next_rule_json,ui_schema_json,
                  change_summary,impact_scope,rollback_source_version_id,
                  definition_schema_version,definition_json,compiled_json,definition_hash,
                  validation_report_json,published_by,published_time,update_by,update_time
                ) values (
                  ?,?,'PUBLISHED',?,
                  cast(? as json),cast(? as json),cast(? as json),cast(? as json),cast(? as json),
                  '修复重试待办自引用版本','TD-003 调度与路由自引用',?,
                  ?,cast(? as json),cast(? as json),null,
                  json_object('errors',json_array(),'warnings',json_array()),
                  'migration',sysdate(),'migration',sysdate()
                )
                """,Statement.RETURN_GENERATED_KEYS))
        {
            insert.setLong(1,source.templateId());
            insert.setInt(2,source.versionNo()+1);
            insert.setLong(3,source.versionId());
            insert.setString(4,source.ownerRuleJson());
            insert.setString(5,source.dodRuleJson());
            insert.setString(6,source.slaRuleJson());
            insert.setString(7,source.nextRuleJson());
            insert.setString(8,source.uiSchemaJson());
            insert.setLong(9,source.versionId());
            insert.setInt(10,source.definitionSchemaVersion());
            insert.setString(11,source.definitionJson());
            insert.setString(12,source.definitionJson());
            if(insert.executeUpdate()!=1)throw new IllegalStateException(
                    "TD-003 canonical self-reference version was not created");
            try(ResultSet keys=insert.getGeneratedKeys())
            {
                if(!keys.next())throw new IllegalStateException(
                        "TD-003 canonical self-reference version ID was not generated");
                return keys.getLong(1);
            }
        }
    }

    private void updateNewVersion(Context context,long versionId,String canonical,String hash,
            JSONObject normalized) throws Exception
    {
        try(PreparedStatement update=context.getConnection().prepareStatement("""
                update todo_template_version
                set owner_rule_json=cast(? as json),
                    dod_rule_json=cast(? as json),
                    sla_rule_json=cast(? as json),
                    next_rule_json=cast(? as json),
                    ui_schema_json=cast(? as json),
                    definition_json=cast(? as json),
                    compiled_json=cast(? as json),
                    definition_hash=?,
                    update_time=sysdate()
                where version_id=? and status='PUBLISHED'
                """))
        {
            update.setString(1,config(normalized,"owner"));
            update.setString(2,config(normalized,"dod"));
            update.setString(3,config(normalized,"sla"));
            update.setString(4,config(normalized,"routing"));
            update.setString(5,config(normalized,"ui"));
            update.setString(6,canonical);
            update.setString(7,canonical);
            update.setString(8,hash);
            update.setLong(9,versionId);
            if(update.executeUpdate()!=1)throw new IllegalStateException(
                    "TD-003 canonical self-reference version could not be finalized");
        }
    }

    private void updatePointers(Context context,long templateId,int versionNo,
            long previousVersionId,long versionId,String canonical) throws Exception
    {
        try(PreparedStatement template=context.getConnection().prepareStatement("""
                update todo_template
                set current_version=?,update_by='migration',update_time=sysdate()
                where template_id=?
                """);
                PreparedStatement policies=context.getConnection().prepareStatement("""
                update biz_lead_assignment_policy
                set retry_rule_json=json_set(retry_rule_json,'$.templateVersionId',?),
                    row_version=row_version+1,update_by='migration',update_time=sysdate()
                where status='ACTIVE'
                  and cast(json_unquote(json_extract(
                        retry_rule_json,'$.templateVersionId')) as unsigned)=?
                """);
                PreparedStatement catalog=context.getConnection().prepareStatement("""
                update todo_prd_definition_catalog
                set definition_json=cast(? as json)
                where template_code='TD-003'
                """))
        {
            template.setInt(1,versionNo);
            template.setLong(2,templateId);
            if(template.executeUpdate()!=1)throw new IllegalStateException(
                    "TD-003 current version pointer could not be advanced");
            policies.setLong(1,versionId);
            policies.setLong(2,previousVersionId);
            policies.executeUpdate();
            catalog.setString(1,canonical);
            if(catalog.executeUpdate()!=1)throw new IllegalStateException(
                    "TD-003 PRD catalog snapshot could not be advanced");
        }
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
    {
        return required(root,section,"config").toJSONString();
    }

    private record Source(long templateId,long versionId,int versionNo,String definitionJson,
            String ownerRuleJson,String dodRuleJson,String slaRuleJson,String nextRuleJson,
            String uiSchemaJson,int definitionSchemaVersion) { }
}
