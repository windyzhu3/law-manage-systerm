package com.ruoyi.web.controller.todo;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.law.todo.application.TodoDefinitionCatalogService;
import com.law.todo.application.TodoDecisionManagementService;
import com.law.todo.application.TodoAutoActionCapabilityCatalogService;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoDecisionCommands.CreateDecisionCommand;
import com.law.todo.application.command.TodoDecisionCommands.UpdateDecisionCommand;
import com.law.todo.domain.TodoException;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;

@RestController
@RequestMapping("/todo")
public class TodoDefinitionCatalogController
{
    private final TodoDefinitionCatalogService catalogs;
    private final TodoDecisionManagementService decisionService;
    private final TodoAutoActionCapabilityCatalogService autoActions;
    @Autowired public TodoDefinitionCatalogController(TodoDefinitionCatalogService catalogs,TodoDecisionManagementService decisionService,TodoAutoActionCapabilityCatalogService autoActions)
    {this.catalogs=catalogs;this.decisionService=decisionService;this.autoActions=autoActions;}
    public TodoDefinitionCatalogController(TodoDefinitionCatalogService catalogs,TodoDecisionManagementService decisionService)
    {this(catalogs,decisionService,new TodoAutoActionCapabilityCatalogService(java.util.List.of()));}
    @PreAuthorize("@ss.hasPermi('todo:definition:view')") @GetMapping("/event-catalog") public AjaxResult events(){return AjaxResult.success(catalogs.events());}
    @PreAuthorize("@ss.hasPermi('todo:definition:view')") @GetMapping("/handler-catalog") public AjaxResult handlers(){return AjaxResult.success(catalogs.handlers());}
    @PreAuthorize("@ss.hasPermi('todo:definition:view')") @GetMapping("/validator-catalog") public AjaxResult validators(){return AjaxResult.success(catalogs.validators());}
    @PreAuthorize("@ss.hasPermi('todo:definition:view')") @GetMapping("/auto-action-capabilities") public AjaxResult autoActionCapabilities(){return AjaxResult.success(autoActions.list());}
    @PreAuthorize("@ss.hasPermi('todo:decision:view')") @GetMapping("/decisions") public AjaxResult decisions(){return AjaxResult.success(decisionService.list());}
    @PreAuthorize("@ss.hasPermi('todo:decision:edit')") @PostMapping("/decisions") public AjaxResult createDecision(@Valid @RequestBody CreateDecisionCommand command){return AjaxResult.success(decisionService.create(command,actor()));}
    @PreAuthorize("@ss.hasPermi('todo:decision:edit')") @PutMapping("/decisions/{decisionId}") public AjaxResult updateDecision(@PathVariable Long decisionId,@Valid @RequestBody UpdateDecisionCommand command)
    {if(!decisionId.equals(command.decisionId()))throw new TodoException("TODO_DECISION_ID_MISMATCH","Path and body decision IDs must match");return AjaxResult.success(decisionService.update(command,actor()));}
    private Actor actor(){return new Actor(SecurityUtils.getUserId(),SecurityUtils.getUsername(),SecurityUtils.getDeptId());}
}
