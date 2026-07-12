package com.ruoyi.web.controller.todo;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.law.todo.application.TodoExceptionOperationService;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoOperationCommands.BatchTransferCommand;
import com.law.todo.application.command.TodoOperationCommands.ForceCommand;
import com.law.todo.application.command.TodoOperationCommands.SlaWaiverCommand;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;

@RestController @RequestMapping("/todo/operations")
public class TodoOperationsController extends BaseController
{
    private final TodoExceptionOperationService service;
    public TodoOperationsController(TodoExceptionOperationService service){this.service=service;}
    @PreAuthorize("@ss.hasPermi('todo:operations:list')") @GetMapping("/dashboard") public AjaxResult dashboard(){return success(service.dashboard());}
    @PreAuthorize("@ss.hasPermi('todo:force:complete')") @PostMapping("/{id}/force-complete") public AjaxResult forceComplete(@PathVariable Long id,@Valid @RequestBody ForceCommand command){return success(service.forceComplete(id,command,actor()));}
    @PreAuthorize("@ss.hasPermi('todo:force:cancel')") @PostMapping("/{id}/force-cancel") public AjaxResult forceCancel(@PathVariable Long id,@Valid @RequestBody ForceCommand command){return success(service.forceCancel(id,command,actor()));}
    @PreAuthorize("@ss.hasPermi('todo:batch:transfer')") @PostMapping("/batch-transfer") public AjaxResult batchTransfer(@Valid @RequestBody BatchTransferCommand command){return success(service.batchTransfer(command,actor()));}
    @PreAuthorize("@ss.hasPermi('todo:sla:waive')") @PostMapping("/{id}/sla-waiver") public AjaxResult waiveSla(@PathVariable Long id,@Valid @RequestBody SlaWaiverCommand command){return success(service.waiveSla(id,command,actor()));}
    @PreAuthorize("@ss.hasPermi('todo:regenerate')") @PostMapping("/{id}/regenerate") public AjaxResult regenerate(@PathVariable Long id,@Valid @RequestBody ForceCommand command){return success(service.regenerate(id,command,actor()));}
    private Actor actor(){return new Actor(SecurityUtils.getUserId(),SecurityUtils.getUsername(),SecurityUtils.getDeptId());}
}
