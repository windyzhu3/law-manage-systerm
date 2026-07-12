package com.ruoyi.system.service.casecenter;

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
import com.law.business.event.BusinessEventPublisher;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.mapper.BizCaseMapper;
import com.ruoyi.system.service.ISysUserService;

@ExtendWith(MockitoExtension.class)
class CaseTransferServiceTest
{
    @Mock BizCaseMapper mapper; @Mock CaseQueryService queryService; @Mock ISysUserService users;
    @Mock CaseWorkflowSupport support; @Mock BusinessEventPublisher publisher;
    @InjectMocks CaseTransferService service;

    @Test void requestRejectsCaseOutsideProcessingState()
    {
        Map<String,Object> c=new HashMap<>();c.put("caseId",1L);
        when(queryService.caseDetail(1L)).thenReturn(Map.of("case_status","pending"));
        ServiceException e=assertThrows(ServiceException.class,()->service.request(c));
        assertEquals("STATE_CONFLICT",e.getBusinessCode()); verify(users,never()).selectUserById(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test void approvalRejectsUnsupportedActionBeforeDatabaseRead()
    {
        Map<String,Object> c=new HashMap<>();c.put("transferId",2L);c.put("action","invalid");c.put("opinion","x");
        ServiceException e=assertThrows(ServiceException.class,()->service.approve(c));
        assertEquals("VALIDATION_FAILED",e.getBusinessCode()); verify(mapper,never()).selectTransferById(2L);
    }
}
