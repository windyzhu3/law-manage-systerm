package com.ruoyi.web.controller.todo;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.law.todo.application.TodoFoundationAdmissionReadinessService;
import com.law.todo.application.view.TodoFoundationAdmissionReadinessView;

class TodoFoundationAdmissionReadinessControllerTest
{
    @Test
    void readiness_returns_the_repository_measured_aggregate_without_mutation()
    {
        TodoFoundationAdmissionReadinessService service=mock(TodoFoundationAdmissionReadinessService.class);
        TodoFoundationAdmissionReadinessView view=new TodoFoundationAdmissionReadinessView(
                "NOT_ADMITTED",false,2,8,List.of());
        when(service.readiness()).thenReturn(view);

        var result=new TodoFoundationAdmissionReadinessController(service).readiness();

        assertSame(view,result.get("data"));
        verify(service).readiness();
    }
}
