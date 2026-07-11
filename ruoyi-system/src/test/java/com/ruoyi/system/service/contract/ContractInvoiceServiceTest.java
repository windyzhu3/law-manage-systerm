package com.ruoyi.system.service.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
class ContractInvoiceServiceTest
{
    @Mock BizContractMapper mapper; @Mock ContractQueryService queryService;
    @Mock ISysDictTypeService dictionaries; @Mock BusinessEventPublisher publisher;
    @InjectMocks ContractInvoiceService service;

    @Test void rejectsInvoiceBeforePaymentConfirmation()
    {
        when(mapper.selectFeePlanById(1L)).thenReturn(Map.of("contract_id",2L,"contractStatus","3","confirm_status","pending","invoice_status","none"));
        ServiceException e=assertThrows(ServiceException.class,()->service.invoice(1L,"invoiced",null,null));
        assertEquals("STATE_CONFLICT",e.getBusinessCode()); verify(mapper,never()).updateFeePlanStatus(org.mockito.ArgumentMatchers.anyMap());
    }

    @Test void partialInvoiceCanOnlyAdvanceToCompleted()
    {
        when(mapper.selectFeePlanById(1L)).thenReturn(Map.of("contract_id",2L,"contractStatus","3","confirm_status","confirmed","invoice_status","partial"));
        when(dictionaries.selectDictDataByType("law_contract_invoice_status")).thenReturn(java.util.List.of(dict("partial")));
        ServiceException e=assertThrows(ServiceException.class,()->service.invoice(1L,"partial",null,null));
        assertEquals("STATE_CONFLICT",e.getBusinessCode()); verify(mapper,never()).updateFeePlanStatus(org.mockito.ArgumentMatchers.anyMap());
    }

    private com.ruoyi.common.core.domain.entity.SysDictData dict(String value){com.ruoyi.common.core.domain.entity.SysDictData d=new com.ruoyi.common.core.domain.entity.SysDictData();d.setDictValue(value);return d;}
}
