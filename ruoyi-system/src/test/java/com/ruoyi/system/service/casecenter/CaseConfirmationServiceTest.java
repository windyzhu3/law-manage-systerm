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

@ExtendWith(MockitoExtension.class)
class CaseConfirmationServiceTest
{
    @Mock BizCaseMapper mapper; @Mock CaseQueryService queryService;
    @Mock CaseWorkflowSupport support; @Mock BusinessEventPublisher publisher;
    @InjectMocks CaseConfirmationService service;

    @Test void rejectsUnsupportedResultBeforeDatabaseRead()
    {
        Map<String,Object> c=new HashMap<>();c.put("confirmId",3L);c.put("confirmResult","unknown");
        ServiceException e=assertThrows(ServiceException.class,()->service.handle(c));
        assertEquals("VALIDATION_FAILED",e.getBusinessCode()); verify(mapper,never()).selectConfirmById(3L);
    }

    @Test void missingConfirmationReturnsStableBusinessCode()
    {
        Map<String,Object> c=new HashMap<>();c.put("confirmId",3L);c.put("confirmResult","accepted");
        when(mapper.selectConfirmById(3L)).thenReturn(null);
        ServiceException e=assertThrows(ServiceException.class,()->service.handle(c));
        assertEquals("DATA_NOT_FOUND",e.getBusinessCode()); verify(mapper,never()).updateConfirm(org.mockito.ArgumentMatchers.anyMap());
    }
}
