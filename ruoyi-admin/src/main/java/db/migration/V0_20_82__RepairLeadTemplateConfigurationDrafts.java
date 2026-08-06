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

/** Retires incompatible mutable lead drafts and creates governed repair drafts without activating them. */
public class V0_20_82__RepairLeadTemplateConfigurationDrafts extends BaseJavaMigration
{
    private static final String TD001_MARKER="V0.20.82 TD-001 routing governance draft";
    private static final String TD002_MARKER="V0.20.82 TD-002 event governance draft";

    @Override
    public void migrate(Context context) throws Exception
    {
        Source td001=currentPublished(context,"TD-001");
        Source td002=currentPublished(context,"TD-002");
        Source td003=currentPublished(context,"TD-003");
        Source td004=currentPublished(context,"TD-004");

        retireMutableDrafts(context,td001.templateId(),TD001_MARKER);
        retireMutableDrafts(context,td002.templateId(),TD002_MARKER);

        createTd001Draft(context,td001,td002.versionId(),td003.versionId(),td004.versionId());
        createTd002Draft(context,td002,td001.versionId());
    }

    private void createTd001Draft(Context context,Source source,long td002VersionId,
            long td003VersionId,long td004VersionId) throws Exception
    {
        if(markerExists(context,source.templateId(),TD001_MARKER))return;
        int versionNo=nextVersionNo(context,source.templateId());
        long draftVersionId=insertPlaceholder(context,source,versionNo,TD001_MARKER,
                "修正首联即时路由、重试计划发布依赖和历史版本显示");
        JSONObject definition=definition("TD-001");
        bindTd001(definition,draftVersionId,td002VersionId,td003VersionId,td004VersionId);
        finalizeDraft(context,draftVersionId,TD001_MARKER,definition);
    }

    private void createTd002Draft(Context context,Source source,long td001VersionId) throws Exception
    {
        if(markerExists(context,source.templateId(),TD002_MARKER))return;
        int versionNo=nextVersionNo(context,source.templateId());
        long draftVersionId=insertPlaceholder(context,source,versionNo,TD002_MARKER,
                "锁定疑似无效复核事件、负责人来源和恢复首联路由");
        JSONObject definition=definition("TD-002");
        bindTd002(definition,draftVersionId,td001VersionId);
        finalizeDraft(context,draftVersionId,TD002_MARKER,definition);
    }

    static void bindTd001(JSONObject definition,long draftVersionId,long td002VersionId,
            long td003VersionId,long td004VersionId)
    {
        JSONObject event=requiredObject(definition,"event");
        event.put("eventType","LEAD_ASSIGNED");event.put("payloadVersion",1);
        JSONObject routing=requiredConfig(definition,"routing");
        JSONArray outcomes=requiredArray(routing,"businessOutcomes");
        int valid=0,suspect=0,unreachable=0;
        for(Object raw:outcomes)
        {
            if(!(raw instanceof JSONObject outcome))continue;
            switch(String.valueOf(outcome.getString("value")))
            {
                case "VALID" ->
                {
                    next(outcome,"TD-004",td004VersionId);valid++;
                }
                case "SUSPECT_INVALID" ->
                {
                    next(outcome,"TD-002",td002VersionId);suspect++;
                }
                case "UNREACHABLE" ->
                {
                    outcome.put("effectKind","END");
                    outcome.remove("targetTemplateCode");outcome.remove("targetVersionId");
                    unreachable++;
                }
                default -> { }
            }
        }
        if(valid!=1||suspect!=1||unreachable!=1)throw new IllegalStateException(
                "TD-001 must contain VALID, SUSPECT_INVALID and UNREACHABLE outcomes");
        JSONObject dependencies=new JSONObject();dependencies.put("TD-003",td003VersionId);
        routing.put("releaseDependencies",dependencies);
        bindTaskNodes(routing,"TD-001",draftVersionId,"TD-002",td002VersionId,
                "TD-004",td004VersionId);
    }

    static void bindTd002(JSONObject definition,long draftVersionId,long td001VersionId)
    {
        JSONObject event=requiredObject(definition,"event");
        event.put("eventType","LEAD_SUSPECT_INVALID_MARKED");event.put("payloadVersion",1);
        JSONObject routing=requiredConfig(definition,"routing");
        JSONArray outcomes=requiredArray(routing,"businessOutcomes");
        int terminal=0,reopened=0;
        for(Object raw:outcomes)
        {
            if(!(raw instanceof JSONObject outcome))continue;
            if("TRUE_INVALID".equals(outcome.getString("value")))
            {
                outcome.put("effectKind","END");outcome.remove("targetTemplateCode");
                outcome.remove("targetVersionId");terminal++;
            }
            if("MISJUDGED_VALID".equals(outcome.getString("value")))
            {
                next(outcome,"TD-001",td001VersionId);reopened++;
            }
        }
        if(terminal!=1||reopened!=1)throw new IllegalStateException(
                "TD-002 must contain TRUE_INVALID and MISJUDGED_VALID outcomes");
        bindTaskNodes(routing,"TD-002",draftVersionId,"TD-001",td001VersionId);
    }

    private static void next(JSONObject outcome,String templateCode,long versionId)
    {
        outcome.put("effectKind","NEXT_TEMPLATE");
        outcome.put("targetTemplateCode",templateCode);
        outcome.put("targetVersionId",versionId);
    }

    private static void bindTaskNodes(JSONObject routing,Object... bindings)
    {
        JSONArray nodes=requiredArray(routing,"nodes");
        int[] matches=new int[bindings.length/2];
        for(Object raw:nodes)
        {
            if(!(raw instanceof JSONObject node)||!"TASK".equals(node.getString("type")))continue;
            for(int index=0;index<bindings.length;index+=2)
            {
                if(String.valueOf(bindings[index]).equals(node.getString("templateCode")))
                {
                    node.put("templateVersionId",bindings[index+1]);matches[index/2]++;
                    break;
                }
            }
        }
        for(int index=0;index<matches.length;index++)if(matches[index]==0)
            throw new IllegalStateException("Route has no task node for "+bindings[index*2]);
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

    private void retireMutableDrafts(Context context,long templateId,String marker) throws Exception
    {
        try(PreparedStatement update=context.getConnection().prepareStatement("""
                update todo_template_version
                set status='RETIRED',update_by='flyway-v0.20.82',update_time=sysdate()
                where template_id=? and status in ('DRAFT','BLOCKED')
                  and (change_summary is null or change_summary<>?)
                """))
        {update.setLong(1,templateId);update.setString(2,marker);update.executeUpdate();}
    }

    private boolean markerExists(Context context,long templateId,String marker) throws Exception
    {
        try(PreparedStatement select=context.getConnection().prepareStatement("""
                select count(*) from todo_template_version
                where template_id=? and change_summary=? and status in ('DRAFT','BLOCKED','RETIRED')
                """))
        {
            select.setLong(1,templateId);select.setString(2,marker);
            try(ResultSet rows=select.executeQuery()){return rows.next()&&rows.getInt(1)>0;}
        }
    }

    private int nextVersionNo(Context context,long templateId) throws Exception
    {
        try(PreparedStatement select=context.getConnection().prepareStatement(
                "select coalesce(max(version_no),0)+1 from todo_template_version where template_id=?"))
        {
            select.setLong(1,templateId);
            try(ResultSet rows=select.executeQuery())
            {
                if(!rows.next())throw new IllegalStateException("Next template version could not be allocated");
                return rows.getInt(1);
            }
        }
    }

    private long insertPlaceholder(Context context,Source source,int versionNo,String marker,
            String impactScope) throws Exception
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
                       ?,?,version_id,definition_schema_version,definition_json,compiled_json,null,
                       json_object('errors',json_array(),'warnings',json_array()),
                       null,null,'flyway-v0.20.82',sysdate()
                from todo_template_version
                where version_id=? and status='PUBLISHED'
                """,Statement.RETURN_GENERATED_KEYS))
        {
            insert.setInt(1,versionNo);insert.setString(2,marker);insert.setString(3,impactScope);
            insert.setLong(4,source.versionId());
            if(insert.executeUpdate()!=1)throw new IllegalStateException("Repair draft placeholder was not created");
            try(ResultSet keys=insert.getGeneratedKeys())
            {
                if(!keys.next())throw new IllegalStateException("Repair draft version ID was not generated");
                return keys.getLong(1);
            }
        }
    }

    private JSONObject definition(String templateCode) throws Exception
    {
        String resource="/todo-definitions/v0.2/"+templateCode+".json";
        try(InputStream input=getClass().getResourceAsStream(resource))
        {
            if(input==null)throw new IllegalStateException("Canonical definition is missing: "+resource);
            JSONObject packaged=JSON.parseObject(new String(input.readAllBytes(),StandardCharsets.UTF_8));
            JSONObject definition=packaged==null?null:packaged.getJSONObject("definition");
            if(definition==null)throw new IllegalStateException("Canonical definition payload is missing: "+resource);
            return definition;
        }
    }

    private void finalizeDraft(Context context,long versionId,String marker,JSONObject definition) throws Exception
    {
        TodoDefinitionCodec codec=new TodoDefinitionCodec();
        String canonical=codec.canonicalJson(codec.read(definition.toJSONString()));
        JSONObject normalized=JSON.parseObject(canonical);
        try(PreparedStatement update=context.getConnection().prepareStatement("""
                update todo_template_version
                set owner_rule_json=cast(? as json),dod_rule_json=cast(? as json),
                    sla_rule_json=cast(? as json),next_rule_json=cast(? as json),
                    ui_schema_json=cast(? as json),definition_schema_version=?,
                    definition_json=cast(? as json),compiled_json=cast(? as json),definition_hash=?,
                    validation_report_json=json_object('errors',json_array(),'warnings',json_array()),
                    update_by='flyway-v0.20.82',update_time=sysdate()
                where version_id=? and status='DRAFT' and change_summary=?
                """))
        {
            update.setString(1,config(normalized,"owner"));update.setString(2,config(normalized,"dod"));
            update.setString(3,config(normalized,"sla"));update.setString(4,config(normalized,"routing"));
            update.setString(5,config(normalized,"ui"));update.setInt(6,normalized.getIntValue("schemaVersion"));
            update.setString(7,canonical);update.setString(8,canonical);update.setString(9,sha256(canonical));
            update.setLong(10,versionId);update.setString(11,marker);
            if(update.executeUpdate()!=1)throw new IllegalStateException("Repair draft could not be finalized");
        }
    }

    private static JSONObject requiredObject(JSONObject root,String key)
    {
        JSONObject value=root==null?null:root.getJSONObject(key);
        if(value==null)throw new IllegalStateException("Definition is missing "+key);return value;
    }

    private static JSONObject requiredConfig(JSONObject root,String section)
    {
        JSONObject value=requiredObject(requiredObject(root,section),"config");return value;
    }

    private static JSONArray requiredArray(JSONObject root,String key)
    {
        JSONArray value=root==null?null:root.getJSONArray(key);
        if(value==null)throw new IllegalStateException("Definition is missing "+key);return value;
    }

    private String config(JSONObject root,String section)
    {return requiredConfig(root,section).toJSONString();}

    private String sha256(String value) throws Exception
    {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.UTF_8)));}

    private record Source(long templateId,long versionId) { }
}
