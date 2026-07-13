package com.ruoyi.web.todo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.web.controller.todo.*;

class TodoPhaseTwoApiTest
{
    @Test void dangerousRoutesHaveDedicatedPermissions() throws Exception
    {
        assertPermission(TodoOperationsController.class,"forceComplete","todo:force:complete");assertPermission(TodoOperationsController.class,"forceCancel","todo:force:cancel");assertPermission(TodoOperationsController.class,"batchTransfer","todo:batch:transfer");assertPermission(TodoOperationsController.class,"waiveSla","todo:sla:waive");assertPermission(TodoOperationsController.class,"regenerate","todo:regenerate");assertPermission(TodoBusinessViewController.class,"chain","todo:chain:query");assertPermission(TodoTemplateController.class,"publishDraft","todo:definition:publish");
    }
    @Test void phaseTwoRoutesRemainStable()
    {
        assertEquals("/todo/operations",TodoOperationsController.class.getAnnotation(RequestMapping.class).value()[0]);assertEquals("/todo/template",TodoTemplateController.class.getAnnotation(RequestMapping.class).value()[0]);assertEquals("/todo",TodoBusinessViewController.class.getAnnotation(RequestMapping.class).value()[0]);
    }
    private void assertPermission(Class<?> type,String methodName,String permission){Method target=null;for(Method method:type.getDeclaredMethods())if(method.getName().equals(methodName)){target=method;break;}if(target==null)throw new AssertionError(methodName);String expression=target.getAnnotation(PreAuthorize.class).value();org.junit.jupiter.api.Assertions.assertTrue(expression.contains(permission),expression);}
}
