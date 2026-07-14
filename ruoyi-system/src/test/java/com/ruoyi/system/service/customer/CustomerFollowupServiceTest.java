package com.ruoyi.system.service.customer;

import static com.ruoyi.system.support.BusinessFixtures.actor;
import static com.ruoyi.system.support.BusinessFixtures.customer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.business.customer.dto.CustomerFollowupCreateCommand;
import com.law.business.security.BusinessActorProvider;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.system.mapper.BizCustomerMapper;
import com.ruoyi.system.service.ISysDictTypeService;

@ExtendWith(MockitoExtension.class)
class CustomerFollowupServiceTest
{
    @Mock private BizCustomerMapper mapper;
    @Mock private CustomerAccessPolicy access;
    @Mock private BusinessActorProvider actors;
    @Mock private ISysDictTypeService dictionaries;
    private CustomerFollowupService service;

    @BeforeEach
    void setUp()
    {
        service = new CustomerFollowupService(mapper, access, actors, dictionaries);
    }

    @Test
    void accessIsCheckedBeforeFollowupIsWritten()
    {
        CustomerFollowupCreateCommand command = new CustomerFollowupCreateCommand();
        command.setCustomerId(10L);
        command.setFollowType("phone");
        command.setContent("确认委托范围");
        when(access.requireOperable(10L)).thenReturn(customer(10L, "0", "0"));
        when(actors.current()).thenReturn(actor());
        when(dictionaries.selectDictDataByType("law_customer_follow_type")).thenReturn(List.of(dict("phone")));
        when(mapper.insertFollowup(any())).thenReturn(1);
        when(mapper.touchCustomerFollowTime(any())).thenReturn(1);

        assertEquals(1, service.create(command));

        InOrder order = inOrder(access, mapper);
        order.verify(access).requireOperable(10L);
        order.verify(mapper).insertFollowup(any());
    }

    private SysDictData dict(String value)
    {
        SysDictData item = new SysDictData();
        item.setDictValue(value);
        return item;
    }
}
