package com.ruoyi.system.service.todo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.domain.TodoException;
import com.ruoyi.system.mapper.TodoBusinessDirectoryMapper;

@ExtendWith(MockitoExtension.class)
class RuoYiTodoBusinessPayloadAccessTest
{
    @Mock TodoBusinessDirectoryMapper mapper;
    @Captor ArgumentCaptor<Map<String,Object>> query;
    private final Actor actor=new Actor(7L,"operator",3L);

    @Test void mapsAllFiveBusinessFamiliesWithStableEventPayloads()
    {
        record Fixture(String type,String event,Map<String,Object> row,String expectedKey,Object expectedValue) { }
        List<Fixture> fixtures=List.of(
                new Fixture("LEAD","LEAD_ASSIGNED",row("business_id",81L,"owner_id",7L,"dept_id",3L),
                        "leadId",81L),
                new Fixture("CUSTOMER","CUSTOMER_PROGRESS_STALE",
                        row("business_id",82L,"owner_id",7L,"last_follow_time","2026-07-22T09:00:00"),
                        "customerId",82L),
                new Fixture("CONTRACT","CONTRACT_SUBMITTED",
                        row("business_id",83L,"owner_id",7L,"sign_amount",new BigDecimal("100000")),
                        "amount",new BigDecimal("100000")),
                new Fixture("CASE","CASE_CREATED",
                        row("business_id",84L,"contract_id",83L,"owner_id",7L),
                        "contractId",83L),
                new Fixture("MATTER","MATTER_NODE_READY",
                        row("business_id",85L,"current_node","HEARING","owner_id",7L),
                        "nodeCode","HEARING"));

        RuoYiTodoBusinessPayloadAccess access=new RuoYiTodoBusinessPayloadAccess(mapper);
        for(Fixture fixture:fixtures)
        {
            when(mapper.selectVisibleBusinessPayload(anyMap())).thenReturn(fixture.row());
            var result=access.hydrate(fixture.event(),1,fixture.type(),fixture.type().equals("LEAD")?81L:
                    fixture.type().equals("CUSTOMER")?82L:fixture.type().equals("CONTRACT")?83L:
                    fixture.type().equals("CASE")?84L:85L,actor);
            assertThat(result.payload()).containsEntry(fixture.expectedKey(),fixture.expectedValue());
            assertThat(result.fields()).allMatch(field->List.of("BUSINESS_OBJECT","SYSTEM_DEFAULT").contains(field.source()));
            assertThat(result.fields()).extracting(com.law.todo.spi.TodoBusinessPayloadAccess.PayloadFieldSource::source)
                    .contains("BUSINESS_OBJECT");
        }
    }

    @Test void mapsCorrectGovernedProjectionForApprovalRejectionAndEnforcementNode()
    {
        RuoYiTodoBusinessPayloadAccess access=new RuoYiTodoBusinessPayloadAccess(mapper);
        when(mapper.selectVisibleBusinessPayload(anyMap())).thenReturn(row(
                "business_id",83L,"approval_id",901L,"approval_approver_id",31L,"lawyer_id",21L));
        assertThat(access.hydrate("CONTRACT_APPROVED",1,"CONTRACT",83L,actor).payload())
                .containsEntry("reviewerId",31L);

        when(mapper.selectVisibleBusinessPayload(anyMap())).thenReturn(row(
                "business_id",84L,"main_lawyer_id",21L,"rejection_reason_code","conflict",
                "rejection_reason","存在利益冲突","business_status","pending"));
        assertThat(access.hydrate("CASE_REJECTED",1,"CASE",84L,actor).payload())
                .containsEntry("reasonCode","conflict")
                .containsEntry("reason","存在利益冲突");

        when(mapper.selectVisibleBusinessPayload(anyMap())).thenReturn(row(
                "business_id",85L,"node_id",1001L,"node_type","ENFORCEMENT_PROGRESS","node_code","HEARING"));
        assertThat(access.hydrate("ENFORCEMENT_SERVICE_NODE_READY",1,"MATTER",85L,actor).payload())
                .containsEntry("nodeType","ENFORCEMENT_PROGRESS")
                .doesNotContainKey("nodeCode");
    }

    @Test void everyGovernedEventTupleHasAnExplicitSchemaMapping()
    {
        record Fixture(String type,String event,String expectedKey) { }
        List<Fixture> fixtures=List.of(
                new Fixture("LEAD","LEAD_ASSIGNED","assignmentId"),
                new Fixture("LEAD","LEAD_SUSPECT_INVALID_MARKED","reasonCode"),
                new Fixture("LEAD","LEAD_FIRST_CONTACT_UNREACHABLE","attempts"),
                new Fixture("CUSTOMER","LEAD_FIRST_CONTACT_VALID","contactResult"),
                new Fixture("CUSTOMER","CUSTOMER_PROGRESS_STALE","lastFollowAt"),
                new Fixture("CONTRACT","QUOTE_DISCOUNT_APPROVAL_REQUESTED","approvalOwnerId"),
                new Fixture("CONTRACT","CONFLICT_SCREENING_FAILED","subjectId"),
                new Fixture("CONTRACT","QUOTE_ACCEPTED_CONFLICT_CLEARED","conflictCleared"),
                new Fixture("CONTRACT","CONTRACT_SUBMITTED","approvalId"),
                new Fixture("CONTRACT","CONTRACT_APPROVED","reviewerId"),
                new Fixture("CONTRACT","CONTRACT_SIGNED","signStatus"),
                new Fixture("CONTRACT","RECEIVABLE_DUE","dueAt"),
                new Fixture("CONTRACT","PAYMENT_CONFIRMED","planId"),
                new Fixture("CONTRACT","PAYMENT_FULLY_RECEIVED","salesOwnerId"),
                new Fixture("CONTRACT","DEAL_CONFIRMED","paymentId"),
                new Fixture("CONTRACT","INVOICE_HANDLED","invoiceId"),
                new Fixture("CASE","CASE_CREATED","caseSource"),
                new Fixture("CASE","CASE_ASSIGNED","assignmentId"),
                new Fixture("CASE","CASE_HANDOFF_SUBMITTED","handoffId"),
                new Fixture("CASE","CASE_HANDOFF_ACCEPTED","acceptedBy"),
                new Fixture("CASE","CASE_CLASSIFIED_COMPREHENSIVE","classification"),
                new Fixture("CASE","CASE_CLASSIFIED_NON_LITIGATION","classification"),
                new Fixture("CASE","CASE_CLASSIFIED_ENFORCEMENT","classification"),
                new Fixture("CASE","CASE_REJECTED","reasonCode"),
                new Fixture("CASE","CASE_TRANSFER_REQUESTED","transferId"),
                new Fixture("CASE","CASE_TRANSFER_APPROVED","approvedBy"),
                new Fixture("MATTER","ARCHIVE_APPLIED","action"),
                new Fixture("MATTER","CASE_CLOSED","archiveNo"),
                new Fixture("MATTER","MATTER_NODE_READY","nodeCode"),
                new Fixture("MATTER","ENFORCEMENT_SERVICE_NODE_READY","nodeType"),
                new Fixture("MATTER","MATTER_EXPENSE_SUBMITTED","expenseId"),
                new Fixture("MATTER","MATTER_DOCUMENT_REQUIRED","reason"),
                new Fixture("MATTER","MATTER_HEARING_COMPLETED","hearingDate"),
                new Fixture("MATTER","RISK_FEE_CONFIRMED","confirmedBy"),
                new Fixture("MATTER","NON_LITIGATION_WORK_COMPLETED","signedAt"),
                new Fixture("MATTER","ENFORCEMENT_ORDER_ACCEPTED","acceptanceResult"));
        Map<String,Object> generic=row(
                "business_id",99L,"owner_id",7L,"dept_id",3L,"assignment_id",1001L,
                "invalid_reason","conflict","followup_count",3,"next_follow_time","2026-07-24",
                "last_follow_time","2026-07-23","customer_id",88L,"sign_amount",new BigDecimal("100"),
                "approval_id",901L,"approval_approver_id",31L,"sign_status","signed",
                "sign_date","2026-07-23","receivable_amount",new BigDecimal("100"),
                "plan_receive_date","2026-07-30","plan_id",701L,"received_amount",new BigDecimal("100"),
                "invoice_status","issued","contract_id",83L,"main_lawyer_id",21L,
                "rejection_reason_code","conflict","rejection_reason","存在利益冲突",
                "transfer_id",601L,"target_lawyer_id",22L,"transfer_reason","specialty",
                "archive_id",501L,"archive_no","AR-001","node_id",401L,"node_code","HEARING",
                "node_type","ENFORCEMENT_PROGRESS","expense_id",301L,"expense_amount",new BigDecimal("50"),
                "recent_progress","补充文档","next_key_date","2026-08-01","last_progress_time","2026-07-23");
        RuoYiTodoBusinessPayloadAccess access=new RuoYiTodoBusinessPayloadAccess(mapper);
        for(Fixture fixture:fixtures)
        {
            when(mapper.selectVisibleBusinessPayload(anyMap())).thenReturn(generic);
            assertThat(access.hydrate(fixture.event(),1,fixture.type(),99L,actor).payload())
                    .as(fixture.type()+"/"+fixture.event()).containsKey(fixture.expectedKey());
        }
    }

    @Test void reusesTheActorScopedDirectoryQueryContract()
    {
        when(mapper.selectVisibleBusinessPayload(anyMap()))
                .thenReturn(row("business_id",81L,"owner_id",7L,"dept_id",3L));

        new RuoYiTodoBusinessPayloadAccess(mapper).hydrate("LEAD_ASSIGNED",1,"LEAD",81L,actor);

        verify(mapper).selectVisibleBusinessPayload(query.capture());
        assertThat(query.getValue()).containsEntry("businessType","LEAD")
                .containsEntry("businessId",81L)
                .containsEntry("currentUserId",7L)
                .containsEntry("currentDeptId",3L)
                .containsEntry("dataScope",true)
                .containsEntry("permissions","lead:query,lead:mine:query");
    }

    @Test void inaccessibleObjectFailsWithStableBusinessCode()
    {
        when(mapper.selectVisibleBusinessPayload(anyMap())).thenReturn(Map.of());

        assertThatThrownBy(()->new RuoYiTodoBusinessPayloadAccess(mapper)
                .hydrate("LEAD_ASSIGNED",1,"LEAD",81L,actor))
                .isInstanceOf(TodoException.class)
                .extracting(error->((TodoException)error).getBusinessCode())
                .isEqualTo("TODO_SIMULATION_BUSINESS_OBJECT_NOT_FOUND");
    }

    @Test void rejectsUnsupportedEventTypeVersionTupleWithExactCode()
    {
        when(mapper.selectVisibleBusinessPayload(anyMap()))
                .thenReturn(row("business_id",81L,"owner_id",7L,"dept_id",3L));

        assertThatThrownBy(()->new RuoYiTodoBusinessPayloadAccess(mapper)
                .hydrate("CONTRACT_SIGNED",1,"LEAD",81L,actor))
                .isInstanceOf(TodoException.class)
                .extracting(error->((TodoException)error).getBusinessCode())
                .isEqualTo("TODO_SIMULATION_PAYLOAD_MAPPING_UNSUPPORTED");
        assertThatThrownBy(()->new RuoYiTodoBusinessPayloadAccess(mapper)
                .hydrate("LEAD_ASSIGNED",2,"LEAD",81L,actor))
                .isInstanceOf(TodoException.class)
                .extracting(error->((TodoException)error).getBusinessCode())
                .isEqualTo("TODO_SIMULATION_PAYLOAD_MAPPING_UNSUPPORTED");
    }

    private Map<String,Object> row(Object... values)
    {
        Map<String,Object> result=new LinkedHashMap<>();
        for(int index=0;index<values.length;index+=2)result.put(String.valueOf(values[index]),values[index+1]);
        return result;
    }
}
