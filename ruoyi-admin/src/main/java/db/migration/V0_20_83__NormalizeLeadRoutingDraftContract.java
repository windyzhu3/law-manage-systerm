package db.migration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HexFormat;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.law.todo.definition.codec.TodoDefinitionCodec;

/** Normalizes only the V0.20.82 mutable repair drafts to the guided-editor routing contract. */
public class V0_20_83__NormalizeLeadRoutingDraftContract extends BaseJavaMigration
{
    private static final String TD001_MARKER="V0.20.82 TD-001 routing governance draft";
    private static final String TD002_MARKER="V0.20.82 TD-002 event governance draft";

    @Override
    public void migrate(Context context) throws Exception
    {
        normalize(context,"TD-001",TD001_MARKER);
        normalize(context,"TD-002",TD002_MARKER);
    }

    private void normalize(Context context,String templateCode,String marker) throws Exception
    {
        long versionId;
        String stored;
        try(PreparedStatement select=context.getConnection().prepareStatement("""
                select v.version_id,cast(v.definition_json as char)
                from todo_template t
                join todo_template_version v on v.template_id=t.template_id
                where t.template_code=? and v.change_summary=? and v.status in ('DRAFT','BLOCKED')
                """))
        {
            select.setString(1,templateCode);select.setString(2,marker);
            try(ResultSet rows=select.executeQuery())
            {
                if(!rows.next())throw new IllegalStateException(
                        "The mutable "+templateCode+" V0.20.82 repair draft is missing");
                versionId=rows.getLong(1);stored=rows.getString(2);
                if(rows.next())throw new IllegalStateException(
                        "More than one mutable "+templateCode+" V0.20.82 repair draft exists");
            }
        }

        JSONObject definition=JSON.parseObject(stored);
        if("TD-001".equals(templateCode))
            V0_20_82__RepairLeadTemplateConfigurationDrafts.normalizeTd001OutcomeContract(definition);
        else V0_20_82__RepairLeadTemplateConfigurationDrafts.normalizeTd002OutcomeContract(definition);

        TodoDefinitionCodec codec=new TodoDefinitionCodec();
        String canonical=codec.canonicalJson(codec.read(definition.toJSONString()));
        JSONObject normalized=JSON.parseObject(canonical);
        try(PreparedStatement update=context.getConnection().prepareStatement("""
                update todo_template_version
                set next_rule_json=cast(? as json),definition_json=cast(? as json),
                    compiled_json=cast(? as json),definition_hash=?,
                    validation_report_json=json_object('errors',json_array(),'warnings',json_array()),
                    update_by='flyway-v0.20.83',update_time=sysdate()
                where version_id=? and change_summary=? and status in ('DRAFT','BLOCKED')
                """))
        {
            update.setString(1,normalized.getJSONObject("routing").getJSONObject("config").toJSONString());
            update.setString(2,canonical);update.setString(3,canonical);update.setString(4,sha256(canonical));
            update.setLong(5,versionId);update.setString(6,marker);
            if(update.executeUpdate()!=1)throw new IllegalStateException(
                    "The mutable "+templateCode+" repair draft changed while its route was normalized");
        }
    }

    private String sha256(String value) throws Exception
    {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
