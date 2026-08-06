package com.law.todo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.law.todo.application.TodoTemplateService.EntrySlotBinding;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoConfigurationCommands.LeadReleaseCommand;
import com.law.todo.application.command.TodoConfigurationCommands.LeadReleaseReadinessQuery;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoConfigurationMapper;

@ExtendWith(MockitoExtension.class)
class LeadTodoReleaseServiceTest
{
    private static final Actor ACTOR=new Actor(7L,"release-owner",2L);
    @Mock private TodoConfigurationMapper mapper;
    @Mock private TodoTemplateService templates;
    private LeadTodoReleaseService service;

    @BeforeEach void setUp(){service=new LeadTodoReleaseService(mapper,templates);}

    @Test void activatesOnlyWhenTd001TargetsTheApprovedDownstreamVersions()
    {
        arrangeReadyRelease();
        when(mapper.insertLeadReleaseActionClaim(any())).thenReturn(1);
        when(mapper.selectLeadReleaseActionForUpdate("release-20260731")).thenAnswer(invocation->claimedAction());
        when(mapper.selectLeadReleaseTriggerForUpdate("LEAD_FIRST_CONTACT_ENTRY",88L))
                .thenReturn(trigger(52L,3,"N"));
        when(templates.switchEntrySlot("LEAD_FIRST_CONTACT_ENTRY",52L,3,ACTOR))
                .thenReturn(new EntrySlotBinding("LEAD_FIRST_CONTACT_ENTRY",52L,88L,"TD-001"));
        when(mapper.completeLeadReleaseAction("release-20260731",fingerprint(),52L)).thenReturn(1);

        var view=service.activate(command(),ACTOR);

        assertThat(view.entrySlotCode()).isEqualTo("LEAD_FIRST_CONTACT_ENTRY");
        assertThat(view.activeTd001VersionId()).isEqualTo(88L);
        assertThat(view.downstreamVersions()).containsExactlyInAnyOrderEntriesOf(
                Map.of("TD-002",80L,"TD-003",89L,"TD-004",79L));
        verify(templates).switchEntrySlot("LEAD_FIRST_CONTACT_ENTRY",52L,3,ACTOR);
    }

    @Test void locksAllRequestedVersionsAfterEntryAndActionSerializationBeforeEvidenceAndSwitch()
    {
        arrangeReadyRelease();
        when(mapper.selectTemplateVersionsForUpdate(List.of(88L,80L,89L,79L))).thenReturn(versions());
        when(mapper.selectLeadReleaseTriggerForUpdate("LEAD_FIRST_CONTACT_ENTRY",88L))
                .thenReturn(trigger(52L,3,"N"));
        when(templates.switchEntrySlot("LEAD_FIRST_CONTACT_ENTRY",52L,3,ACTOR))
                .thenReturn(new EntrySlotBinding("LEAD_FIRST_CONTACT_ENTRY",52L,88L,"TD-001"));
        when(mapper.completeLeadReleaseAction("release-20260731",fingerprint(),52L)).thenReturn(1);

        service.activate(command(),ACTOR);

        InOrder order=org.mockito.Mockito.inOrder(mapper,templates);
        order.verify(mapper).selectLeadEntrySlotBindingsForUpdate("LEAD_FIRST_CONTACT_ENTRY");
        order.verify(mapper).insertLeadReleaseActionClaim(any());
        order.verify(mapper).selectLeadReleaseActionForUpdate("release-20260731");
        order.verify(mapper).selectTemplateVersionsForUpdate(List.of(88L,80L,89L,79L));
        order.verify(mapper).selectSimulationReadinessBatch(List.of(88L,80L,89L,79L));
        order.verify(templates).switchEntrySlot("LEAD_FIRST_CONTACT_ENTRY",52L,3,ACTOR);
        verify(mapper,never()).selectLeadReleaseVersions(any());
    }

    @Test void rejectsDuplicateRequestedVersionIdsBeforeTakingAnyVersionRowLock()
    {
        LeadReleaseCommand duplicate=new LeadReleaseCommand(
                "release-duplicate",88L,"hash-88",80L,89L,89L,3);
        when(mapper.selectLeadEntrySlotBindingsForUpdate("LEAD_FIRST_CONTACT_ENTRY"))
                .thenReturn(List.of(activeBinding()));
        when(mapper.insertLeadReleaseActionClaim(any())).thenReturn(1);
        when(mapper.selectLeadReleaseActionForUpdate("release-duplicate"))
                .thenReturn(claimedAction(duplicate,"release-duplicate"));

        assertThatThrownBy(()->service.activate(duplicate,ACTOR))
                .isInstanceOfSatisfying(TodoException.class,error->assertThat(error.getBusinessCode())
                        .isEqualTo("TODO_LEAD_RELEASE_VERSION_INVALID"));
        verify(mapper,never()).selectTemplateVersionsForUpdate(any());
        verify(mapper,never()).selectLeadReleaseVersions(any());
        verify(templates,never()).switchEntrySlot(any(),any(Long.class),any(Integer.class),eq(ACTOR));
    }

    @Test void releaseReadinessComesFromServerEvidenceAndCurrentEntryBinding()
    {
        when(mapper.selectLeadEntrySlotBindings("LEAD_FIRST_CONTACT_ENTRY")).thenReturn(List.of(activeBinding()));
        when(mapper.selectLeadReleaseVersions(List.of(88L,80L,89L,79L))).thenReturn(versions());
        when(mapper.selectSimulationReadinessBatch(List.of(88L,80L,89L,79L))).thenReturn(readyReadiness());
        when(mapper.selectLeadReleaseTrigger("LEAD_FIRST_CONTACT_ENTRY",88L)).thenReturn(trigger(52L,3,"N"));

        var state=service.readiness(new LeadReleaseReadinessQuery(88L,"hash-88",80L,89L,79L));

        assertThat(state.activationReady()).isTrue();
        assertThat(state.evidenceReady()).containsOnlyKeys("TD-001","TD-002","TD-003","TD-004")
                .allSatisfy((code,ready)->assertThat(ready).as(code).isTrue());
        assertThat(state.activeRelease().activeTd001VersionId()).isEqualTo(70L);
        assertThat(state.triggerExpectedVersion()).isEqualTo(3);
    }

    @Test void releaseReadinessAcceptsTerminalUnreachableOutcomeWithLockedRetryDependency()
    {
        List<Map<String,Object>> rows=versions();
        rows.get(0).put("compiled_json",compiledWithTerminalRetry(
                Map.of("TD-002",80L,"TD-003",89L,"TD-004",79L)));
        when(mapper.selectLeadEntrySlotBindings("LEAD_FIRST_CONTACT_ENTRY")).thenReturn(List.of(activeBinding()));
        when(mapper.selectLeadReleaseVersions(List.of(88L,80L,89L,79L))).thenReturn(rows);
        when(mapper.selectSimulationReadinessBatch(List.of(88L,80L,89L,79L))).thenReturn(readyReadiness());
        when(mapper.selectLeadReleaseTrigger("LEAD_FIRST_CONTACT_ENTRY",88L)).thenReturn(trigger(52L,3,"N"));

        var state=service.readiness(new LeadReleaseReadinessQuery(88L,"hash-88",80L,89L,79L));

        assertThat(state.activationReady()).isTrue();
        assertThat(state.evidenceReady()).allSatisfy((code,ready)->assertThat(ready).as(code).isTrue());
    }

    @Test void releaseReadinessReturnsPerVersionBlockersInsteadOfFabricatingClientEvidence()
    {
        when(mapper.selectLeadEntrySlotBindings("LEAD_FIRST_CONTACT_ENTRY")).thenReturn(List.of(activeBinding()));
        when(mapper.selectLeadReleaseVersions(List.of(88L,80L,89L,79L))).thenReturn(versions());
        List<Map<String,Object>> readiness=readyReadiness();
        readiness.set(3,readiness(79L,"hash-79",1,0,false));
        when(mapper.selectSimulationReadinessBatch(List.of(88L,80L,89L,79L))).thenReturn(readiness);
        when(mapper.selectLeadReleaseTrigger("LEAD_FIRST_CONTACT_ENTRY",88L)).thenReturn(trigger(52L,3,"N"));

        var state=service.readiness(new LeadReleaseReadinessQuery(88L,"hash-88",80L,89L,79L));

        assertThat(state.activationReady()).isFalse();
        assertThat(state.evidenceReady()).containsEntry("TD-004",false);
        assertThat(state.blockers()).contains("TD-004");
    }

    @Test void rejectsMissingCurrentHashEvidenceBeforePreparingOrSwitchingTrigger()
    {
        arrangeReadyRelease();
        List<Map<String,Object>> readiness=readyReadiness();
        readiness.set(2,readiness(89L,"hash-89",1,0,true));
        when(mapper.selectSimulationReadinessBatch(List.of(88L,80L,89L,79L))).thenReturn(readiness);

        assertThatThrownBy(()->service.activate(command(),ACTOR))
                .isInstanceOfSatisfying(TodoException.class,error->assertThat(error.getBusinessCode())
                        .isEqualTo("TODO_LEAD_RELEASE_EVIDENCE_INCOMPLETE"));
        verify(mapper,never()).insertLeadReleaseDisabledTrigger(any());
        verify(templates,never()).switchEntrySlot(any(),eq(52L),eq(3),eq(ACTOR));
    }

    @Test void rejectsUnpublishedOrCrossBusinessVersion()
    {
        arrangeReadyRelease();
        List<Map<String,Object>> rows=versions();
        rows.set(1,version(80L,"TD-002","LEAD","DRAFT","hash-80",null,4));
        when(mapper.selectTemplateVersionsForUpdate(List.of(88L,80L,89L,79L))).thenReturn(rows);

        assertThatThrownBy(()->service.activate(command(),ACTOR))
                .isInstanceOfSatisfying(TodoException.class,error->assertThat(error.getBusinessCode())
                        .isEqualTo("TODO_LEAD_RELEASE_VERSION_INVALID"));
        verify(templates,never()).switchEntrySlot(any(),any(Long.class),any(Integer.class),eq(ACTOR));
    }

    @Test void releaseReadinessRejectsAnInactiveSelectedTemplate()
    {
        List<Map<String,Object>> rows=versions();
        rows.get(1).put("template_status","1");
        when(mapper.selectLeadReleaseVersions(List.of(88L,80L,89L,79L))).thenReturn(rows);

        assertThatThrownBy(()->service.readiness(
                new LeadReleaseReadinessQuery(88L,"hash-88",80L,89L,79L)))
                .isInstanceOfSatisfying(TodoException.class,error->assertThat(error.getBusinessCode())
                        .isEqualTo("TODO_LEAD_RELEASE_VERSION_INVALID"));
        verify(mapper,never()).selectSimulationReadinessBatch(any());
    }

    @Test void activationRejectsAnInactiveSelectedTemplateBeforeEvidenceOrSwitch()
    {
        arrangeReadyRelease();
        List<Map<String,Object>> rows=versions();
        rows.get(3).put("template_status","1");
        when(mapper.selectTemplateVersionsForUpdate(List.of(88L,80L,89L,79L))).thenReturn(rows);

        assertThatThrownBy(()->service.activate(command(),ACTOR))
                .isInstanceOfSatisfying(TodoException.class,error->assertThat(error.getBusinessCode())
                        .isEqualTo("TODO_LEAD_RELEASE_VERSION_INVALID"));
        verify(mapper,never()).selectSimulationReadinessBatch(any());
        verify(templates,never()).switchEntrySlot(any(),any(Long.class),any(Integer.class),eq(ACTOR));
    }

    @Test void rejectsTd001GraphPointingToLegacyOrDifferentDownstreamVersion()
    {
        arrangeReadyRelease();
        List<Map<String,Object>> rows=versions();
        rows.set(0,version(88L,"TD-001","LEAD","PUBLISHED","hash-88",
                compiled(Map.of("TD-002",80L,"TD-003",89L,"LEAD_FIRST_CONTACT",79L)),5));
        when(mapper.selectTemplateVersionsForUpdate(List.of(88L,80L,89L,79L))).thenReturn(rows);

        assertThatThrownBy(()->service.activate(command(),ACTOR))
                .isInstanceOfSatisfying(TodoException.class,error->assertThat(error.getBusinessCode())
                        .isEqualTo("TODO_LEAD_RELEASE_ROUTING_MISMATCH"));
        verify(templates,never()).switchEntrySlot(any(),any(Long.class),any(Integer.class),eq(ACTOR));
    }

    @Test void acceptsDeferredRetryDependencyWithoutAnImmediateTd003OutcomeOrTaskNode()
    {
        arrangeReadyRelease();
        List<Map<String,Object>> rows=versions();
        rows.set(0,version(88L,"TD-001","LEAD","PUBLISHED","hash-88",
                scheduleDrivenTd001(Map.of("TD-002",80L,"TD-004",79L),89L),5));
        when(mapper.selectTemplateVersionsForUpdate(List.of(88L,80L,89L,79L))).thenReturn(rows);
        when(mapper.selectLeadReleaseTriggerForUpdate("LEAD_FIRST_CONTACT_ENTRY",88L))
                .thenReturn(trigger(52L,3,"N"));
        when(templates.switchEntrySlot("LEAD_FIRST_CONTACT_ENTRY",52L,3,ACTOR))
                .thenReturn(new EntrySlotBinding("LEAD_FIRST_CONTACT_ENTRY",52L,88L,"TD-001"));
        when(mapper.completeLeadReleaseAction("release-20260731",fingerprint(),52L)).thenReturn(1);

        var activated=service.activate(command(),ACTOR);

        assertThat(activated.activeTd001VersionId()).isEqualTo(88L);
        assertThat(activated.downstreamVersions()).containsEntry("TD-003",89L);
    }

    @Test void acceptsTd002LogicalReopenTargetWithoutLockingAFutureTd001Version()
    {
        arrangeReadyRelease();
        List<Map<String,Object>> rows=versions();
        rows.set(1,version(80L,"TD-002","LEAD","PUBLISHED","hash-80",
                td002Compiled(80L,70L),4));
        when(mapper.selectTemplateVersionsForUpdate(List.of(88L,80L,89L,79L))).thenReturn(rows);
        when(mapper.selectLeadReleaseTriggerForUpdate("LEAD_FIRST_CONTACT_ENTRY",88L))
                .thenReturn(trigger(52L,3,"N"));
        when(templates.switchEntrySlot("LEAD_FIRST_CONTACT_ENTRY",52L,3,ACTOR))
                .thenReturn(new EntrySlotBinding("LEAD_FIRST_CONTACT_ENTRY",52L,88L,"TD-001"));
        when(mapper.completeLeadReleaseAction("release-20260731",fingerprint(),52L)).thenReturn(1);

        assertThat(service.activate(command(),ACTOR).activeTd001VersionId()).isEqualTo(88L);
        verify(templates).switchEntrySlot("LEAD_FIRST_CONTACT_ENTRY",52L,3,ACTOR);
    }

    @Test void rejectsAnImmediateTd003OutcomeEvenWhenTheScheduledDependencyIsCorrect()
    {
        arrangeReadyRelease();
        List<Map<String,Object>> rows=versions();
        String invalid=scheduleDrivenTd001(Map.of("TD-002",80L,"TD-004",79L),89L)
                .replace("\"businessOutcomes\":[","\"businessOutcomes\":[{\"targetTemplateCode\":"
                        +"\"TD-003\",\"targetVersionId\":89},");
        rows.set(0,version(88L,"TD-001","LEAD","PUBLISHED","hash-88",invalid,5));
        when(mapper.selectTemplateVersionsForUpdate(List.of(88L,80L,89L,79L))).thenReturn(rows);

        assertThatThrownBy(()->service.activate(command(),ACTOR))
                .isInstanceOfSatisfying(TodoException.class,error->assertThat(error.getBusinessCode())
                        .isEqualTo("TODO_LEAD_RELEASE_ROUTING_MISMATCH"));
        verify(templates,never()).switchEntrySlot(any(),any(Long.class),any(Integer.class),eq(ACTOR));
    }

    @Test void rejectsMasterGraphWithoutMisjudgedValidReopenEdge()
    {
        arrangeReadyRelease();
        List<Map<String,Object>> rows=versions();
        String disconnected=compiledWithReopenedTd001(
                Map.of("TD-002",80L,"TD-003",89L,"TD-004",79L))
                .replace("{\"to\":\"reopenedTd001\",\"key\":\"review-reopen\",\"from\":\"reviewResult\","
                        +"\"priority\":10,\"condition\":{\"$expression\":{\"root\":{\"type\":\"AND\","
                        +"\"conditions\":[{\"field\":\"reviewResult\",\"value\":\"MISJUDGED_VALID\","
                        +"\"operator\":\"EQ\"}]},\"version\":1}}},","");
        rows.set(0,version(88L,"TD-001","LEAD","PUBLISHED","hash-88",disconnected,5));
        when(mapper.selectTemplateVersionsForUpdate(List.of(88L,80L,89L,79L))).thenReturn(rows);

        assertThatThrownBy(()->service.activate(command(),ACTOR))
                .isInstanceOfSatisfying(TodoException.class,error->assertThat(error.getBusinessCode())
                        .isEqualTo("TODO_LEAD_RELEASE_ROUTING_MISMATCH"));
        verify(templates,never()).switchEntrySlot(any(),any(Long.class),any(Integer.class),eq(ACTOR));
    }

    @Test void rejectsMasterGraphWithWrongTd002ReopenCondition()
    {
        arrangeReadyRelease();
        List<Map<String,Object>> rows=versions();
        String wrongCondition=compiledWithReopenedTd001(
                Map.of("TD-002",80L,"TD-003",89L,"TD-004",79L))
                .replace("\"value\":\"MISJUDGED_VALID\"","\"value\":\"TRUE_INVALID\"");
        rows.set(0,version(88L,"TD-001","LEAD","PUBLISHED","hash-88",wrongCondition,5));
        when(mapper.selectTemplateVersionsForUpdate(List.of(88L,80L,89L,79L))).thenReturn(rows);

        assertThatThrownBy(()->service.activate(command(),ACTOR))
                .isInstanceOfSatisfying(TodoException.class,error->assertThat(error.getBusinessCode())
                        .isEqualTo("TODO_LEAD_RELEASE_ROUTING_MISMATCH"));
        verify(templates,never()).switchEntrySlot(any(),any(Long.class),any(Integer.class),eq(ACTOR));
    }

    @Test void rejectsMasterGraphWithCorrectReopenEdgeAndAnAdditionalWrongReturnEdge()
    {
        arrangeReadyRelease();
        List<Map<String,Object>> rows=versions();
        String additionalWrongReturn="{\"to\":\"reopenedTd001\",\"key\":\"review-wrong-return\","+
                "\"from\":\"reviewResult\",\"priority\":9,\"condition\":{\"$expression\":{\"root\":"+
                "{\"type\":\"AND\",\"conditions\":[{\"field\":\"reviewResult\",\"value\":"+
                "\"TRUE_INVALID\",\"operator\":\"EQ\"}]},\"version\":1}}},";
        String ambiguous=compiledWithReopenedTd001(
                Map.of("TD-002",80L,"TD-003",89L,"TD-004",79L))
                .replace("{\"to\":\"end\",\"key\":\"reopened-end\"",additionalWrongReturn+
                        "{\"to\":\"end\",\"key\":\"reopened-end\"");
        rows.set(0,version(88L,"TD-001","LEAD","PUBLISHED","hash-88",ambiguous,5));
        when(mapper.selectTemplateVersionsForUpdate(List.of(88L,80L,89L,79L))).thenReturn(rows);

        assertThatThrownBy(()->service.activate(command(),ACTOR))
                .isInstanceOfSatisfying(TodoException.class,error->assertThat(error.getBusinessCode())
                        .isEqualTo("TODO_LEAD_RELEASE_ROUTING_MISMATCH"));
        verify(templates,never()).switchEntrySlot(any(),any(Long.class),any(Integer.class),eq(ACTOR));
    }

    @Test void rejectsMasterGraphWhenAHigherPriorityDirectEdgeBypassesTd002ReviewDecision()
    {
        arrangeReadyRelease();
        List<Map<String,Object>> rows=versions();
        String directBypass="{\"to\":\"end\",\"key\":\"td002-direct-bypass\","+
                "\"from\":\"td002\",\"priority\":100},";
        String ambiguous=compiledWithReopenedTd001(
                Map.of("TD-002",80L,"TD-003",89L,"TD-004",79L))
                .replace("{\"to\":\"reviewResult\",\"key\":\"td002-result\"",
                        directBypass+"{\"to\":\"reviewResult\",\"key\":\"td002-result\"");
        rows.set(0,version(88L,"TD-001","LEAD","PUBLISHED","hash-88",ambiguous,5));
        when(mapper.selectTemplateVersionsForUpdate(List.of(88L,80L,89L,79L))).thenReturn(rows);

        assertThatThrownBy(()->service.activate(command(),ACTOR))
                .isInstanceOfSatisfying(TodoException.class,error->assertThat(error.getBusinessCode())
                        .isEqualTo("TODO_LEAD_RELEASE_ROUTING_MISMATCH"));
        verify(templates,never()).switchEntrySlot(any(),any(Long.class),any(Integer.class),eq(ACTOR));
    }

    @Test void rejectsMasterGraphWhenTd002ReviewDecisionHasAnAdditionalBranch()
    {
        arrangeReadyRelease();
        List<Map<String,Object>> rows=versions();
        String extraBranch="{\"to\":\"end\",\"key\":\"review-extra\",\"from\":\"reviewResult\","+
                "\"priority\":15,\"condition\":{\"$expression\":{\"root\":{\"type\":\"AND\","+
                "\"conditions\":[{\"field\":\"reviewResult\",\"value\":\"SUSPECT_INVALID\","+
                "\"operator\":\"EQ\"}]},\"version\":1}}},";
        String ambiguous=compiledWithReopenedTd001(
                Map.of("TD-002",80L,"TD-003",89L,"TD-004",79L))
                .replace("{\"to\":\"end\",\"key\":\"review-default\"",
                        extraBranch+"{\"to\":\"end\",\"key\":\"review-default\"");
        rows.set(0,version(88L,"TD-001","LEAD","PUBLISHED","hash-88",ambiguous,5));
        when(mapper.selectTemplateVersionsForUpdate(List.of(88L,80L,89L,79L))).thenReturn(rows);

        assertThatThrownBy(()->service.activate(command(),ACTOR))
                .isInstanceOfSatisfying(TodoException.class,error->assertThat(error.getBusinessCode())
                        .isEqualTo("TODO_LEAD_RELEASE_ROUTING_MISMATCH"));
        verify(templates,never()).switchEntrySlot(any(),any(Long.class),any(Integer.class),eq(ACTOR));
    }

    @Test void rejectsTd003ConnectedRouteThatTargetsAnotherTd004Version()
    {
        arrangeReadyRelease();
        List<Map<String,Object>> rows=versions();
        rows.set(2,version(89L,"TD-003","LEAD","PUBLISHED","hash-89",
                td003Compiled(89L,78L),6));
        when(mapper.selectTemplateVersionsForUpdate(List.of(88L,80L,89L,79L))).thenReturn(rows);

        assertThatThrownBy(()->service.activate(command(),ACTOR))
                .isInstanceOfSatisfying(TodoException.class,error->assertThat(error.getBusinessCode())
                        .isEqualTo("TODO_LEAD_RELEASE_ROUTING_MISMATCH"));
        verify(templates,never()).switchEntrySlot(any(),any(Long.class),any(Integer.class),eq(ACTOR));
    }

    @Test void rejectsTd004ScheduleSelfRouteThatTargetsAnotherVersion()
    {
        arrangeReadyRelease();
        List<Map<String,Object>> rows=versions();
        rows.set(3,version(79L,"TD-004","LEAD","PUBLISHED","hash-79",
                td004Compiled(79L,78L),3));
        when(mapper.selectTemplateVersionsForUpdate(List.of(88L,80L,89L,79L))).thenReturn(rows);

        assertThatThrownBy(()->service.activate(command(),ACTOR))
                .isInstanceOfSatisfying(TodoException.class,error->assertThat(error.getBusinessCode())
                        .isEqualTo("TODO_LEAD_RELEASE_ROUTING_MISMATCH"));
        verify(templates,never()).switchEntrySlot(any(),any(Long.class),any(Integer.class),eq(ACTOR));
    }

    @Test void rejectsStaleTriggerExpectedVersionWithoutCompletingAction()
    {
        arrangeReadyRelease();
        when(mapper.insertLeadReleaseActionClaim(any())).thenReturn(1);
        when(mapper.selectLeadReleaseActionForUpdate("release-20260731")).thenAnswer(invocation->claimedAction());
        when(mapper.selectLeadReleaseTriggerForUpdate("LEAD_FIRST_CONTACT_ENTRY",88L))
                .thenReturn(trigger(52L,4,"N"));

        assertThatThrownBy(()->service.activate(command(),ACTOR))
                .isInstanceOfSatisfying(TodoException.class,error->assertThat(error.getBusinessCode())
                        .isEqualTo("TODO_TRIGGER_VERSION_CONFLICT"));
        verify(templates,never()).switchEntrySlot(any(),any(Long.class),any(Integer.class),eq(ACTOR));
        verify(mapper,never()).completeLeadReleaseAction(any(),any(),any());
    }

    @Test void rejectsAReusableCandidateThatDoesNotHaveTheDeterministicReleaseIdentity()
    {
        arrangeReadyRelease();
        Map<String,Object> invalid=new HashMap<>(trigger(52L,3,"N"));
        invalid.put("rule_code","MANUAL_BYPASS");
        when(mapper.selectLeadReleaseTriggerForUpdate("LEAD_FIRST_CONTACT_ENTRY",88L)).thenReturn(invalid);

        assertThatThrownBy(()->service.activate(command(),ACTOR))
                .isInstanceOfSatisfying(TodoException.class,error->assertThat(error.getBusinessCode())
                        .isEqualTo("TODO_LEAD_RELEASE_TRIGGER_INVALID"));
        verify(templates,never()).switchEntrySlot(any(),any(Long.class),any(Integer.class),eq(ACTOR));
    }

    @Test void sameAppliedActionReturnsEquivalentViewWithoutASecondSwitch()
    {
        when(mapper.selectLeadEntrySlotBindingsForUpdate("LEAD_FIRST_CONTACT_ENTRY"))
                .thenReturn(List.of(activeBinding()));
        when(mapper.insertLeadReleaseActionClaim(any())).thenReturn(0);
        Map<String,Object> action=claimedAction();
        action.put("action_status","APPLIED");action.put("entity_id",52L);
        when(mapper.selectLeadReleaseActionForUpdate("release-20260731")).thenReturn(action);

        var view=service.activate(command(),ACTOR);

        assertThat(view.activeTriggerRuleId()).isEqualTo(52L);
        assertThat(view.activeTd001VersionId()).isEqualTo(88L);
        verify(mapper,never()).selectLeadReleaseVersions(any());
        verify(mapper,never()).selectTemplateVersionsForUpdate(any());
        verify(templates,never()).switchEntrySlot(any(),any(Long.class),any(Integer.class),eq(ACTOR));
    }

    @Test void sameActionIdWithDifferentImmutableCommandIsAStableConflict()
    {
        when(mapper.selectLeadEntrySlotBindingsForUpdate("LEAD_FIRST_CONTACT_ENTRY"))
                .thenReturn(List.of(activeBinding()));
        when(mapper.insertLeadReleaseActionClaim(any())).thenReturn(0);
        Map<String,Object> action=claimedAction();action.put("request_fingerprint","different");
        when(mapper.selectLeadReleaseActionForUpdate("release-20260731")).thenReturn(action);

        assertThatThrownBy(()->service.activate(command(),ACTOR))
                .isInstanceOfSatisfying(TodoException.class,error->assertThat(error.getBusinessCode())
                        .isEqualTo("TODO_LEAD_RELEASE_ACTION_CONFLICT"));
        verify(templates,never()).switchEntrySlot(any(),any(Long.class),any(Integer.class),eq(ACTOR));
    }

    @Test void createsOnlyADisabledDeterministicTd001SlotTriggerThroughTheReleasePath()
    {
        arrangeReadyRelease();
        LeadReleaseCommand createCommand=command(0);
        when(mapper.selectLeadReleaseActionForUpdate("release-20260731")).thenReturn(claimedAction(createCommand));
        when(mapper.selectLeadReleaseTriggerForUpdate("LEAD_FIRST_CONTACT_ENTRY",88L)).thenReturn(null,trigger(61L,0,"N"));
        when(mapper.insertLeadReleaseDisabledTrigger(any())).thenAnswer(invocation->{
            Map<String,Object> row=invocation.getArgument(0);row.put("triggerRuleId",61L);return 1;});
        when(templates.switchEntrySlot("LEAD_FIRST_CONTACT_ENTRY",61L,0,ACTOR))
                .thenReturn(new EntrySlotBinding("LEAD_FIRST_CONTACT_ENTRY",61L,88L,"TD-001"));
        when(mapper.completeLeadReleaseAction(eq("release-20260731"),any(),eq(61L))).thenReturn(1);

        service.activate(createCommand,ACTOR);

        ArgumentCaptor<Map<String,Object>> row=ArgumentCaptor.forClass(Map.class);
        verify(mapper).insertLeadReleaseDisabledTrigger(row.capture());
        assertThat(row.getValue()).containsEntry("ruleCode","TRIGGER_LEAD_ASSIGNED_TD001_V5")
                .containsEntry("eventType","LEAD_ASSIGNED").containsEntry("businessType","LEAD")
                .containsEntry("entrySlotCode","LEAD_FIRST_CONTACT_ENTRY").containsEntry("enabled","N")
                .containsEntry("templateVersionId",88L);
    }

    private void arrangeReadyRelease()
    {
        when(mapper.selectLeadEntrySlotBindingsForUpdate("LEAD_FIRST_CONTACT_ENTRY"))
                .thenReturn(List.of(activeBinding()));
        when(mapper.selectTemplateVersionsForUpdate(List.of(88L,80L,89L,79L))).thenReturn(versions());
        org.mockito.Mockito.lenient().when(mapper.selectSimulationReadinessBatch(List.of(88L,80L,89L,79L)))
                .thenReturn(readyReadiness());
        when(mapper.insertLeadReleaseActionClaim(any())).thenReturn(1);
        when(mapper.selectLeadReleaseActionForUpdate("release-20260731")).thenAnswer(invocation->claimedAction());
    }

    private LeadReleaseCommand command()
    {return new LeadReleaseCommand("release-20260731",88L,"hash-88",80L,89L,79L,3);}
    private LeadReleaseCommand command(int expectedVersion)
    {return new LeadReleaseCommand("release-20260731",88L,"hash-88",80L,89L,79L,expectedVersion);}
    private String fingerprint(){return LeadTodoReleaseService.fingerprint(command());}
    private String fingerprint(LeadReleaseCommand command){return LeadTodoReleaseService.fingerprint(command);}
    private Map<String,Object> claimedAction()
    {return claimedAction(command());}
    private Map<String,Object> claimedAction(LeadReleaseCommand command)
    {return claimedAction(command,"release-20260731");}
    private Map<String,Object> claimedAction(LeadReleaseCommand command,String actionId)
    {return new HashMap<>(Map.of("action_id",actionId,"action_type","ACTIVATE_LEAD_RELEASE",
            "action_status","CLAIMED","request_fingerprint",fingerprint(command),"entity_type","LEAD_RELEASE",
            "operator_id",7L,"operator_name","release-owner","operator_dept_id",2L));}
    private Map<String,Object> activeBinding()
    {return Map.of("entry_slot_code","LEAD_FIRST_CONTACT_ENTRY","trigger_rule_id",41L,
            "template_version_id",70L,"enabled","Y","trigger_version",6,"template_code","TD-001");}
    private List<Map<String,Object>> versions()
    {return new ArrayList<>(List.of(
            version(88L,"TD-001","LEAD","PUBLISHED","hash-88",compiledWithReopenedTd001(Map.of("TD-002",80L,"TD-003",89L,"TD-004",79L)),5),
            version(80L,"TD-002","LEAD","PUBLISHED","hash-80",td002Compiled(80L,70L),4),
            version(89L,"TD-003","LEAD","PUBLISHED","hash-89",td003CompiledWithoutNodeCodes(89L,79L),6),
            version(79L,"TD-004","LEAD","PUBLISHED","hash-79",td004Compiled(79L,79L),3)));
    }
    private List<Map<String,Object>> readyReadiness()
    {return new ArrayList<>(List.of(readiness(88L,"hash-88",3,3,true),readiness(80L,"hash-80",2,2,true),
            readiness(89L,"hash-89",4,4,true),readiness(79L,"hash-79",1,1,true)));}
    private Map<String,Object> readiness(long id,String hash,int required,int passed,boolean full)
    {return Map.of("version_id",id,"definition_hash",hash,"required_scenario_count",required,
            "passed_scenario_count",passed,"full_simulation_passed",full?1:0);}
    private Map<String,Object> version(long id,String code,String business,String status,String hash,String compiled,int no)
    {Map<String,Object> row=new HashMap<>();row.put("version_id",id);row.put("version_no",no);row.put("template_id",id+100);
        row.put("template_code",code);row.put("template_name",code);row.put("template_status","0");
        row.put("business_type",business);row.put("version_status",status);row.put("definition_hash",hash);
        row.put("compiled_json",compiled);row.put("event_type","LEAD_ASSIGNED");row.put("payload_version",1);return row;}
    private Map<String,Object> trigger(long id,int version,String enabled)
    {return Map.ofEntries(Map.entry("trigger_rule_id",id),Map.entry("trigger_version",version),Map.entry("enabled",enabled),
            Map.entry("template_version_id",88L),Map.entry("entry_slot_code","LEAD_FIRST_CONTACT_ENTRY"),
            Map.entry("template_code","TD-001"),Map.entry("rule_code","TRIGGER_LEAD_ASSIGNED_TD001_V5"),
            Map.entry("event_type","LEAD_ASSIGNED"),Map.entry("business_type","LEAD"));}
    private String compiled(Map<String,Long> targets)
    {
        Map<String,Long> immediateNodes=new LinkedHashMap<>(targets);
        immediateNodes.remove("TD-003");
        return compiled(targets,immediateNodes);
    }
    private String compiledWithReopenedTd001(Map<String,Long> targets)
    {
        Map<String,Long> immediateTargets=new LinkedHashMap<>(targets);
        Long retryVersion=immediateTargets.remove("TD-003");
        String outcomes=immediateTargets.entrySet().stream().map(entry->"{\"targetTemplateCode\":\""+entry.getKey()
                +"\",\"targetVersionId\":"+entry.getValue()+"}").collect(java.util.stream.Collectors.joining(","));
        String dependency=retryVersion==null?"":"\"releaseDependencies\":{\"TD-003\":"+retryVersion+"},";
        return "{\"templateCode\":\"TD-001\",\"routing\":{\"config\":{"+dependency+"\"businessOutcomes\":["+outcomes
                +"],\"nodes\":["
                +"{\"key\":\"td001\",\"type\":\"TASK\",\"templateCode\":\"TD-001\",\"templateVersionId\":88},"
                +"{\"key\":\"firstResult\",\"type\":\"DECISION\"},"
                +"{\"key\":\"td002\",\"type\":\"TASK\",\"templateCode\":\"TD-002\",\"templateVersionId\":80},"
                +"{\"key\":\"reviewResult\",\"type\":\"DECISION\"},"
                +"{\"key\":\"td004\",\"type\":\"TASK\",\"templateCode\":\"TD-004\",\"templateVersionId\":79},"
                +"{\"key\":\"reopenedTd001\",\"type\":\"TASK\",\"templateCode\":\"TD-001\",\"templateVersionId\":88},"
                +"{\"key\":\"end\",\"type\":\"END\"}],\"edges\":["
                +"{\"to\":\"firstResult\",\"key\":\"td001-result\",\"from\":\"td001\",\"priority\":0},"
                +"{\"to\":\"td002\",\"key\":\"first-suspect\",\"from\":\"firstResult\",\"priority\":20},"
                +"{\"to\":\"reviewResult\",\"key\":\"td002-result\",\"from\":\"td002\",\"priority\":0},"
                +"{\"to\":\"end\",\"key\":\"review-invalid\",\"from\":\"reviewResult\","
                +"\"priority\":20,\"condition\":{\"$expression\":{\"root\":{\"type\":\"AND\","
                +"\"conditions\":[{\"field\":\"reviewResult\",\"value\":\"TRUE_INVALID\","
                +"\"operator\":\"EQ\"}]},\"version\":1}}},"
                +"{\"to\":\"reopenedTd001\",\"key\":\"review-reopen\",\"from\":\"reviewResult\","
                +"\"priority\":10,\"condition\":{\"$expression\":{\"root\":{\"type\":\"AND\","
                +"\"conditions\":[{\"field\":\"reviewResult\",\"value\":\"MISJUDGED_VALID\","
                +"\"operator\":\"EQ\"}]},\"version\":1}}},"
                +"{\"to\":\"end\",\"key\":\"review-default\",\"from\":\"reviewResult\","
                +"\"priority\":-1,\"default\":true},"
                +"{\"to\":\"end\",\"key\":\"reopened-end\",\"from\":\"reopenedTd001\",\"priority\":0}]}}}";
    }
    private String scheduleDrivenTd001(Map<String,Long> immediateTargets,long retryVersionId)
    {
        return compiledWithReopenedTd001(immediateTargets)
                .replace("\"businessOutcomes\":[","\"releaseDependencies\":{\"TD-003\":"
                        +retryVersionId+"},\"businessOutcomes\":[");
    }
    private String compiledWithTerminalRetry(Map<String,Long> targets)
    {
        return compiledWithReopenedTd001(targets)
                .replace("],\"nodes\":[",",{\"effectKind\":\"END\",\"businessAction\":\"START_RETRY\","+
                        "\"resultValue\":\"UNREACHABLE\"}],\"nodes\":[");
    }
    private String compiled(Map<String,Long> targets,Map<String,Long> nodeTargets)
    {
        String outcomes=targets.entrySet().stream().map(entry->"{\"targetTemplateCode\":\""+entry.getKey()
                +"\",\"targetVersionId\":"+entry.getValue()+"}").collect(java.util.stream.Collectors.joining(","));
        String nodes=nodeTargets.entrySet().stream().map(entry->"{\"type\":\"TASK\",\"templateVersionId\":"
                +entry.getValue()+"}").collect(java.util.stream.Collectors.joining(","));
        return "{\"templateCode\":\"TD-001\",\"routing\":{\"config\":{\"businessOutcomes\":["+outcomes
                +"],\"nodes\":[{\"type\":\"TASK\",\"templateVersionId\":88},"+nodes+"]}}}";
    }
    private String td002Compiled(long self,long td001)
    {return routeCompiled("TD-002",self,"MISJUDGED_VALID","NEXT_TEMPLATE","TD-001",td001);}
    private String td003Compiled(long self,long td004)
    {return routeCompiled("TD-003",self,"CONNECTED","NEXT_TEMPLATE","TD-004",td004);}
    private String td003CompiledWithoutNodeCodes(long self,long td004)
    {
        return td003Compiled(self,td004)
                .replace("\"type\":\"TASK\",\"templateCode\":\"TD-003\",",
                        "\"type\":\"TASK\",")
                .replace("\"type\":\"TASK\",\"templateCode\":\"TD-004\",",
                        "\"type\":\"TASK\",");
    }
    private String td004Compiled(long self,long next)
    {
        if(next!=self)return routeCompiled("TD-004",self,"PROGRESS_RECORDED","SCHEDULE_SELF","TD-004",next);
        return "{\"templateCode\":\"TD-004\",\"routing\":{\"config\":{\"businessOutcomes\":["
                +"{\"value\":\"PROGRESS_RECORDED\",\"effectKind\":\"SCHEDULE_SELF\","
                +"\"targetTemplateCode\":\"TD-004\"}],\"nodes\":[{\"type\":\"TASK\","
                +"\"templateCode\":\"TD-004\",\"templateVersionId\":"+self+"}]}}}";
    }
    private String routeCompiled(String code,long self,String result,String effect,String targetCode,long target)
    {return "{\"templateCode\":\""+code+"\",\"routing\":{\"config\":{\"businessOutcomes\":["
            +"{\"value\":\""+result+"\",\"effectKind\":\""+effect+"\",\"targetTemplateCode\":\""
            +targetCode+"\",\"targetVersionId\":"+target+"}],\"nodes\":["
            +"{\"type\":\"TASK\",\"templateCode\":\""+code+"\",\"templateVersionId\":"+self+"},"
            +"{\"type\":\"TASK\",\"templateCode\":\""+targetCode+"\",\"templateVersionId\":"+target+"}]}}}";}
}
