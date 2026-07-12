package com.ruoyi.web.controller.todo;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.law.todo.application.TodoCalendarService;
import com.law.todo.application.command.TodoManagementCommands.CalendarCommand;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;

@RestController @RequestMapping("/todo/calendar")
public class TodoCalendarController extends BaseController
{
    private final TodoCalendarService service;public TodoCalendarController(TodoCalendarService service){this.service=service;}
    @PreAuthorize("@ss.hasPermi('todo:calendar:manage')") @GetMapping public AjaxResult list(){return success(service.list());}
    @PreAuthorize("@ss.hasPermi('todo:calendar:manage')") @PostMapping public AjaxResult save(@Valid @RequestBody CalendarCommand value){return toAjax(service.save(value));}
    @PreAuthorize("@ss.hasPermi('todo:calendar:manage')") @PutMapping public AjaxResult update(@Valid @RequestBody CalendarCommand value){return toAjax(service.save(value));}
}
