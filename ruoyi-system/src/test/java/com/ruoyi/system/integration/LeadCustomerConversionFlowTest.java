package com.ruoyi.system.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.law.business.event.BusinessEventCommand;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.lead.dto.LeadFollowupCommand;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.law.business.shared.status.LeadStatus;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.system.domain.BizCustomer;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.domain.BizLeadSetting;
import com.ruoyi.system.mapper.BizCustomerMapper;
import com.ruoyi.system.mapper.BizLeadMapper;
import com.ruoyi.system.mapper.SysUserMapper;
import com.ruoyi.system.service.ISysDictTypeService;
import com.ruoyi.system.service.customer.CustomerAccessPolicy;
import com.ruoyi.system.service.customer.CustomerCommandService;
import com.ruoyi.system.service.lead.LeadAccessPolicy;
import com.ruoyi.system.service.lead.LeadAssignmentService;
import com.ruoyi.system.service.lead.LeadCommandService;
import com.ruoyi.system.service.lead.LeadConversionService;
import com.ruoyi.system.service.lead.LeadFollowupService;

class LeadCustomerConversionFlowTest
{
    @Test
    void createAssignFollowupAndConvertRemainOneIdempotentFlow()
    {
        BizLeadMapper leads = mock(BizLeadMapper.class);
        SysUserMapper users = mock(SysUserMapper.class);
        BizCustomerMapper customers = mock(BizCustomerMapper.class);
        ISysDictTypeService dictionaries = mock(ISysDictTypeService.class);
        BusinessEventPublisher events = mock(BusinessEventPublisher.class);
        BusinessActorProvider actors = mock(BusinessActorProvider.class);
        LeadAccessPolicy leadAccess = mock(LeadAccessPolicy.class);
        CustomerAccessPolicy customerAccess = mock(CustomerAccessPolicy.class);
        BusinessActor actor = new BusinessActor(8L, "alice", "Alice", 3L, false);
        when(actors.current()).thenReturn(actor);
        when(dictionaries.selectDictDataByType(any())).thenAnswer(invocation ->
                List.of(dict(defaultValue(invocation.getArgument(0)))));
        when(leads.selectSettingList(any(BizLeadSetting.class))).thenReturn(List.of(new BizLeadSetting()));

        BizLead lead = lead();
        when(leads.insertLead(lead)).thenAnswer(invocation -> { lead.setLeadId(7L); return 1; });
        when(leads.insertLeadSourceTagRelationIfAbsent(anyLong(), any(), any())).thenReturn(1);
        new LeadCommandService(leads, dictionaries, events, actors, leadAccess).create(lead);

        when(leadAccess.requireReadable(7L, false, true)).thenReturn(lead);
        when(leadAccess.requireOperable(7L)).thenReturn(lead);
        when(leads.assignLead(7L, 8L, "alice", LeadStatus.UNASSIGNED.code(), 0)).thenReturn(1);
        when(leads.insertAssignmentLog(any())).thenAnswer(invocation -> {
            invocation.<Map<String, Object>>getArgument(0).put("logId", 21L);
            return 1;
        });
        com.ruoyi.common.core.domain.entity.SysUser owner = new com.ruoyi.common.core.domain.entity.SysUser();
        owner.setDeptId(3L);
        owner.setStatus("0");
        owner.setDelFlag("0");
        when(users.selectUserById(8L)).thenReturn(owner);
        new LeadAssignmentService(leads, leadAccess, actors, events, users).assign(7L, 8L, "首次分配");
        lead.setOwnerId(8L); lead.setDeptId(3L); lead.setPoolStatus("0");
        lead.setStatus(LeadStatus.WAIT_FOLLOW.code()); lead.setRowVersion(1);

        when(leads.insertFollowup(any())).thenReturn(1);
        when(leads.touchLeadFollowTimeConditionally(anyLong(), any(), any(), any(), any())).thenReturn(1);
        LeadFollowupCommand followup = new LeadFollowupCommand();
        followup.setLeadId(7L); followup.setFollowType("phone"); followup.setFollowResult("interested");
        followup.setContent("客户确认需要合同服务");
        new LeadFollowupService(leads, leadAccess, actors, dictionaries).add(followup, true);
        lead.setRowVersion(2);

        when(customers.selectCustomerByLeadId(7L)).thenReturn(null);
        when(customers.selectDuplicateCustomerInScope(any(), any(), any(), anyLong(), anyLong(), any(), any())).thenReturn(null);
        when(customers.insertCustomer(any(BizCustomer.class))).thenAnswer(invocation -> {
            invocation.<BizCustomer>getArgument(0).setCustomerId(31L);
            return 1;
        });
        when(customers.countContactByCustomerAndMobileOrName(anyLong(), any(), any())).thenReturn(0);
        when(customers.insertContact(any())).thenReturn(1);
        when(leads.bindCustomerConditionally(7L, 31L, "alice", LeadStatus.WAIT_FOLLOW.code(), 2)).thenReturn(1);
        CustomerCommandService customerCommands = new CustomerCommandService(customers, leads, dictionaries,
                actors, customerAccess);
        LeadConversionService conversion = new LeadConversionService(leads, leadAccess, actors, events,
                customerCommands);

        assertEquals(31L, conversion.convert(7L, true));
        lead.setStatus(LeadStatus.CONVERTED.code()); lead.setCustomerId(31L);
        assertEquals(31L, conversion.convert(7L, true));

        verify(customers, times(1)).insertCustomer(any(BizCustomer.class));
        verify(customers, times(1)).insertContact(any());
        verify(leads, times(1)).bindCustomerConditionally(7L, 31L, "alice", LeadStatus.WAIT_FOLLOW.code(), 2);
        ArgumentCaptor<BusinessEventCommand> captured = ArgumentCaptor.forClass(BusinessEventCommand.class);
        verify(events, times(3)).publish(captured.capture());
        assertEquals(List.of("LEAD_CREATED:7", "LEAD_ASSIGNED:7:21", "LEAD_CONVERTED:7:31"),
                captured.getAllValues().stream().map(BusinessEventCommand::getIdempotencyKey).toList());
    }

    private BizLead lead()
    {
        BizLead lead = new BizLead();
        lead.setRowVersion(0);
        lead.setLeadName("张三咨询"); lead.setContactName("张三"); lead.setMobile("13800000000");
        lead.setSourceCode("web"); lead.setPriority("2"); lead.setLegalDemand("合同审查");
        return lead;
    }

    private SysDictData dict(String value)
    {
        SysDictData option = new SysDictData();
        option.setDictValue(value); option.setIsDefault("Y");
        return option;
    }

    private String defaultValue(String type)
    {
        return switch (type)
        {
            case "law_lead_priority", "law_customer_level" -> "2";
            case "law_lead_follow_type" -> "phone";
            case "law_customer_type" -> "personal";
            case "law_contact_relation" -> "daily";
            case "law_yes_no_flag" -> "1";
            default -> "0";
        };
    }
}
