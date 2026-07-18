package com.ruoyi.web.controller.todo;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.law.todo.application.TodoFoundationAdmissionReadinessService;
import com.ruoyi.common.core.domain.AjaxResult;

@RestController
@RequestMapping("/todo/foundation-admission")
public class TodoFoundationAdmissionReadinessController
{
    private final TodoFoundationAdmissionReadinessService service;

    public TodoFoundationAdmissionReadinessController(TodoFoundationAdmissionReadinessService service)
    {
        this.service=service;
    }

    @PreAuthorize("@ss.hasPermi('todo:admission:view')")
    @GetMapping
    public AjaxResult readiness()
    {
        return AjaxResult.success(service.readiness());
    }
}
