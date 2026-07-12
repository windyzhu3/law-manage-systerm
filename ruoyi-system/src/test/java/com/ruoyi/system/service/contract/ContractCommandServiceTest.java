package com.ruoyi.system.service.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.mapper.BizContractMapper;
import com.ruoyi.system.mapper.BizCustomerMapper;
import com.ruoyi.system.service.ISysDictTypeService;

@ExtendWith(MockitoExtension.class)
class ContractCommandServiceTest
{
    @Mock BizContractMapper mapper;
    @Mock BizCustomerMapper customers;
    @Mock ContractQueryService queryService;
    @Mock ContractNumberService numberService;
    @Mock ISysDictTypeService dictionaries;
    @InjectMocks ContractCommandService service;

    @Test void createRejectsMissingContractBeforeDatabaseAccess()
    {
        ServiceException error = assertThrows(ServiceException.class, () -> service.create(null));
        assertEquals("VALIDATION_FAILED", error.getBusinessCode());
        verify(mapper, never()).insertContract(org.mockito.ArgumentMatchers.any());
    }

    @Test void deleteRequiresAtLeastOneContract()
    {
        ServiceException error = assertThrows(ServiceException.class, () -> service.delete(new Long[0]));
        assertEquals("VALIDATION_FAILED", error.getBusinessCode());
        verify(mapper, never()).deleteContractByIds(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }
}
