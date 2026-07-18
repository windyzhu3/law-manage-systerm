package com.ruoyi.web.controller.todo;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.law.todo.application.TodoDefinitionCatalogService;
import com.law.todo.application.TodoDefinitionDiffService;
import com.law.todo.application.TodoDefinitionService;
import com.law.todo.application.TodoDefinitionSimulationService;
import com.law.todo.application.TodoTemplateService;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoDefinitionCommands.CopyTemplateCommand;
import com.law.todo.application.command.TodoDefinitionCommands.CopyVersionCommand;
import com.law.todo.application.command.TodoDefinitionCommands.PublishDraftCommand;
import com.law.todo.application.command.TodoDefinitionCommands.RollbackDraftCommand;
import com.law.todo.application.command.TodoDefinitionCommands.SimulateDefinitionCommand;
import com.law.todo.application.command.TodoDefinitionCommands.UpdateDraftCommand;
import com.law.todo.application.command.TodoManagementCommands.PublishCommand;
import com.law.todo.application.command.TodoManagementCommands.TemplateCommand;
import com.law.todo.application.command.TodoManagementCommands.TriggerCommand;
import com.law.todo.domain.TodoException;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;

@RestController
@RequestMapping({"/todo/template","/todo/definitions"})
public class TodoTemplateController
{
    private final TodoTemplateService service;
    private final TodoDefinitionService definitions;
    private final TodoDefinitionSimulationService simulation;
    private final TodoDefinitionDiffService diff;
    private final TodoDefinitionCatalogService catalogs;

    public TodoTemplateController(TodoTemplateService service,TodoDefinitionService definitions,
            TodoDefinitionSimulationService simulation,TodoDefinitionDiffService diff,
            TodoDefinitionCatalogService catalogs)
    {this.service=service;this.definitions=definitions;this.simulation=simulation;this.diff=diff;this.catalogs=catalogs;}

    @PreAuthorize("@ss.hasPermi('todo:definition:list')") @GetMapping public AjaxResult list(){return AjaxResult.success(service.listTemplates());}
    @PreAuthorize("@ss.hasPermi('todo:definition:edit')") @PostMapping public AjaxResult create(@Valid @RequestBody TemplateCommand value){return AjaxResult.success(service.saveTemplate(value,SecurityUtils.getUsername()));}
    @PreAuthorize("@ss.hasPermi('todo:definition:edit')") @PutMapping public AjaxResult update(@Valid @RequestBody TemplateCommand value){return AjaxResult.success(service.saveTemplate(value,SecurityUtils.getUsername()));}
    @PreAuthorize("@ss.hasPermi('todo:definition:list')") @GetMapping("/trigger") public AjaxResult triggers(){return AjaxResult.success(service.listTriggers());}
    @PreAuthorize("@ss.hasPermi('todo:definition:edit')") @PostMapping("/trigger") public AjaxResult saveTrigger(@Valid @RequestBody TriggerCommand value){return AjaxResult.success(service.saveTrigger(value));}
    @PreAuthorize("@ss.hasPermi('todo:definition:edit')") @PutMapping("/trigger") public AjaxResult updateTrigger(@Valid @RequestBody TriggerCommand value){return AjaxResult.success(service.saveTrigger(value));}
    @PreAuthorize("@ss.hasPermi('todo:definition:publish')") @PostMapping("/{id}/publish") public AjaxResult publish(@PathVariable Long id,@Valid @RequestBody PublishCommand c){return AjaxResult.success(service.publish(id,c.versionNo(),c.ownerRuleJson(),c.dodRuleJson(),c.slaRuleJson(),c.nextRuleJson(),c.uiSchemaJson(),SecurityUtils.getUsername()));}
    @PreAuthorize("@ss.hasPermi('todo:definition:list')") @GetMapping("/{id}/versions") public AjaxResult versions(@PathVariable Long id){return AjaxResult.success(definitions.versions(id));}
    @PreAuthorize("@ss.hasPermi('todo:definition:edit')") @PostMapping("/{id}/copy") public AjaxResult copyTemplate(@PathVariable Long id,@Valid @RequestBody CopyTemplateCommand c){return AjaxResult.success(definitions.copyTemplate(id,c,actor()));}
    @PreAuthorize("@ss.hasPermi('todo:definition:edit')") @PostMapping("/{id}/version/{version}/copy") public AjaxResult copyVersion(@PathVariable Long id,@PathVariable int version,@Valid @RequestBody CopyVersionCommand c){return AjaxResult.success(definitions.copyVersion(id,version,c,actor()));}
    @PreAuthorize("@ss.hasPermi('todo:definition:edit')") @PutMapping("/version/{versionId}") public AjaxResult updateDraft(@PathVariable Long versionId,@Valid @RequestBody UpdateDraftCommand c){requireSameVersion(versionId,c.versionId());return AjaxResult.success(definitions.updateDraft(c,actor()));}
    @PreAuthorize("@ss.hasPermi('todo:definition:publish')") @PostMapping("/version/{versionId}/publish") public AjaxResult publishDraft(@PathVariable Long versionId,@Valid @RequestBody PublishDraftCommand c){requireSameVersion(versionId,c.versionId());return AjaxResult.success(definitions.publish(c,actor()));}
    @PreAuthorize("@ss.hasPermi('todo:definition:preflight')") @PostMapping({"/version/{versionId}/preflight","/{versionId}/preflight"}) public AjaxResult preflight(@PathVariable Long versionId){return AjaxResult.success(definitions.preflight(versionId));}
    @PreAuthorize("@ss.hasPermi('todo:definition:simulate')") @PostMapping({"/version/{versionId}/simulate","/{versionId}/simulate"}) public AjaxResult simulate(@PathVariable Long versionId,@Valid @RequestBody SimulateDefinitionCommand c){return AjaxResult.success(simulation.simulate(versionId,c));}
    @PreAuthorize("@ss.hasPermi('todo:definition:edit')") @PostMapping({"/version/{versionId}/rollback-draft","/{versionId}/rollback-draft"}) public AjaxResult rollbackDraft(@PathVariable Long versionId,@Valid @RequestBody RollbackDraftCommand c){return AjaxResult.success(definitions.rollbackDraft(versionId,c,actor()));}
    @PreAuthorize("@ss.hasPermi('todo:definition:diff')") @GetMapping("/versions/{left}/diff/{right}") public AjaxResult diff(@PathVariable Long left,@PathVariable Long right){return AjaxResult.success(diff.diff(left,right));}
    @PreAuthorize("@ss.hasPermi('todo:definition:view')") @GetMapping("/catalog/events") public AjaxResult events(){return AjaxResult.success(catalogs.events());}
    @PreAuthorize("@ss.hasPermi('todo:definition:view')") @GetMapping("/catalog/handlers") public AjaxResult handlers(){return AjaxResult.success(catalogs.handlers());}
    @PreAuthorize("@ss.hasPermi('todo:definition:view')") @GetMapping("/catalog/validators") public AjaxResult validators(){return AjaxResult.success(catalogs.validators());}
    @PreAuthorize("@ss.hasPermi('todo:decision:view')") @GetMapping("/catalog/decisions") public AjaxResult decisions(){return AjaxResult.success(catalogs.decisions());}

    private void requireSameVersion(Long path,Long body){if(!path.equals(body))throw new TodoException("TODO_TEMPLATE_VERSION_ID_MISMATCH","Path and body version IDs must match");}
    private Actor actor(){return new Actor(SecurityUtils.getUserId(),SecurityUtils.getUsername(),SecurityUtils.getDeptId());}
}
