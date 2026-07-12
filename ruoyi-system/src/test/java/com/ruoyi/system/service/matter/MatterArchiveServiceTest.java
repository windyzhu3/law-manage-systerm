package com.ruoyi.system.service.matter;

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
class MatterArchiveServiceTest
{
    @Mock BizMatterMapper mapper;
    @Mock ISysDictTypeService dictionaries;

    @Test void unfinishedNodesBlockCloseApplication()
    {
        when(mapper.selectMatterById(9L)).thenReturn(Map.of("case_status", "processing"));
        when(mapper.countUnfinishedNodes(9L)).thenReturn(2);
        Map<String, Object> command = new HashMap<>();
        command.put("caseId", 9L);
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class))
        {
            security.when(SecurityUtils::isAdmin).thenReturn(true);
            assertThrows(ServiceException.class,
                    () -> new MatterArchiveService(mapper, dictionaries).apply(command));
        }
        verify(mapper, never()).insertArchive(command);
    }
}
