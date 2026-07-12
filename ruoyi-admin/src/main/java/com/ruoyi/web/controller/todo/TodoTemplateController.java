package com.ruoyi.web.controller.todo;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.law.todo.application.TodoTemplateService;
import com.law.todo.application.command.TodoManagementCommands.PublishCommand;
import com.law.todo.application.command.TodoManagementCommands.TemplateCommand;
import com.law.todo.application.command.TodoManagementCommands.TriggerCommand;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;

@RestController @RequestMapping("/todo/template")
public class TodoTemplateController
{
    private final TodoTemplateService service;public TodoTemplateController(TodoTemplateService service){this.service=service;}
    @PreAuthorize("@ss.hasPermi('todo:template:manage')") @GetMapping public AjaxResult list(){return AjaxResult.success(service.listTemplates());}
    @PreAuthorize("@ss.hasPermi('todo:template:manage')") @PostMapping public AjaxResult create(@Valid @RequestBody TemplateCommand value){return AjaxResult.success(service.saveTemplate(value,SecurityUtils.getUsername()));}
    @PreAuthorize("@ss.hasPermi('todo:template:manage')") @PutMapping public AjaxResult update(@Valid @RequestBody TemplateCommand value){return AjaxResult.success(service.saveTemplate(value,SecurityUtils.getUsername()));}
    @PreAuthorize("@ss.hasPermi('todo:template:manage')") @GetMapping("/trigger") public AjaxResult triggers(){return AjaxResult.success(service.listTriggers());}
    @PreAuthorize("@ss.hasPermi('todo:template:manage')") @PostMapping("/trigger") public AjaxResult saveTrigger(@Valid @RequestBody TriggerCommand value){return AjaxResult.success(service.saveTrigger(value));}
    @PreAuthorize("@ss.hasPermi('todo:template:manage')") @PostMapping("/{id}/publish") public AjaxResult publish(@PathVariable Long id,@Valid @RequestBody PublishCommand c){return AjaxResult.success(service.publish(id,c.versionNo(),c.ownerRuleJson(),c.dodRuleJson(),c.slaRuleJson(),c.nextRuleJson(),c.uiSchemaJson(),SecurityUtils.getUsername()));}
}
