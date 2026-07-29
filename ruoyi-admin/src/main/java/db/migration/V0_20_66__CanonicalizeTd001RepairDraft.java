package db.migration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HexFormat;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import com.law.todo.definition.codec.TodoDefinitionCodec;

/**
 * Converts the mutable repair draft created by V0.20.65 to the exact canonical
 * representation used by the compiler. Published versions and evidence are
 * deliberately outside this migration's update predicate.
 */
public class V0_20_66__CanonicalizeTd001RepairDraft extends BaseJavaMigration
{
    private static final String MARKER="V0.20.65 TD-001 配置易用性修复草稿";

    @Override
    public void migrate(Context context) throws Exception
    {
        long versionId;
        String status;
        String stored;
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
                        "The V0.20.65 mutable TD-001 repair draft is missing");
                versionId=rows.getLong(1);
                status=rows.getString(2);
                stored=rows.getString(3);
                if(rows.next())throw new IllegalStateException(
                        "More than one mutable TD-001 repair draft uses the migration marker");
            }
        }

        TodoDefinitionCodec codec=new TodoDefinitionCodec();
        String canonical=codec.canonicalJson(codec.read(stored));
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        try(PreparedStatement update=context.getConnection().prepareStatement("""
                update todo_template_version
                set definition_json=?,
                    compiled_json=?,
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
            update.setString(1,canonical);
            update.setString(2,canonical);
            update.setString(3,hash);
            update.setLong(4,versionId);
            update.setString(5,status);
            update.setString(6,MARKER);
            if(update.executeUpdate()!=1)throw new IllegalStateException(
                    "The mutable TD-001 repair draft changed while it was canonicalized");
        }
    }
}
