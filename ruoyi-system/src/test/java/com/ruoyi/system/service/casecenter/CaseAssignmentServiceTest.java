package com.ruoyi.system.service.casecenter;

import static com.ruoyi.system.support.BusinessFixtures.actor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.lawcase.dto.CaseAssignmentCommand;
import com.law.business.lawcase.dto.CaseBatchAssignmentCommand;
import com.law.business.security.BusinessActorProvider;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.mapper.BizCaseMapper;
import com.ruoyi.system.service.ISysDictTypeService;
import com.ruoyi.system.service.ISysNoticeService;
import com.ruoyi.system.service.ISysUserService;

@ExtendWith(MockitoExtension.class)
class CaseAssignmentServiceTest
{
    @Mock private BizCaseMapper mapper;
    @Mock private CaseAccessPolicy access;
    @Mock private ISysDictTypeService dictionaries;
    @Mock private ISysUserService users;
    @Mock private ISysNoticeService notices;
    @Mock private BusinessEventPublisher events;
    @Mock private BusinessActorProvider actors;
    @InjectMocks private CaseAssignmentService service;

    @Test
    void batchAssignmentRequiresAtLeastOneCase()
    {
        CaseBatchAssignmentCommand command = new CaseBatchAssignmentCommand();

        ServiceException error = assertThrows(ServiceException.class, () -> service.batchAssign(command));

        assertEquals("请选择案件", error.getMessage());
        assertEquals("VALIDATION_FAILED", error.getBusinessCode());
        verify(mapper, never()).updateCaseAssignment(anyMap());
    }

    @Test
    void assignmentPublishesGeneratedAssignmentAndConfirmationIds()
    {
        CaseAssignmentCommand command = command();
        when(actors.current()).thenReturn(actor());
        when(access.requireAssignable(8L, actor())).thenReturn(Map.of(
                "case_id", 8L, "case_no", "CS-8", "case_status", "pending"));
        SysUser lawyer = new SysUser(); lawyer.setUserId(12L); lawyer.setNickName("律师甲"); lawyer.setStatus("0");
        when(users.selectUserById(12L)).thenReturn(lawyer);
        when(mapper.selectLawyerProfileByUserId(12L)).thenReturn(Map.of(
                "profileId", 22L, "assignEnabled", "Y", "lawyerRole", "lawyer"));
        when(dictionaries.selectDictDataByType("law_case_assign_method")).thenReturn(options("manual"));
        when(dictionaries.selectDictDataByType("law_case_priority")).thenReturn(options("medium"));
        when(dictionaries.selectDictDataByType("law_case_assign_reason")).thenReturn(options("normal"));
        when(dictionaries.selectDictDataByType("law_case_status_action")).thenReturn(options("assign"));
        when(mapper.updateCaseAssignment(anyMap())).thenReturn(1);
        when(mapper.insertAssignment(anyMap())).thenAnswer(invocation -> {
            invocation.<Map<String,Object>>getArgument(0).put("assignmentId", 41L);
            return 1;
        });
        when(mapper.insertConfirm(anyMap())).thenAnswer(invocation -> {
            invocation.<Map<String,Object>>getArgument(0).put("confirmId", 51L);
            return 1;
        });
        when(mapper.insertStatusLog(anyMap())).thenReturn(1);

        assertEquals(1, service.assign(command));

        verify(events).publish(org.mockito.ArgumentMatchers.argThat(event ->
                "CASE_ASSIGNED:8:41".equals(event.getIdempotencyKey())
                && Long.valueOf(41L).equals(event.getPayload().get("assignmentId"))
                && Long.valueOf(51L).equals(event.getPayload().get("confirmId"))));
    }

    private CaseAssignmentCommand command()
    {
        CaseAssignmentCommand value = new CaseAssignmentCommand();
        value.setCaseId(8L); value.setMainLawyerId(12L); value.setAssignMethod("manual");
        value.setPriority("medium"); value.setAssignReason("normal");
        value.setEstimatedWorkload(new BigDecimal("24")); value.setNotifyFlag("Y");
        return value;
    }

    private List<SysDictData> options(String value)
    {
        SysDictData item = new SysDictData(); item.setDictValue(value); item.setStatus("0");
        return List.of(item);
    }
}
