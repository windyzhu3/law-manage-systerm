package com.ruoyi.system.service.todo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.domain.TodoException;
import com.law.todo.spi.TodoBusinessPayloadAccess;
import com.law.todo.spi.TodoBusinessPayloadAccess.PayloadFieldSource;
import com.law.todo.spi.TodoBusinessPayloadAccess.PayloadHydration;
import com.ruoyi.system.mapper.TodoBusinessDirectoryMapper;

/** RuoYi business payload adapter that applies exactly the directory's actor-scoped SQL contract. */
@Component
public class RuoYiTodoBusinessPayloadAccess implements TodoBusinessPayloadAccess
{
    private static final Set<String> TYPES=Set.of("LEAD","CUSTOMER","CONTRACT","CASE","MATTER");
    private final TodoBusinessDirectoryMapper mapper;

    public RuoYiTodoBusinessPayloadAccess(TodoBusinessDirectoryMapper mapper){this.mapper=mapper;}

    @Override public boolean supports(String businessType){return TYPES.contains(businessType);}

    @Override
    public PayloadHydration hydrate(String eventType,int payloadVersion,String businessType,long businessId,Actor actor)
    {
        Map<String,Object> row=mapper.selectVisibleBusinessPayload(query(businessType,businessId,actor));
        if(row==null||row.isEmpty())throw new TodoException("TODO_SIMULATION_BUSINESS_OBJECT_NOT_FOUND",
                "Simulation business object does not exist or is not accessible");
        Map<String,Object> payload=payloadFor(eventType,payloadVersion,businessType,row);
        return new PayloadHydration(payload,fieldSources(eventType,payloadVersion,businessType,payload),false);
    }

    private Map<String,Object> query(String type,long id,Actor actor)
    {
        Map<String,Object> query=RuoYiTodoBusinessDirectoryAccess.actorScopedQuery(type,actor);
        query.put("businessId",id);return query;
    }

    private Map<String,Object> payloadFor(String eventType,int payloadVersion,String businessType,Map<String,Object> row)
    {
        if(payloadVersion!=1)throw unsupported();
        return switch(businessType)
        {
            case "LEAD" -> leadPayload(eventType,row);
            case "CUSTOMER" -> customerPayload(eventType,row);
            case "CONTRACT" -> contractPayload(eventType,row);
            case "CASE" -> casePayload(eventType,row);
            case "MATTER" -> matterPayload(eventType,row);
            default -> throw unsupported();
        };
    }

    private List<PayloadFieldSource> fieldSources(String eventType,int payloadVersion,String businessType,
            Map<String,Object> payload)
    {
        if(payloadVersion!=1)throw unsupported();
        switch(businessType)
        {
            case "LEAD" -> leadPayload(eventType,Map.of());
            case "CUSTOMER" -> customerPayload(eventType,Map.of());
            case "CONTRACT" -> contractPayload(eventType,Map.of());
            case "CASE" -> casePayload(eventType,Map.of());
            case "MATTER" -> matterPayload(eventType,Map.of());
            default -> throw unsupported();
        }
        Set<String> defaults=Set.of("contactResult","caseSource","classification","action");
        List<PayloadFieldSource> fields=new ArrayList<>();
        flatten(fields,"",payload,defaults);
        return List.copyOf(fields);
    }

    private Map<String,Object> leadPayload(String event,Map<String,Object> row)
    {
        Map<String,Object> payload=base(row,"leadId");
        switch(event)
        {
            case "LEAD_ASSIGNED" ->
            {
                put(payload,"assignmentId",value(row,"assignment_id","assignmentId"));
                put(payload,"ownerId",value(row,"owner_id","ownerId"));
                put(payload,"ownerDeptId",value(row,"dept_id","deptId"));
            }
            case "LEAD_SUSPECT_INVALID_MARKED" ->
            {
                put(payload,"reasonCode",value(row,"invalid_reason","invalidReason"));
                put(payload,"ownerId",value(row,"owner_id","ownerId"));
            }
            case "LEAD_FIRST_CONTACT_UNREACHABLE" ->
            {
                put(payload,"ownerId",value(row,"owner_id","ownerId"));
                put(payload,"attempts",value(row,"followup_count","followupCount"));
                put(payload,"nextContactAt",value(row,"next_follow_time","nextFollowTime"));
            }
            default -> throw unsupported();
        }
        return immutable(payload);
    }

    private Map<String,Object> customerPayload(String event,Map<String,Object> row)
    {
        Map<String,Object> payload=base(row,"customerId");
        switch(event)
        {
            case "LEAD_FIRST_CONTACT_VALID" ->
            {
                put(payload,"customerId",value(row,"business_id","businessId"));
                put(payload,"ownerId",value(row,"owner_id","ownerId"));
                payload.put("contactResult","VALID");
            }
            case "CUSTOMER_PROGRESS_STALE" ->
            {
                put(payload,"customerId",value(row,"business_id","businessId"));
                put(payload,"ownerId",value(row,"owner_id","ownerId"));
                put(payload,"lastFollowAt",value(row,"last_follow_time","lastFollowTime"));
            }
            default -> throw unsupported();
        }
        return immutable(payload);
    }

    private Map<String,Object> contractPayload(String event,Map<String,Object> row)
    {
        Map<String,Object> payload=base(row,"contractId");
        Object owner=value(row,"owner_id","ownerId");Object amount=value(row,"sign_amount","signAmount");
        switch(event)
        {
            case "QUOTE_DISCOUNT_APPROVAL_REQUESTED" ->
            {put(payload,"approvalOwnerId",owner);put(payload,"quoteAmount",amount);}
            case "CONFLICT_SCREENING_FAILED" ->
            {put(payload,"subjectId",value(row,"customer_id","customerId"));put(payload,"caseManagerId",owner);}
            case "QUOTE_ACCEPTED_CONFLICT_CLEARED" ->
            {put(payload,"quoteId",value(row,"business_id","businessId"));payload.put("conflictCleared",true);}
            case "CONTRACT_SUBMITTED" ->
            {put(payload,"approvalId",value(row,"approval_id","approvalId"));put(payload,"amount",amount);put(payload,"ownerId",owner);}
            case "CONTRACT_APPROVED" ->
            {put(payload,"approvalId",value(row,"approval_id","approvalId"));put(payload,"reviewerId",value(row,"approval_approver_id","approvalApproverId"));put(payload,"ownerId",owner);}
            case "CONTRACT_SIGNED" ->
            {put(payload,"signStatus",value(row,"sign_status","signStatus"));put(payload,"signedAt",value(row,"sign_date","signDate"));put(payload,"ownerId",owner);}
            case "RECEIVABLE_DUE" ->
            {put(payload,"amount",value(row,"receivable_amount","receivableAmount"));put(payload,"dueAt",value(row,"plan_receive_date","planReceiveDate"));put(payload,"ownerId",owner);}
            case "PAYMENT_CONFIRMED" ->
            {put(payload,"planId",value(row,"plan_id","planId"));put(payload,"amount",value(row,"received_amount","receivedAmount"));put(payload,"ownerId",owner);}
            case "PAYMENT_FULLY_RECEIVED" ->
            {put(payload,"receivedAmount",value(row,"received_amount","receivedAmount"));put(payload,"salesOwnerId",owner);}
            case "DEAL_CONFIRMED" ->
            {put(payload,"paymentId",value(row,"plan_id","planId"));put(payload,"salesOwnerId",owner);put(payload,"dealAmount",amount);}
            case "INVOICE_HANDLED" ->
            {put(payload,"invoiceId",value(row,"plan_id","planId"));put(payload,"invoiceStatus",value(row,"invoice_status","invoiceStatus"));put(payload,"ownerId",owner);}
            default -> throw unsupported();
        }
        return immutable(payload);
    }

    private Map<String,Object> casePayload(String event,Map<String,Object> row)
    {
        Map<String,Object> payload=base(row,"caseId");Object owner=value(row,"owner_id","ownerId");
        Object lawyer=value(row,"main_lawyer_id","mainLawyerId");
        switch(event)
        {
            case "CASE_CREATED" ->
            {put(payload,"contractId",value(row,"contract_id","contractId"));put(payload,"ownerId",owner);payload.put("caseSource","CONTRACT");}
            case "CASE_ASSIGNED" ->
            {put(payload,"assignmentId",value(row,"assignment_id","assignmentId"));put(payload,"lawyerId",lawyer);put(payload,"ownerId",owner);}
            case "CASE_HANDOFF_SUBMITTED" ->
            {put(payload,"handoffId",value(row,"assignment_id","assignmentId"));put(payload,"submittedBy",owner);}
            case "CASE_HANDOFF_ACCEPTED" ->
            {put(payload,"handoffId",value(row,"assignment_id","assignmentId"));put(payload,"acceptedBy",owner);}
            case "CASE_CLASSIFIED_COMPREHENSIVE" ->
            {payload.put("classification","COMPREHENSIVE");put(payload,"partnerLawyerId",lawyer);put(payload,"caseManagerId",owner);}
            case "CASE_CLASSIFIED_NON_LITIGATION" ->
            {payload.put("classification","NON_LITIGATION");put(payload,"assigneeId",lawyer);put(payload,"caseManagerId",owner);}
            case "CASE_CLASSIFIED_ENFORCEMENT" ->
            {payload.put("classification","ENFORCEMENT");put(payload,"primaryAssistantId",lawyer);put(payload,"caseManagerId",owner);}
            case "CASE_REJECTED" ->
            {
                put(payload,"reasonCode",value(row,"rejection_reason_code","rejectionReasonCode"));
                put(payload,"reason",value(row,"rejection_reason","rejectionReason"));
                put(payload,"lawyerId",lawyer);
            }
            case "CASE_TRANSFER_REQUESTED" ->
            {put(payload,"transferId",value(row,"transfer_id","transferId"));put(payload,"targetLawyerId",value(row,"target_lawyer_id","targetLawyerId"));put(payload,"reason",value(row,"transfer_reason","transferReason"));}
            case "CASE_TRANSFER_APPROVED" ->
            {put(payload,"transferId",value(row,"transfer_id","transferId"));put(payload,"targetLawyerId",value(row,"target_lawyer_id","targetLawyerId"));put(payload,"approvedBy",owner);}
            default -> throw unsupported();
        }
        return immutable(payload);
    }

    private Map<String,Object> matterPayload(String event,Map<String,Object> row)
    {
        Map<String,Object> payload=base(row,"matterId");Object owner=value(row,"owner_id","ownerId");
        Object lawyer=value(row,"main_lawyer_id","mainLawyerId");
        switch(event)
        {
            case "ARCHIVE_APPLIED" ->
            {put(payload,"archiveId",value(row,"archive_id","archiveId"));put(payload,"applicantId",owner);payload.put("action","apply");}
            case "CASE_CLOSED" ->
            {payload.put("action","pass");put(payload,"archiveNo",value(row,"archive_no","archiveNo"));put(payload,"assistantId",lawyer);}
            case "MATTER_NODE_READY" ->
            {
                put(payload,"nodeId",value(row,"node_id","nodeId"));put(payload,"ownerId",owner);
                Object node=value(row,"node_code","nodeCode");
                put(payload,"nodeCode",node==null?value(row,"current_node","currentNode"):node);
            }
            case "ENFORCEMENT_SERVICE_NODE_READY" ->
            {
                put(payload,"nodeId",value(row,"node_id","nodeId"));put(payload,"ownerId",owner);
                put(payload,"nodeType",value(row,"node_type","nodeType"));
            }
            case "MATTER_EXPENSE_SUBMITTED" ->
            {put(payload,"expenseId",value(row,"expense_id","expenseId"));put(payload,"amount",value(row,"expense_amount","expenseAmount"));put(payload,"ownerId",owner);}
            case "MATTER_DOCUMENT_REQUIRED" ->
            {put(payload,"ownerId",owner);put(payload,"reason",value(row,"recent_progress","recentProgress"));}
            case "MATTER_HEARING_COMPLETED" ->
            {put(payload,"hearingDate",value(row,"next_key_date","nextKeyDate"));put(payload,"secondaryLawyerId",lawyer);}
            case "RISK_FEE_CONFIRMED" ->
            {put(payload,"salesOwnerId",owner);put(payload,"confirmedBy",lawyer);}
            case "NON_LITIGATION_WORK_COMPLETED" ->
            {put(payload,"assigneeId",lawyer);put(payload,"signedAt",value(row,"last_progress_time","lastProgressTime"));}
            case "ENFORCEMENT_ORDER_ACCEPTED" ->
            {put(payload,"primaryAssistantId",lawyer);payload.put("acceptanceResult","ACCEPTED");}
            default -> throw unsupported();
        }
        return immutable(payload);
    }

    private Map<String,Object> base(Map<String,Object> row,String idName)
    {
        Map<String,Object> payload=new LinkedHashMap<>();
        put(payload,idName,value(row,"business_id","businessId"));
        put(payload,"businessNo",value(row,"business_no","businessNo"));
        put(payload,"businessName",value(row,"business_name","businessName"));
        return payload;
    }

    private void put(Map<String,Object> payload,String key,Object value){if(value!=null)payload.put(key,value);}
    private Map<String,Object> immutable(Map<String,Object> payload)
    {return Collections.unmodifiableMap(new LinkedHashMap<>(payload));}
    private Object value(Map<String,Object> row,String snake,String camel)
    {return row.containsKey(snake)?row.get(snake):row.get(camel);}

    private void flatten(List<PayloadFieldSource> fields,String prefix,Map<String,Object> payload,Set<String> defaults)
    {
        payload.forEach((key,value)->
        {
            String path=prefix.isEmpty()?key:prefix+"."+key;
            if(value instanceof Map<?,?> nested)
            {
                Map<String,Object> mapped=new LinkedHashMap<>();
                nested.forEach((nestedKey,nestedValue)->mapped.put(String.valueOf(nestedKey),nestedValue));
                flatten(fields,path,mapped,defaults);
            }
            else fields.add(new PayloadFieldSource(path,value,defaults.contains(path)?"SYSTEM_DEFAULT":"BUSINESS_OBJECT",
                    false,false,null,false));
        });
    }

    private TodoException unsupported()
    {return new TodoException("TODO_SIMULATION_PAYLOAD_MAPPING_UNSUPPORTED","The selected event, business type, and payload version are not supported");}
}
