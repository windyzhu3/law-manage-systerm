package com.ruoyi.web.controller.system;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.system.domain.BusinessEventRecord;
import com.ruoyi.system.service.event.BusinessEventOutboxAdminService;

@RestController
@RequestMapping("/system/business-event")
public class BusinessEventOutboxController extends BaseController
{
    private final BusinessEventOutboxAdminService service;

    public BusinessEventOutboxController(BusinessEventOutboxAdminService service)
    {
        this.service = service;
    }

    @PreAuthorize("@ss.hasPermi('system:business-event:list')")
    @GetMapping("/list")
    public TableDataInfo list(BusinessEventRecord query)
    {
        startPage();
        List<BusinessEventRecord> events = service.list(query);
        return getDataTable(events);
    }

    @PreAuthorize("@ss.hasPermi('system:business-event:requeue')")
    @Log(title = "业务事件Outbox", businessType = BusinessType.UPDATE)
    @PostMapping("/{eventId}/requeue")
    public AjaxResult requeue(@PathVariable Long eventId)
    {
        return service.requeueDead(eventId) ? success() : error("仅允许重放DEAD状态事件");
    }
}
