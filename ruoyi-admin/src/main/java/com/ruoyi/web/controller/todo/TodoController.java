package com.ruoyi.web.controller.todo;

import java.util.List;
import java.util.Map;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.law.todo.application.TodoCommandService;
import com.law.todo.application.TodoQueryService;
import com.law.todo.application.TodoCollaborationService;
import com.law.todo.application.command.TodoActionCommands.ActionCommand;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.utils.SecurityUtils;

@RestController @RequestMapping("/todo")
public class TodoController extends BaseController
{
    private final TodoQueryService query;private final TodoCommandService command;private final TodoCollaborationService collaboration;
    public TodoController(TodoQueryService query,TodoCommandService command,TodoCollaborationService collaboration){this.query=query;this.command=command;this.collaboration=collaboration;}
    public record AttachmentCommand(String actionId,String attachmentType,String fileName,String fileUrl) { }
    public record ParticipantCommand(String participantType,Long participantValue) { }
    @PreAuthorize("@ss.hasPermi('todo:list')") @GetMapping("/dashboard") public AjaxResult dashboard(){return success(query.dashboard(SecurityUtils.getUserId(),SecurityUtils.getDeptId()));}
    @PreAuthorize("@ss.hasPermi('todo:list')") @GetMapping("/list") public TableDataInfo list(@RequestParam Map<String,Object> q){startPage();List<Map<String,Object>> rows=query.list(q,SecurityUtils.getUserId(),SecurityUtils.getDeptId());return getDataTable(rows);}
    @PreAuthorize("@ss.hasPermi('todo:query')") @GetMapping("/{id}") public AjaxResult detail(@PathVariable Long id){return success(query.detailView(id,SecurityUtils.getUserId(),SecurityUtils.getDeptId()));}
    @PreAuthorize("@ss.hasPermi('todo:claim')") @PostMapping("/{id}/claim") public AjaxResult claim(@PathVariable Long id,@Valid @RequestBody ActionCommand c){return success(command.claim(id,c,actor()));}
    @PreAuthorize("@ss.hasPermi('todo:start')") @PostMapping("/{id}/start") public AjaxResult start(@PathVariable Long id,@Valid @RequestBody ActionCommand c){return success(command.start(id,c,actor()));}
    @PreAuthorize("@ss.hasPermi('todo:submit')") @PostMapping("/{id}/submit") public AjaxResult submit(@PathVariable Long id,@Valid @RequestBody ActionCommand c){return success(command.submit(id,c,actor()));}
    @PreAuthorize("@ss.hasPermi('todo:complete')") @PostMapping("/{id}/complete") public AjaxResult complete(@PathVariable Long id,@Valid @RequestBody ActionCommand c){return success(command.complete(id,c,actor()));}
    @PreAuthorize("@ss.hasPermi('todo:return')") @PostMapping("/{id}/return") public AjaxResult back(@PathVariable Long id,@Valid @RequestBody ActionCommand c){return success(command.returnTodo(id,c,actor()));}
    @PreAuthorize("@ss.hasPermi('todo:transfer')") @PostMapping("/{id}/transfer") public AjaxResult transfer(@PathVariable Long id,@Valid @RequestBody ActionCommand c){return success(command.transfer(id,c,actor()));}
    @PreAuthorize("@ss.hasPermi('todo:cancel')") @PostMapping("/{id}/cancel") public AjaxResult cancel(@PathVariable Long id,@Valid @RequestBody ActionCommand c){return success(command.cancel(id,c,actor()));}
    @PreAuthorize("@ss.hasPermi('todo:submit')") @PostMapping("/{id}/attachment") public AjaxResult attachment(@PathVariable Long id,@RequestBody AttachmentCommand c){return toAjax(collaboration.addAttachment(id,c.actionId(),c.attachmentType(),c.fileName(),c.fileUrl(),SecurityUtils.getUserId()));}
    @PreAuthorize("@ss.hasPermi('todo:transfer')") @PostMapping("/{id}/candidate") public AjaxResult candidate(@PathVariable Long id,@RequestBody ParticipantCommand c){return toAjax(collaboration.addCandidate(id,c.participantType(),c.participantValue(),SecurityUtils.getUserId()));}
    @PreAuthorize("@ss.hasPermi('todo:query')") @PostMapping("/{id}/cc") public AjaxResult cc(@PathVariable Long id,@RequestBody ParticipantCommand c){return toAjax(collaboration.addCc(id,c.participantValue(),c.participantType(),SecurityUtils.getUserId()));}
    private Actor actor(){return new Actor(SecurityUtils.getUserId(),SecurityUtils.getUsername(),SecurityUtils.getDeptId());}
}
