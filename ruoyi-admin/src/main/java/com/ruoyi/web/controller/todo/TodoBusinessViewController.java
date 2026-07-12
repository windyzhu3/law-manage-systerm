package com.ruoyi.web.controller.todo;

import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.law.todo.application.TodoBusinessViewService;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.utils.SecurityUtils;

@RestController @RequestMapping("/todo")
public class TodoBusinessViewController extends BaseController
{
    private final TodoBusinessViewService service;
    public TodoBusinessViewController(TodoBusinessViewService service){this.service=service;}

    @PreAuthorize("@ss.hasPermi('todo:query')")
    @GetMapping("/business/{type}/{id}/summary")
    public AjaxResult summary(@PathVariable String type,@PathVariable Long id){return success(service.summary(normalize(type),id,actor()));}

    @PreAuthorize("@ss.hasPermi('todo:query')")
    @GetMapping("/business/{type}/{id}/list")
    public TableDataInfo list(@PathVariable String type,@PathVariable Long id){startPage();List<Map<String,Object>> rows=service.businessTodos(normalize(type),id,actor());return getDataTable(rows);}

    @PreAuthorize("@ss.hasPermi('todo:chain:query')")
    @GetMapping("/chain/{rootTodoId}")
    public AjaxResult chain(@PathVariable Long rootTodoId){return success(service.chain(rootTodoId,actor()));}

    private Actor actor(){return new Actor(SecurityUtils.getUserId(),SecurityUtils.getUsername(),SecurityUtils.getDeptId());}
    private String normalize(String type){return type.toUpperCase(java.util.Locale.ROOT);}
}
