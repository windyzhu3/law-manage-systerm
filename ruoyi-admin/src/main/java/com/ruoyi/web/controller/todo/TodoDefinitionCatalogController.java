package com.ruoyi.web.controller.todo;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.law.todo.application.TodoDefinitionCatalogService;
import com.ruoyi.common.core.domain.AjaxResult;

@RestController
@RequestMapping("/todo")
public class TodoDefinitionCatalogController
{
    private final TodoDefinitionCatalogService catalogs;
    public TodoDefinitionCatalogController(TodoDefinitionCatalogService catalogs){this.catalogs=catalogs;}
    @PreAuthorize("@ss.hasPermi('todo:definition:view')") @GetMapping("/event-catalog") public AjaxResult events(){return AjaxResult.success(catalogs.events());}
    @PreAuthorize("@ss.hasPermi('todo:definition:view')") @GetMapping("/handler-catalog") public AjaxResult handlers(){return AjaxResult.success(catalogs.handlers());}
    @PreAuthorize("@ss.hasPermi('todo:definition:view')") @GetMapping("/validator-catalog") public AjaxResult validators(){return AjaxResult.success(catalogs.validators());}
    @PreAuthorize("@ss.hasPermi('todo:decision:view')") @GetMapping("/decisions") public AjaxResult decisions(){return AjaxResult.success(catalogs.decisions());}
}
