package com.ruoyi.system.service.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.business.event.BusinessEventPublisher;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.mapper.BizContractMapper;
import com.ruoyi.system.service.ISysDictTypeService;

@ExtendWith(MockitoExtension.class)
class ContractPaymentServiceTest
{
    @Mock BizContractMapper mapper;@Mock ContractQueryService queryService;@Mock ISysDictTypeService dictionaries;@Mock BusinessEventPublisher publisher;
    @InjectMocks ContractPaymentService service;
    @Test void fullyPaidPlanCannotBeConfirmedAgain(){when(mapper.selectFeePlanById(1L)).thenReturn(plan("confirmed",100,100));ServiceException e=assertThrows(ServiceException.class,()->service.confirm(1L,"10",null,null));assertEquals("STATE_CONFLICT",e.getBusinessCode());verify(mapper,never()).updateFeePlanStatus(org.mockito.ArgumentMatchers.anyMap());}
    @Test void rejectedPlanCannotBeRejectedAgain(){when(mapper.selectFeePlanById(1L)).thenReturn(plan("rejected",100,0));ServiceException e=assertThrows(ServiceException.class,()->service.reject(1L,"again"));assertEquals("STATE_CONFLICT",e.getBusinessCode());verify(mapper,never()).updateFeePlanStatus(org.mockito.ArgumentMatchers.anyMap());}
    private Map<String,Object> plan(String status,int due,int paid){Map<String,Object> p=new HashMap<>();p.put("contract_id",2L);p.put("contractStatus","3");p.put("confirm_status",status);p.put("invoice_status","none");p.put("receivable_amount",BigDecimal.valueOf(due));p.put("received_amount",BigDecimal.valueOf(paid));return p;}
}
