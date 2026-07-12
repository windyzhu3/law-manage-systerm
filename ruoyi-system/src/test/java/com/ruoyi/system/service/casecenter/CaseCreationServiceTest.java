package com.ruoyi.system.service.casecenter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.business.event.BusinessEventPublisher;
import com.ruoyi.system.domain.BizContract;
import com.ruoyi.system.mapper.BizCaseMapper;
import com.ruoyi.system.service.ISysDictTypeService;
import com.ruoyi.system.service.ISysNoticeService;

@ExtendWith(MockitoExtension.class)
class CaseCreationServiceTest
{
    @Mock BizCaseMapper mapper;
    @Mock ISysDictTypeService dictionaries;
    @Mock ISysNoticeService notices;
    @Mock BusinessEventPublisher publisher;
    @InjectMocks CaseCreationService service;

    @Test void missingContractIsIgnoredWithoutSideEffects()
    {
        assertEquals(0, service.createFromContract(null));
        verify(mapper, never()).insertCase(org.mockito.ArgumentMatchers.anyMap());
    }

    @Test void existingContractCaseMakesCreationIdempotent()
    {
        BizContract contract = new BizContract();
        contract.setContractId(12L);
        when(mapper.selectCaseByContractId(12L)).thenReturn(java.util.Map.of("case_id", 30L));

        assertEquals(0, service.createFromContract(contract));
        verify(mapper, never()).insertCase(org.mockito.ArgumentMatchers.anyMap());
        verify(publisher, never()).publish(org.mockito.ArgumentMatchers.any());
    }
}
