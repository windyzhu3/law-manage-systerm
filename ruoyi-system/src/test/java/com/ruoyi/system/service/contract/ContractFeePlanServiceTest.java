package com.ruoyi.system.service.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.mapper.BizContractMapper;

@ExtendWith(MockitoExtension.class)
class ContractFeePlanServiceTest
{
    @Mock BizContractMapper mapper;@Mock ContractQueryService queryService;@Mock ContractActionLogService actionLogs;@InjectMocks ContractFeePlanService service;
    @Test void confirmedPlanCannotBeEdited(){Map<String,Object> p=plan("1","0","3");when(mapper.selectFeePlanById(1L)).thenReturn(p);Map<String,Object> c=new HashMap<>();c.put("planId",1L);ServiceException e=assertThrows(ServiceException.class,()->service.update(c));assertEquals("STATE_CONFLICT",e.getBusinessCode());verify(mapper,never()).updateFeePlan(c);}
    @Test void invoicedPlanCannotBeDeleted(){Map<String,Object> p=plan("0","1","3");when(mapper.selectFeePlanById(1L)).thenReturn(p);ServiceException e=assertThrows(ServiceException.class,()->service.delete(1L));assertEquals("STATE_CONFLICT",e.getBusinessCode());verify(mapper,never()).deleteFeePlan(org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());}
    private Map<String,Object> plan(String receive,String invoice,String contract){Map<String,Object> p=new HashMap<>();p.put("contract_id",2L);p.put("confirm_status",receive);p.put("invoice_status",invoice);p.put("contractStatus",contract);return p;}
}
