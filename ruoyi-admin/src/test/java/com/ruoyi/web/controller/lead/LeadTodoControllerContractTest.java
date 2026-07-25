package com.ruoyi.web.controller.lead;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Set;
import jakarta.validation.Valid;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestBody;
import com.law.business.lead.dto.LeadAssignmentPolicyCommand;
import com.law.business.lead.dto.LeadDeadPoolRestoreCommand;
import com.law.business.lead.dto.LeadInvalidReviewCompleteCommand;
import com.law.business.lead.dto.LeadManualCallRecordCommand;
import com.law.business.lead.dto.LeadTagConfirmationRequest;

class LeadTodoControllerContractTest
{
    @Test
    void exactRoutesMethodsPermissionsAndValidationAreStable() throws Exception
    {
        assertClassPath(BizLeadController.class, "/lead");
        assertEndpoint(BizLeadController.class, "confirmTag", POST, "/tag/confirm",
                "lead:tag:confirm", LeadTagConfirmationRequest.class);
        assertEndpointExpression(BizLeadController.class, "ownerOptions", GET, "/owner/options",
                "@ss.hasAnyPermi('lead:add,lead:edit,lead:assign,lead:assignment-policy:list')");
        assertEndpointExpression(BizLeadController.class, "getInfo", GET, "/{leadId}",
                "@ss.hasAnyPermi('lead:query,lead:mine:query,lead:pool:query,lead:recycle:query,lead:dead-pool:list')");
        assertEndpointExpression(BizLeadController.class, "callRecords", GET, "/{leadId}/call-records",
                "@ss.hasAnyPermi('lead:call-record:view,lead:dead-pool:list')");
        assertEndpoint(BizLeadController.class, "addCallRecord", POST, "/{leadId}/call-records",
                "lead:call-record:add", LeadManualCallRecordCommand.class);

        assertClassPath(LeadInvalidReviewController.class, "/lead/invalid-review");
        assertEndpoint(LeadInvalidReviewController.class, "list", GET, "/list",
                "lead:invalid-review:list", null);
        assertEndpoint(LeadInvalidReviewController.class, "complete", POST, "/{todoId}/complete",
                "lead:invalid-review:handle", LeadInvalidReviewCompleteCommand.class);

        assertClassPath(LeadRetryController.class, "/lead");
        assertEndpoint(LeadRetryController.class, "list", GET, "/retry/list",
                "lead:retry:list", null);
        assertEndpoint(LeadRetryController.class, "timeline", GET, "/{leadId}/retry-timeline",
                "lead:retry:list", null);

        assertClassPath(LeadDeadPoolController.class, "/lead/dead-pool");
        assertEndpoint(LeadDeadPoolController.class, "list", GET, "/list",
                "lead:dead-pool:list", null);
        assertEndpoint(LeadDeadPoolController.class, "restore", POST, "/{leadId}/restore",
                "lead:dead-pool:restore", LeadDeadPoolRestoreCommand.class);

        assertClassPath(LeadAssignmentPolicyController.class, "/lead/assignment-policy");
        assertEndpoint(LeadAssignmentPolicyController.class, "list", GET, "",
                "lead:assignment-policy:list", null);
        assertEndpoint(LeadAssignmentPolicyController.class, "save", PUT, "",
                "lead:assignment-policy:edit", LeadAssignmentPolicyCommand.class);
    }

    @Test
    void requestJsonCannotSupplyActorTargetStateOrPersistedBusinessIdentity()
    {
        assertNoFields(LeadTagConfirmationRequest.class, "leadId", "actorId", "operatorId",
                "confirmStatus", "targetStatus");
        assertNoFields(LeadManualCallRecordCommand.class, "leadId", "todoId", "actorId",
                "operatorId", "callChannel", "providerSummaryHash", "targetStatus");
        assertNoFields(LeadInvalidReviewCompleteCommand.class, "leadId", "reviewId", "todoId",
                "actorId", "operatorId", "targetStatus", "status", "reviewComment");
        assertTrue(Arrays.stream(LeadInvalidReviewCompleteCommand.class.getDeclaredFields())
                .anyMatch(field -> "reviewOpinion".equals(field.getName())));
        assertNoFields(LeadDeadPoolRestoreCommand.class, "leadId", "actorId", "operatorId",
                "targetStatus", "disposition", "rowVersion");
        assertNoFields(LeadAssignmentPolicyCommand.class, "businessType", "actorId", "operatorId",
                "targetStatus", "status");
    }

    @Test
    void providerCallbackIsNotExposedAsAnOrdinaryLeadHttpEndpoint()
    {
        for (Class<?> controller : Set.of(BizLeadController.class, LeadInvalidReviewController.class,
                LeadRetryController.class, LeadDeadPoolController.class, LeadAssignmentPolicyController.class))
        {
            for (Method method : controller.getDeclaredMethods())
            {
                assertFalse(method.getName().toLowerCase().contains("callback"));
                assertFalse(mappingPath(method).toLowerCase().contains("callback"));
            }
        }
    }

    private static final RequestMethod GET = RequestMethod.GET;
    private static final RequestMethod POST = RequestMethod.POST;
    private static final RequestMethod PUT = RequestMethod.PUT;

    private void assertClassPath(Class<?> type, String expected)
    {
        RequestMapping mapping = type.getAnnotation(RequestMapping.class);
        assertNotNull(mapping);
        assertEquals(expected, mapping.value()[0]);
    }

    private void assertEndpoint(Class<?> type, String name, RequestMethod httpMethod, String path,
            String permission, Class<?> bodyType) throws Exception
    {
        Method method = Arrays.stream(type.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst().orElseThrow();
        assertEquals(path, mappingPath(method));
        assertEquals(httpMethod, mappingMethod(method));
        PreAuthorize authorize = method.getAnnotation(PreAuthorize.class);
        assertNotNull(authorize);
        assertEquals("@ss.hasPermi('" + permission + "')", authorize.value());
        if (bodyType != null)
        {
            Parameter body = Arrays.stream(method.getParameters())
                    .filter(parameter -> parameter.getAnnotation(RequestBody.class) != null)
                    .findFirst().orElseThrow();
            assertEquals(bodyType, body.getType());
            assertTrue(hasAnnotation(body, Valid.class), "request body must use @Valid");
        }
    }

    private void assertEndpointExpression(Class<?> type, String name, RequestMethod httpMethod,
            String path, String expression) throws Exception
    {
        Method method = Arrays.stream(type.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst().orElseThrow();
        assertEquals(path, mappingPath(method));
        assertEquals(httpMethod, mappingMethod(method));
        PreAuthorize authorize = method.getAnnotation(PreAuthorize.class);
        assertNotNull(authorize);
        assertEquals(expression, authorize.value());
    }

    private RequestMethod mappingMethod(Method method)
    {
        if (method.getAnnotation(GetMapping.class) != null) return GET;
        if (method.getAnnotation(PostMapping.class) != null) return POST;
        if (method.getAnnotation(PutMapping.class) != null) return PUT;
        throw new AssertionError("Missing HTTP mapping on " + method);
    }

    private String mappingPath(Method method)
    {
        GetMapping get = method.getAnnotation(GetMapping.class);
        if (get != null) return first(get.value());
        PostMapping post = method.getAnnotation(PostMapping.class);
        if (post != null) return first(post.value());
        PutMapping put = method.getAnnotation(PutMapping.class);
        if (put != null) return first(put.value());
        return "";
    }

    private String first(String[] values) { return values.length == 0 ? "" : values[0]; }

    private boolean hasAnnotation(Parameter parameter, Class<? extends Annotation> type)
    {
        return parameter.getAnnotation(type) != null;
    }

    private void assertNoFields(Class<?> type, String... forbidden)
    {
        Set<String> fields = Arrays.stream(type.getDeclaredFields()).map(Field::getName)
                .collect(java.util.stream.Collectors.toSet());
        for (String name : forbidden) assertFalse(fields.contains(name),
                () -> type.getSimpleName() + " must not accept " + name);
    }
}
