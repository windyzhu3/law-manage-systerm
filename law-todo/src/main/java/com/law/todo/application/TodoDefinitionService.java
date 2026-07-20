package com.law.todo.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoDefinitionCommands.CopyTemplateCommand;
import com.law.todo.application.command.TodoDefinitionCommands.CopyVersionCommand;
import com.law.todo.application.command.TodoDefinitionCommands.PublishDraftCommand;
import com.law.todo.application.command.TodoDefinitionCommands.RollbackDraftCommand;
import com.law.todo.application.command.TodoDefinitionCommands.RuleReference;
import com.law.todo.application.command.TodoDefinitionCommands.UpdateDraftCommand;
import com.law.todo.definition.catalog.TodoDecisionService;
import com.law.todo.definition.catalog.TodoEventCatalogService;
import com.law.todo.definition.codec.LegacyDefinitionAdapter;
import com.law.todo.definition.codec.TodoDefinitionCodec;
import com.law.todo.definition.compiler.DefinitionValidationReport;
import com.law.todo.definition.compiler.DefinitionValidationReport.ValidationIssue;
import com.law.todo.definition.compiler.TodoDefinitionCompiler;
import com.law.todo.definition.compiler.TodoDefinitionCompiler.CompilationContext;
import com.law.todo.definition.compiler.TodoDefinitionCompiler.TemplateVersion;
import com.law.todo.definition.model.TodoDefinitionDocument;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.mapper.TodoConfigurationMapper;
import com.law.todo.spi.TodoAutoActionCapabilityRegistry;

@Service
public class TodoDefinitionService
{
    private static final String DRAFT = "DRAFT";
    private static final String PUBLISHED = "PUBLISHED";

    private final TodoMapper mapper;
    private final TodoDefinitionCompiler compiler;
    private final TodoConfigurationMapper configurationMapper;
    private final TodoDefinitionCodec codec = new TodoDefinitionCodec();
    private final LegacyDefinitionAdapter legacyAdapter = new LegacyDefinitionAdapter();

    @Autowired
    public TodoDefinitionService(TodoMapper mapper, TodoDefinitionCompiler compiler,
            TodoConfigurationMapper configurationMapper)
    {
        this.mapper = mapper;
        this.compiler = compiler;
        this.configurationMapper=configurationMapper;
    }

    public TodoDefinitionService(TodoMapper mapper, TodoDefinitionCompiler compiler)
    {this(mapper,compiler,null);}

    public TodoDefinitionService(TodoMapper mapper)
    {
        this(mapper,new TodoAutoActionCapabilityRegistry(List.of()));
    }

    /** Direct construction must supply the same capability registry as the executing application. */
    public TodoDefinitionService(TodoMapper mapper,TodoAutoActionCapabilityRegistry autoActions)
    {
        this(mapper,new TodoDefinitionCompiler(new TodoDefinitionCodec(),new TodoEventCatalogService(mapper),
                new TodoDecisionService(mapper),new com.law.todo.expression.ConditionValidator(),autoActions));
    }

    public List<Map<String, Object>> versions(Long templateId)
    {
        requireTemplate(templateId);
        return mapper.selectTemplateVersions(templateId);
    }

    @Transactional
    public Long copyTemplate(Long sourceTemplateId, CopyTemplateCommand command, Actor actor)
    {
        Long repeated = repeatedEntity(command.actionId());
        if (repeated != null)
            return repeated;
        Map<String, Object> source = requireTemplate(sourceTemplateId);
        claim(command.actionId(), "COPY_TEMPLATE", "TEMPLATE", sourceTemplateId, actor,
                Map.of("newTemplateCode", command.newTemplateCode()));
        Map<String, Object> target = new HashMap<>();
        target.put("templateCode", command.newTemplateCode());
        target.put("templateName", command.newTemplateName());
        target.put("businessType", value(source, "business_type", "businessType"));
        target.put("status", "0");
        target.put("createBy", actor.userName());
        if (mapper.insertTemplate(target) <= 0)
            throw new TodoException("TODO_TEMPLATE_COPY_FAILED", "Template copy failed");
        Long id = longValue(target.get("templateId"));
        mapper.updateDefinitionActionEntity(command.actionId(), id);
        return id;
    }

    @Transactional
    public Long copyVersion(Long templateId, int sourceVersionNo, CopyVersionCommand command, Actor actor)
    {
        Long repeated = repeatedEntity(command.actionId());
        if (repeated != null)
            return repeated;
        Map<String, Object> source = mapper.selectTemplateVersion(templateId, sourceVersionNo);
        if (source == null || source.isEmpty())
            throw new TodoException("TODO_TEMPLATE_VERSION_NOT_FOUND", "Template version not found");
        claim(command.actionId(), "COPY_VERSION", "VERSION",
                longValue(value(source, "version_id", "versionId")), actor,
                Map.of("newVersionNo", command.newVersionNo()));
        Map<String, Object> target = new HashMap<>();
        target.put("templateId", templateId);
        target.put("versionNo", command.newVersionNo());
        target.put("status", DRAFT);
        target.put("sourceVersionId", longValue(value(source, "version_id", "versionId")));
        TodoDefinitionDocument copiedDefinition = definition(source);
        Object sourceSchemaVersion = value(source, "definition_schema_version",
                "definitionSchemaVersion");
        String sourceDefinitionJson = text(value(source, "definition_json", "definitionJson"));
        target.put("definitionSchemaVersion", sourceSchemaVersion == null
                ? copiedDefinition.schemaVersion() : sourceSchemaVersion);
        target.put("definitionJson", sourceDefinitionJson == null || sourceDefinitionJson.isBlank()
                ? codec.canonicalJson(copiedDefinition) : sourceDefinitionJson);
        target.put("compiledJson", null);
        target.put("definitionHash", null);
        target.put("validationReportJson", null);
        projectLegacyRules(copiedDefinition, target);
        if (mapper.insertTemplateVersion(target) <= 0)
            throw new TodoException("TODO_TEMPLATE_VERSION_COPY_FAILED", "Template version copy failed");
        Long id = longValue(target.get("versionId"));
        mapper.updateDefinitionActionEntity(command.actionId(), id);
        return id;
    }

    /** Creates a new editable draft from immutable published history; history is never modified. */
    @Transactional
    public Long rollbackDraft(Long sourceVersionId,RollbackDraftCommand command,Actor actor)
    {
        Map<String,Object> source=requireVersion(sourceVersionId);String status=text(value(source,"status","status"));
        if(!PUBLISHED.equals(status)&&!"RETIRED".equals(status))
            throw new TodoException("TODO_ROLLBACK_SOURCE_IMMUTABLE_REQUIRED","Rollback source must be published or retired");
        Long templateId=longValue(value(source,"template_id","templateId"));
        String fingerprint=rollbackFingerprint(sourceVersionId,templateId,command.newVersionNo(),actor);
        Long repeated=claimRollback(command.actionId(),sourceVersionId,templateId,command.newVersionNo(),actor,fingerprint);
        if(repeated!=null)return repeated;
        TodoDefinitionDocument copied=definition(source);Map<String,Object> target=new HashMap<>();
        target.put("templateId",templateId);target.put("versionNo",command.newVersionNo());target.put("status",DRAFT);
        target.put("sourceVersionId",sourceVersionId);target.put("rollbackSourceVersionId",sourceVersionId);target.put("definitionSchemaVersion",copied.schemaVersion());
        target.put("definitionJson",codec.canonicalJson(copied));target.put("compiledJson",null);target.put("definitionHash",null);target.put("validationReportJson",null);
        projectLegacyRules(copied,target);
        try
        {
            if(mapper.insertTemplateVersion(target)<=0)
                throw new TodoException("TODO_ROLLBACK_DRAFT_FAILED","Rollback draft creation failed");
        }
        catch(DuplicateKeyException collision)
        {
            throw new TodoException("TODO_DEFINITION_VERSION_CONFLICT","The rollback target version already exists");
        }
        Long targetId=longValue(target.get("versionId"));
        if(mapper.completeDefinitionAction(command.actionId(),fingerprint,targetId)<=0)
            throw new TodoException("TODO_DEFINITION_ACTION_CONFLICT","Rollback action claim could not be completed");
        return targetId;
    }

    private Long claimRollback(String actionId,Long sourceVersionId,Long templateId,Integer targetVersionNo,
            Actor actor,String fingerprint)
    {
        Map<String,Object> action=new HashMap<>();action.put("actionId",actionId);action.put("actionType","ROLLBACK_DRAFT");
        action.put("entityType","VERSION");action.put("sourceEntityId",sourceVersionId);action.put("operatorId",actor.userId());
        action.put("operatorName",actor.userName());action.put("operatorDeptId",actor.deptId());action.put("requestFingerprint",fingerprint);
        action.put("payloadJson",JSON.toJSONString(Map.of("targetTemplateId",templateId,"newVersionNo",targetVersionNo)));
        mapper.insertDefinitionActionClaim(action);
        Map<String,Object> claimed=mapper.selectDefinitionActionForUpdate(actionId);
        if(claimed==null||claimed.isEmpty()||!"ROLLBACK_DRAFT".equals(text(value(claimed,"action_type","actionType")))
                ||!fingerprint.equals(text(value(claimed,"request_fingerprint","requestFingerprint")))
                ||!sourceVersionId.equals(longValue(value(claimed,"source_entity_id","sourceEntityId")))
                ||!actor.userId().equals(longValue(value(claimed,"operator_id","operatorId")))
                ||!actor.userName().equals(text(value(claimed,"operator_name","operatorName"))))
            throw new TodoException("TODO_DEFINITION_ACTION_CONFLICT","Definition action idempotency key belongs to a different request");
        Long result=longValue(value(claimed,"entity_id","entityId"));
        if(result!=null)
        {
            if(!"APPLIED".equals(text(value(claimed,"action_status","actionStatus"))))
                throw new TodoException("TODO_DEFINITION_ACTION_CONFLICT","Recorded rollback result is incomplete");
            return result;
        }
        if(!"CLAIMED".equals(text(value(claimed,"action_status","actionStatus"))))
            throw new TodoException("TODO_DEFINITION_ACTION_CONFLICT","Rollback action is not claimable");
        return null;
    }

    private String rollbackFingerprint(Long sourceVersionId,Long templateId,Integer targetVersionNo,Actor actor)
    {
        Map<String,Object> value=new java.util.TreeMap<>();value.put("actionType","ROLLBACK_DRAFT");
        value.put("actorDeptId",actor.deptId());value.put("actorId",actor.userId());value.put("actorName",actor.userName());
        value.put("sourceVersionId",sourceVersionId);value.put("targetTemplateId",templateId);value.put("targetVersionNo",targetVersionNo);
        return TodoDefinitionSimulationService.sha256(JSON.toJSONString(value));
    }

    @Transactional
    public Long updateDraft(UpdateDraftCommand command, Actor actor)
    {
        Map<String, Object> current = requireVersion(command.versionId());
        requireDraft(current);
        String storedDefinitionJson=text(value(current,"definition_json","definitionJson"));
        TodoDefinitionDocument definition;
        if (command.definitionJson() != null && !command.definitionJson().isBlank())
        {
            try { definition=codec.read(command.definitionJson()); }
            catch (RuntimeException invalid) { throw new TodoException("TODO_TEMPLATE_JSON_INVALID","Invalid template JSON: definitionJson"); }
            String code=text(value(current,"template_code","templateCode"));
            if (code != null && !code.equals(definition.templateCode()))
                throw new TodoException("TODO_TEMPLATE_CODE_MISMATCH","Definition templateCode does not match the draft");
        }
        else
        {
            validate(command.ownerRuleJson(), command.dodRuleJson(), command.slaRuleJson(),command.nextRuleJson(), command.uiSchemaJson());
            Map<String,Object> legacy=new HashMap<>(current);
            legacy.put("owner_rule_json",command.ownerRuleJson());legacy.put("dod_rule_json",command.dodRuleJson());legacy.put("sla_rule_json",command.slaRuleJson());legacy.put("next_rule_json",command.nextRuleJson());legacy.put("ui_schema_json",command.uiSchemaJson());
            definition=legacyAdapter.fromLegacy(legacy);
        }
        validateDefinition(definition);
        String canonical=codec.canonicalJson(definition);
        String fingerprint=updateFingerprint(command,actor,canonical,command.expectedDefinitionJson());
        Long repeated=claimUpdateDraft(command,actor,fingerprint);
        if(repeated!=null)return repeated;
        if (storedDefinitionJson != null && !storedDefinitionJson.isBlank()
                && (command.expectedDefinitionJson()==null || command.expectedDefinitionJson().isBlank()))
            throw new TodoException("TODO_TEMPLATE_SOURCE_TOKEN_REQUIRED","Reload the draft before saving a canonical definition");
        if (command.expectedDefinitionJson()!=null && !command.expectedDefinitionJson().equals(storedDefinitionJson))
            throw new TodoException("TODO_TEMPLATE_VERSION_CONFLICT","Draft version changed; reload before saving");
        Map<String, Object> update = new HashMap<>();
        update.put("versionId", command.versionId());
        update.put("definitionSchemaVersion",definition.schemaVersion());
        update.put("definitionJson",canonical);update.put("expectedDefinitionJson",storedDefinitionJson);
        update.put("compiledJson",null);update.put("definitionHash",null);update.put("validationReportJson",null);
        projectLegacyRules(definition,update);
        update.put("changeSummary",command.changeSummary());update.put("impactScope",command.impactScope());
        if (mapper.updateTemplateVersionDraft(update) <= 0)
            throw new TodoException("TODO_TEMPLATE_VERSION_CONFLICT", "Draft version changed");
        replaceDraftRuleReferences(command.versionId(),command.ruleReferences());
        if(mapper.completeDefinitionAction(command.actionId(),fingerprint,command.versionId())<=0)
            throw new TodoException("TODO_DEFINITION_ACTION_CONFLICT","Draft action claim could not be completed");
        return command.versionId();
    }

    private void validateDefinition(TodoDefinitionDocument definition)
    {
        validateDefinitionStructure(definition);
        validateStableOwnerReferences(definition.owner().config());
        validate(JSON.toJSONString(definition.owner().config()),JSON.toJSONString(definition.dod().config()),
                JSON.toJSONString(definition.sla().config()),JSON.toJSONString(definition.routing().config()),JSON.toJSONString(definition.ui().config()));
    }

    private void validateDefinitionStructure(TodoDefinitionDocument definition)
    {
        if (definition == null || definition.event() == null || definition.owner() == null || definition.dod() == null
                || definition.sla() == null || definition.ui() == null || definition.routing() == null)
            throw new TodoException("TODO_TEMPLATE_JSON_INVALID","Canonical definition contains required missing sections");
    }

    @SuppressWarnings("unchecked")
    private void validateStableOwnerReferences(Object value)
    {
        if(value instanceof java.util.Collection<?> entries)
        {
            for(Object entry:entries)validateStableOwnerReferences(entry);
            return;
        }
        if(!(value instanceof Map<?,?> raw))return;
        Map<String,Object> config=new HashMap<>();raw.forEach((key,entry)->config.put(String.valueOf(key),entry));
        Object roleKey=config.get("roleKey");
        if(roleKey!=null && !String.valueOf(roleKey).isBlank()
                && mapper.selectRoleIdByKey(String.valueOf(roleKey))==null)
            throw new TodoException("TODO_OWNER_ROLE_KEY_NOT_FOUND","Definition owner references an unknown or disabled roleKey");
        Object departmentCode=config.get("departmentCode");
        if(departmentCode!=null && !String.valueOf(departmentCode).isBlank())
        {
            String code=String.valueOf(departmentCode);
            if(code.matches("DEPT_[0-9]+"))
                throw new TodoException("TODO_OWNER_DEPARTMENT_CODE_LEGACY","Definition owner must use a managed stable departmentCode");
            if(mapper.selectDepartmentIdByCode(code)==null)
                throw new TodoException("TODO_OWNER_DEPARTMENT_CODE_NOT_FOUND","Definition owner references an unknown or disabled departmentCode");
        }
        for(Object child:config.values())validateStableOwnerReferences(child);
    }

    private String updateFingerprint(UpdateDraftCommand command,Actor actor,String canonical,String expectedDefinitionJson)
    {
        Map<String,Object> value=new java.util.TreeMap<>();value.put("actionType","UPDATE_DRAFT");
        value.put("actorDeptId",actor.deptId());value.put("actorId",actor.userId());value.put("actorName",actor.userName());
        value.put("versionId",command.versionId());value.put("definitionJson",canonical);value.put("expectedDefinitionJson",expectedDefinitionJson);
        value.put("ruleReferences",command.ruleReferences());value.put("changeSummary",command.changeSummary());value.put("impactScope",command.impactScope());
        return TodoDefinitionSimulationService.sha256(JSON.toJSONString(value));
    }

    private Long claimUpdateDraft(UpdateDraftCommand command,Actor actor,String fingerprint)
    {
        Map<String,Object> action=new HashMap<>();action.put("actionId",command.actionId());action.put("actionType","UPDATE_DRAFT");
        action.put("entityType","VERSION");action.put("sourceEntityId",command.versionId());action.put("operatorId",actor.userId());
        action.put("operatorName",actor.userName());action.put("operatorDeptId",actor.deptId());action.put("requestFingerprint",fingerprint);
        action.put("payloadJson",JSON.toJSONString(Map.of("versionId",command.versionId())));
        mapper.insertDefinitionActionClaim(action);
        Map<String,Object> claimed=mapper.selectDefinitionActionForUpdate(command.actionId());
        if(claimed==null||claimed.isEmpty()||!"UPDATE_DRAFT".equals(text(value(claimed,"action_type","actionType")))
                ||!fingerprint.equals(text(value(claimed,"request_fingerprint","requestFingerprint")))
                ||!command.versionId().equals(longValue(value(claimed,"source_entity_id","sourceEntityId")))
                ||!actor.userId().equals(longValue(value(claimed,"operator_id","operatorId")))
                ||!actor.userName().equals(text(value(claimed,"operator_name","operatorName"))))
            throw new TodoException("TODO_DEFINITION_ACTION_CONFLICT","Definition action idempotency key belongs to a different request");
        Long result=longValue(value(claimed,"entity_id","entityId"));
        if(result!=null)
        {
            if(!"APPLIED".equals(text(value(claimed,"action_status","actionStatus"))))
                throw new TodoException("TODO_DEFINITION_ACTION_CONFLICT","Recorded draft result is incomplete");
            return result;
        }
        if(!"CLAIMED".equals(text(value(claimed,"action_status","actionStatus"))))
            throw new TodoException("TODO_DEFINITION_ACTION_CONFLICT","Draft action is not claimable");
        return null;
    }

    /** A null reference list preserves compatibility; an explicit empty list clears the editable bindings. */
    private void replaceDraftRuleReferences(Long versionId,List<RuleReference> references)
    {
        if(references==null)return;
        if(configurationMapper==null)
            throw new TodoException("TODO_CONFIGURATION_BINDING_UNAVAILABLE","Rule binding persistence is unavailable");
        configurationMapper.deleteDraftRuleRefs(versionId);
        references.stream().sorted(java.util.Comparator.comparing(RuleReference::order)).forEach(reference->{
            Map<String,Object> row=new HashMap<>();row.put("versionId",versionId);row.put("refType",reference.type());
            row.put("refIdValue",reference.id());row.put("sortOrder",reference.order());row.put("configJson",null);
            if(configurationMapper.insertDraftRuleRef(row)<=0)
                throw new TodoException("TODO_TEMPLATE_RULE_BINDING_FAILED","Draft rule reference could not be saved");
        });
    }

    @Transactional(noRollbackFor = PreflightFailedException.class)
    public Long publish(PublishDraftCommand command, Actor actor)
    {
        Long repeated = repeatedEntity(command.actionId());
        if (repeated != null)
            return repeated;
        Map<String, Object> current = requireVersion(command.versionId());
        requireDraft(current);
        RuleBinding binding=bindReferencedRules(command.versionId(),definition(current));
        TodoDefinitionDocument definition=binding.definition();
        if(binding.bound())
        {
            Map<String,Object> snapshot=snapshotRow(command.versionId(),definition);
            if(mapper.updateDefinitionDocument(snapshot)<=0)
                throw new TodoException("TODO_TEMPLATE_VERSION_CONFLICT","Draft version changed before rule snapshot compilation");
            current=new HashMap<>(current);
            current.put("definition_json",snapshot.get("definitionJson"));current.put("definitionJson",snapshot.get("definitionJson"));
            current.put("owner_rule_json",snapshot.get("ownerRuleJson"));current.put("dod_rule_json",snapshot.get("dodRuleJson"));
            current.put("sla_rule_json",snapshot.get("slaRuleJson"));current.put("next_rule_json",snapshot.get("nextRuleJson"));
            current.put("ui_schema_json",snapshot.get("uiSchemaJson"));
        }
        PreflightResult preflight = null;
        if (isPrdBlocked(current))
        {
            preflight = preflight(command.versionId(), true,current,definition);
            if (!preflight.publishable())
                throw new PreflightFailedException();
        }
        validate(text(value(current, "owner_rule_json", "ownerRuleJson")),
                text(value(current, "dod_rule_json", "dodRuleJson")),
                text(value(current, "sla_rule_json", "slaRuleJson")),
                text(value(current, "next_rule_json", "nextRuleJson")),
                text(value(current, "ui_schema_json", "uiSchemaJson")));
        if (preflight == null)
            preflight = preflight(command.versionId(), true,current,definition);
        if (!preflight.publishable())
            throw new PreflightFailedException();
        claim(command.actionId(), "PUBLISH_VERSION", "VERSION", command.versionId(), actor,
                Map.of("definitionHash", preflight.report().definitionHash()));
        if (mapper.publishTemplateVersionConditionally(command.versionId(),
                preflight.report().definitionHash(), actor.userName()) <= 0)
            throw new TodoException("TODO_TEMPLATE_VERSION_CONFLICT",
                    "Definition changed or is no longer a draft");
        return command.versionId();
    }

    @Transactional
    public PreflightResult preflight(long versionId)
    {
        return preflight(versionId, false);
    }

    private PreflightResult preflight(long versionId, boolean guardedPublishPreflight)
    {
        Map<String, Object> current = requireVersion(versionId);
        return preflight(versionId,guardedPublishPreflight,current,definition(current));
    }

    private PreflightResult preflight(long versionId,boolean guardedPublishPreflight,Map<String,Object> current,
            TodoDefinitionDocument definition)
    {
        validateDefinitionStructure(definition);
        boolean prdBlocked = isPrdBlocked(current);
        if (!prdBlocked)
            validateStableOwnerReferences(definition.owner().config());
        CompilationContext context = new CompilationContext(versionId, guardedPublishPreflight, id -> {
            Map<String, Object> target = mapper.selectTemplateVersionById(id);
            return target == null || target.isEmpty() ? null
                    : new TemplateVersion(id, text(value(target, "status", "status")));
        });
        DefinitionValidationReport report = applyPrdCatalogueGate(compiler.compile(definition, context), current,
                prdBlocked);
        Map<String, Object> persisted = new HashMap<>();
        persisted.put("versionId", versionId);
        persisted.put("definitionSchemaVersion", definition.schemaVersion());
        persisted.put("definitionJson", codec.canonicalJson(definition));
        persisted.put("compiledJson", report.compiledJson());
        persisted.put("definitionHash", report.definitionHash());
        persisted.put("validationReportJson", JSON.toJSONString(report));
        persisted.put("sourceDefinitionJson", value(current, "definition_json", "definitionJson"));
        persisted.put("sourceOwnerRuleJson", value(current, "owner_rule_json", "ownerRuleJson"));
        persisted.put("sourceDodRuleJson", value(current, "dod_rule_json", "dodRuleJson"));
        persisted.put("sourceSlaRuleJson", value(current, "sla_rule_json", "slaRuleJson"));
        persisted.put("sourceNextRuleJson", value(current, "next_rule_json", "nextRuleJson"));
        persisted.put("sourceUiSchemaJson", value(current, "ui_schema_json", "uiSchemaJson"));
        if (mapper.updateDefinitionCompilation(persisted) <= 0)
            throw new TodoException("TODO_TEMPLATE_VERSION_CONFLICT", "Definition changed during preflight");
        return new PreflightResult(versionId, report);
    }

    private Map<String,Object> snapshotRow(Long versionId,TodoDefinitionDocument definition)
    {
        Map<String,Object> row=new HashMap<>();row.put("versionId",versionId);row.put("definitionSchemaVersion",definition.schemaVersion());
        row.put("definitionJson",codec.canonicalJson(definition));row.put("compiledJson",null);row.put("definitionHash",null);
        row.put("validationReportJson",null);projectLegacyRules(definition,row);return row;
    }

    private RuleBinding bindReferencedRules(Long versionId,TodoDefinitionDocument definition)
    {
        if(configurationMapper==null)return new RuleBinding(definition,false);
        List<Map<String,Object>> references=configurationMapper.selectDraftRuleRefs(versionId);
        if(references==null||references.isEmpty())return new RuleBinding(definition,false);
        Map<String,Object> sla=new java.util.LinkedHashMap<>(definition.sla().config());
        Map<String,Object> dod=new java.util.LinkedHashMap<>(definition.dod().config());
        List<Map<String,Object>> slaSnapshots=new java.util.ArrayList<>();
        List<Map<String,Object>> dodSnapshots=new java.util.ArrayList<>();
        for(Map<String,Object> reference:references)
        {
            String type=text(value(reference,"ref_type","refType"));Long id=longValue(value(reference,"ref_id_value","refIdValue"));
            if("SLA".equals(type))
            {
                Map<String,Object> rule=requiredActiveRule(configurationMapper.selectSlaRule(id),"SLA",id);
                Map<String,Object> snapshot=slaSnapshot(rule);slaSnapshots.add(snapshot);sla.put("calendarCode",snapshot.get("calendarCode"));
                sla.put("minutes",snapshot.get("minutes"));
            }
            else if("DOD".equals(type))
            {
                Map<String,Object> snapshot=dodSnapshot(requiredActiveRule(configurationMapper.selectDodRule(id),"DOD",id));
                dodSnapshots.add(snapshot);mergeDodSnapshot(dod,snapshot);
            }
            else throw new TodoException("TODO_TEMPLATE_RULE_TYPE_INVALID","Unsupported draft rule reference type: "+type);
        }
        if(!slaSnapshots.isEmpty())sla.put("ruleSnapshots",slaSnapshots);
        if(!dodSnapshots.isEmpty())dod.put("ruleSnapshots",dodSnapshots);
        return new RuleBinding(new TodoDefinitionDocument(definition.schemaVersion(),definition.templateCode(),definition.event(),definition.owner(),
                new TodoDefinitionDocument.DodRule(dod),new TodoDefinitionDocument.SlaRule(sla),definition.ui(),definition.routing(),
                definition.autoActions(),definition.decisionRefs(),definition.acceptanceRefs()),true);
    }

    private record RuleBinding(TodoDefinitionDocument definition,boolean bound) { }

    private Map<String,Object> requiredActiveRule(Map<String,Object> rule,String type,Long id)
    {
        if(rule==null||rule.isEmpty())throw new TodoException("TODO_TEMPLATE_RULE_NOT_FOUND",type+" rule not found: "+id);
        if(!"0".equals(text(value(rule,"status","status"))))
            throw new TodoException("TODO_TEMPLATE_RULE_DISABLED",type+" rule is disabled: "+id);
        return rule;
    }

    private Map<String,Object> slaSnapshot(Map<String,Object> rule)
    {
        Map<String,Object> snapshot=new java.util.LinkedHashMap<>();snapshot.put("ruleId",longValue(value(rule,"sla_rule_id","slaRuleId")));
        snapshot.put("ruleCode",text(value(rule,"rule_code","ruleCode")));snapshot.put("ruleName",text(value(rule,"rule_name","ruleName")));
        snapshot.put("slaType",text(value(rule,"sla_type","slaType")));snapshot.put("calendarCode",text(value(rule,"calendar_code","calendarCode")));
        snapshot.put("startStrategy",text(value(rule,"start_strategy","startStrategy")));snapshot.put("minutes",slaMinutes(rule));
        snapshot.put("softRemindPercent",value(rule,"soft_remind_percent","softRemindPercent"));snapshot.put("hardRemindPercent",value(rule,"hard_remind_percent","hardRemindPercent"));
        snapshot.put("escalatePercent",value(rule,"escalate_percent","escalatePercent"));snapshot.put("pausePolicyJson",text(value(rule,"pause_policy_json","pausePolicyJson")));
        snapshot.put("escalationPolicyJson",text(value(rule,"escalation_policy_json","escalationPolicyJson")));snapshot.put("autoActionJson",text(value(rule,"auto_action_json","autoActionJson")));
        return snapshot;
    }

    private long slaMinutes(Map<String,Object> rule)
    {
        long duration=Long.parseLong(String.valueOf(value(rule,"duration_value","durationValue")));String unit=text(value(rule,"duration_unit","durationUnit"));
        return switch(unit){case "DAY" -> duration*24*60;case "HOUR" -> duration*60;default -> duration;};
    }

    private Map<String,Object> dodSnapshot(Map<String,Object> rule)
    {
        Map<String,Object> snapshot=new java.util.LinkedHashMap<>();snapshot.put("ruleId",longValue(value(rule,"dod_rule_id","dodRuleId")));
        snapshot.put("ruleCode",text(value(rule,"rule_code","ruleCode")));snapshot.put("ruleName",text(value(rule,"rule_name","ruleName")));
        snapshot.put("ruleType",text(value(rule,"rule_type","ruleType")));snapshot.put("requiredFields",jsonArray(rule,"required_fields_json","requiredFieldsJson"));
        snapshot.put("requiredAttachments",jsonArray(rule,"required_attachments_json","requiredAttachmentsJson"));snapshot.put("conditionalRules",jsonArray(rule,"conditional_rules_json","conditionalRulesJson"));
        snapshot.put("validatorRefs",jsonArray(rule,"validator_refs_json","validatorRefsJson"));snapshot.put("errorMessages",jsonObject(rule,"error_messages_json","errorMessagesJson"));return snapshot;
    }

    private List<Object> jsonArray(Map<String,Object> rule,String snake,String camel)
    {String json=text(value(rule,snake,camel));return json==null||json.isBlank()?List.of():JSON.parseArray(json,Object.class);}
    private Map<String,Object> jsonObject(Map<String,Object> rule,String snake,String camel)
    {String json=text(value(rule,snake,camel));if(json==null||json.isBlank())return Map.of();Map<String,Object> result=new java.util.LinkedHashMap<>();JSON.parseObject(json).forEach((key,value)->result.put(key,value));return result;}
    private void mergeDodSnapshot(Map<String,Object> target,Map<String,Object> snapshot)
    {for(String field:List.of("requiredFields","requiredAttachments","conditionalRules","validatorRefs"))
        {List<Object> values=new java.util.ArrayList<>();Object current=target.get(field);if(current instanceof List<?> list)values.addAll(list);Object referenced=snapshot.get(field);if(referenced instanceof List<?> list)values.addAll(list);target.put(field,values);} }

    private DefinitionValidationReport applyPrdCatalogueGate(DefinitionValidationReport report,
            Map<String, Object> current, boolean blocked)
    {
        if (!blocked)
            return report;
        List<ValidationIssue> errors = new java.util.ArrayList<>(report.errors());
        String blockers = text(value(current, "prd_blockers_json", "prdBlockersJson"));
        String message = "PRD template is BLOCKED by its definition catalogue";
        if (blockers != null && !blockers.isBlank())
            message += ": " + blockers;
        errors.add(new ValidationIssue("TODO_PRD_TEMPLATE_BLOCKED", "productionState", message));
        return new DefinitionValidationReport(errors, report.warnings(), report.compiledJson(),
                report.definitionHash());
    }

    private boolean isPrdBlocked(Map<String, Object> version)
    {
        return "BLOCKED".equalsIgnoreCase(
                text(value(version, "prd_foundation_state", "prdFoundationState")))
                || "BLOCKED".equalsIgnoreCase(
                        text(value(version, "prd_production_state", "prdProductionState")));
    }

    public record PreflightResult(long versionId, DefinitionValidationReport report)
    {
        public boolean publishable()
        {
            return report != null && report.publishable();
        }
    }

    private static final class PreflightFailedException extends TodoException
    {
        private PreflightFailedException()
        {
            super("TODO_DEFINITION_PREFLIGHT_FAILED", "Definition preflight failed");
        }
    }

    private void validate(String owner, String dod, String sla, String next, String ui)
    {
        validJson(owner, "ownerRuleJson");
        validObject(dod, "dodRuleJson");
        validObject(sla, "slaRuleJson");
        validObject(next, "nextRuleJson");
        validObject(ui, "uiSchemaJson");
        if (sla != null && !sla.isBlank())
        {
            JSONObject rule = JSON.parseObject(sla);
            String code = rule.getString("calendarCode");
            if (code == null || mapper.selectCalendarByCode(code) == null)
                throw new TodoException("TODO_SLA_CALENDAR_NOT_FOUND", "SLA calendar not found");
            if (rule.getLongValue("minutes") <= 0)
                throw new TodoException("TODO_SLA_MINUTES_INVALID", "SLA minutes must be positive");
        }
        if (next != null && !next.isBlank())
        {
            Long id = JSON.parseObject(next).getLong("templateVersionId");
            if (id != null)
            {
                Map<String, Object> target = requireVersion(id);
                if (!PUBLISHED.equals(text(value(target, "status", "status"))))
                    throw new TodoException("TODO_NEXT_TEMPLATE_NOT_PUBLISHED",
                            "Next template version is not published");
            }
        }
    }

    private void validJson(String json, String field)
    {
        if (json == null || json.isBlank() || !JSON.isValid(json))
            throw new TodoException("TODO_TEMPLATE_JSON_INVALID", "Invalid template JSON: " + field);
    }

    private void validObject(String json, String field)
    {
        if (json != null && !json.isBlank() && !JSON.isValidObject(json))
            throw new TodoException("TODO_TEMPLATE_JSON_INVALID", "Invalid template JSON: " + field);
    }

    private TodoDefinitionDocument definition(Map<String, Object> current)
    {
        String canonical = text(value(current, "definition_json", "definitionJson"));
        if(canonical==null||canonical.isBlank())return legacyAdapter.fromLegacy(current);
        try{return codec.read(canonical);}
        catch(RuntimeException invalid)
        {throw new TodoException("TODO_TEMPLATE_JSON_INVALID","Invalid canonical template JSON");}
    }

    private Map<String, Object> requireTemplate(Long id)
    {
        Map<String, Object> result = mapper.selectTemplateById(id);
        if (result == null || result.isEmpty())
            throw new TodoException("TODO_TEMPLATE_NOT_FOUND", "Todo template not found");
        return result;
    }

    private Map<String, Object> requireVersion(Long id)
    {
        Map<String, Object> result = mapper.selectTemplateVersionById(id);
        if (result == null || result.isEmpty())
            throw new TodoException("TODO_TEMPLATE_VERSION_NOT_FOUND", "Template version not found");
        return result;
    }

    private void requireDraft(Map<String, Object> version)
    {
        if (!DRAFT.equals(text(value(version, "status", "status"))))
            throw new TodoException("TODO_TEMPLATE_VERSION_IMMUTABLE",
                    "Published template versions are immutable");
    }

    private Long repeatedEntity(String actionId)
    {
        Map<String, Object> action = mapper.selectDefinitionActionById(actionId);
        return action == null || action.isEmpty() ? null
                : longValue(value(action, "entity_id", "entityId"));
    }

    private void claim(String actionId, String actionType, String entityType, Long source, Actor actor,
            Map<String, Object> payload)
    {
        Map<String, Object> action = new HashMap<>();
        action.put("actionId", actionId);
        action.put("actionType", actionType);
        action.put("entityType", entityType);
        action.put("sourceEntityId", source);
        action.put("operatorId", actor.userId());
        action.put("operatorName", actor.userName());
        action.put("payloadJson", JSON.toJSONString(payload));
        if (mapper.insertDefinitionActionIfAbsent(action) <= 0)
            throw new TodoException("TODO_DEFINITION_ACTION_CONFLICT",
                    "Definition action idempotency key conflict");
    }

    private void projectLegacyRules(TodoDefinitionDocument definition, Map<String, Object> target)
    {
        target.put("ownerRuleJson", JSON.toJSONString(definition.owner().config()));
        target.put("dodRuleJson", JSON.toJSONString(definition.dod().config()));
        target.put("slaRuleJson", JSON.toJSONString(definition.sla().config()));
        target.put("nextRuleJson", JSON.toJSONString(definition.routing().config()));
        target.put("uiSchemaJson", JSON.toJSONString(definition.ui().config()));
    }

    private Object value(Map<String, Object> map, String snake, String camel)
    {
        return map.containsKey(snake) ? map.get(snake) : map.get(camel);
    }

    private Long longValue(Object value)
    {
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }

    private String text(Object value)
    {
        return value == null ? null : String.valueOf(value);
    }
}
