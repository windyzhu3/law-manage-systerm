package com.ruoyi.web.controller.todo;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.law.todo.application.TodoAcceptanceEvidenceService;
import com.law.todo.application.command.TodoAcceptanceEvidenceCommands.BatchBindMappingsCommand;
import com.law.todo.application.command.TodoAcceptanceEvidenceCommands.CreateScenarioCommand;
import com.law.todo.application.command.TodoAcceptanceEvidenceCommands.UpdateMappingCommand;
import com.law.todo.application.command.TodoAcceptanceEvidenceCommands.UpdateScenarioCommand;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.domain.TodoException;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/todo")
public class TodoAcceptanceEvidenceController
{
    private final TodoAcceptanceEvidenceService service;

    public TodoAcceptanceEvidenceController(TodoAcceptanceEvidenceService service)
    {
        this.service = service;
    }

    @PreAuthorize("@ss.hasPermi('todo:admission:view')")
    @GetMapping("/acceptance-scenarios")
    public AjaxResult scenarios()
    {
        return AjaxResult.success(service.scenarios());
    }

    @PreAuthorize("@ss.hasPermi('todo:admission:view')")
    @GetMapping("/acceptance-mappings")
    public AjaxResult mappings(@RequestParam(required = false) String templateCode,
            @RequestParam(required = false) String dimensionCode,
            @RequestParam(required = false) String status)
    {
        return AjaxResult.success(service.mappings(templateCode, dimensionCode, status));
    }

    @PreAuthorize("@ss.hasPermi('todo:admission:view')")
    @GetMapping("/acceptance-governance-options")
    public AjaxResult governanceOptions()
    {
        return AjaxResult.success(service.governanceOptions());
    }

    @PreAuthorize("@ss.hasPermi('todo:admission:edit')")
    @PostMapping("/acceptance-scenarios")
    public AjaxResult createScenario(@Valid @RequestBody CreateScenarioCommand command)
    {
        return AjaxResult.success(service.createScenario(command, actor()));
    }

    @PreAuthorize("@ss.hasPermi('todo:admission:edit')")
    @PutMapping("/acceptance-scenarios/{scenarioId}")
    public AjaxResult updateScenario(@PathVariable Long scenarioId,
            @Valid @RequestBody UpdateScenarioCommand command)
    {
        if (!scenarioId.equals(command.scenarioId()))
        {
            throw new TodoException("TODO_ACCEPTANCE_ID_MISMATCH", "Path and body scenario IDs must match");
        }
        return AjaxResult.success(service.updateScenario(command, actor()));
    }

    @PreAuthorize("@ss.hasPermi('todo:admission:edit')")
    @PutMapping("/acceptance-mappings/{mappingId}")
    public AjaxResult updateMapping(@PathVariable Long mappingId,
            @Valid @RequestBody UpdateMappingCommand command)
    {
        if (!mappingId.equals(command.mappingId()))
        {
            throw new TodoException("TODO_ACCEPTANCE_ID_MISMATCH", "Path and body mapping IDs must match");
        }
        return AjaxResult.success(service.updateMapping(command, actor()));
    }

    @PreAuthorize("@ss.hasPermi('todo:admission:edit')")
    @PutMapping("/acceptance-mappings/batch-bind")
    public AjaxResult batchBind(@Valid @RequestBody BatchBindMappingsCommand command)
    {
        return AjaxResult.success(service.batchBind(command, actor()));
    }

    private Actor actor()
    {
        return new Actor(SecurityUtils.getUserId(), SecurityUtils.getUsername(), SecurityUtils.getDeptId());
    }
}
