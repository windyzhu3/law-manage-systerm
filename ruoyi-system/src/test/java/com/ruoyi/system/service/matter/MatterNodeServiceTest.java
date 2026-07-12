package com.ruoyi.system.service.matter;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
class MatterNodeServiceTest
{
    @Mock BizMatterMapper mapper;
    @Mock ISysDictTypeService dictionaries;

    @Test void nonProcessingMatterCannotCreateNode()
    {
        when(mapper.selectMatterById(8L)).thenReturn(Map.of("case_status", "closed"));
        Map<String, Object> command = new HashMap<>();
        command.put("caseId", 8L);
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class))
        {
            security.when(SecurityUtils::isAdmin).thenReturn(true);
            ServiceException error = assertThrows(ServiceException.class,
                    () -> new MatterNodeService(mapper, dictionaries).create(command));
            assertEquals("STATE_CONFLICT", error.getBusinessCode());
        }
        verify(mapper, never()).insertNode(command);
    }
}
