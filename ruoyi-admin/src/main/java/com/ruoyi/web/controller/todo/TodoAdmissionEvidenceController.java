package com.ruoyi.web.controller.todo;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.law.todo.application.TodoAdmissionEvidenceService;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoAdmissionEvidenceCommands.UpdateAdmissionEvidenceCommand;
import com.law.todo.domain.TodoException;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/todo/admission-evidence")
public class TodoAdmissionEvidenceController
{
    private final TodoAdmissionEvidenceService service;
    public TodoAdmissionEvidenceController(TodoAdmissionEvidenceService service){this.service=service;}

    @PreAuthorize("@ss.hasPermi('todo:admission:view')")
    @GetMapping
    public AjaxResult list(){return AjaxResult.success(service.list());}

    @PreAuthorize("@ss.hasPermi('todo:admission:view')")
    @GetMapping("/governance-options")
    public AjaxResult governanceOptions(){return AjaxResult.success(service.governanceOptions());}

    @PreAuthorize("@ss.hasPermi('todo:admission:edit')")
    @PutMapping("/{evidenceId}")
    public AjaxResult update(@PathVariable Long evidenceId,@Valid @RequestBody UpdateAdmissionEvidenceCommand command)
    {
        if(!evidenceId.equals(command.evidenceId()))throw new TodoException("TODO_ADMISSION_ID_MISMATCH","Path and body evidence IDs must match");
        return AjaxResult.success(service.update(command,actor()));
    }

    private Actor actor(){return new Actor(SecurityUtils.getUserId(),SecurityUtils.getUsername(),SecurityUtils.getDeptId());}
}
