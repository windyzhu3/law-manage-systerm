package com.ruoyi.web.controller.todo;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.law.todo.application.TodoNotificationService;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;

@RestController @RequestMapping("/todo/notification")
public class TodoNotificationController extends BaseController
{
    private final TodoNotificationService service;public TodoNotificationController(TodoNotificationService service){this.service=service;}
    @PreAuthorize("@ss.hasPermi('todo:list')") @GetMapping public AjaxResult list(@RequestParam(required=false) String status){return success(service.list(SecurityUtils.getUserId(),status));}
    @PreAuthorize("@ss.hasPermi('todo:list')") @PostMapping("/{id}/read") public AjaxResult read(@PathVariable Long id){return service.read(id,SecurityUtils.getUserId())?success():error("通知不存在或已读");}
}
