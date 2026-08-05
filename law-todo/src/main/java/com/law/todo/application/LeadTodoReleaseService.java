package com.law.todo.application;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoConfigurationCommands.LeadReleaseCommand;
import com.law.todo.application.command.TodoConfigurationCommands.LeadReleaseReadinessQuery;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoConfigurationMapper;

/** Atomically activates the governed four-template lead release as one entry-slot switch. */
@Service
public class LeadTodoReleaseService
{
    public static final String ENTRY_SLOT="LEAD_FIRST_CONTACT_ENTRY";
    private static final String ACTION_TYPE="ACTIVATE_LEAD_RELEASE";
    private static final String ENTITY_TYPE="LEAD_RELEASE";
    private static final List<String> CODES=List.of("TD-001","TD-002","TD-003","TD-004");

    public record LeadReleaseView(String entrySlotCode,long activeTriggerRuleId,
            long activeTd001VersionId,Map<String,Long> downstreamVersions,
            LocalDateTime activatedAt,String activatedBy)
    {
        public LeadReleaseView
        { downstreamVersions=Map.copyOf(downstreamVersions); }
    }

    public record LeadReleaseReadinessView(boolean activationReady,Map<String,Boolean> evidenceReady,
            LeadReleaseView activeRelease,int triggerExpectedVersion,List<String> blockers)
    {
        public LeadReleaseReadinessView
        {evidenceReady=Map.copyOf(evidenceReady);blockers=List.copyOf(blockers);}
    }

    private final TodoConfigurationMapper mapper;
    private final TodoTemplateService templates;

    public LeadTodoReleaseService(TodoConfigurationMapper mapper,TodoTemplateService templates)
    {this.mapper=mapper;this.templates=templates;}

    @Transactional(readOnly=true)
    public LeadReleaseReadinessView readiness(LeadReleaseReadinessQuery query)
    {
        LeadReleaseCommand command=new LeadReleaseCommand("readiness",query.td001VersionId(),
                query.td001DefinitionHash(),query.td002VersionId(),query.td003VersionId(),query.td004VersionId(),0);
        List<Long> ids=List.of(query.td001VersionId(),query.td002VersionId(),query.td003VersionId(),query.td004VersionId());
        Map<String,Map<String,Object>> versions=validateVersions(command,mapper.selectLeadReleaseVersions(ids));
        List<Map<String,Object>> evidenceRows=mapper.selectSimulationReadinessBatch(ids);
        Map<String,Boolean> evidence=evidenceStatus(versions,evidenceRows);
        validateRouting(command,
                text(value(versions.get("TD-001"),"compiled_json","compiledJson")));
        validateCoordinatedRouting(command,versions);
        Map<String,Object> candidate=mapper.selectLeadReleaseTrigger(ENTRY_SLOT,query.td001VersionId());
        int expected=candidate==null?0:integer(value(candidate,"trigger_version","triggerVersion"));
        List<Map<String,Object>> bindings=mapper.selectLeadEntrySlotBindings(ENTRY_SLOT);
        Map<String,Object> active=(bindings==null?List.<Map<String,Object>>of():bindings).stream()
                .filter(row->"Y".equals(text(value(row,"enabled","enabled")))).findFirst().orElse(null);
        LeadReleaseView activeView=active==null?null:new LeadReleaseView(ENTRY_SLOT,
                number(value(active,"trigger_rule_id","triggerRuleId")),
                number(value(active,"template_version_id","templateVersionId")),activeDownstream(active),null,null);
        List<String> blockers=CODES.stream().filter(code->!Boolean.TRUE.equals(evidence.get(code))).toList();
        return new LeadReleaseReadinessView(blockers.isEmpty(),evidence,activeView,expected,blockers);
    }

    @Transactional
    public LeadReleaseView activate(LeadReleaseCommand command,Actor actor)
    {
        List<Map<String,Object>> locked=mapper.selectLeadEntrySlotBindingsForUpdate(ENTRY_SLOT);
        if(locked==null||locked.isEmpty())
            throw new TodoException("TODO_LEAD_RELEASE_ENTRY_SLOT_NOT_FOUND","Lead entry slot was not found");

        String fingerprint=fingerprint(command);
        Map<String,Object> claim=new HashMap<>();
        claim.put("actionId",command.actionId());claim.put("requestFingerprint",fingerprint);
        claim.put("sourceEntityId",command.td001VersionId());claim.put("operatorId",actor.userId());
        claim.put("operatorName",actor.userName());claim.put("operatorDeptId",actor.deptId());
        claim.put("payloadJson",JSON.toJSONString(command));
        int inserted=mapper.insertLeadReleaseActionClaim(claim);
        Map<String,Object> action=mapper.selectLeadReleaseActionForUpdate(command.actionId());
        Long replay=validateAction(action,command,fingerprint,inserted);
        if(replay!=null)return view(command,replay,time(value(action,"create_time","createTime")),
                text(value(action,"operator_name","operatorName")));

        List<Long> ids=List.of(command.td001VersionId(),command.td002VersionId(),
                command.td003VersionId(),command.td004VersionId());
        if(new LinkedHashSet<>(ids).size()!=CODES.size())
            throw new TodoException("TODO_LEAD_RELEASE_VERSION_INVALID",
                    "Lead release requires four unique template versions");
        Map<String,Map<String,Object>> versions=validateVersions(command,
                mapper.selectTemplateVersionsForUpdate(ids));
        validateEvidence(versions,mapper.selectSimulationReadinessBatch(ids));
        validateRouting(command,text(value(versions.get("TD-001"),"compiled_json","compiledJson")));
        validateCoordinatedRouting(command,versions);

        Map<String,Object> trigger=mapper.selectLeadReleaseTriggerForUpdate(ENTRY_SLOT,command.td001VersionId());
        if(trigger==null||trigger.isEmpty())trigger=createDisabledTrigger(versions.get("TD-001"),actor);
        long triggerId=number(value(trigger,"trigger_rule_id","triggerRuleId"));
        int triggerVersion=integer(value(trigger,"trigger_version","triggerVersion"));
        if(triggerVersion!=command.triggerExpectedVersion())
            throw new TodoException("TODO_TRIGGER_VERSION_CONFLICT","Trigger changed; refresh before retrying");
        validatePreparedTrigger(trigger,versions.get("TD-001"));

        TodoTemplateService.EntrySlotBinding binding=templates.switchEntrySlot(
                ENTRY_SLOT,triggerId,command.triggerExpectedVersion(),actor);
        if(binding.templateVersionId()!=command.td001VersionId())
            throw new TodoException("TODO_LEAD_RELEASE_ENTRY_SWITCH_FAILED","Lead entry switch returned an unexpected version");
        if(mapper.completeLeadReleaseAction(command.actionId(),fingerprint,triggerId)<=0)
            throw new TodoException("TODO_LEAD_RELEASE_ACTION_CONFLICT","Lead release action could not be completed");
        return view(command,triggerId,LocalDateTime.now(),actor.userName());
    }

    private Map<String,Map<String,Object>> validateVersions(LeadReleaseCommand command,List<Map<String,Object>> rows)
    {
        Map<String,Long> requested=allVersions(command);
        Map<String,Map<String,Object>> result=new LinkedHashMap<>();
        for(Map<String,Object> row:rows==null?List.<Map<String,Object>>of():rows)
        {
            String code=text(value(row,"template_code","templateCode"));
            Long id=number(value(row,"version_id","versionId"));
            if(!CODES.contains(code)||!Objects.equals(requested.get(code),id)
                    ||!"LEAD".equals(text(value(row,"business_type","businessType")))
                    ||!"PUBLISHED".equals(text(value(row,"version_status","versionStatus"))))
                throw new TodoException("TODO_LEAD_RELEASE_VERSION_INVALID","Lead release requires exact published LEAD versions");
            if(result.put(code,row)!=null)
                throw new TodoException("TODO_LEAD_RELEASE_VERSION_INVALID","Lead release version identity is ambiguous");
        }
        if(!result.keySet().equals(new LinkedHashSet<>(CODES)))
            throw new TodoException("TODO_LEAD_RELEASE_VERSION_INVALID","All four lead template versions are required");
        if(!command.td001DefinitionHash().equals(text(value(result.get("TD-001"),"definition_hash","definitionHash"))))
            throw new TodoException("TODO_LEAD_RELEASE_DEFINITION_STALE","TD-001 definition changed; refresh before activation");
        return result;
    }

    private void validateEvidence(Map<String,Map<String,Object>> versions,List<Map<String,Object>> rows)
    {
        Map<Long,Map<String,Object>> evidence=new HashMap<>();
        for(Map<String,Object> row:rows==null?List.<Map<String,Object>>of():rows)
            evidence.put(number(value(row,"version_id","versionId")),row);
        for(String code:CODES)
        {
            Map<String,Object> version=versions.get(code);
            long id=number(value(version,"version_id","versionId"));
            Map<String,Object> state=evidence.get(id);
            String currentHash=text(value(version,"definition_hash","definitionHash"));
            int required=state==null?0:integer(value(state,"required_scenario_count","requiredScenarioCount"));
            int passed=state==null?0:integer(value(state,"passed_scenario_count","passedScenarioCount"));
            boolean full=state!=null&&truth(value(state,"full_simulation_passed","fullSimulationPassed"));
            String evidenceHash=state==null?null:text(value(state,"definition_hash","definitionHash"));
            if(required<=0||required!=passed||!full||!Objects.equals(currentHash,evidenceHash))
                throw new TodoException("TODO_LEAD_RELEASE_EVIDENCE_INCOMPLETE",
                        code+" requires current-hash passing scenarios and full simulation");
        }
    }

    private Map<String,Boolean> evidenceStatus(Map<String,Map<String,Object>> versions,List<Map<String,Object>> rows)
    {
        Map<Long,Map<String,Object>> byId=new HashMap<>();
        for(Map<String,Object> row:rows==null?List.<Map<String,Object>>of():rows)
            byId.put(number(value(row,"version_id","versionId")),row);
        Map<String,Boolean> result=new LinkedHashMap<>();
        for(String code:CODES)
        {
            Map<String,Object> version=versions.get(code);Map<String,Object> state=byId.get(number(value(version,"version_id","versionId")));
            int required=state==null?0:integer(value(state,"required_scenario_count","requiredScenarioCount"));
            int passed=state==null?0:integer(value(state,"passed_scenario_count","passedScenarioCount"));
            result.put(code,state!=null&&required>0&&required==passed
                    &&truth(value(state,"full_simulation_passed","fullSimulationPassed"))
                    &&Objects.equals(text(value(version,"definition_hash","definitionHash")),
                            text(value(state,"definition_hash","definitionHash"))));
        }
        return result;
    }

    private void validateRouting(LeadReleaseCommand command,String compiled)
    {
        try
        {
            JSONObject root=JSON.parseObject(compiled);
            JSONObject routing=root.getJSONObject("routing");
            routing=routing==null?null:routing.getJSONObject("config");
            if(routing==null)throw routingMismatch();
            Map<String,Long> expected=downstream(command);
            Map<String,Long> outcomes=targets(routing.getJSONArray("businessOutcomes"),"targetTemplateCode","targetVersionId",false);
            Set<Long> expectedNodes=new LinkedHashSet<>();
            expectedNodes.add(command.td001VersionId());
            expectedNodes.add(command.td002VersionId());
            expectedNodes.add(command.td004VersionId());
            Set<Long> nodes=taskVersionIds(routing.getJSONArray("nodes"));
            if(!expected.equals(outcomes)||!expectedNodes.equals(nodes))throw routingMismatch();
        }
        catch(TodoException expected){throw expected;}
        catch(Exception invalid){throw routingMismatch();}
    }

    private Map<String,Long> targets(JSONArray values,String codeKey,String idKey,boolean allowCurrent)
    {
        if(values==null)throw routingMismatch();
        Map<String,Long> result=new LinkedHashMap<>();
        for(Object raw:values)
        {
            if(!(raw instanceof JSONObject value))throw routingMismatch();
            if(allowCurrent&&!"TASK".equals(value.getString("type")))continue;
            String code=value.getString(codeKey);Long id=value.getLong(idKey);
            if(allowCurrent&&"TD-001".equals(code))continue;
            if(!Set.of("TD-002","TD-003","TD-004").contains(code)||id==null||result.put(code,id)!=null)
                throw routingMismatch();
        }
        return result;
    }

    private void validateCoordinatedRouting(LeadReleaseCommand command,
            Map<String,Map<String,Object>> versions)
    {
        // TD-002's immutable standalone graph is intentionally not locked to a future TD-001
        // version. The release candidate's master graph must instead connect the selected TD-002
        // node back to a second node of this exact TD-001 version for MISJUDGED_VALID.
        validateTd002ReopenRoute(versions.get("TD-001"),command.td001VersionId(),command.td002VersionId());
        validateLockedRoute(versions.get("TD-003"),"TD-003",command.td003VersionId(),
                "CONNECTED","NEXT_TEMPLATE","TD-004",command.td004VersionId());
        validateLockedRoute(versions.get("TD-004"),"TD-004",command.td004VersionId(),
                "PROGRESS_RECORDED","SCHEDULE_SELF","TD-004",command.td004VersionId());
    }

    private void validateTd002ReopenRoute(Map<String,Object> td001,long td001VersionId,long td002VersionId)
    {
        try
        {
            JSONObject root=JSON.parseObject(text(value(td001,"compiled_json","compiledJson")));
            JSONObject routing=root==null?null:root.getJSONObject("routing");
            routing=routing==null?null:routing.getJSONObject("config");
            JSONArray nodes=routing==null?null:routing.getJSONArray("nodes");
            JSONArray edges=routing==null?null:routing.getJSONArray("edges");
            if(nodes==null||edges==null)throw routingMismatch();

            Map<String,JSONObject> byKey=new LinkedHashMap<>();
            String td002Node=null;
            Set<String> td001Nodes=new LinkedHashSet<>();
            for(Object raw:nodes)
            {
                if(!(raw instanceof JSONObject node))throw routingMismatch();
                String key=node.getString("key");
                if(key==null||key.isBlank()||byKey.put(key,node)!=null)throw routingMismatch();
                if(!"TASK".equals(node.getString("type")))continue;
                String code=node.getString("templateCode");
                Long versionId=node.getLong("templateVersionId");
                if("TD-002".equals(code)&&Objects.equals(td002VersionId,versionId))
                {
                    if(td002Node!=null)throw routingMismatch();
                    td002Node=key;
                }
                if("TD-001".equals(code)&&Objects.equals(td001VersionId,versionId))td001Nodes.add(key);
            }
            if(td002Node==null||td001Nodes.size()<2)throw routingMismatch();

            Set<String> reviewDecisions=new LinkedHashSet<>();
            for(Object raw:edges)
            {
                if(!(raw instanceof JSONObject edge)||!td002Node.equals(edge.getString("from")))continue;
                String to=edge.getString("to");
                JSONObject target=byKey.get(to);
                if(target!=null&&"DECISION".equals(target.getString("type")))reviewDecisions.add(to);
            }
            int matching=0;
            int returnEdges=0;
            for(Object raw:edges)
            {
                if(!(raw instanceof JSONObject edge)||!reviewDecisions.contains(edge.getString("from"))
                        ||!td001Nodes.contains(edge.getString("to")))continue;
                returnEdges++;
                JSONObject condition=edge.getJSONObject("condition");
                JSONObject expression=condition==null?null:condition.getJSONObject("$expression");
                JSONObject predicate=expression==null?null:singlePredicate(expression.getJSONObject("root"));
                if(predicate!=null&&"reviewResult".equals(predicate.getString("field"))
                        &&"MISJUDGED_VALID".equals(predicate.getString("value"))
                        &&"EQ".equals(predicate.getString("operator")))matching++;
            }
            if(returnEdges!=1||matching!=1)throw routingMismatch();
        }
        catch(TodoException expected){throw expected;}
        catch(Exception invalid){throw routingMismatch();}
    }

    private JSONObject singlePredicate(JSONObject root)
    {
        if(root==null)return null;
        if(root.containsKey("field"))return root;
        JSONArray conditions=root.getJSONArray("conditions");
        if(!"AND".equals(root.getString("type"))||conditions==null||conditions.size()!=1)return null;
        Object only=conditions.get(0);
        return only instanceof JSONObject predicate&&predicate.containsKey("field")?predicate:null;
    }

    private void validateLockedRoute(Map<String,Object> version,String selfCode,long selfVersionId,
            String resultValue,String effectKind,String targetCode,long targetVersionId)
    {
        try
        {
            JSONObject root=JSON.parseObject(text(value(version,"compiled_json","compiledJson")));
            JSONObject routing=root==null?null:root.getJSONObject("routing");
            routing=routing==null?null:routing.getJSONObject("config");
            if(routing==null)throw routingMismatch();
            int matching=0;
            for(Object raw:routing.getJSONArray("businessOutcomes"))
            {
                if(!(raw instanceof JSONObject outcome))throw routingMismatch();
                String value=outcome.getString("resultValue");
                if(value==null)value=outcome.getString("value");
                String configuredCode=outcome.getString("targetTemplateCode");
                Long configuredVersion=outcome.getLong("targetVersionId");
                if(resultValue.equals(value))
                {
                    matching++;
                    boolean implicitSelf="SCHEDULE_SELF".equals(effectKind)
                            &&selfCode.equals(targetCode)&&configuredVersion==null;
                    if(!effectKind.equals(outcome.getString("effectKind"))
                            ||!targetCode.equals(configuredCode)
                            ||(!implicitSelf&&!Objects.equals(targetVersionId,configuredVersion)))throw routingMismatch();
                }
                else if(configuredCode!=null||configuredVersion!=null)throw routingMismatch();
            }
            if(matching!=1)throw routingMismatch();

            JSONArray nodes=routing.getJSONArray("nodes");
            Set<Long> expected=new LinkedHashSet<>();
            expected.add(selfVersionId);
            expected.add(targetVersionId);
            Set<Long> actual=taskVersionIds(nodes);
            if(!expected.equals(actual))throw routingMismatch();
        }
        catch(TodoException expected){throw expected;}
        catch(Exception invalid){throw routingMismatch();}
    }

    private Set<Long> taskVersionIds(JSONArray values)
    {
        if(values==null)throw routingMismatch();
        Set<Long> result=new LinkedHashSet<>();
        for(Object raw:values)
        {
            if(!(raw instanceof JSONObject value))throw routingMismatch();
            if(!"TASK".equals(value.getString("type")))continue;
            Long id=value.getLong("templateVersionId");
            if(id==null)throw routingMismatch();
            result.add(id);
        }
        return result;
    }

    private Map<String,Object> createDisabledTrigger(Map<String,Object> td001,Actor actor)
    {
        Map<String,Object> row=new HashMap<>();
        row.put("ruleCode","TRIGGER_LEAD_ASSIGNED_TD001_V"+integer(value(td001,"version_no","versionNo")));
        row.put("ruleName","线索已分配 -> TD-001 v"+integer(value(td001,"version_no","versionNo")));
        row.put("eventType","LEAD_ASSIGNED");row.put("payloadVersion",1);
        row.put("templateId",number(value(td001,"template_id","templateId")));
        row.put("templateVersionId",number(value(td001,"version_id","versionId")));
        row.put("businessType","LEAD");row.put("entrySlotCode",ENTRY_SLOT);row.put("enabled","N");
        row.put("conditionJson",null);row.put("createBy",actor.userName());row.put("updateBy",actor.userName());
        try{mapper.insertLeadReleaseDisabledTrigger(row);}catch(DuplicateKeyException concurrent){/* re-read below */}
        Map<String,Object> created=mapper.selectLeadReleaseTriggerForUpdate(ENTRY_SLOT,
                number(value(td001,"version_id","versionId")));
        if(created==null||created.isEmpty())
            throw new TodoException("TODO_LEAD_RELEASE_TRIGGER_PREPARE_FAILED","Disabled lead entry trigger could not be prepared");
        return created;
    }

    private void validatePreparedTrigger(Map<String,Object> trigger,Map<String,Object> td001)
    {
        long versionId=number(value(td001,"version_id","versionId"));
        String deterministicCode="TRIGGER_LEAD_ASSIGNED_TD001_V"+integer(value(td001,"version_no","versionNo"));
        if(!ENTRY_SLOT.equals(text(value(trigger,"entry_slot_code","entrySlotCode")))
                ||!"TD-001".equals(text(value(trigger,"template_code","templateCode")))
                ||!Objects.equals(versionId,number(value(trigger,"template_version_id","templateVersionId")))
                ||!deterministicCode.equals(text(value(trigger,"rule_code","ruleCode")))
                ||!"LEAD_ASSIGNED".equals(text(value(trigger,"event_type","eventType")))
                ||!"LEAD".equals(text(value(trigger,"business_type","businessType")))
                ||!"N".equals(text(value(trigger,"enabled","enabled"))))
            throw new TodoException("TODO_LEAD_RELEASE_TRIGGER_INVALID","Release trigger must be a disabled TD-001 entry candidate");
    }

    private Long validateAction(Map<String,Object> action,LeadReleaseCommand command,String fingerprint,int inserted)
    {
        if(action==null||!ACTION_TYPE.equals(text(value(action,"action_type","actionType")))
                ||!ENTITY_TYPE.equals(text(value(action,"entity_type","entityType")))
                ||!fingerprint.equals(text(value(action,"request_fingerprint","requestFingerprint"))))
            throw new TodoException("TODO_LEAD_RELEASE_ACTION_CONFLICT","actionId is already bound to another release");
        Long entity=number(value(action,"entity_id","entityId"));
        String status=text(value(action,"action_status","actionStatus"));
        if(entity!=null)
        {
            if(!"APPLIED".equals(status))throw new TodoException("TODO_LEAD_RELEASE_ACTION_CONFLICT","Recorded release action is incomplete");
            return entity;
        }
        if(inserted<=0||!"CLAIMED".equals(status))
            throw new TodoException("TODO_LEAD_RELEASE_ACTION_CONFLICT","Recorded release action is incomplete");
        return null;
    }

    static String fingerprint(LeadReleaseCommand command)
    {
        Map<String,Object> canonical=new TreeMap<>();canonical.put("actionType",ACTION_TYPE);
        canonical.put("command",JSON.parse(JSON.toJSONString(command)));
        return TodoDefinitionSimulationService.sha256(JSON.toJSONString(canonical));
    }

    private LeadReleaseView view(LeadReleaseCommand command,long triggerId,LocalDateTime at,String by)
    {return new LeadReleaseView(ENTRY_SLOT,triggerId,command.td001VersionId(),downstream(command),at,by);}
    private Map<String,Long> allVersions(LeadReleaseCommand command)
    {Map<String,Long> values=new LinkedHashMap<>();values.put("TD-001",command.td001VersionId());values.putAll(downstream(command));return values;}
    private Map<String,Long> downstream(LeadReleaseCommand command)
    {Map<String,Long> values=new LinkedHashMap<>();values.put("TD-002",command.td002VersionId());values.put("TD-003",command.td003VersionId());values.put("TD-004",command.td004VersionId());return values;}
    private Map<String,Long> activeDownstream(Map<String,Object> active)
    {
        try
        {
            JSONObject root=JSON.parseObject(text(value(active,"compiled_json","compiledJson")));
            JSONObject routing=root==null?null:root.getJSONObject("routing");
            routing=routing==null?null:routing.getJSONObject("config");
            return routing==null?Map.of():targets(routing.getJSONArray("businessOutcomes"),
                    "targetTemplateCode","targetVersionId",false);
        }
        catch(Exception ignored){return Map.of();}
    }
    private TodoException routingMismatch(){return new TodoException("TODO_LEAD_RELEASE_ROUTING_MISMATCH","Coordinated lead routing must lock the exact approved template versions");}
    private Object value(Map<String,Object> row,String snake,String camel){return row.containsKey(snake)?row.get(snake):row.get(camel);}
    private String text(Object value){return value==null?null:String.valueOf(value);}
    private Long number(Object value){return value==null?null:Long.valueOf(String.valueOf(value));}
    private int integer(Object value){return value==null?0:Integer.parseInt(String.valueOf(value));}
    private boolean truth(Object value){return value instanceof Boolean b?b:value!=null&&Set.of("1","true","Y").contains(String.valueOf(value));}
    private LocalDateTime time(Object value){return value instanceof LocalDateTime time?time:null;}
}
