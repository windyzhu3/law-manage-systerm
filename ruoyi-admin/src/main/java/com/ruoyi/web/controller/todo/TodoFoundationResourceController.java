package com.ruoyi.web.controller.todo;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.law.todo.application.TodoFoundationResourceService;
import com.ruoyi.common.core.domain.AjaxResult;

@RestController
@RequestMapping("/todo/foundation-resources")
public class TodoFoundationResourceController
{
    private final TodoFoundationResourceService service;
    public TodoFoundationResourceController(TodoFoundationResourceService service){this.service=service;}

    @PreAuthorize("@ss.hasPermi('todo:admission:view')")
    @GetMapping
    public AjaxResult readiness(@RequestParam(defaultValue="G-02") String gateCode)
    {
        return AjaxResult.success(service.readiness(gateCode));
    }
}
