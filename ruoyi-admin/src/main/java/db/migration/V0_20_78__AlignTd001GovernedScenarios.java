package db.migration;

import java.sql.PreparedStatement;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

/** Aligns TD-001 dry-run fixtures with the governed completion proof and conditional fields. */
public class V0_20_78__AlignTd001GovernedScenarios extends BaseJavaMigration
{
    @Override
    public void migrate(Context context) throws Exception
    {
        JSONObject valid=payload("VALID");
        valid.put("name","王五");
        valid.put("city","合肥");
        valid.put("demand","劳动争议咨询");
        valid.put("visited","0");
        upsert(context,"TD001_VALID","有效首联","验证有效首联后进入五天实质进展",10,
                scenario("TD001_VALID",valid,"TD-004"));

        JSONObject suspect=payload("SUSPECT_INVALID");
        suspect.put("invalidReasonCode","NO_DEMAND");
        suspect.put("salesExplanation","客户明确表示暂无法律服务需求");
        upsert(context,"TD001_SUSPECT_INVALID","疑似无效","验证疑似无效后进入主管复核",20,
                scenario("TD001_SUSPECT_INVALID",suspect,"TD-002"));

        upsert(context,"TD001_UNREACHABLE","未接通","验证未接通后进入重试计划",30,
                scenario("TD001_UNREACHABLE",payload("UNREACHABLE"),"TD-003"));
    }

    private JSONObject payload(String contactResult)
    {
        JSONObject value=new JSONObject();
        value.put("contactResult",contactResult);
        value.put("contactedAt","${SIMULATION_NOW}");
        return value;
    }

    private JSONObject scenario(String code,JSONObject payload,String targetTemplateCode)
    {
        JSONObject value=new JSONObject();
        value.put("templateCode","TD-001");
        value.put("scenarioCode",code);
        value.put("scenarioVersion",2);
        value.put("completionPayload",payload);
        value.put("editableFields",new JSONArray(payload.keySet()));
        value.put("requiredMaterials",JSON.parseArray("[\"CONTACT_PROOF\"]"));
        value.put("completionNodeKey","TD-001");
        value.put("occurrence",1);
        value.put("requiredForPublish",true);
        JSONObject effect=new JSONObject();
        effect.put("kind","NEXT_TEMPLATE");
        effect.put("targetTemplateCode",targetTemplateCode);
        value.put("expectedEffect",effect);
        return value;
    }

    private void upsert(Context context,String code,String name,String description,int sortOrder,
            JSONObject value) throws Exception
    {
        try(PreparedStatement statement=context.getConnection().prepareStatement("""
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
            statement.setString(1,code);
            statement.setString(2,name);
            statement.setString(3,description);
            statement.setString(4,json);
            statement.setInt(5,sortOrder);
            statement.setString(6,name);
            statement.setString(7,description);
            statement.setString(8,json);
            statement.setInt(9,sortOrder);
            if(statement.executeUpdate()<1)
                throw new IllegalStateException("TD-001 scenario "+code+" was not stored");
        }
    }
}
