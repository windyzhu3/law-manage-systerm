package com.ruoyi.system.service.casecenter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.mapper.BizCaseMapper;
import com.ruoyi.system.service.ISysDictTypeService;
import com.ruoyi.system.service.ISysUserService;

@ExtendWith(MockitoExtension.class)
class LawyerProfileServiceTest
{
    @Mock private BizCaseMapper mapper;
    @Mock private ISysUserService userService;
    @Mock private ISysDictTypeService dictService;
    private LawyerProfileService service;

    @BeforeEach
    void setUp()
    {
        service = new LawyerProfileService(mapper, userService, dictService);
    }

    @Test
    void updateStatusRejectsInvalidAssignmentFlag()
    {
        Map<String, Object> command = new HashMap<>();
        command.put("userId", 10L);
        command.put("assignEnabled", "INVALID");

        ServiceException error = assertThrows(ServiceException.class, () -> service.updateStatus(command));

        assertEquals("VALIDATION_FAILED", error.getBusinessCode());
        verify(mapper, never()).selectLawyerProfileByUserId(10L);
    }

    @Test
    void saveRejectsUnknownLawyerBeforeDictionaryValidation()
    {
        Map<String, Object> command = new HashMap<>();
        command.put("userId", 10L);
        when(userService.selectUserById(10L)).thenReturn(null);

        ServiceException error = assertThrows(ServiceException.class, () -> service.save(command));

        assertEquals("DATA_NOT_FOUND", error.getBusinessCode());
        verify(dictService, never()).selectDictDataByType(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void updateStatusRequiresSavedProfileBeforeEnablingAssignment()
    {
        Map<String, Object> command = new HashMap<>();
        command.put("userId", 10L);
        command.put("assignEnabled", "Y");
        when(mapper.selectLawyerProfileByUserId(10L)).thenReturn(Map.of("userId", 10L));

        ServiceException error = assertThrows(ServiceException.class, () -> service.updateStatus(command));

        assertEquals("PRECONDITION_FAILED", error.getBusinessCode());
        verify(mapper, never()).updateLawyerProfileStatus(command);
    }
}
