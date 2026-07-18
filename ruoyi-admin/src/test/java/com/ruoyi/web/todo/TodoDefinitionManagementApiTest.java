package com.ruoyi.web.todo;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

import com.ruoyi.web.controller.todo.TodoDefinitionCatalogController;
import com.ruoyi.web.controller.todo.TodoAdmissionEvidenceController;
import com.ruoyi.web.controller.todo.TodoFoundationResourceController;
import com.ruoyi.web.controller.todo.TodoHistoricalMigrationReadinessController;
import com.ruoyi.web.controller.todo.TodoFileSecurityReadinessController;
import com.ruoyi.web.controller.todo.TodoTemplateController;

class TodoDefinitionManagementApiTest
{
    @Test void definition_alias_and_canonical_catalog_routes_are_exposed()
    {
        assertArrayEquals(new String[]{"/todo/template","/todo/definitions"},TodoTemplateController.class.getAnnotation(RequestMapping.class).value());
        assertArrayEquals(new String[]{"/todo"},TodoDefinitionCatalogController.class.getAnnotation(RequestMapping.class).value());
    }

    @Test void management_actions_use_separate_permissions()
    {
        assertPermission(TodoTemplateController.class,"preflight","todo:definition:preflight");
        assertPermission(TodoTemplateController.class,"simulate","todo:definition:simulate");
        assertPermission(TodoTemplateController.class,"rollbackDraft","todo:definition:edit");
        assertPermission(TodoTemplateController.class,"diff","todo:definition:diff");
        assertPermission(TodoDefinitionCatalogController.class,"events","todo:definition:view");
        assertPermission(TodoDefinitionCatalogController.class,"decisions","todo:decision:view");
        assertPermission(TodoDefinitionCatalogController.class,"decisionGovernanceOptions","todo:decision:view");
        assertPermission(TodoDefinitionCatalogController.class,"createDecision","todo:decision:edit");
        assertPermission(TodoDefinitionCatalogController.class,"updateDecision","todo:decision:edit");
        assertPermission(TodoAdmissionEvidenceController.class,"list","todo:admission:view");
        assertPermission(TodoAdmissionEvidenceController.class,"governanceOptions","todo:admission:view");
        assertPermission(TodoAdmissionEvidenceController.class,"update","todo:admission:edit");
        assertPermission(TodoFoundationResourceController.class,"readiness","todo:admission:view");
        assertPermission(TodoHistoricalMigrationReadinessController.class,"readiness","todo:admission:view");
        assertPermission(TodoFileSecurityReadinessController.class,"readiness","todo:admission:view");
    }

    private void assertPermission(Class<?> type,String methodName,String permission)
    {
        Method target=java.util.Arrays.stream(type.getDeclaredMethods()).filter(method->method.getName().equals(methodName)).findFirst().orElseThrow();
        assertTrue(target.getAnnotation(PreAuthorize.class).value().contains(permission));
    }
}
