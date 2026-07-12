package com.ruoyi.web.controller.todo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.law.todo.application.TodoTemplateService;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;

@RestController @RequestMapping("/todo/template")
public class TodoTemplateController
{
    private final TodoTemplateService service;public TodoTemplateController(TodoTemplateService service){this.service=service;}
    public record PublishCommand(@NotNull @Min(1) Integer versionNo,String ownerRuleJson,String dodRuleJson,String slaRuleJson,String nextRuleJson) { }
    @PreAuthorize("@ss.hasPermi('todo:template:manage')") @PostMapping("/{id}/publish") public AjaxResult publish(@PathVariable Long id,@RequestBody PublishCommand c){return AjaxResult.success(service.publish(id,c.versionNo(),c.ownerRuleJson(),c.dodRuleJson(),c.slaRuleJson(),c.nextRuleJson(),SecurityUtils.getUsername()));}
}
