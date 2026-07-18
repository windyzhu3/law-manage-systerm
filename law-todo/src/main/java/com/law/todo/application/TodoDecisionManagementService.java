package com.law.todo.application;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson2.JSON;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoDecisionCommands.CreateDecisionCommand;
import com.law.todo.application.command.TodoDecisionCommands.UpdateDecisionCommand;
import com.law.todo.application.view.TodoDecisionView;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoMapper;

@Service
public class TodoDecisionManagementService
{
    private static final List<String> DELIVERY_PHASES=List.of("PHASE_ONE","PHASE_TWO","CROSS_PHASE");
    private final TodoMapper mapper;
    public TodoDecisionManagementService(TodoMapper mapper){this.mapper=mapper;}

    @Transactional(readOnly=true)
    public List<TodoDecisionView> list()
    {return mapper.selectDecisions().stream().map(this::view).sorted(Comparator.comparing(TodoDecisionView::code)).toList();}

    @Transactional(readOnly=true)
    public Map<String,Object> governanceOptions()
    {
        Map<String,Object> options=new LinkedHashMap<>();
        options.put("users",List.copyOf(mapper.selectDecisionGovernanceUsers()));
        options.put("roles",List.copyOf(mapper.selectDecisionGovernanceRoles()));
        options.put("deliveryPhases",DELIVERY_PHASES);
        return Map.copyOf(options);
    }

    @Transactional
    public TodoDecisionView create(CreateDecisionCommand command,Actor actor)
    {
        validate(command.status(),command.conclusion(),command.resolution());
        validateGovernance(command.blocking(),command.ownerUserId(),command.ownerRoleKey(),command.dueAt(),command.deliveryPhase());
        String fingerprint=fingerprint("CREATE_DECISION",null,null,command,actor);
        Long replay=claim(command.actionId(),"CREATE_DECISION",null,fingerprint,actor,command);
        if(replay!=null)return require(replay);
        Map<String,Object> existing=mapper.selectDecisionByCode(command.code());
        if(present(existing))fail("TODO_DECISION_CODE_CONFLICT","Decision code already exists");
        Map<String,Object> row=values(command.code(),command.title(),command.description(),command.blocking(),command.status(),command.conclusion(),command.resolution(),
                command.ownerUserId(),command.ownerRoleKey(),command.dueAt(),command.deliveryPhase(),actor);
        row.put("createBy",actor.userName());
        if(mapper.insertDecision(row)<=0)fail("TODO_DECISION_CODE_CONFLICT","Decision code already exists");
        Long id=number(row.get("decisionId"));complete(command.actionId(),fingerprint,id);return require(id);
    }

    @Transactional
    public TodoDecisionView update(UpdateDecisionCommand command,Actor actor)
    {
        validate(command.status(),command.conclusion(),command.resolution());
        validateGovernance(command.blocking(),command.ownerUserId(),command.ownerRoleKey(),command.dueAt(),command.deliveryPhase());
        Map<String,Object> current=raw(command.decisionId());
        if(!command.code().equals(text(value(current,"decision_code","decisionCode"))))
            fail("TODO_DECISION_CODE_IMMUTABLE","Decision code cannot be changed");
        String fingerprint=fingerprint("UPDATE_DECISION",command.decisionId(),command.version(),command,actor);
        Long replay=claim(command.actionId(),"UPDATE_DECISION",command.decisionId(),fingerprint,actor,command);
        if(replay!=null)return require(replay);
        Map<String,Object> row=values(command.code(),command.title(),command.description(),command.blocking(),command.status(),command.conclusion(),command.resolution(),
                command.ownerUserId(),command.ownerRoleKey(),command.dueAt(),command.deliveryPhase(),actor);
        row.put("decisionId",command.decisionId());row.put("expectedVersion",command.version());row.put("updateBy",actor.userName());
        if(mapper.updateDecisionConditionally(row)<=0)fail("TODO_DECISION_VERSION_CONFLICT","Decision changed; refresh before retrying");
        complete(command.actionId(),fingerprint,command.decisionId());return require(command.decisionId());
    }

    public static String fingerprint(String type,Long decisionId,Integer expectedVersion,Object command,Actor actor)
    {
        Map<String,Object> values=new TreeMap<>();values.put("actionType",type);values.put("actorDeptId",actor.deptId());
        values.put("actorId",actor.userId());values.put("actorName",actor.userName());values.put("decisionId",decisionId);
        values.put("expectedVersion",expectedVersion);values.put("request",JSON.parse(JSON.toJSONString(command)));
        return TodoDefinitionSimulationService.sha256(JSON.toJSONString(values));
    }

    private Long claim(String actionId,String type,Long source,String fingerprint,Actor actor,Object command)
    {
        Map<String,Object> action=new HashMap<>();action.put("actionId",actionId);action.put("actionType",type);action.put("entityType","DECISION");
        action.put("sourceEntityId",source);action.put("operatorId",actor.userId());action.put("operatorName",actor.userName());
        action.put("operatorDeptId",actor.deptId());action.put("requestFingerprint",fingerprint);action.put("payloadJson",JSON.toJSONString(command));
        mapper.insertDefinitionActionClaim(action);Map<String,Object> locked=mapper.selectDefinitionActionForUpdate(actionId);
        if(!present(locked)||!type.equals(text(value(locked,"action_type","actionType")))
                ||!fingerprint.equals(text(value(locked,"request_fingerprint","requestFingerprint")))
                ||!actor.userId().equals(number(value(locked,"operator_id","operatorId")))
                ||!actor.userName().equals(text(value(locked,"operator_name","operatorName"))))
            fail("TODO_DECISION_ACTION_CONFLICT","Decision action id belongs to another request");
        Long entity=number(value(locked,"entity_id","entityId"));String status=text(value(locked,"action_status","actionStatus"));
        if(entity!=null)
        {
            if(!"APPLIED".equals(status))fail("TODO_DECISION_ACTION_CONFLICT","Recorded decision action is incomplete");
            return entity;
        }
        if(!"CLAIMED".equals(status))fail("TODO_DECISION_ACTION_CONFLICT","Decision action is not claimable");
        return null;
    }

    private void complete(String actionId,String fingerprint,Long id)
    {if(id==null||mapper.completeDefinitionAction(actionId,fingerprint,id)<=0)fail("TODO_DECISION_ACTION_CONFLICT","Decision action could not be completed");}
    private void validate(String status,String conclusion,String resolution)
    {
        if(!List.of("OPEN","RESOLVED","CLOSED").contains(status))fail("TODO_DECISION_STATUS_INVALID","Decision status is invalid");
        boolean decided=!"OPEN".equals(status);
        if(decided&&blank(conclusion))fail("TODO_DECISION_CONCLUSION_REQUIRED","Resolved or closed decisions require a conclusion");
        if(decided&&blank(resolution))fail("TODO_DECISION_RESOLUTION_REQUIRED","Resolved or closed decisions require a resolution");
        if(!decided&&(!blank(conclusion)||!blank(resolution)))fail("TODO_DECISION_OPEN_RESULT_INVALID","Open decisions cannot have a conclusion or resolution");
    }
    private void validateGovernance(Boolean blocking,Long ownerUserId,String ownerRoleKey,LocalDateTime dueAt,String deliveryPhase)
    {
        if(!DELIVERY_PHASES.contains(deliveryPhase))fail("TODO_DECISION_PHASE_INVALID","Decision delivery phase is invalid");
        if(Boolean.TRUE.equals(blocking))
        {
            if(ownerUserId==null)fail("TODO_DECISION_OWNER_REQUIRED","Blocking decisions require an accountable owner");
            if(blank(ownerRoleKey))fail("TODO_DECISION_OWNER_ROLE_REQUIRED","Blocking decisions require a responsibility role");
            if(dueAt==null)fail("TODO_DECISION_DUE_REQUIRED","Blocking decisions require a due date");
        }
        if(ownerUserId!=null&&!present(mapper.selectDecisionGovernanceUser(ownerUserId)))
            fail("TODO_DECISION_OWNER_INVALID","Decision owner must be an active user");
        if(!blank(ownerRoleKey)&&mapper.selectRoleIdByKey(ownerRoleKey)==null)
            fail("TODO_DECISION_OWNER_ROLE_INVALID","Decision responsibility role must be active");
    }
    private Map<String,Object> values(String code,String title,String description,Boolean blocking,String status,String conclusion,String resolution,
            Long ownerUserId,String ownerRoleKey,LocalDateTime dueAt,String deliveryPhase,Actor actor)
    {
        Map<String,Object> row=new HashMap<>();row.put("decisionCode",code);row.put("title",title);row.put("description",description);
        row.put("blocking",Boolean.TRUE.equals(blocking)?"Y":"N");row.put("status",status);row.put("conclusion",conclusion);row.put("resolution",resolution);
        row.put("ownerUserId",ownerUserId);row.put("ownerRoleKey",ownerRoleKey);row.put("dueAt",dueAt);row.put("deliveryPhase",deliveryPhase);
        row.put("decidedBy","OPEN".equals(status)?null:actor.userName());return row;
    }
    private TodoDecisionView require(Long id){return view(raw(id));}
    private Map<String,Object> raw(Long id){Map<String,Object> row=mapper.selectDecisionById(id);if(!present(row))fail("TODO_DECISION_NOT_FOUND","Decision not found");return row;}
    private TodoDecisionView view(Map<String,Object> row){return new TodoDecisionView(number(value(row,"decision_id","decisionId")),text(value(row,"decision_code","decisionCode")),text(value(row,"title","title")),text(value(row,"description","description")),truth(value(row,"blocking","blocking")),text(value(row,"status","status")),text(value(row,"conclusion","conclusion")),text(value(row,"resolution","resolution")),text(value(row,"decided_by","decidedBy")),date(value(row,"decided_time","decidedTime")),number(value(row,"owner_user_id","ownerUserId")),text(value(row,"owner_user_name","ownerUserName")),text(value(row,"owner_nick_name","ownerNickName")),text(value(row,"owner_role_key","ownerRoleKey")),date(value(row,"due_at","dueAt")),text(value(row,"delivery_phase","deliveryPhase")),text(value(row,"create_by","createBy")),date(value(row,"create_time","createTime")),text(value(row,"update_by","updateBy")),date(value(row,"update_time","updateTime")),integer(value(row,"version","version")),codes(text(value(row,"impacted_template_codes","impactedTemplateCodes"))));}
    private List<String> codes(String values){return values==null||values.isBlank()?List.of():List.of(values.split(","));}
    private Object value(Map<String,Object> row,String snake,String camel){return row.containsKey(snake)?row.get(snake):row.get(camel);}
    private boolean present(Map<String,Object> row){return row!=null&&!row.isEmpty();}private boolean blank(String value){return value==null||value.isBlank();}
    private String text(Object value){return value==null?null:String.valueOf(value);}private Long number(Object value){return value==null?null:Long.valueOf(String.valueOf(value));}
    private int integer(Object value){return value==null?0:Integer.parseInt(String.valueOf(value));}private boolean truth(Object value){return Boolean.TRUE.equals(value)||"Y".equalsIgnoreCase(text(value))||"1".equals(text(value))||"true".equalsIgnoreCase(text(value));}
    private LocalDateTime date(Object value){if(value instanceof LocalDateTime date)return date;if(value instanceof Timestamp timestamp)return timestamp.toLocalDateTime();return null;}
    private void fail(String code,String message){throw new TodoException(code,message);}
}
