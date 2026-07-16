package com.ruoyi.web.controller.todo;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.law.todo.application.TodoSlaService;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.extension.TodoExtensionCommands.DecisionCommand;
import com.law.todo.extension.TodoExtensionCommands.RequestCommand;
import com.law.todo.extension.TodoExtensionService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/todo")
public class TodoSlaController extends BaseController
{
    private final TodoSlaService service;
    private final TodoExtensionService extensions;

    public TodoSlaController(TodoSlaService service){this(service,null);}
    @Autowired public TodoSlaController(TodoSlaService service,TodoExtensionService extensions){this.service=service;this.extensions=extensions;}

    @PreAuthorize("@ss.hasPermi('todo:template:manage')")
    @PostMapping("/sla/scan") public AjaxResult scan(){return success(service.scanAndEscalate(LocalDateTime.now()));}

    @PreAuthorize("@ss.hasPermi('todo:submit')")
    @PostMapping("/sla/{id}/pause") public AjaxResult pause(@PathVariable Long id){return service.pause(id,SecurityUtils.getUserId(),LocalDateTime.now())?success():error("SLA is not currently pausable");}

    @PreAuthorize("@ss.hasPermi('todo:submit')")
    @PostMapping("/sla/{id}/resume") public AjaxResult resume(@PathVariable Long id){return service.resume(id,SecurityUtils.getUserId(),LocalDateTime.now())?success():error("SLA is not currently resumable");}

    @PreAuthorize("@ss.hasPermi('todo:extension:request')")
    @PostMapping("/{id}/extension-requests") public AjaxResult request(@PathVariable Long id,@Valid @RequestBody RequestCommand command)
    {return success(extensions.request(id,command,actor()));}

    @PreAuthorize("@ss.hasPermi('todo:extension:approve')")
    @PostMapping("/extension-requests/{id}/approve") public AjaxResult approve(@PathVariable Long id,@Valid @RequestBody DecisionCommand command)
    {return success(extensions.approve(id,command,actor()));}

    @PreAuthorize("@ss.hasPermi('todo:extension:approve')")
    @PostMapping("/extension-requests/{id}/reject") public AjaxResult reject(@PathVariable Long id,@Valid @RequestBody DecisionCommand command)
    {return success(extensions.reject(id,command,actor()));}

    private Actor actor(){return new Actor(SecurityUtils.getUserId(),SecurityUtils.getUsername(),SecurityUtils.getDeptId());}
}
