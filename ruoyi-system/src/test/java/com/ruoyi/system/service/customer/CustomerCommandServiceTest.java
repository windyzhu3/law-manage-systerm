package com.ruoyi.system.service.customer;

import static com.ruoyi.system.support.BusinessFixtures.actor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.business.security.BusinessActorProvider;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.BizCustomer;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.domain.BizLeadSetting;
import com.ruoyi.system.mapper.BizCustomerMapper;
import com.ruoyi.system.mapper.BizLeadMapper;
import com.ruoyi.system.service.ISysDictTypeService;

@ExtendWith(MockitoExtension.class)
class CustomerCommandServiceTest
{
    @Mock private BizCustomerMapper mapper;
    @Mock private BizLeadMapper leads;
    @Mock private ISysDictTypeService dictionaries;
    @Mock private BusinessActorProvider actors;
    @Mock private CustomerAccessPolicy access;
    private CustomerCommandService service;

    @BeforeEach
    void setUp()
    {
        service = new CustomerCommandService(mapper, leads, dictionaries, actors, access);
    }

    @Test
    void duplicateMobileIsRejected()
    {
        BizCustomer input = customer("13800000000", null, "张三");
        when(actors.current()).thenReturn(actor());
        when(mapper.selectDuplicateCustomerInScope(eq("13800000000"), isNull(), eq("张三"),
                eq(8L), eq(3L), eq(true), anyString()))
                .thenReturn(customer("13800000000", null, "既有客户"));

        ServiceException exception = assertThrows(ServiceException.class, () -> service.create(input));

        assertEquals("PRECONDITION_FAILED", exception.getBusinessCode());
        verify(mapper, never()).insertCustomer(any());
    }

    @Test
    void createFromLeadCreatesPrimaryContactWithoutUpdatingLead()
    {
        BizLead lead = lead();
        when(actors.current()).thenReturn(actor());
        when(mapper.selectCustomerByLeadId(7L)).thenReturn(null);
        when(mapper.selectDuplicateCustomerInScope(anyString(), isNull(), anyString(),
                anyLong(), anyLong(), anyBoolean(), anyString())).thenReturn(null);
        when(dictionaries.selectDictDataByType(anyString())).thenAnswer(invocation ->
                List.of(dict(preferredValue(invocation.getArgument(0)))));
        when(leads.selectSettingList(any(BizLeadSetting.class))).thenReturn(List.of(new BizLeadSetting()));
        when(mapper.insertCustomer(any())).thenAnswer(invocation -> {
            ((BizCustomer) invocation.getArgument(0)).setCustomerId(31L);
            return 1;
        });
        when(mapper.countContactByCustomerAndMobileOrName(31L, "13800000000", "张三")).thenReturn(0);
        when(mapper.insertContact(any())).thenReturn(1);

        assertEquals(31L, service.createFromLead(lead));

        verify(mapper).insertContact(argThat((Map<String, Object> contact) ->
                "1".equals(contact.get("keyContact")) && Long.valueOf(31L).equals(contact.get("customerId"))));
        verify(leads, never()).bindCustomerConditionally(any(), any(), any(), any());
    }

    private BizCustomer customer(String mobile, String creditCode, String name)
    {
        BizCustomer value = new BizCustomer();
        value.setMobile(mobile);
        value.setCreditCode(creditCode);
        value.setCustomerName(name);
        return value;
    }

    private BizLead lead()
    {
        BizLead value = new BizLead();
        value.setLeadId(7L);
        value.setLeadName("张三咨询");
        value.setContactName("张三");
        value.setMobile("13800000000");
        value.setSourceCode("web");
        value.setPriority("2");
        value.setLegalDemand("合同纠纷");
        value.setOwnerId(8L);
        value.setDeptId(3L);
        return value;
    }

    private SysDictData dict(String value)
    {
        SysDictData item = new SysDictData();
        item.setDictValue(value);
        return item;
    }

    private String preferredValue(String type)
    {
        return switch (type)
        {
            case "law_customer_type" -> "personal";
            case "law_customer_level" -> "2";
            case "law_contact_relation" -> "daily";
            case "law_yes_no_flag" -> "1";
            default -> "0";
        };
    }
}
