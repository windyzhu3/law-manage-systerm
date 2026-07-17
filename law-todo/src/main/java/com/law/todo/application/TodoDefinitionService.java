package com.law.todo.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoDefinitionCommands.CopyTemplateCommand;
import com.law.todo.application.command.TodoDefinitionCommands.CopyVersionCommand;
import com.law.todo.application.command.TodoDefinitionCommands.PublishDraftCommand;
import com.law.todo.application.command.TodoDefinitionCommands.RollbackDraftCommand;
import com.law.todo.application.command.TodoDefinitionCommands.UpdateDraftCommand;
import com.law.todo.definition.catalog.TodoDecisionService;
import com.law.todo.definition.catalog.TodoEventCatalogService;
import com.law.todo.definition.codec.LegacyDefinitionAdapter;
import com.law.todo.definition.codec.TodoDefinitionCodec;
import com.law.todo.definition.compiler.DefinitionValidationReport;
import com.law.todo.definition.compiler.TodoDefinitionCompiler;
import com.law.todo.definition.compiler.TodoDefinitionCompiler.CompilationContext;
import com.law.todo.definition.compiler.TodoDefinitionCompiler.TemplateVersion;
import com.law.todo.definition.model.TodoDefinitionDocument;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoMapper;

@Service
public class TodoDefinitionService
{
    private static final String DRAFT = "DRAFT";
    private static final String PUBLISHED = "PUBLISHED";

    private final TodoMapper mapper;
    private final TodoDefinitionCompiler compiler;
    private final TodoDefinitionCodec codec = new TodoDefinitionCodec();
    private final LegacyDefinitionAdapter legacyAdapter = new LegacyDefinitionAdapter();

    @Autowired
    public TodoDefinitionService(TodoMapper mapper, TodoDefinitionCompiler compiler)
    {
        this.mapper = mapper;
        this.compiler = compiler;
    }

    public TodoDefinitionService(TodoMapper mapper)
    {
        this(mapper, new TodoDefinitionCompiler(new TodoDefinitionCodec(),
                new TodoEventCatalogService(mapper), new TodoDecisionService(mapper)));
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
        target.put("sourceVersionId",sourceVersionId);target.put("definitionSchemaVersion",copied.schemaVersion());
        target.put("definitionJson",codec.canonicalJson(copied));target.put("compiledJson",null);target.put("definitionHash",null);target.put("validationReportJson",null);
        projectLegacyRules(copied,target);
        if(mapper.insertTemplateVersion(target)<=0)throw new TodoException("TODO_ROLLBACK_DRAFT_FAILED","Rollback draft creation failed");
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
        Long repeated = repeatedEntity(command.actionId());
        if (repeated != null)
            return repeated;
        Map<String, Object> current = requireVersion(command.versionId());
        requireDraft(current);
        validate(command.ownerRuleJson(), command.dodRuleJson(), command.slaRuleJson(),
                command.nextRuleJson(), command.uiSchemaJson());
        claim(command.actionId(), "UPDATE_DRAFT", "VERSION", command.versionId(), actor, Map.of());
        Map<String, Object> update = new HashMap<>();
        update.put("versionId", command.versionId());
        update.put("ownerRuleJson", command.ownerRuleJson());
        update.put("dodRuleJson", command.dodRuleJson());
        update.put("slaRuleJson", command.slaRuleJson());
        update.put("nextRuleJson", command.nextRuleJson());
        update.put("uiSchemaJson", command.uiSchemaJson());
        if (mapper.updateTemplateVersionDraft(update) <= 0)
            throw new TodoException("TODO_TEMPLATE_VERSION_CONFLICT", "Draft version changed");
        return command.versionId();
    }

    @Transactional(noRollbackFor = PreflightFailedException.class)
    public Long publish(PublishDraftCommand command, Actor actor)
    {
        Long repeated = repeatedEntity(command.actionId());
        if (repeated != null)
            return repeated;
        Map<String, Object> current = requireVersion(command.versionId());
        requireDraft(current);
        validate(text(value(current, "owner_rule_json", "ownerRuleJson")),
                text(value(current, "dod_rule_json", "dodRuleJson")),
                text(value(current, "sla_rule_json", "slaRuleJson")),
                text(value(current, "next_rule_json", "nextRuleJson")),
                text(value(current, "ui_schema_json", "uiSchemaJson")));
        PreflightResult preflight = preflight(command.versionId(), true);
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
        TodoDefinitionDocument definition = definition(current);
        CompilationContext context = new CompilationContext(versionId, guardedPublishPreflight, id -> {
            Map<String, Object> target = mapper.selectTemplateVersionById(id);
            return target == null || target.isEmpty() ? null
                    : new TemplateVersion(id, text(value(target, "status", "status")));
        });
        DefinitionValidationReport report = compiler.compile(definition, context);
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
        return canonical == null || canonical.isBlank()
                ? legacyAdapter.fromLegacy(current) : codec.read(canonical);
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
