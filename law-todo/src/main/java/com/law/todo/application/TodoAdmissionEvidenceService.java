package com.law.todo.application;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson2.JSON;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoAdmissionEvidenceCommands.UpdateAdmissionEvidenceCommand;
import com.law.todo.application.view.TodoAdmissionEvidenceView;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoAdmissionEvidenceMapper;

@Service
public class TodoAdmissionEvidenceService
{
    private static final List<String> STATUSES=List.of("OPEN","IN_REVIEW","APPROVED","REJECTED");
    private final TodoAdmissionEvidenceMapper mapper;
    private final TodoFoundationResourceService resources;
    private final TodoHistoricalMigrationReadinessService migrations;
    public TodoAdmissionEvidenceService(TodoAdmissionEvidenceMapper mapper,TodoFoundationResourceService resources,
            TodoHistoricalMigrationReadinessService migrations){this.mapper=mapper;this.resources=resources;this.migrations=migrations;}

    @Transactional(readOnly=true)
    public List<TodoAdmissionEvidenceView> list(){return mapper.selectEvidence().stream().map(this::view).toList();}

    @Transactional(readOnly=true)
    public Map<String,Object> governanceOptions()
    {
        Map<String,Object> options=new LinkedHashMap<>();options.put("users",List.copyOf(mapper.selectActiveUsers()));options.put("statuses",STATUSES);
        return Map.copyOf(options);
    }

    @Transactional
    public TodoAdmissionEvidenceView update(UpdateAdmissionEvidenceCommand command,Actor actor)
    {
        validateRequiredAccountability(command);
        Map<String,Object> current=raw(command.evidenceId());
        validate(command,actor,current);
        String fingerprint=fingerprint(command,actor);
        Long replay=claim(command,fingerprint,actor);
        if(replay!=null)return require(replay);
        Map<String,Object> value=new HashMap<>();value.put("evidenceId",command.evidenceId());value.put("expectedVersion",command.version());
        value.put("ownerUserId",command.ownerUserId());value.put("reviewerUserId",command.reviewerUserId());value.put("dueAt",command.dueAt());
        value.put("status",command.status());value.put("artifactRef",command.artifactRef());value.put("conclusion",command.conclusion());
        value.put("reviewedBy",terminal(command.status())?actor.userName():null);value.put("updateBy",actor.userName());
        if(mapper.updateEvidenceConditionally(value)<=0)fail("TODO_ADMISSION_VERSION_CONFLICT","Admission evidence changed; refresh before retrying");
        if(mapper.completeEvidenceAction(command.actionId(),fingerprint,command.evidenceId())<=0)fail("TODO_ADMISSION_ACTION_CONFLICT","Admission evidence action could not be completed");
        return require(command.evidenceId());
    }

    public static String fingerprint(UpdateAdmissionEvidenceCommand command,Actor actor)
    {
        Map<String,Object> values=new TreeMap<>();values.put("type","UPDATE_ADMISSION_EVIDENCE");values.put("id",command.evidenceId());
        values.put("version",command.version());values.put("actor",actor.userId());values.put("request",JSON.parse(JSON.toJSONString(command)));
        return TodoDefinitionSimulationService.sha256(JSON.toJSONString(values));
    }

    private void validate(UpdateAdmissionEvidenceCommand command,Actor actor,Map<String,Object> current)
    {
        if(!present(mapper.selectActiveUser(command.ownerUserId())))fail("TODO_ADMISSION_OWNER_INVALID","Admission evidence owner must be active");
        if(!present(mapper.selectActiveUser(command.reviewerUserId())))fail("TODO_ADMISSION_REVIEWER_INVALID","Admission evidence reviewer must be active");
        if(!"OPEN".equals(command.status())&&(blank(command.artifactRef())||blank(command.conclusion())))
            fail("TODO_ADMISSION_ARTIFACT_REQUIRED","Submitted evidence requires an artifact reference and conclusion");
        if(terminal(command.status())&&!command.reviewerUserId().equals(actor.userId()))
            fail("TODO_ADMISSION_REVIEWER_REQUIRED","Only the selected reviewer can approve or reject admission evidence");
        String from=text(value(current,"status","status"));
        boolean valid=(from.equals(command.status())&&!terminal(from))||("OPEN".equals(from)&&"IN_REVIEW".equals(command.status()))
                ||("IN_REVIEW".equals(from)&&terminal(command.status()))||("REJECTED".equals(from)&&"IN_REVIEW".equals(command.status()));
        if(!valid)fail("TODO_ADMISSION_STATE_INVALID","Admission evidence state transition is invalid");
        if("APPROVED".equals(command.status())&&"G-02".equals(text(value(current,"gate_code","gateCode")))&&!resources.gateReady("G-02"))
            fail("TODO_ADMISSION_RESOURCE_NOT_READY","G-02 cannot be approved while repository resources are unresolved or missing at runtime");
        if("APPROVED".equals(command.status())&&"G-04".equals(text(value(current,"gate_code","gateCode")))&&!migrations.gateReady("G-04"))
            fail("TODO_ADMISSION_MIGRATION_NOT_READY","G-04 cannot be approved while the historical migration contract is unresolved or invalid");
    }

    private void validateRequiredAccountability(UpdateAdmissionEvidenceCommand command)
    {
        if(command.ownerUserId()==null)fail("TODO_ADMISSION_OWNER_REQUIRED","Admission evidence requires an accountable owner");
        if(command.reviewerUserId()==null)fail("TODO_ADMISSION_REVIEWER_REQUIRED","Admission evidence requires an independent reviewer");
        if(command.ownerUserId().equals(command.reviewerUserId()))fail("TODO_ADMISSION_REVIEWER_INDEPENDENCE_REQUIRED","Owner and reviewer must be different users");
        if(command.dueAt()==null)fail("TODO_ADMISSION_DUE_REQUIRED","Admission evidence requires a due date");
    }

    private Long claim(UpdateAdmissionEvidenceCommand command,String fingerprint,Actor actor)
    {
        Map<String,Object> action=new HashMap<>();action.put("actionId",command.actionId());action.put("evidenceId",command.evidenceId());
        action.put("requestFingerprint",fingerprint);action.put("operatorId",actor.userId());action.put("operatorName",actor.userName());
        action.put("operatorDeptId",actor.deptId());action.put("payloadJson",JSON.toJSONString(command));mapper.insertEvidenceActionClaim(action);
        Map<String,Object> locked=mapper.selectEvidenceActionForUpdate(command.actionId());
        if(!present(locked)||!fingerprint.equals(text(value(locked,"request_fingerprint","requestFingerprint")))
                ||!actor.userId().equals(number(value(locked,"operator_id","operatorId"))))
            fail("TODO_ADMISSION_ACTION_CONFLICT","Action id was already used for another admission evidence request");
        String status=text(value(locked,"action_status","actionStatus"));Long entity=number(value(locked,"evidence_id","evidenceId"));
        if("APPLIED".equals(status)&&entity!=null)return entity;
        if(!"CLAIMED".equals(status))fail("TODO_ADMISSION_ACTION_CONFLICT","Admission evidence action has an invalid state");
        return null;
    }

    private TodoAdmissionEvidenceView require(Long id){return view(raw(id));}
    private Map<String,Object> raw(Long id){Map<String,Object> row=mapper.selectEvidenceById(id);if(!present(row))fail("TODO_ADMISSION_EVIDENCE_NOT_FOUND","Admission evidence item not found");return row;}
    private TodoAdmissionEvidenceView view(Map<String,Object> row)
    {
        return new TodoAdmissionEvidenceView(number(value(row,"evidence_id","evidenceId")),text(value(row,"evidence_code","evidenceCode")),
                text(value(row,"gate_code","gateCode")),text(value(row,"category","category")),text(value(row,"title","title")),
                text(value(row,"description","description")),text(value(row,"delivery_phase","deliveryPhase")),text(value(row,"status","status")),
                number(value(row,"owner_user_id","ownerUserId")),text(value(row,"owner_user_name","ownerUserName")),text(value(row,"owner_nick_name","ownerNickName")),
                number(value(row,"reviewer_user_id","reviewerUserId")),text(value(row,"reviewer_user_name","reviewerUserName")),text(value(row,"reviewer_nick_name","reviewerNickName")),
                date(value(row,"due_at","dueAt")),text(value(row,"artifact_ref","artifactRef")),text(value(row,"conclusion","conclusion")),
                text(value(row,"reviewed_by","reviewedBy")),date(value(row,"reviewed_time","reviewedTime")),text(value(row,"update_by","updateBy")),
                date(value(row,"update_time","updateTime")),integer(value(row,"version","version")));
    }
    private Object value(Map<String,Object> row,String snake,String camel){return row.containsKey(snake)?row.get(snake):row.get(camel);}
    private boolean present(Map<String,Object> row){return row!=null&&!row.isEmpty();}private boolean blank(String value){return value==null||value.isBlank();}
    private boolean terminal(String status){return "APPROVED".equals(status)||"REJECTED".equals(status);}
    private String text(Object value){return value==null?null:String.valueOf(value);}private Long number(Object value){return value==null?null:Long.valueOf(String.valueOf(value));}
    private int integer(Object value){return value==null?0:Integer.parseInt(String.valueOf(value));}
    private LocalDateTime date(Object value){if(value instanceof LocalDateTime date)return date;if(value instanceof Timestamp timestamp)return timestamp.toLocalDateTime();return null;}
    private void fail(String code,String message){throw new TodoException(code,message);}
}
