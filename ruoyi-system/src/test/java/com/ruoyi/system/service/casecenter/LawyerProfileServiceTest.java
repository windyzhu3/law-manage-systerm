package com.ruoyi.system.service.casecenter;

import static com.ruoyi.system.support.BusinessFixtures.actor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.business.lawcase.dto.LawyerProfileSaveCommand;
import com.law.business.lawcase.dto.LawyerProfileStatusCommand;
import com.law.business.security.BusinessActorProvider;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.mapper.BizCaseMapper;
import com.ruoyi.system.service.ISysDictTypeService;
import com.ruoyi.system.service.ISysUserService;

@ExtendWith(MockitoExtension.class)
class LawyerProfileServiceTest
{
    @Mock private BizCaseMapper mapper;
    @Mock private ISysUserService users;
    @Mock private ISysDictTypeService dictionaries;
    @Mock private BusinessActorProvider actors;
    @InjectMocks private LawyerProfileService service;

    @Test
    void updateStatusRejectsInvalidAssignmentFlag()
    {
        LawyerProfileStatusCommand command = new LawyerProfileStatusCommand();
        command.setUserId(10L); command.setAssignEnabled("INVALID");

        ServiceException error = assertThrows(ServiceException.class, () -> service.updateStatus(command));

        assertEquals("VALIDATION_FAILED", error.getBusinessCode());
        verify(mapper, never()).selectLawyerProfileByUserId(10L);
    }

    @Test
    void saveRejectsUnknownLawyerBeforeDictionaryValidation()
    {
        LawyerProfileSaveCommand command = new LawyerProfileSaveCommand(); command.setUserId(10L);
        when(users.selectUserById(10L)).thenReturn(null);

        ServiceException error = assertThrows(ServiceException.class, () -> service.save(command));

        assertEquals("DATA_NOT_FOUND", error.getBusinessCode());
        verify(dictionaries, never()).selectDictDataByType(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void updateStatusRequiresSavedProfileBeforeEnablingAssignment()
    {
        LawyerProfileStatusCommand command = new LawyerProfileStatusCommand();
        command.setUserId(10L); command.setAssignEnabled("Y");
        when(mapper.selectLawyerProfileByUserId(10L)).thenReturn(Map.of("userId", 10L));

        ServiceException error = assertThrows(ServiceException.class, () -> service.updateStatus(command));

        assertEquals("PRECONDITION_FAILED", error.getBusinessCode());
        verify(mapper, never()).updateLawyerProfileStatus(anyMap());
    }

    @Test
    void saveDerivesAuditActorInsteadOfAcceptingClientAuditFields()
    {
        LawyerProfileSaveCommand command = new LawyerProfileSaveCommand();
        command.setUserId(10L); command.setLawyerRole("lawyer"); command.setSpecialties("civil");
        command.setAssignEnabled("N");
        com.ruoyi.common.core.domain.entity.SysUser user = new com.ruoyi.common.core.domain.entity.SysUser();
        user.setUserId(10L); user.setStatus("0");
        when(users.selectUserById(10L)).thenReturn(user);
        when(dictionaries.selectDictDataByType("law_lawyer_role")).thenReturn(option("lawyer"));
        when(dictionaries.selectDictDataByType("law_case_type")).thenReturn(option("civil"));
        when(mapper.selectLawyerProfileByUserId(10L)).thenReturn(Map.of("userId", 10L));
        when(actors.current()).thenReturn(actor());
        when(mapper.insertLawyerProfile(anyMap())).thenReturn(1);

        service.save(command);

        verify(mapper).insertLawyerProfile(org.mockito.ArgumentMatchers.argThat(row ->
                "alice".equals(row.get("createBy")) && "alice".equals(row.get("updateBy"))));
    }

    private java.util.List<com.ruoyi.common.core.domain.entity.SysDictData> option(String value)
    {
        com.ruoyi.common.core.domain.entity.SysDictData item = new com.ruoyi.common.core.domain.entity.SysDictData();
        item.setDictValue(value); return java.util.List.of(item);
    }
}
