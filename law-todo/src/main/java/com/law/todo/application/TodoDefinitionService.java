package com.law.todo.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.JSONObject;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoDefinitionCommands.CopyTemplateCommand;
import com.law.todo.application.command.TodoDefinitionCommands.CreateTemplateCommand;
import com.law.todo.application.command.TodoDefinitionCommands.ImportTemplateCommand;
import com.law.todo.application.command.TodoDefinitionCommands.CopyVersionCommand;
import com.law.todo.application.command.TodoDefinitionCommands.PublishDraftCommand;
import com.law.todo.application.command.TodoDefinitionCommands.RollbackDraftCommand;
import com.law.todo.application.command.TodoDefinitionCommands.ReleaseDraftCommand;
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
import com.law.todo.spi.TodoDictionaryValidationPort;

@Service
public class TodoDefinitionService
{
    private static final Logger log = LoggerFactory.getLogger(TodoDefinitionService.class);
    private static final String DRAFT = "DRAFT";
    private static final String PUBLISHED = "PUBLISHED";

    private final TodoMapper mapper;
    private final TodoDefinitionCompiler compiler;
    private final TodoConfigurationMapper configurationMapper;
    private final TodoDictionaryValidationPort dictionaries;
    private final TodoSimulationEvidenceService simulationEvidence;
    private final TodoDefinitionCodec codec = new TodoDefinitionCodec();
    private final LegacyDefinitionAdapter legacyAdapter = new LegacyDefinitionAdapter();

    @Autowired
    public TodoDefinitionService(TodoMapper mapper, TodoDefinitionCompiler compiler,
            TodoConfigurationMapper configurationMapper,TodoDictionaryValidationPort dictionaries,
            TodoSimulationEvidenceService simulationEvidence)
    {
        this.mapper = mapper;
        this.compiler = compiler;
        this.configurationMapper=configurationMapper;this.dictionaries=dictionaries;
        this.simulationEvidence=simulationEvidence;
    }
    public TodoDefinitionService(TodoMapper mapper, TodoDefinitionCompiler compiler,
            TodoConfigurationMapper configurationMapper,TodoDictionaryValidationPort dictionaries)
    {this(mapper,compiler,configurationMapper,dictionaries,null);}
    public TodoDefinitionService(TodoMapper mapper, TodoDefinitionCompiler compiler,TodoConfigurationMapper configurationMapper)
    {this(mapper,compiler,configurationMapper,(type,value)->true,null);}

    public TodoDefinitionService(TodoMapper mapper, TodoDefinitionCompiler compiler)
    {this(mapper,compiler,null,(type,value)->true,null);}

    public TodoDefinitionService(TodoMapper mapper)
    {
        this(mapper,new TodoAutoActionCapabilityRegistry(List.of()));
    }

    /** Direct construction must supply the same capability registry as the executing application. */
    public TodoDefinitionService(TodoMapper mapper,TodoAutoActionCapabilityRegistry autoActions)
    {
        this(mapper,new TodoDefinitionCompiler(new TodoDefinitionCodec(),new TodoEventCatalogService(mapper),
                new TodoDecisionService(mapper),new com.law.todo.expression.ConditionValidator(),autoActions),null,(type,value)->true,null);
    }

    public List<Map<String, Object>> versions(Long templateId)
    {
        requireTemplate(templateId);
        return mapper.selectTemplateVersions(templateId);
    }

    @Transactional
    public TemplateDraftResult createTemplateDraft(CreateTemplateCommand command,Actor actor)
    {
        validateBusinessType(command.businessType());
        String fingerprint=templateFingerprint("CREATE_TEMPLATE_DRAFT",null,command,actor);Long repeated=claimTemplateDraft(command.actionId(),"CREATE_TEMPLATE_DRAFT",null,fingerprint,actor);if(repeated!=null)return resultForVersion(repeated);
        Map<String,Object> template=new HashMap<>();template.put("templateCode",command.templateCode());template.put("templateName",command.templateName());template.put("businessType",command.businessType());template.put("status","0");template.put("createBy",actor.userName());
        if(mapper.insertTemplate(template)<=0)throw new TodoException("TODO_TEMPLATE_CREATE_FAILED","Template creation failed");
        Map<String,Object> draft=emptyDraft(longValue(template.get("templateId")));if(mapper.insertTemplateVersion(draft)<=0)throw new TodoException("TODO_TEMPLATE_DRAFT_CREATE_FAILED","Template draft creation failed");
        Long versionId=longValue(draft.get("versionId"));if(mapper.completeDefinitionAction(command.actionId(),fingerprint,versionId)<=0)throw new TodoException("TODO_DEFINITION_ACTION_CONFLICT","Template draft action could not be completed");return new TemplateDraftResult(longValue(template.get("templateId")),versionId);
    }

    /** Imports the versioned interchange envelope into a new template and editable draft only. */
    @Transactional
    public TemplateDraftResult importTemplateDraft(ImportTemplateCommand command,Actor actor)
    {
        if(!Integer.valueOf(1).equals(command.schemaVersion()))
            throw new TodoException("TODO_TEMPLATE_IMPORT_SCHEMA_UNSUPPORTED","Unsupported template import schema version");
        validateBusinessType(command.businessType());
        TodoDefinitionDocument definition;
        try{definition=codec.read(command.definitionJson());}
        catch(RuntimeException invalid){throw new TodoException("TODO_TEMPLATE_JSON_INVALID","Invalid canonical template JSON");}
        if(!command.templateCode().equals(definition.templateCode()))
            throw new TodoException("TODO_TEMPLATE_CODE_MISMATCH","Definition templateCode does not match the import envelope");
        validateDefinition(definition,command.businessType());
        String fingerprint=templateFingerprint("IMPORT_TEMPLATE_DRAFT",null,command,actor);
        Long repeated=claimTemplateDraft(command.actionId(),"IMPORT_TEMPLATE_DRAFT",null,fingerprint,actor);
        if(repeated!=null)return resultForVersion(repeated);
        Map<String,Object> template=new HashMap<>();template.put("templateCode",command.templateCode());
        template.put("templateName",command.templateName());template.put("businessType",command.businessType());
        template.put("status","0");template.put("createBy",actor.userName());
        if(mapper.insertTemplate(template)<=0)throw new TodoException("TODO_TEMPLATE_IMPORT_FAILED","Template import failed");
        Long templateId=longValue(template.get("templateId"));Map<String,Object> draft=emptyDraft(templateId);
        draft.put("definitionSchemaVersion",definition.schemaVersion());draft.put("definitionJson",codec.canonicalJson(definition));
        draft.put("changeSummary",command.changeSummary());draft.put("impactScope",command.impactScope());projectLegacyRules(definition,draft);
        if(mapper.insertTemplateVersion(draft)<=0)throw new TodoException("TODO_TEMPLATE_DRAFT_CREATE_FAILED","Imported draft creation failed");
        Long versionId=longValue(draft.get("versionId"));
        if(command.ruleReferences()!=null&&!command.ruleReferences().isEmpty())replaceDraftRuleReferences(versionId,command.ruleReferences());
        if(mapper.completeDefinitionAction(command.actionId(),fingerprint,versionId)<=0)
            throw new TodoException("TODO_DEFINITION_ACTION_CONFLICT","Template import action could not be completed");
        return new TemplateDraftResult(templateId,versionId);
    }

    @Transactional
    public Long copyTemplate(Long sourceTemplateId, CopyTemplateCommand command, Actor actor)
    {return copyTemplateDraft(sourceTemplateId,command,actor).templateId();}

    @Transactional
    public TemplateDraftResult copyTemplateDraft(Long sourceTemplateId, CopyTemplateCommand command, Actor actor)
    {
        String fingerprint=templateFingerprint("COPY_TEMPLATE_DRAFT",sourceTemplateId,command,actor);Long repeated=claimTemplateDraft(command.actionId(),"COPY_TEMPLATE_DRAFT",sourceTemplateId,fingerprint,actor);if(repeated!=null)return resultForVersion(repeated);
        Map<String, Object> source = requireTemplate(sourceTemplateId);
        validateBusinessType(text(value(source,"business_type","businessType")));
        Map<String, Object> target = new HashMap<>();
        target.put("templateCode", command.newTemplateCode());
        target.put("templateName", command.newTemplateName());
        target.put("businessType", value(source, "business_type", "businessType"));
        target.put("status", "0");
        target.put("createBy", actor.userName());
        if (mapper.insertTemplate(target) <= 0)
            throw new TodoException("TODO_TEMPLATE_COPY_FAILED", "Template copy failed");
        Long id = longValue(target.get("templateId"));Map<String,Object> draft=emptyDraft(id);List<Map<String,Object>> versions=mapper.selectTemplateVersions(sourceTemplateId);
        if(versions!=null&&!versions.isEmpty()){Map<String,Object> version=versions.get(0);draft.put("sourceVersionId",value(version,"version_id","versionId"));draft.put("definitionSchemaVersion",value(version,"definition_schema_version","definitionSchemaVersion"));draft.put("definitionJson",copyDefinitionJson(value(version,"definition_json","definitionJson"),command.newTemplateCode()));draft.put("ownerRuleJson",value(version,"owner_rule_json","ownerRuleJson"));draft.put("dodRuleJson",value(version,"dod_rule_json","dodRuleJson"));draft.put("slaRuleJson",value(version,"sla_rule_json","slaRuleJson"));draft.put("nextRuleJson",value(version,"next_rule_json","nextRuleJson"));draft.put("uiSchemaJson",value(version,"ui_schema_json","uiSchemaJson"));}
        if(mapper.insertTemplateVersion(draft)<=0)throw new TodoException("TODO_TEMPLATE_DRAFT_CREATE_FAILED","Template draft creation failed");Long versionId=longValue(draft.get("versionId"));if(mapper.completeDefinitionAction(command.actionId(),fingerprint,versionId)<=0)throw new TodoException("TODO_DEFINITION_ACTION_CONFLICT","Template copy action could not be completed");return new TemplateDraftResult(id,versionId);
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
    public Long copyReleaseDraft(Long sourceVersionId,ReleaseDraftCommand command,Actor actor)
    {return createReleaseDraft(sourceVersionId,command,actor,"COPY_RELEASE_DRAFT",false);}

    @Transactional
    public Long rollbackReleaseDraft(Long sourceVersionId,ReleaseDraftCommand command,Actor actor)
    {return createReleaseDraft(sourceVersionId,command,actor,"ROLLBACK_RELEASE_DRAFT",true);}

    private Long createReleaseDraft(Long sourceVersionId,ReleaseDraftCommand command,Actor actor,String actionType,boolean rollback)
    {
        Map<String,Object> source=requireVersion(sourceVersionId);String status=text(value(source,"status","status"));
        if(!PUBLISHED.equals(status)&&!"RETIRED".equals(status))
            throw new TodoException("TODO_RELEASE_SOURCE_IMMUTABLE_REQUIRED","Release draft source must be published or retired");
        Long templateId=longValue(value(source,"template_id","templateId"));
        String fingerprint=releaseDraftFingerprint(actionType,sourceVersionId,templateId,actor);
        Long repeated=claimTemplateDraft(command.actionId(),actionType,sourceVersionId,fingerprint,actor);
        if(repeated!=null)return repeated;
        Map<String,Object> locked=mapper.selectTemplateForUpdate(templateId);
        if(locked==null||locked.isEmpty())throw new TodoException("TODO_TEMPLATE_NOT_FOUND","Todo template not found");
        int versionNo=mapper.selectNextTemplateVersionNo(templateId);
        TodoDefinitionDocument copied=definition(source);Map<String,Object> target=new HashMap<>();
        target.put("templateId",templateId);target.put("versionNo",versionNo);target.put("status",DRAFT);
        target.put("sourceVersionId",sourceVersionId);target.put("rollbackSourceVersionId",rollback?sourceVersionId:null);
        target.put("definitionSchemaVersion",copied.schemaVersion());target.put("definitionJson",codec.canonicalJson(copied));
        target.put("compiledJson",null);target.put("definitionHash",null);target.put("validationReportJson",null);
        projectLegacyRules(copied,target);
        try
        {
            if(mapper.insertTemplateVersion(target)<=0)
                throw new TodoException("TODO_RELEASE_DRAFT_CREATE_FAILED","Release draft creation failed");
        }
        catch(DuplicateKeyException collision)
        {throw new TodoException("TODO_DEFINITION_VERSION_CONFLICT","The allocated template version already exists");}
        Long targetId=longValue(target.get("versionId"));
        if(mapper.completeDefinitionAction(command.actionId(),fingerprint,targetId)<=0)
            throw new TodoException("TODO_DEFINITION_ACTION_CONFLICT","Release draft action could not be completed");
        return targetId;
    }

    private String releaseDraftFingerprint(String actionType,Long sourceVersionId,Long templateId,Actor actor)
    {
        Map<String,Object> value=new java.util.TreeMap<>();value.put("actionType",actionType);
        value.put("actorDeptId",actor.deptId());value.put("actorId",actor.userId());value.put("actorName",actor.userName());
        value.put("sourceVersionId",sourceVersionId);value.put("targetTemplateId",templateId);
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
            catch (RuntimeException invalid)
            {
                log.warn("Unable to parse Todo template definition JSON for version {}", command.versionId(), invalid);
                throw new TodoException("TODO_TEMPLATE_JSON_INVALID","Invalid template JSON: definitionJson");
            }
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
        validateDefinition(definition,text(value(current,"business_type","businessType")));
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
        update.put("changeSummary",command.changeSummary()==null?value(current,"change_summary","changeSummary"):command.changeSummary());
        update.put("impactScope",command.impactScope()==null?value(current,"impact_scope","impactScope"):command.impactScope());
        update.put("updateBy",actor.userName());
        if (mapper.updateTemplateVersionDraft(update) <= 0)
            throw new TodoException("TODO_TEMPLATE_VERSION_CONFLICT", "Draft version changed");
        replaceDraftRuleReferences(command.versionId(),command.ruleReferences());
        if(mapper.completeDefinitionAction(command.actionId(),fingerprint,command.versionId())<=0)
            throw new TodoException("TODO_DEFINITION_ACTION_CONFLICT","Draft action claim could not be completed");
        return command.versionId();
    }

    private void validateDefinition(TodoDefinitionDocument definition,String businessType)
    {
        validateDefinitionStructure(definition);
        validateDefinitionEvent(definition,businessType);
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

    private void validateDefinitionEvent(TodoDefinitionDocument definition,String businessType)
    {
        Map<String,Object> event=mapper.selectEventCatalog(definition.event().eventType(),definition.event().payloadVersion());
        if(event==null||event.isEmpty()||!"ACTIVE".equals(text(value(event,"status","status")))
                ||text(value(event,"payload_schema_json","payloadSchemaJson"))==null)
            throw new TodoException("TODO_EVENT_CATALOG_REQUIRED","Definition event type and payload version must be active");
        String eventBusinessType=text(value(event,"business_object_type","businessObjectType"));
        if(businessType!=null&&!businessType.isBlank()&&eventBusinessType!=null&&!eventBusinessType.isBlank()
                &&!businessType.equals(eventBusinessType))
            throw new TodoException("TODO_TEMPLATE_EVENT_BUSINESS_TYPE_MISMATCH","Definition event business type does not match the template");
    }

    /** A null reference list preserves compatibility; an explicit empty list clears the editable bindings. */
    private void replaceDraftRuleReferences(Long versionId,List<RuleReference> references)
    {
        if(references==null)return;
        if(configurationMapper==null)
            throw new TodoException("TODO_CONFIGURATION_BINDING_UNAVAILABLE","Rule binding persistence is unavailable");
        List<RuleReference> ordered=validateRuleReferences(references);
        configurationMapper.deleteDraftRuleRefs(versionId);
        ordered.forEach(reference->{
            Map<String,Object> row=new HashMap<>();row.put("versionId",versionId);row.put("refType",reference.type());
            row.put("refIdValue",reference.id());row.put("sortOrder",reference.order());row.put("configJson",null);
            if(configurationMapper.insertDraftRuleRef(row)<=0)
                throw new TodoException("TODO_TEMPLATE_RULE_BINDING_FAILED","Draft rule reference could not be saved");
        });
    }

    @Transactional(noRollbackFor = PublishGateException.class)
    public Long publish(PublishDraftCommand command, Actor actor)
    {
        Long repeated = repeatedEntity(command.actionId());
        if (repeated != null)
            return repeated;
        Map<String, Object> current = requireLockedVersion(command.versionId());
        requireDraft(current);
        RuleBinding binding=bindReferencedRules(command.versionId(),definition(current));
        TodoDefinitionDocument definition=binding.definition();
        if(binding.bound())
        {
            Map<String,Object> snapshot=snapshotRow(command.versionId(),definition,current);
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
            assertPublishable(preflight.report(),command.warningReason());
        }
        validate(text(value(current, "owner_rule_json", "ownerRuleJson")),
                text(value(current, "dod_rule_json", "dodRuleJson")),
                text(value(current, "sla_rule_json", "slaRuleJson")),
                text(value(current, "next_rule_json", "nextRuleJson")),
                text(value(current, "ui_schema_json", "uiSchemaJson")));
        if (preflight == null)
            preflight = preflight(command.versionId(), true,current,definition);
        assertPublishable(preflight.report(),command.warningReason());
        if(command.expectedDefinitionHash()!=null&&!command.expectedDefinitionHash().isBlank()
                &&!command.expectedDefinitionHash().equals(preflight.report().definitionHash()))
            throw new TodoException("TODO_TEMPLATE_PREFLIGHT_STALE","Definition or bound rules changed after preflight; run preflight again");
        Map<String,Object> publication=new HashMap<>();
        publication.put("definitionHash",preflight.report().definitionHash());
        if(command.warningReason()!=null&&!command.warningReason().isBlank())
            publication.put("warningReason",command.warningReason().trim());
        claim(command.actionId(), "PUBLISH_VERSION", "VERSION", command.versionId(), actor,publication);
        if (mapper.publishTemplateVersionConditionally(command.versionId(),
                preflight.report().definitionHash(), actor.userName()) <= 0)
            throw new TodoException("TODO_TEMPLATE_VERSION_CONFLICT",
                    "Definition changed or is no longer a draft");
        return command.versionId();
    }

    private void assertPublishable(DefinitionValidationReport report,String warningReason)
    {
        if(report==null||!report.errors().isEmpty())
            throw new PreflightFailedException();
        if(!report.warnings().isEmpty()&&(warningReason==null||warningReason.isBlank()))
            throw new WarningReasonRequiredException();
    }

    @Transactional
    public PreflightResult preflight(long versionId)
    {
        Map<String,Object> current=requireVersion(versionId);
        String status=text(value(current,"status","status"));
        TodoDefinitionDocument definition=definition(current);
        if(DRAFT.equals(status))
        {
            RuleBinding binding=bindReferencedRules(versionId,definition);
            definition=binding.definition();
            if(binding.bound())
            {
                Map<String,Object> snapshot=snapshotRow(versionId,definition,current);
                if(mapper.updateDefinitionDocument(snapshot)<=0)
                    throw new TodoException("TODO_TEMPLATE_VERSION_CONFLICT","Draft version changed before rule snapshot compilation");
                current=withSnapshot(current,snapshot);
            }
            return preflight(versionId,true,current,definition,true);
        }
        return preflight(versionId,false,current,definition,false);
    }

    /**
     * Verifies that a simulation still targets the exact preflighted draft and referenced-rule snapshots.
     * Published/retired definitions remain immutable and therefore never consult the mutable rule library.
     */
    @Transactional(readOnly=true)
    public void assertSimulationGate(long versionId,String expectedDefinitionHash)
    {
        Map<String,Object> current=requireVersion(versionId);
        String persistedHash=text(value(current,"definition_hash","definitionHash"));
        if(expectedDefinitionHash==null||!expectedDefinitionHash.equals(persistedHash))throw stalePreflight();
        if(!DRAFT.equals(text(value(current,"status","status"))))return;
        TodoDefinitionDocument persisted=definition(current);RuleBinding rebound;
        try{rebound=bindReferencedRules(versionId,persisted);}
        catch(TodoException changed){throw stalePreflight();}
        Object persistedSla=persisted.sla().config().get("ruleSnapshots");Object persistedDod=persisted.dod().config().get("ruleSnapshots");
        Object currentSla=rebound.bound()?rebound.definition().sla().config().get("ruleSnapshots"):null;
        Object currentDod=rebound.bound()?rebound.definition().dod().config().get("ruleSnapshots"):null;
        if(!canonicalRuleSnapshot(persistedSla).equals(canonicalRuleSnapshot(currentSla))
                ||!canonicalRuleSnapshot(persistedDod).equals(canonicalRuleSnapshot(currentDod)))throw stalePreflight();
    }

    private String canonicalRuleSnapshot(Object value)
    {return JSON.toJSONString(value==null?List.of():value,JSONWriter.Feature.SortMapEntriesByKeys);}

    private TodoException stalePreflight()
    {return new TodoException("TODO_TEMPLATE_PREFLIGHT_STALE","Definition or bound rules changed after preflight; run preflight again");}

    private PreflightResult preflight(long versionId, boolean guardedPublishPreflight)
    {
        Map<String, Object> current = requireVersion(versionId);
        return preflight(versionId,guardedPublishPreflight,current,definition(current),DRAFT.equals(text(value(current,"status","status"))));
    }

    private PreflightResult preflight(long versionId,boolean guardedPublishPreflight,Map<String,Object> current,
            TodoDefinitionDocument definition)
    {return preflight(versionId,guardedPublishPreflight,current,definition,true);}

    private PreflightResult preflight(long versionId,boolean guardedPublishPreflight,Map<String,Object> current,
            TodoDefinitionDocument definition,boolean persistCompilation)
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
        if(simulationEvidence!=null)report=simulationEvidence.applyPreflightGate(versionId,report);
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
        if (persistCompilation && mapper.updateDefinitionCompilation(persisted) <= 0)
            throw new TodoException("TODO_TEMPLATE_VERSION_CONFLICT", "Definition changed during preflight");
        return new PreflightResult(versionId, report);
    }

    private Map<String,Object> withSnapshot(Map<String,Object> current,Map<String,Object> snapshot)
    {
        Map<String,Object> updated=new HashMap<>(current);
        updated.put("definition_json",snapshot.get("definitionJson"));updated.put("definitionJson",snapshot.get("definitionJson"));
        updated.put("owner_rule_json",snapshot.get("ownerRuleJson"));updated.put("ownerRuleJson",snapshot.get("ownerRuleJson"));
        updated.put("dod_rule_json",snapshot.get("dodRuleJson"));updated.put("dodRuleJson",snapshot.get("dodRuleJson"));
        updated.put("sla_rule_json",snapshot.get("slaRuleJson"));updated.put("slaRuleJson",snapshot.get("slaRuleJson"));
        updated.put("next_rule_json",snapshot.get("nextRuleJson"));updated.put("nextRuleJson",snapshot.get("nextRuleJson"));
        updated.put("ui_schema_json",snapshot.get("uiSchemaJson"));updated.put("uiSchemaJson",snapshot.get("uiSchemaJson"));
        return updated;
    }

    private Map<String,Object> snapshotRow(Long versionId,TodoDefinitionDocument definition,Map<String,Object> source)
    {
        Map<String,Object> row=new HashMap<>();row.put("versionId",versionId);row.put("definitionSchemaVersion",definition.schemaVersion());
        row.put("definitionJson",codec.canonicalJson(definition));row.put("compiledJson",null);row.put("definitionHash",null);
        row.put("validationReportJson",null);projectLegacyRules(definition,row);
        row.put("sourceDefinitionJson",value(source,"definition_json","definitionJson"));
        row.put("sourceOwnerRuleJson",value(source,"owner_rule_json","ownerRuleJson"));
        row.put("sourceDodRuleJson",value(source,"dod_rule_json","dodRuleJson"));
        row.put("sourceSlaRuleJson",value(source,"sla_rule_json","slaRuleJson"));
        row.put("sourceNextRuleJson",value(source,"next_rule_json","nextRuleJson"));
        row.put("sourceUiSchemaJson",value(source,"ui_schema_json","uiSchemaJson"));
        return row;
    }

    private RuleBinding bindReferencedRules(Long versionId,TodoDefinitionDocument definition)
    {
        if(configurationMapper==null)return new RuleBinding(definition,false);
        List<Map<String,Object>> rows=configurationMapper.selectDraftRuleRefs(versionId);
        if(rows==null||rows.isEmpty())return new RuleBinding(definition,false);
        List<RuleReference> references=validateRuleReferences(rows.stream().map(row->new RuleReference(
                text(value(row,"ref_type","refType")),longValue(value(row,"ref_id_value","refIdValue")),
                integerValue(value(row,"sort_order","sortOrder")))).toList());
        Map<String,Object> sla=new java.util.LinkedHashMap<>(definition.sla().config());
        Map<String,Object> dod=new java.util.LinkedHashMap<>(definition.dod().config());
        List<Map<String,Object>> slaSnapshots=new java.util.ArrayList<>();
        List<Map<String,Object>> dodSnapshots=new java.util.ArrayList<>();
        for(RuleReference reference:references)
        {
            String type=reference.type();Long id=reference.id();
            if("SLA".equals(type))
            {
                Map<String,Object> rule=requiredActiveRule(configurationMapper.selectSlaRule(id),"SLA",id);
                Map<String,Object> snapshot=slaSnapshot(rule);slaSnapshots.add(snapshot);applySlaSnapshot(sla,snapshot);
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

    private List<RuleReference> validateRuleReferences(List<RuleReference> references)
    {
        java.util.Set<String> identities=new java.util.HashSet<>();java.util.Set<Integer> orders=new java.util.HashSet<>();int slaCount=0;
        List<RuleReference> ordered=new java.util.ArrayList<>(references);
        for(RuleReference reference:ordered)
        {
            if(reference==null||reference.id()==null||reference.id()<=0||reference.order()==null||reference.order()<0)
                throw bindingInvalid("Rule reference id and order must be positive");
            if(!"SLA".equals(reference.type())&&!"DOD".equals(reference.type()))
                throw new TodoException("TODO_TEMPLATE_RULE_TYPE_INVALID","Unsupported draft rule reference type: "+reference.type());
            if(!identities.add(reference.type()+":"+reference.id())||!orders.add(reference.order()))
                throw bindingInvalid("Duplicate draft rule reference id or order");
            if("SLA".equals(reference.type()))
            {
                if(++slaCount>1)throw bindingInvalid("Only one SLA rule may be bound to a template draft");
                requiredActiveRule(configurationMapper.selectSlaRule(reference.id()),"SLA",reference.id());
            }
            else requiredActiveRule(configurationMapper.selectDodRule(reference.id()),"DOD",reference.id());
        }
        ordered.sort(java.util.Comparator.comparing(RuleReference::order));
        return List.copyOf(ordered);
    }

    private TodoException bindingInvalid(String message)
    {return new TodoException("TODO_TEMPLATE_RULE_BINDING_INVALID",message);}

    private Map<String,Object> requiredActiveRule(Map<String,Object> rule,String type,Long id)
    {
        if(rule==null||rule.isEmpty())throw new TodoException("TODO_TEMPLATE_RULE_NOT_FOUND",type+" rule not found: "+id);
        Long actualId="SLA".equals(type)?longValue(value(rule,"sla_rule_id","slaRuleId"))
                : longValue(value(rule,"dod_rule_id","dodRuleId"));
        if(!id.equals(actualId))throw new TodoException("TODO_TEMPLATE_RULE_NOT_FOUND",type+" rule not found: "+id);
        if(!"0".equals(text(value(rule,"status","status"))))
            throw new TodoException("TODO_TEMPLATE_RULE_DISABLED",type+" rule is disabled: "+id);
        return rule;
    }

    private Map<String,Object> slaSnapshot(Map<String,Object> rule)
    {
        Map<String,Object> snapshot=new java.util.LinkedHashMap<>();snapshot.put("ruleId",longValue(value(rule,"sla_rule_id","slaRuleId")));
        snapshot.put("ruleCode",text(value(rule,"rule_code","ruleCode")));snapshot.put("ruleName",text(value(rule,"rule_name","ruleName")));
        snapshot.put("slaType",text(value(rule,"sla_type","slaType")));snapshot.put("durationValue",value(rule,"duration_value","durationValue"));snapshot.put("durationUnit",text(value(rule,"duration_unit","durationUnit")));snapshot.put("calendarCode",text(value(rule,"calendar_code","calendarCode")));
        snapshot.put("startStrategy",text(value(rule,"start_strategy","startStrategy")));snapshot.put("minutes",slaMinutes(rule));
        snapshot.put("softRemindPercent",value(rule,"soft_remind_percent","softRemindPercent"));snapshot.put("hardRemindPercent",value(rule,"hard_remind_percent","hardRemindPercent"));
        snapshot.put("escalatePercent",value(rule,"escalate_percent","escalatePercent"));snapshot.put("pausePolicy",jsonValue(rule,"pause_policy_json","pausePolicyJson"));
        snapshot.put("escalationPolicy",jsonValue(rule,"escalation_policy_json","escalationPolicyJson"));snapshot.put("autoAction",jsonValue(rule,"auto_action_json","autoActionJson"));snapshot.put("status",text(value(rule,"status","status")));snapshot.put("version",value(rule,"version","version"));
        return snapshot;
    }

    private void applySlaSnapshot(Map<String,Object> target,Map<String,Object> snapshot)
    {for(String key:List.of("slaType","calendarCode","startStrategy","minutes","softRemindPercent","hardRemindPercent","escalatePercent","pausePolicy","escalationPolicy","autoAction"))target.put(key,snapshot.get(key));}

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
        snapshot.put("validatorRefs",jsonArray(rule,"validator_refs_json","validatorRefsJson"));snapshot.put("errorMessages",jsonObject(rule,"error_messages_json","errorMessagesJson"));snapshot.put("status",text(value(rule,"status","status")));snapshot.put("version",value(rule,"version","version"));return snapshot;
    }

    private List<Object> jsonArray(Map<String,Object> rule,String snake,String camel)
    {String json=text(value(rule,snake,camel));return json==null||json.isBlank()?List.of():JSON.parseArray(json,Object.class);}
    private Map<String,Object> jsonObject(Map<String,Object> rule,String snake,String camel)
    {String json=text(value(rule,snake,camel));if(json==null||json.isBlank())return Map.of();Map<String,Object> result=new java.util.LinkedHashMap<>();JSON.parseObject(json).forEach((key,value)->result.put(key,value));return result;}
    private Object jsonValue(Map<String,Object> rule,String snake,String camel)
    {String json=text(value(rule,snake,camel));return json==null||json.isBlank()?Map.of():JSON.parse(json);}
    private void mergeDodSnapshot(Map<String,Object> target,Map<String,Object> snapshot)
    {
        for(String field:List.of("requiredFields","requiredAttachments","conditionalRules","validatorRefs"))
        {List<Object> values=new java.util.ArrayList<>();Object current=target.get(field);if(current instanceof List<?> list)values.addAll(list);Object referenced=snapshot.get(field);if(referenced instanceof List<?> list)values.addAll(list);target.put(field,deduplicate(values));}
        Map<String,Object> errors=new java.util.LinkedHashMap<>();Object currentErrors=target.get("errorMessages");if(currentErrors instanceof Map<?,?> map)map.forEach((key,value)->errors.put(String.valueOf(key),value));Object referencedErrors=snapshot.get("errorMessages");if(referencedErrors instanceof Map<?,?> map)map.forEach((key,value)->errors.put(String.valueOf(key),value));target.put("errorMessages",errors);
        List<Object> types=new java.util.ArrayList<>();Object currentTypes=target.get("ruleTypes");if(currentTypes instanceof List<?> list)types.addAll(list);types.add(snapshot.get("ruleType"));types=deduplicate(types);target.put("ruleTypes",types);if(types.size()==1)target.put("ruleType",types.get(0));else target.remove("ruleType");
    }

    private List<Object> deduplicate(List<Object> values)
    {return new java.util.ArrayList<>(new java.util.LinkedHashSet<>(values));}

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

    private abstract static class PublishGateException extends TodoException
    {
        private PublishGateException(String code,String message){super(code,message);}
    }

    private static final class PreflightFailedException extends PublishGateException
    {
        private PreflightFailedException()
        {
            super("TODO_PUBLISH_BLOCKED", "Resolve blocking configuration issues before publishing");
        }
    }

    private static final class WarningReasonRequiredException extends PublishGateException
    {
        private WarningReasonRequiredException()
        {
            super("TODO_PUBLISH_WARNING_REASON_REQUIRED","Publication explanation is required for warnings");
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
            if(!rule.isEmpty())
            {
                String code = rule.getString("calendarCode");
                if (code == null || mapper.selectCalendarByCode(code) == null)
                    throw new TodoException("TODO_SLA_CALENDAR_NOT_FOUND", "SLA calendar not found");
                if (rule.getLongValue("minutes") <= 0)
                    throw new TodoException("TODO_SLA_MINUTES_INVALID", "SLA minutes must be positive");
            }
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

    private Map<String,Object> requireLockedVersion(Long id)
    {
        Map<String,Object> result=mapper.selectTemplateVersionForUpdate(id);
        if(result==null||result.isEmpty())
            throw new TodoException("TODO_TEMPLATE_VERSION_NOT_FOUND","Template version not found");
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

    private Map<String,Object> emptyDraft(Long templateId)
    {Map<String,Object> value=new HashMap<>();value.put("templateId",templateId);value.put("versionNo",1);value.put("status",DRAFT);value.put("definitionSchemaVersion",1);return value;}
    private String copyDefinitionJson(Object source,String templateCode)
    {
        if(source==null||String.valueOf(source).isBlank())return null;
        TodoDefinitionDocument original=codec.read(String.valueOf(source));
        return codec.canonicalJson(new TodoDefinitionDocument(original.schemaVersion(),templateCode,original.event(),original.owner(),
                original.dod(),original.sla(),original.ui(),original.routing(),original.autoActions(),original.decisionRefs(),original.acceptanceRefs()));
    }
    private String templateFingerprint(String type,Long source,Object command,Actor actor){Map<String,Object> value=new java.util.TreeMap<>();value.put("type",type);value.put("source",source);value.put("actor",actor.userId());value.put("name",actor.userName());value.put("dept",actor.deptId());value.put("request",JSON.parse(JSON.toJSONString(command)));return TodoDefinitionSimulationService.sha256(JSON.toJSONString(value));}
    private Long claimTemplateDraft(String actionId,String type,Long source,String fingerprint,Actor actor){Map<String,Object> action=new HashMap<>();action.put("actionId",actionId);action.put("actionType",type);action.put("entityType","VERSION");action.put("sourceEntityId",source);action.put("operatorId",actor.userId());action.put("operatorName",actor.userName());action.put("operatorDeptId",actor.deptId());action.put("requestFingerprint",fingerprint);action.put("payloadJson","{}");mapper.insertDefinitionActionClaim(action);Map<String,Object> locked=mapper.selectDefinitionActionForUpdate(actionId);if(locked==null||!type.equals(text(value(locked,"action_type","actionType")))||!fingerprint.equals(text(value(locked,"request_fingerprint","requestFingerprint")))||!actor.userId().equals(longValue(value(locked,"operator_id","operatorId")))||!actor.userName().equals(text(value(locked,"operator_name","operatorName"))))throw new TodoException("TODO_DEFINITION_ACTION_CONFLICT","Template action id belongs to a different request");Long id=longValue(value(locked,"entity_id","entityId"));if(id!=null)return id;if(!"CLAIMED".equals(text(value(locked,"action_status","actionStatus"))))throw new TodoException("TODO_DEFINITION_ACTION_CONFLICT","Template action is not claimable");return null;}
    private void validateBusinessType(String businessType){if(!dictionaries.isEnabled("law_todo_business_type",businessType))throw new TodoException("TODO_TEMPLATE_BUSINESS_TYPE_INVALID","Business type is unknown or disabled");}

    private TemplateDraftResult resultForVersion(Long versionId)
    {Map<String,Object> version=requireVersion(versionId);return new TemplateDraftResult(longValue(value(version,"template_id","templateId")),versionId);}

    public record TemplateDraftResult(Long templateId,Long versionId) { }

    private Integer integerValue(Object value)
    {
        return value == null ? null : Integer.valueOf(String.valueOf(value));
    }

    private String text(Object value)
    {
        return value == null ? null : String.valueOf(value);
    }
}
