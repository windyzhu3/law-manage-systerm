package com.law.todo.application;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.law.todo.application.command.TodoAcceptanceEvidenceCommands.BatchBindMappingsCommand;
import com.law.todo.application.command.TodoAcceptanceEvidenceCommands.CreateScenarioCommand;
import com.law.todo.application.command.TodoAcceptanceEvidenceCommands.MappingTarget;
import com.law.todo.application.command.TodoAcceptanceEvidenceCommands.UpdateMappingCommand;
import com.law.todo.application.command.TodoAcceptanceEvidenceCommands.UpdateScenarioCommand;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.view.TodoAcceptanceMappingView;
import com.law.todo.application.view.TodoAcceptanceScenarioView;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoAcceptanceEvidenceMapper;

@Service
public class TodoAcceptanceEvidenceService
{
    private static final String CHECKSUM = "[0-9a-fA-F]{64}";
    private final TodoAcceptanceEvidenceMapper mapper;

    public TodoAcceptanceEvidenceService(TodoAcceptanceEvidenceMapper mapper)
    {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<TodoAcceptanceScenarioView> scenarios()
    {
        return mapper.selectScenarios().stream().map(this::scenarioView).toList();
    }

    @Transactional(readOnly = true)
    public List<TodoAcceptanceMappingView> mappings(String templateCode, String dimensionCode, String status)
    {
        return mapper.selectMappings(templateCode, dimensionCode, status).stream().map(this::mappingView).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> governanceOptions()
    {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("users", List.copyOf(mapper.selectActiveUsers()));
        options.put("scenarioStatuses", List.of("DRAFT", "IN_REVIEW", "APPROVED", "REJECTED"));
        options.put("mappingStatuses", List.of("UNMAPPED", "MAPPED", "IN_REVIEW", "APPROVED", "REJECTED"));
        options.put("dimensions", List.of("OWNER", "SLA", "DOD", "ROUTE", "HANDLER", "UI"));
        return Map.copyOf(options);
    }

    @Transactional
    public TodoAcceptanceScenarioView createScenario(CreateScenarioCommand command, Actor actor)
    {
        if (!"DRAFT".equals(command.status()))
        {
            fail("TODO_ACCEPTANCE_SCENARIO_STATE_INVALID", "New acceptance scenarios must start as DRAFT");
        }
        validateJson(command.preconditionsJson(), command.stepsJson(), command.expectedOutcomesJson(), false);
        validateScenarioAccountability(command.ownerUserId(), command.acceptorUserId(), command.reviewerUserId(),
                command.dueAt(), false);
        String fingerprint = fingerprint("CREATE_SCENARIO", null, null, command, actor);
        Long replay = claim(command.actionId(), "SCENARIO", "CREATE_SCENARIO", null, fingerprint, actor, command);
        if (replay != null)
        {
            return requireScenario(replay);
        }
        if (present(mapper.selectScenarioByCode(command.scenarioCode())))
        {
            fail("TODO_ACCEPTANCE_SCENARIO_CODE_CONFLICT", "Acceptance scenario code already exists");
        }
        Map<String, Object> value = scenarioValues(command.scenarioName(), command.businessPath(),
                command.preconditionsJson(), command.stepsJson(), command.expectedOutcomesJson(),
                command.datasetRef(), command.datasetChecksum(), command.datasetVersion(), command.ownerUserId(),
                command.acceptorUserId(), command.reviewerUserId(), command.dueAt(), "DRAFT", null, null, actor);
        value.put("scenarioCode", command.scenarioCode()); value.put("createBy", actor.userName());
        if (mapper.insertScenario(value) <= 0 || number(value.get("scenarioId")) == null)
        {
            fail("TODO_ACCEPTANCE_SCENARIO_CODE_CONFLICT", "Acceptance scenario could not be created");
        }
        Long id = number(value.get("scenarioId")); complete(command.actionId(), fingerprint, id);
        return requireScenario(id);
    }

    @Transactional
    public TodoAcceptanceScenarioView updateScenario(UpdateScenarioCommand command, Actor actor)
    {
        Map<String, Object> current = rawScenario(command.scenarioId());
        if (!command.scenarioCode().equals(text(value(current, "scenario_code", "scenarioCode"))))
        {
            fail("TODO_ACCEPTANCE_SCENARIO_CODE_IMMUTABLE", "Acceptance scenario code cannot be changed");
        }
        String from = text(value(current, "status", "status"));
        validateScenarioTransition(from, command.status());
        boolean reviewReady = "IN_REVIEW".equals(command.status()) || terminal(command.status());
        validateScenarioAccountability(command.ownerUserId(), command.acceptorUserId(), command.reviewerUserId(),
                command.dueAt(), reviewReady);
        validateJson(command.preconditionsJson(), command.stepsJson(), command.expectedOutcomesJson(), reviewReady);
        if (reviewReady)
        {
            validateDataset(command.datasetRef(), command.datasetChecksum(), command.datasetVersion());
        }
        if (terminal(command.status()))
        {
            requireReviewer(command.reviewerUserId(), actor);
            if (blank(command.conclusion()))
            {
                fail("TODO_ACCEPTANCE_CONCLUSION_REQUIRED", "Scenario review requires a conclusion");
            }
        }
        String fingerprint = fingerprint("UPDATE_SCENARIO", command.scenarioId(), command.version(), command, actor);
        Long replay = claim(command.actionId(), "SCENARIO", "UPDATE_SCENARIO", command.scenarioId(),
                fingerprint, actor, command);
        if (replay != null)
        {
            return requireScenario(replay);
        }
        Map<String, Object> update = scenarioValues(command.scenarioName(), command.businessPath(),
                command.preconditionsJson(), command.stepsJson(), command.expectedOutcomesJson(), command.datasetRef(),
                command.datasetChecksum(), command.datasetVersion(), command.ownerUserId(), command.acceptorUserId(),
                command.reviewerUserId(), command.dueAt(), command.status(), command.conclusion(),
                terminal(command.status()) ? actor.userName() : null, actor);
        update.put("scenarioId", command.scenarioId()); update.put("expectedVersion", command.version());
        update.put("expectedStatus", from); update.put("updateBy", actor.userName());
        if (mapper.updateScenarioConditionally(update) <= 0)
        {
            fail("TODO_ACCEPTANCE_VERSION_CONFLICT", "Acceptance scenario changed; refresh before retrying");
        }
        complete(command.actionId(), fingerprint, command.scenarioId()); return requireScenario(command.scenarioId());
    }

    @Transactional
    public TodoAcceptanceMappingView updateMapping(UpdateMappingCommand command, Actor actor)
    {
        Map<String, Object> current = rawMapping(command.mappingId());
        String from = text(value(current, "status", "status"));
        validateMappingTransition(from, command.status());
        Map<String, Object> scenario = requireMappedScenario(command.scenarioId());
        boolean reviewReady = "IN_REVIEW".equals(command.status()) || terminal(command.status());
        validateMappingAccountability(command.ownerUserId(), command.reviewerUserId(), command.dueAt(), reviewReady);
        if (blank(command.plannedTestRef()))
        {
            fail("TODO_ACCEPTANCE_MAPPING_REQUIRED", "AT mapping requires a planned test reference");
        }
        if (terminal(command.status()))
        {
            requireReviewer(command.reviewerUserId(), actor);
            if (blank(command.conclusion()))
            {
                fail("TODO_ACCEPTANCE_CONCLUSION_REQUIRED", "AT mapping review requires a conclusion");
            }
        }
        if ("APPROVED".equals(command.status())
                && !"APPROVED".equals(text(value(scenario, "status", "status"))))
        {
            fail("TODO_ACCEPTANCE_SCENARIO_NOT_APPROVED", "AT mapping cannot be approved before its scenario");
        }
        String fingerprint = fingerprint("UPDATE_MAPPING", command.mappingId(), command.version(), command, actor);
        Long replay = claim(command.actionId(), "MAPPING", "UPDATE_MAPPING", command.mappingId(),
                fingerprint, actor, command);
        if (replay != null)
        {
            return requireMapping(replay);
        }
        Map<String, Object> update = mappingValues(command.mappingId(), command.version(), from,
                command.scenarioId(), command.plannedTestRef(), command.evidenceNote(), command.ownerUserId(),
                command.reviewerUserId(), command.dueAt(), command.status(), command.conclusion(),
                terminal(command.status()) ? actor.userName() : null, actor.userName());
        if (mapper.updateMappingConditionally(update) <= 0)
        {
            fail("TODO_ACCEPTANCE_VERSION_CONFLICT", "AT mapping changed; refresh before retrying");
        }
        complete(command.actionId(), fingerprint, command.mappingId()); return requireMapping(command.mappingId());
    }

    @Transactional
    public List<TodoAcceptanceMappingView> batchBind(BatchBindMappingsCommand command, Actor actor)
    {
        requireMappedScenario(command.scenarioId());
        String fingerprint = fingerprint("BATCH_BIND_MAPPING", null, null, command, actor);
        Long replay = claim(command.actionId(), "MAPPING", "BATCH_BIND_MAPPING", null, fingerprint, actor, command);
        if (replay != null)
        {
            return command.mappings().stream().map(target -> requireMapping(target.mappingId())).toList();
        }
        List<Long> ids = new ArrayList<>();
        for (MappingTarget target : command.mappings())
        {
            Map<String, Object> current = rawMapping(target.mappingId());
            String from = text(value(current, "status", "status"));
            if (!List.of("UNMAPPED", "MAPPED", "REJECTED").contains(from))
            {
                fail("TODO_ACCEPTANCE_MAPPING_STATE_INVALID", "Batch binding cannot change reviewed AT mappings");
            }
            Map<String, Object> update = mappingValues(target.mappingId(), target.version(), from,
                    command.scenarioId(), command.plannedTestRef(), command.evidenceNote(), null, null, null,
                    "MAPPED", null, null, actor.userName());
            if (mapper.updateMappingConditionally(update) <= 0)
            {
                fail("TODO_ACCEPTANCE_VERSION_CONFLICT", "AT mapping changed; refresh before retrying");
            }
            ids.add(target.mappingId());
        }
        complete(command.actionId(), fingerprint, ids.get(0));
        return ids.stream().map(this::requireMapping).toList();
    }

    public static String fingerprint(String type, Long entityId, Integer expectedVersion, Object command, Actor actor)
    {
        Map<String, Object> values = new TreeMap<>(); values.put("actionType", type);
        values.put("actorId", actor.userId()); values.put("actorName", actor.userName());
        values.put("actorDeptId", actor.deptId()); values.put("entityId", entityId);
        values.put("expectedVersion", expectedVersion); values.put("request", JSON.parse(JSON.toJSONString(command)));
        return TodoDefinitionSimulationService.sha256(JSON.toJSONString(values));
    }

    private Long claim(String actionId, String entityType, String actionType, Long entityId,
            String fingerprint, Actor actor, Object command)
    {
        Map<String, Object> action = new HashMap<>(); action.put("actionId", actionId);
        action.put("entityType", entityType); action.put("entityId", entityId); action.put("actionType", actionType);
        action.put("requestFingerprint", fingerprint); action.put("operatorId", actor.userId());
        action.put("operatorName", actor.userName()); action.put("operatorDeptId", actor.deptId());
        action.put("payloadJson", JSON.toJSONString(command)); mapper.insertActionClaim(action);
        Map<String, Object> locked = mapper.selectActionForUpdate(actionId);
        if (!present(locked) || !actionType.equals(text(value(locked, "action_type", "actionType")))
                || !fingerprint.equals(text(value(locked, "request_fingerprint", "requestFingerprint")))
                || !actor.userId().equals(number(value(locked, "operator_id", "operatorId"))))
        {
            fail("TODO_ACCEPTANCE_ACTION_CONFLICT", "Acceptance action id belongs to another request");
        }
        String status = text(value(locked, "action_status", "actionStatus"));
        Long result = number(value(locked, "result_entity_id", "resultEntityId"));
        if ("APPLIED".equals(status) && result != null)
        {
            return result;
        }
        if (!"CLAIMED".equals(status))
        {
            fail("TODO_ACCEPTANCE_ACTION_CONFLICT", "Acceptance action is not claimable");
        }
        return null;
    }

    private void complete(String actionId, String fingerprint, Long resultId)
    {
        if (resultId == null || mapper.completeAction(actionId, fingerprint, resultId) <= 0)
        {
            fail("TODO_ACCEPTANCE_ACTION_CONFLICT", "Acceptance action could not be completed");
        }
    }

    private void validateScenarioTransition(String from, String to)
    {
        boolean valid = ("DRAFT".equals(from) && List.of("DRAFT", "IN_REVIEW").contains(to))
                || ("IN_REVIEW".equals(from) && List.of("APPROVED", "REJECTED").contains(to))
                || ("REJECTED".equals(from) && "IN_REVIEW".equals(to));
        if (!valid)
        {
            fail("TODO_ACCEPTANCE_SCENARIO_STATE_INVALID", "Acceptance scenario state transition is invalid");
        }
    }

    private void validateMappingTransition(String from, String to)
    {
        boolean valid = ("UNMAPPED".equals(from) && "MAPPED".equals(to))
                || ("MAPPED".equals(from) && List.of("MAPPED", "IN_REVIEW").contains(to))
                || ("IN_REVIEW".equals(from) && List.of("APPROVED", "REJECTED").contains(to))
                || ("REJECTED".equals(from) && List.of("MAPPED", "IN_REVIEW").contains(to));
        if (!valid)
        {
            fail("TODO_ACCEPTANCE_MAPPING_STATE_INVALID", "AT mapping state transition is invalid");
        }
    }

    private void validateScenarioAccountability(Long ownerId, Long acceptorId, Long reviewerId,
            LocalDateTime dueAt, boolean required)
    {
        if (reviewerId != null && (reviewerId.equals(ownerId) || reviewerId.equals(acceptorId)))
        {
            fail("TODO_ACCEPTANCE_REVIEWER_INDEPENDENCE_REQUIRED", "Reviewer must differ from owner and acceptor");
        }
        if (required && (ownerId == null || acceptorId == null || reviewerId == null || dueAt == null))
        {
            fail("TODO_ACCEPTANCE_ACCOUNTABILITY_REQUIRED", "Scenario review requires owner, acceptor, reviewer and due date");
        }
        validateActive(ownerId); validateActive(acceptorId); validateActive(reviewerId);
    }

    private void validateMappingAccountability(Long ownerId, Long reviewerId, LocalDateTime dueAt, boolean required)
    {
        if (reviewerId != null && reviewerId.equals(ownerId))
        {
            fail("TODO_ACCEPTANCE_REVIEWER_INDEPENDENCE_REQUIRED", "Reviewer must differ from mapping owner");
        }
        if (required && (ownerId == null || reviewerId == null || dueAt == null))
        {
            fail("TODO_ACCEPTANCE_ACCOUNTABILITY_REQUIRED", "AT review requires owner, reviewer and due date");
        }
        validateActive(ownerId); validateActive(reviewerId);
    }

    private void validateActive(Long userId)
    {
        if (userId != null && !present(mapper.selectActiveUser(userId)))
        {
            fail("TODO_ACCEPTANCE_USER_INVALID", "Acceptance accountability user must be active");
        }
    }

    private void validateDataset(String datasetRef, String checksum, Integer version)
    {
        if (blank(datasetRef) || checksum == null || !checksum.matches(CHECKSUM) || version == null || version <= 0)
        {
            fail("TODO_ACCEPTANCE_DATASET_REQUIRED", "Scenario review requires dataset reference, checksum and positive version");
        }
    }

    private void validateJson(String preconditions, String steps, String outcomes, boolean requireContent)
    {
        try
        {
            canonical(preconditions); JSONArray stepList = JSON.parseArray(steps); JSONArray outcomeList = JSON.parseArray(outcomes);
            if (requireContent && (stepList.isEmpty() || outcomeList.isEmpty()))
            {
                fail("TODO_ACCEPTANCE_SCENARIO_CONTENT_REQUIRED", "Scenario review requires steps and expected outcomes");
            }
        }
        catch (TodoException exception)
        {
            throw exception;
        }
        catch (RuntimeException exception)
        {
            fail("TODO_ACCEPTANCE_JSON_INVALID", "Scenario JSON is invalid");
        }
    }

    private String canonical(String json)
    {
        return JSON.toJSONString(JSON.parse(json));
    }

    private void requireReviewer(Long reviewerId, Actor actor)
    {
        if (reviewerId == null || !reviewerId.equals(actor.userId()))
        {
            fail("TODO_ACCEPTANCE_REVIEWER_REQUIRED", "Only the selected reviewer can approve or reject");
        }
    }

    private Map<String, Object> requireMappedScenario(Long scenarioId)
    {
        if (scenarioId == null)
        {
            fail("TODO_ACCEPTANCE_MAPPING_REQUIRED", "AT mapping requires an acceptance scenario");
        }
        return rawScenario(scenarioId);
    }

    private Map<String, Object> scenarioValues(String name, String path, String preconditions, String steps,
            String outcomes, String datasetRef, String checksum, Integer dataVersion, Long ownerId,
            Long acceptorId, Long reviewerId, LocalDateTime dueAt, String status, String conclusion,
            String reviewedBy, Actor actor)
    {
        Map<String, Object> value = new HashMap<>(); value.put("scenarioName", name); value.put("businessPath", path);
        value.put("preconditionsJson", canonical(preconditions)); value.put("stepsJson", canonical(steps));
        value.put("expectedOutcomesJson", canonical(outcomes)); value.put("datasetRef", datasetRef);
        value.put("datasetChecksum", checksum == null ? null : checksum.toLowerCase()); value.put("datasetVersion", dataVersion);
        value.put("ownerUserId", ownerId); value.put("acceptorUserId", acceptorId); value.put("reviewerUserId", reviewerId);
        value.put("dueAt", dueAt); value.put("status", status); value.put("conclusion", conclusion);
        value.put("reviewedBy", reviewedBy); return value;
    }

    private Map<String, Object> mappingValues(Long id, Integer version, String from, Long scenarioId,
            String plannedTestRef, String note, Long ownerId, Long reviewerId, LocalDateTime dueAt,
            String status, String conclusion, String reviewedBy, String updateBy)
    {
        Map<String, Object> value = new HashMap<>(); value.put("mappingId", id); value.put("expectedVersion", version);
        value.put("expectedStatus", from); value.put("scenarioId", scenarioId); value.put("plannedTestRef", plannedTestRef);
        value.put("evidenceNote", note); value.put("ownerUserId", ownerId); value.put("reviewerUserId", reviewerId);
        value.put("dueAt", dueAt); value.put("status", status); value.put("conclusion", conclusion);
        value.put("reviewedBy", reviewedBy); value.put("updateBy", updateBy); return value;
    }

    private TodoAcceptanceScenarioView requireScenario(Long id) { return scenarioView(rawScenario(id)); }
    private TodoAcceptanceMappingView requireMapping(Long id) { return mappingView(rawMapping(id)); }
    private Map<String, Object> rawScenario(Long id)
    {
        Map<String, Object> row = mapper.selectScenarioById(id);
        if (!present(row)) fail("TODO_ACCEPTANCE_SCENARIO_NOT_FOUND", "Acceptance scenario not found");
        return row;
    }
    private Map<String, Object> rawMapping(Long id)
    {
        Map<String, Object> row = mapper.selectMappingById(id);
        if (!present(row)) fail("TODO_ACCEPTANCE_MAPPING_NOT_FOUND", "AT mapping not found");
        return row;
    }

    private TodoAcceptanceScenarioView scenarioView(Map<String, Object> row)
    {
        return new TodoAcceptanceScenarioView(number(value(row, "scenario_id", "scenarioId")),
                text(value(row, "scenario_code", "scenarioCode")), text(value(row, "scenario_name", "scenarioName")),
                text(value(row, "delivery_phase", "deliveryPhase")), text(value(row, "business_path", "businessPath")),
                text(value(row, "preconditions_json", "preconditionsJson")), text(value(row, "steps_json", "stepsJson")),
                text(value(row, "expected_outcomes_json", "expectedOutcomesJson")), text(value(row, "dataset_ref", "datasetRef")),
                text(value(row, "dataset_checksum", "datasetChecksum")), integerOrNull(value(row, "dataset_version", "datasetVersion")),
                number(value(row, "owner_user_id", "ownerUserId")), text(value(row, "owner_user_name", "ownerUserName")),
                text(value(row, "owner_nick_name", "ownerNickName")), number(value(row, "acceptor_user_id", "acceptorUserId")),
                text(value(row, "acceptor_user_name", "acceptorUserName")), text(value(row, "acceptor_nick_name", "acceptorNickName")),
                number(value(row, "reviewer_user_id", "reviewerUserId")), text(value(row, "reviewer_user_name", "reviewerUserName")),
                text(value(row, "reviewer_nick_name", "reviewerNickName")), date(value(row, "due_at", "dueAt")),
                text(value(row, "status", "status")), text(value(row, "conclusion", "conclusion")),
                text(value(row, "reviewed_by", "reviewedBy")), date(value(row, "reviewed_time", "reviewedTime")),
                text(value(row, "create_by", "createBy")), date(value(row, "create_time", "createTime")),
                text(value(row, "update_by", "updateBy")), date(value(row, "update_time", "updateTime")),
                integer(value(row, "version", "version")));
    }

    private TodoAcceptanceMappingView mappingView(Map<String, Object> row)
    {
        return new TodoAcceptanceMappingView(number(value(row, "mapping_id", "mappingId")),
                text(value(row, "acceptance_ref", "acceptanceRef")), text(value(row, "template_code", "templateCode")),
                text(value(row, "dimension_code", "dimensionCode")), number(value(row, "scenario_id", "scenarioId")),
                text(value(row, "scenario_code", "scenarioCode")), text(value(row, "scenario_name", "scenarioName")),
                text(value(row, "scenario_status", "scenarioStatus")), text(value(row, "planned_test_ref", "plannedTestRef")),
                text(value(row, "evidence_note", "evidenceNote")), number(value(row, "owner_user_id", "ownerUserId")),
                text(value(row, "owner_user_name", "ownerUserName")), text(value(row, "owner_nick_name", "ownerNickName")),
                number(value(row, "reviewer_user_id", "reviewerUserId")), text(value(row, "reviewer_user_name", "reviewerUserName")),
                text(value(row, "reviewer_nick_name", "reviewerNickName")), date(value(row, "due_at", "dueAt")),
                text(value(row, "status", "status")), text(value(row, "conclusion", "conclusion")),
                text(value(row, "reviewed_by", "reviewedBy")), date(value(row, "reviewed_time", "reviewedTime")),
                text(value(row, "update_by", "updateBy")), date(value(row, "update_time", "updateTime")),
                integer(value(row, "version", "version")));
    }

    private Object value(Map<String, Object> row, String snake, String camel) { return row.containsKey(snake) ? row.get(snake) : row.get(camel); }
    private boolean present(Map<String, Object> row) { return row != null && !row.isEmpty(); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private boolean terminal(String status) { return "APPROVED".equals(status) || "REJECTED".equals(status); }
    private String text(Object value) { return value == null ? null : String.valueOf(value); }
    private Long number(Object value) { return value == null ? null : Long.valueOf(String.valueOf(value)); }
    private int integer(Object value) { return value == null ? 0 : Integer.parseInt(String.valueOf(value)); }
    private Integer integerOrNull(Object value) { return value == null ? null : Integer.valueOf(String.valueOf(value)); }
    private LocalDateTime date(Object value) { if (value instanceof LocalDateTime date) return date; if (value instanceof Timestamp time) return time.toLocalDateTime(); return null; }
    private void fail(String code, String message) { throw new TodoException(code, message); }
}
