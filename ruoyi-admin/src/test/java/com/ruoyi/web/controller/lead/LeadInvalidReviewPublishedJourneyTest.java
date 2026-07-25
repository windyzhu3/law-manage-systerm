package com.ruoyi.web.controller.lead;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.law.business.lead.dto.LeadInvalidReviewCommand;
import com.law.business.lead.dto.LeadInvalidReviewCompleteCommand;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.law.todo.application.TodoCommandService;
import com.law.todo.application.TodoDodService;
import com.law.todo.application.TodoQueryService;
import com.law.todo.domain.TodoAccessPolicy;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;
import com.law.todo.spi.TodoDictionaryValidationPort;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.system.service.event.LeadInvalidReviewTodoHandler;
import com.ruoyi.system.service.event.LeadTodoSourceContextService;
import com.ruoyi.system.service.impl.BizLeadServiceImpl;
import com.ruoyi.system.service.lead.LeadAssignmentPolicyService;
import com.ruoyi.system.service.lead.LeadCallRecordService;
import com.ruoyi.system.service.lead.LeadDeadPoolService;
import com.ruoyi.system.service.lead.LeadInvalidReviewService;
import com.ruoyi.system.service.lead.LeadInvalidReviewService.InvalidReviewOutcome;
import com.ruoyi.system.service.lead.LeadQueryService;
import com.ruoyi.system.service.lead.LeadTagConfirmationService;
import com.ruoyi.system.service.lead.LeadTodoApiService;

/**
 * Published-contract proof for controller -> Lead facade -> Todo API -> real
 * Todo DoD/state machine -> typed TD-002 completion handler.
 */
class LeadInvalidReviewPublishedJourneyTest
{
    private static final long TODO_ID=9_930_201L;
    private static final long LEAD_ID=9_930_101L;
    private static final long REVIEW_ID=9_930_301L;
    private static final long SOURCE_TODO_ID=9_930_202L;
    private static final long REVIEWER_ID=9_930_401L;

    @ParameterizedTest
    @ValueSource(strings={"TRUE_INVALID","MISJUDGED_VALID"})
    void publishedTd002CompletesEveryGovernedStateThroughThePublicController(String result)
            throws Exception
    {
        TodoMapper mapper=mock(TodoMapper.class);
        TodoInstance todo=td002();
        List<String> transitions=new ArrayList<>();
        when(mapper.selectById(TODO_ID)).thenReturn(todo);
        when(mapper.selectTemplateVersionById(2L))
                .thenReturn(Map.of("compiled_json",publishedDefinition()));
        when(mapper.insertActionIfAbsent(any())).thenReturn(1);
        when(mapper.updateStatusConditionally(eq(TODO_ID),anyString(),anyString(),
                any(),anyString())).thenAnswer(invocation->{
                    String from=invocation.getArgument(1);
                    String to=invocation.getArgument(2);
                    Long owner=invocation.getArgument(3);
                    if(!from.equals(todo.getStatus()))return 0;
                    transitions.add(from+"->"+to);
                    todo.setStatus(to);
                    if(owner!=null)todo.setOwnerId(owner);
                    return 1;
                });

        TodoAccessPolicy access=mock(TodoAccessPolicy.class);
        when(access.canView(any(),eq(REVIEWER_ID),eq(101L))).thenReturn(true);
        when(access.canClaim(any(),eq(REVIEWER_ID),eq(101L))).thenReturn(true);
        when(access.canOperate(any(),eq(REVIEWER_ID))).thenReturn(true);
        TodoDictionaryValidationPort dictionaries=mock(TodoDictionaryValidationPort.class);
        when(dictionaries.isEnabled("law_lead_invalid_review_result",result)).thenReturn(true);

        LeadInvalidReviewService reviews=mock(LeadInvalidReviewService.class);
        LeadTodoSourceContextService contexts=mock(LeadTodoSourceContextService.class);
        when(contexts.requireInvalidReview(any(),any()))
                .thenReturn(new LeadTodoSourceContextService.InvalidReviewContext(
                        REVIEW_ID,SOURCE_TODO_ID));
        when(reviews.review(any())).thenAnswer(invocation->{
            LeadInvalidReviewCommand command=invocation.getArgument(0);
            return new InvalidReviewOutcome(command.getReviewResult(),REVIEW_ID,
                    "MISJUDGED_VALID".equals(command.getReviewResult())?99L:null,
                    "TRUE_INVALID".equals(command.getReviewResult())?98L:null,
                    false,"MISJUDGED_VALID".equals(command.getReviewResult())?77L:null);
        });
        LeadInvalidReviewTodoHandler handler=new LeadInvalidReviewTodoHandler(reviews,contexts);
        TodoDodService dod=new TodoDodService(List.of(),List.of(),dictionaries);
        TodoCommandService commands=new TodoCommandService(mapper,access,dod,
                List.of(handler),null);
        TodoQueryService queries=new TodoQueryService(mapper,access);
        BusinessActorProvider actors=()->new BusinessActor(
                REVIEWER_ID,"reviewer","Review supervisor",101L,false);
        LeadTodoApiService api=new LeadTodoApiService(
                mock(LeadQueryService.class),mock(LeadTagConfirmationService.class),
                mock(LeadCallRecordService.class),mock(LeadDeadPoolService.class),
                mock(LeadAssignmentPolicyService.class),commands,queries,actors);
        BizLeadServiceImpl facade=new BizLeadServiceImpl();
        ReflectionTestUtils.setField(facade,"leadTodoApiService",api);
        LeadInvalidReviewController controller=new LeadInvalidReviewController(facade);

        LeadInvalidReviewCompleteCommand request=new LeadInvalidReviewCompleteCommand();
        request.setActionId("task10-review-"+result);
        request.setReviewResult(result);
        request.setReviewOpinion("  Supervisor verified the evidence  ");
        AjaxResult response=controller.complete(TODO_ID,request);

        assertEquals(200,response.get(AjaxResult.CODE_TAG));
        assertEquals("COMPLETED",todo.getStatus());
        assertEquals(List.of("CREATED->CLAIMED","CLAIMED->IN_PROGRESS",
                "IN_PROGRESS->SUBMITTED","SUBMITTED->COMPLETED"),transitions);
        ArgumentCaptor<LeadInvalidReviewCommand> handled=
                ArgumentCaptor.forClass(LeadInvalidReviewCommand.class);
        org.mockito.Mockito.verify(reviews).review(handled.capture());
        assertEquals(result,handled.getValue().getReviewResult());
        assertEquals("Supervisor verified the evidence",
                handled.getValue().getReviewComment().trim());
        assertEquals(LEAD_ID,handled.getValue().getLeadId());
        assertEquals(REVIEW_ID,handled.getValue().getReviewId());
        assertEquals(SOURCE_TODO_ID,handled.getValue().getTodoId());
    }

    @Test
    void oldReviewCommentJsonIsAcceptedOnlyAtTheJacksonCompatibilityBoundary()
            throws Exception
    {
        LeadInvalidReviewCompleteCommand command=new ObjectMapper().readValue("""
                {"actionId":"legacy-action","reviewResult":"TRUE_INVALID",
                 "reviewComment":"legacy opinion","fileObjectIds":[]}
                """,LeadInvalidReviewCompleteCommand.class);
        assertEquals("legacy opinion",command.getReviewOpinion());
        assertNotNull(LeadInvalidReviewCompleteCommand.class
                .getDeclaredField("reviewOpinion"));
    }

    private TodoInstance td002()
    {
        TodoInstance todo=new TodoInstance();
        todo.setTodoId(TODO_ID);
        todo.setTemplateId(2L);
        todo.setTemplateVersionId(2L);
        todo.setTemplateCode("TD-002");
        todo.setBusinessType("LEAD");
        todo.setBusinessId(LEAD_ID);
        todo.setStatus("CREATED");
        todo.setOwnerDeptId(101L);
        return todo;
    }

    private String publishedDefinition() throws Exception
    {
        try(InputStream input=getClass().getResourceAsStream(
                "/todo-definitions/v0.2/TD-002.json"))
        {
            assertNotNull(input);
            return JSON.parseObject(new String(input.readAllBytes(),StandardCharsets.UTF_8))
                    .getJSONObject("definition").toJSONString();
        }
    }
}
