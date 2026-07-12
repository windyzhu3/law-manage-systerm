package com.ruoyi.system.service.matter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.mapper.BizMatterMapper;
import com.ruoyi.system.service.ISysDictTypeService;

@ExtendWith(MockitoExtension.class)
class MatterSubdomainGuardTest
{
    @Mock BizMatterMapper mapper;
    @Mock ISysDictTypeService dictionaries;

    @Test void closedMatterRejectsProgressCreation()
    {
        Map<String, Object> command = command(11L);
        when(mapper.selectMatterById(11L)).thenReturn(Map.of("case_status", "closed"));
        try (MockedStatic<SecurityUtils> security = admin())
        {
            ServiceException error = assertThrows(ServiceException.class,
                    () -> new MatterProgressService(mapper).create(command));
            assertEquals("STATE_CONFLICT", error.getBusinessCode());
        }
        verify(mapper, never()).insertProgress(command);
    }

    @Test void closedMatterRejectsDocumentCreation()
    {
        Map<String, Object> command = command(12L);
        when(mapper.selectMatterById(12L)).thenReturn(Map.of("case_status", "closed"));
        try (MockedStatic<SecurityUtils> security = admin())
        {
            ServiceException error = assertThrows(ServiceException.class,
                    () -> new MatterDocumentService(mapper, dictionaries).create(command));
            assertEquals("STATE_CONFLICT", error.getBusinessCode());
        }
        verify(mapper, never()).insertDocument(command);
    }

    @Test void closedMatterRejectsExpenseCreation()
    {
        Map<String, Object> command = command(13L);
        when(mapper.selectMatterById(13L)).thenReturn(Map.of("case_status", "closed"));
        try (MockedStatic<SecurityUtils> security = admin())
        {
            ServiceException error = assertThrows(ServiceException.class,
                    () -> new MatterExpenseService(mapper, dictionaries).create(command));
            assertEquals("STATE_CONFLICT", error.getBusinessCode());
        }
        verify(mapper, never()).insertExpense(command);
    }

    private Map<String, Object> command(Long caseId)
    {
        Map<String, Object> value = new HashMap<>();
        value.put("caseId", caseId);
        return value;
    }

    private MockedStatic<SecurityUtils> admin()
    {
        MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class);
        security.when(SecurityUtils::isAdmin).thenReturn(true);
        return security;
    }
}
