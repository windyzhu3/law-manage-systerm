package com.ruoyi.web.controller.todo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import com.law.todo.application.TodoTemplateService;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;

@RestController @RequestMapping("/todo/template")
public class TodoTemplateController
{
    private final TodoTemplateService service;public TodoTemplateController(TodoTemplateService service){this.service=service;}
    public record PublishCommand(@NotNull @Min(1) Integer versionNo,String ownerRuleJson,String dodRuleJson,String slaRuleJson,String nextRuleJson) { }
    @PreAuthorize("@ss.hasPermi('todo:template:manage')") @GetMapping public AjaxResult list(){return AjaxResult.success(service.listTemplates());}
    @PreAuthorize("@ss.hasPermi('todo:template:manage')") @PostMapping public AjaxResult create(@RequestBody Map<String,Object> value){value.put("createBy",SecurityUtils.getUsername());return AjaxResult.success(service.saveTemplate(value));}
    @PreAuthorize("@ss.hasPermi('todo:template:manage')") @PutMapping public AjaxResult update(@RequestBody Map<String,Object> value){value.put("updateBy",SecurityUtils.getUsername());return AjaxResult.success(service.saveTemplate(value));}
    @PreAuthorize("@ss.hasPermi('todo:template:manage')") @GetMapping("/trigger") public AjaxResult triggers(){return AjaxResult.success(service.listTriggers());}
    @PreAuthorize("@ss.hasPermi('todo:template:manage')") @PostMapping("/trigger") public AjaxResult saveTrigger(@RequestBody Map<String,Object> value){return AjaxResult.success(service.saveTrigger(value));}
    @PreAuthorize("@ss.hasPermi('todo:template:manage')") @PostMapping("/{id}/publish") public AjaxResult publish(@PathVariable Long id,@RequestBody PublishCommand c){return AjaxResult.success(service.publish(id,c.versionNo(),c.ownerRuleJson(),c.dodRuleJson(),c.slaRuleJson(),c.nextRuleJson(),SecurityUtils.getUsername()));}
}
