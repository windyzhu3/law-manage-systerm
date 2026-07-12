package com.ruoyi.web.controller.todo;

import java.time.LocalDateTime;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.law.todo.application.TodoSlaService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;

@RestController @RequestMapping("/todo/sla")
public class TodoSlaController extends BaseController
{
    private final TodoSlaService service;public TodoSlaController(TodoSlaService service){this.service=service;}
    @PreAuthorize("@ss.hasPermi('todo:template:manage')") @PostMapping("/scan") public AjaxResult scan(){return success(service.scanAndEscalate(LocalDateTime.now()));}
    @PreAuthorize("@ss.hasPermi('todo:submit')") @PostMapping("/{id}/pause") public AjaxResult pause(@PathVariable Long id){return service.pause(id,LocalDateTime.now())?success():error("SLA当前不能暂停");}
    @PreAuthorize("@ss.hasPermi('todo:submit')") @PostMapping("/{id}/resume") public AjaxResult resume(@PathVariable Long id){return service.resume(id,LocalDateTime.now())?success():error("SLA当前不能恢复");}
}
