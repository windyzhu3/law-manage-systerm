package com.ruoyi.system.service.contract;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import com.ruoyi.system.domain.BizContract;
import com.ruoyi.system.mapper.BizContractMapper;

class ContractQueryServiceTest
{
    @Test
    void detailDelegatesToContractAccessPolicy()
    {
        BizContractMapper mapper = mock(BizContractMapper.class);
        ContractAccessPolicy accessPolicy = mock(ContractAccessPolicy.class);
        BizContract expected = new BizContract();
        when(accessPolicy.requireReadable(9L)).thenReturn(expected);

        BizContract actual = new ContractQueryService(mapper, accessPolicy).contract(9L);

        assertSame(expected, actual);
        verify(accessPolicy).requireReadable(9L);
    }
}
