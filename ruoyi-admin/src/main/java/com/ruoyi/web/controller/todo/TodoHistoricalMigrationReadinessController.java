package com.ruoyi.web.controller.todo;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.law.todo.application.TodoHistoricalMigrationReadinessService;
import com.ruoyi.common.core.domain.AjaxResult;

@RestController
@RequestMapping("/todo/foundation-migration")
public class TodoHistoricalMigrationReadinessController
{
    private final TodoHistoricalMigrationReadinessService service;
    public TodoHistoricalMigrationReadinessController(TodoHistoricalMigrationReadinessService service){this.service=service;}

    @PreAuthorize("@ss.hasPermi('todo:admission:view')")
    @GetMapping
    public AjaxResult readiness(@RequestParam(defaultValue="G-04") String gateCode)
    {
        return AjaxResult.success(service.readiness(gateCode));
    }
}
