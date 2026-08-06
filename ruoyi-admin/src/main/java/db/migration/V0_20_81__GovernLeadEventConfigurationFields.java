package db.migration;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

/** Gives every lead event field a business meaning and an explicit configuration purpose. */
public class V0_20_81__GovernLeadEventConfigurationFields extends BaseJavaMigration
{
    private static final Map<String,FieldMeaning> FIELDS=fields();
    private static final Map<String,EventPolicy> EVENTS=events();

    @Override
    public void migrate(Context context) throws Exception
    {
        for(String eventType:EVENTS.keySet())governEvent(context,eventType);
    }

    private void governEvent(Context context,String eventType) throws Exception
    {
        try(PreparedStatement select=context.getConnection().prepareStatement("""
                select event_catalog_id,cast(payload_schema_json as char),version
                from todo_event_catalog
                where event_type=? and payload_version=1 and status='ACTIVE'
                """))
        {
            select.setString(1,eventType);
            try(ResultSet rows=select.executeQuery())
            {
                if(!rows.next())throw new IllegalStateException(
                        "Active lead event resource is unavailable: "+eventType);
                long id=rows.getLong(1);
                GovernedEvent governed=govern(eventType,JSON.parseObject(rows.getString(2)));
                int version=rows.getInt(3);
                if(rows.next())throw new IllegalStateException(
                        "More than one active lead event resource was found: "+eventType);
                update(context,id,version,governed);
            }
        }
    }

    private void update(Context context,long id,int version,GovernedEvent governed) throws Exception
    {
        try(PreparedStatement update=context.getConnection().prepareStatement("""
                update todo_event_catalog
                set event_name=?,description=?,payload_schema_json=cast(? as json),
                    owner_field_paths_json=cast(? as json),
                    condition_field_paths_json=cast(? as json),
                    default_value_field_paths_json=cast(? as json),
                    schema_status='READY',version=version+1,
                    update_by='flyway-v0.20.81',update_time=sysdate()
                where event_catalog_id=? and version=? and status='ACTIVE'
                """))
        {
            update.setString(1,governed.eventName());
            update.setString(2,governed.description());
            update.setString(3,governed.schema().toJSONString());
            update.setString(4,array(governed.ownerFields()));
            update.setString(5,array(governed.conditionFields()));
            update.setString(6,array(governed.defaultValueFields()));
            update.setLong(7,id);
            update.setInt(8,version);
            if(update.executeUpdate()!=1)throw new IllegalStateException(
                    "Lead event resource changed while it was governed: "+governed.eventType());
        }
    }

    static GovernedEvent govern(String eventType,JSONObject sourceSchema)
    {
        EventPolicy policy=EVENTS.get(eventType);
        if(policy==null)throw new IllegalArgumentException("Unsupported lead event: "+eventType);
        JSONObject schema=sourceSchema==null?null:JSON.parseObject(sourceSchema.toJSONString());
        JSONObject properties=schema==null?null:schema.getJSONObject("properties");
        if(properties==null||properties.isEmpty())throw new IllegalStateException(
                eventType+" does not define payload properties");
        for(String field:properties.keySet())
        {
            FieldMeaning meaning=FIELDS.get(field);
            if(meaning==null)throw new IllegalStateException(
                    eventType+" contains an ungoverned field: "+field);
            JSONObject property=properties.getJSONObject(field);
            if(property==null)throw new IllegalStateException(
                    eventType+" contains an invalid field definition: "+field);
            property.put("title",meaning.title());
            property.put("description",meaning.description());
            property.put("x-semantic-type",meaning.semanticType());
            property.put("x-business-type","LEAD");
            property.put("x-read-only-business-context",true);
            if(meaning.optionSource()==null)property.remove("x-option-source");
            else property.put("x-option-source",meaning.optionSource());
            if(meaning.dictType()==null)property.remove("x-dict-type");
            else property.put("x-dict-type",meaning.dictType());
        }
        requireFields(eventType,properties,policy.ownerFields());
        requireFields(eventType,properties,policy.conditionFields());
        requireFields(eventType,properties,policy.defaultValueFields());
        return new GovernedEvent(eventType,policy.eventName(),policy.description(),schema,
                policy.ownerFields(),policy.conditionFields(),policy.defaultValueFields());
    }

    private static void requireFields(String eventType,JSONObject properties,Set<String> fields)
    {
        for(String field:fields)if(!properties.containsKey(field))throw new IllegalStateException(
                eventType+" configuration policy references a missing field: "+field);
    }

    private static String array(Set<String> values)
    {
        JSONArray result=new JSONArray();result.addAll(values);return result.toJSONString();
    }

    private static Map<String,EventPolicy> events()
    {
        Map<String,EventPolicy> result=new LinkedHashMap<>();
        result.put("LEAD_ASSIGNED",event("线索已分配","线索完成负责人分配，进入首联处理",set("ownerId"),set(),set()));
        result.put("LEAD_TAG_CONFIRMED",event("线索标签已确认","线索来源标签已完成确认或纠正",set(),set("confirmStatus"),set()));
        result.put("LEAD_FIRST_CONTACT_VALID",event("首联结果有效","销售完成有效首联，线索进入实质进展跟进",set("ownerId"),set("contactResult"),set()));
        result.put("LEAD_SUSPECT_INVALID_MARKED",event("线索已标记疑似无效","销售提交疑似无效原因，等待主管复核",set("reviewerId"),set("reasonCode"),set()));
        result.put("LEAD_FIRST_CONTACT_UNREACHABLE",event("首联未接通","首联未接通，系统建立后续重试计划",set("ownerId"),set("attempts"),set()));
        result.put("LEAD_INVALID_REVIEW_CONFIRMED",event("主管确认线索无效","主管复核确认线索无效",set(),set("reviewResult"),set()));
        result.put("LEAD_INVALID_REVIEW_MISJUDGED",event("主管判定线索仍有效","主管复核判定疑似无效为误判并恢复首联",set("ownerId"),set("reviewResult"),set()));
        result.put("LEAD_RETRY_WINDOW_DUE",event("线索重试窗口到期","预设的线索重试联系窗口已到达",set("ownerId"),set("windowCode","occurrenceNo"),set()));
        result.put("LEAD_RETRY_CONNECTED",event("线索重试已接通","销售在重试窗口成功联系线索",set("ownerId"),set(),set()));
        result.put("LEAD_RETRY_EXHAUSTED",event("线索重试已耗尽","所有预设重试窗口均已完成但仍未接通",set(),set(),set()));
        result.put("LEAD_MOVED_TO_DEAD_POOL",event("线索已进入无效池","确认无效的线索已进入隔离的无效线索池",set(),set("reasonCode"),set()));
        return Collections.unmodifiableMap(result);
    }

    private static EventPolicy event(String name,String description,Set<String> owners,
            Set<String> conditions,Set<String> defaults)
    {return new EventPolicy(name,description,owners,conditions,defaults);}

    private static Set<String> set(String... values)
    {
        Set<String> result=new LinkedHashSet<>();
        if(values!=null)for(String value:values)result.add(value);
        return Collections.unmodifiableSet(result);
    }

    private static Map<String,FieldMeaning> fields()
    {
        Map<String,FieldMeaning> result=new LinkedHashMap<>();
        field(result,"schemaVersion","载荷版本","系统维护的事件数据结构版本","SYSTEM_VERSION",null,null);
        field(result,"leadId","线索","当前事件关联的线索","BUSINESS_REF","LEAD_DIRECTORY",null);
        field(result,"assignmentId","分配记录","系统生成的本次线索分配记录，仅用于追踪","SYSTEM_ID",null,null);
        field(result,"tagRelationId","标签确认记录","系统生成的标签确认记录，仅用于追踪","SYSTEM_ID",null,null);
        field(result,"followupId","首联记录","系统生成的首联跟进记录，仅用于追踪","SYSTEM_ID",null,null);
        field(result,"reviewId","主管复核记录","系统生成的疑似无效复核记录，仅用于追踪","SYSTEM_ID",null,null);
        field(result,"planId","重试计划","系统生成的无法联系重试计划，仅用于追踪","SYSTEM_ID",null,null);
        field(result,"retryRecordId","重试联系记录","系统生成的单次重试记录，仅用于追踪","SYSTEM_ID",null,null);
        field(result,"deadPoolLogId","无效池记录","系统生成的线索进入无效池记录，仅用于追踪","SYSTEM_ID",null,null);
        field(result,"ownerId","线索负责人","当前负责跟进该线索的销售人员","USER_ID","SYSTEM_USER",null);
        field(result,"ownerDeptId","负责人部门","线索负责人当前所属部门","DEPT_ID","SYSTEM_DEPARTMENT",null);
        field(result,"operatorId","事件操作人","触发本次业务事件的操作人员","USER_ID","SYSTEM_USER",null);
        field(result,"reviewerId","主管复核人","负责处理疑似无效复核的主管","USER_ID","SYSTEM_USER",null);
        field(result,"confirmStatus","标签确认结果","线索来源标签的确认或纠正结果","DICT","SYSTEM_DICTIONARY","law_lead_tag_confirm_status");
        field(result,"contactResult","首联结果","本次首联得到的业务结果","DICT","SYSTEM_DICTIONARY","law_first_contact_result");
        field(result,"reasonCode","疑似无效原因","销售标记或主管确认线索无效时使用的标准原因","DICT","SYSTEM_DICTIONARY","law_lead_invalid_reason");
        field(result,"reviewResult","主管复核结果","主管对疑似无效线索作出的复核结论","DICT","SYSTEM_DICTIONARY","law_lead_invalid_review_result");
        field(result,"attempts","已尝试联系次数","当前重试计划已经执行的联系次数","SYSTEM_COUNTER",null,null);
        field(result,"occurrenceNo","重试窗口序号","当前重试窗口在计划中的执行序号","SYSTEM_COUNTER",null,null);
        field(result,"windowCode","重试时间窗口","当前到期的标准重试时间窗口","DICT","SYSTEM_DICTIONARY","law_retry_stage");
        field(result,"nextContactAt","下次联系时间","系统根据重试策略计算的下次联系时间","BUSINESS_DATETIME",null,null);
        return Collections.unmodifiableMap(result);
    }

    private static void field(Map<String,FieldMeaning> values,String code,String title,
            String description,String semanticType,String optionSource,String dictType)
    {values.put(code,new FieldMeaning(title,description,semanticType,optionSource,dictType));}

    record GovernedEvent(String eventType,String eventName,String description,JSONObject schema,
            Set<String> ownerFields,Set<String> conditionFields,Set<String> defaultValueFields) { }
    private record EventPolicy(String eventName,String description,Set<String> ownerFields,
            Set<String> conditionFields,Set<String> defaultValueFields) { }
    private record FieldMeaning(String title,String description,String semanticType,
            String optionSource,String dictType) { }
}
