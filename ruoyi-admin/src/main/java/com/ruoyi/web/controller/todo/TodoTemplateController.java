package com.ruoyi.web.controller.todo;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.law.todo.application.TodoTemplateService;
import com.law.todo.application.TodoDefinitionService;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoDefinitionCommands.CopyTemplateCommand;
import com.law.todo.application.command.TodoDefinitionCommands.CopyVersionCommand;
import com.law.todo.application.command.TodoDefinitionCommands.UpdateDraftCommand;
import com.law.todo.application.command.TodoDefinitionCommands.PublishDraftCommand;
import com.law.todo.application.command.TodoManagementCommands.PublishCommand;
import com.law.todo.application.command.TodoManagementCommands.TemplateCommand;
import com.law.todo.application.command.TodoManagementCommands.TriggerCommand;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;

@RestController @RequestMapping("/todo/template")
public class TodoTemplateController
{
    private final TodoTemplateService service;private final TodoDefinitionService definitions;public TodoTemplateController(TodoTemplateService service,TodoDefinitionService definitions){this.service=service;this.definitions=definitions;}
    @PreAuthorize("@ss.hasPermi('todo:template:manage')") @GetMapping public AjaxResult list(){return AjaxResult.success(service.listTemplates());}
    @PreAuthorize("@ss.hasPermi('todo:template:manage')") @PostMapping public AjaxResult create(@Valid @RequestBody TemplateCommand value){return AjaxResult.success(service.saveTemplate(value,SecurityUtils.getUsername()));}
    @PreAuthorize("@ss.hasPermi('todo:template:manage')") @PutMapping public AjaxResult update(@Valid @RequestBody TemplateCommand value){return AjaxResult.success(service.saveTemplate(value,SecurityUtils.getUsername()));}
    @PreAuthorize("@ss.hasPermi('todo:template:manage')") @GetMapping("/trigger") public AjaxResult triggers(){return AjaxResult.success(service.listTriggers());}
    @PreAuthorize("@ss.hasPermi('todo:template:manage')") @PostMapping("/trigger") public AjaxResult saveTrigger(@Valid @RequestBody TriggerCommand value){return AjaxResult.success(service.saveTrigger(value));}
    @PreAuthorize("@ss.hasPermi('todo:template:manage')") @PostMapping("/{id}/publish") public AjaxResult publish(@PathVariable Long id,@Valid @RequestBody PublishCommand c){return AjaxResult.success(service.publish(id,c.versionNo(),c.ownerRuleJson(),c.dodRuleJson(),c.slaRuleJson(),c.nextRuleJson(),c.uiSchemaJson(),SecurityUtils.getUsername()));}
    @PreAuthorize("@ss.hasPermi('todo:template:list')") @GetMapping("/{id}/versions") public AjaxResult versions(@PathVariable Long id){return AjaxResult.success(definitions.versions(id));}
    @PreAuthorize("@ss.hasPermi('todo:template:copy')") @PostMapping("/{id}/copy") public AjaxResult copyTemplate(@PathVariable Long id,@Valid @RequestBody CopyTemplateCommand c){return AjaxResult.success(definitions.copyTemplate(id,c,actor()));}
    @PreAuthorize("@ss.hasPermi('todo:template:copy')") @PostMapping("/{id}/version/{version}/copy") public AjaxResult copyVersion(@PathVariable Long id,@PathVariable int version,@Valid @RequestBody CopyVersionCommand c){return AjaxResult.success(definitions.copyVersion(id,version,c,actor()));}
    @PreAuthorize("@ss.hasPermi('todo:template:edit')") @PutMapping("/version/{versionId}") public AjaxResult updateDraft(@PathVariable Long versionId,@Valid @RequestBody UpdateDraftCommand c){if(!versionId.equals(c.versionId()))return AjaxResult.error("版本ID不一致");return AjaxResult.success(definitions.updateDraft(c,actor()));}
    @PreAuthorize("@ss.hasPermi('todo:template:publish')") @PostMapping("/version/{versionId}/publish") public AjaxResult publishDraft(@PathVariable Long versionId,@Valid @RequestBody PublishDraftCommand c){if(!versionId.equals(c.versionId()))return AjaxResult.error("版本ID不一致");return AjaxResult.success(definitions.publish(c,actor()));}
    private Actor actor(){return new Actor(SecurityUtils.getUserId(),SecurityUtils.getUsername(),SecurityUtils.getDeptId());}
}
