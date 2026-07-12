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
import com.ruoyi.system.service.ISysDictTypeService;
import com.ruoyi.system.service.ISysNoticeService;
import com.ruoyi.system.service.ISysUserService;

@ExtendWith(MockitoExtension.class)
class CaseAssignmentServiceTest
{
    @Mock private BizCaseMapper mapper;
    @Mock private CaseQueryService queryService;
    @Mock private ISysDictTypeService dictTypeService;
    @Mock private ISysUserService userService;
    @Mock private ISysNoticeService noticeService;
    @Mock private BusinessEventPublisher eventPublisher;
    @InjectMocks private CaseAssignmentService service;

    @Test
    void batchAssignmentRequiresAtLeastOneCase()
    {
        ServiceException error = assertThrows(ServiceException.class, () -> service.batchAssign(new HashMap<>()));

        assertEquals("请选择案件", error.getMessage());
        assertEquals("VALIDATION_FAILED", error.getBusinessCode());
        verify(mapper, never()).updateCaseAssignment(org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void assignmentRejectsCaseThatIsNotPending()
    {
        Map<String, Object> command = new HashMap<>();
        command.put("caseId", 12L);
        when(queryService.caseDetail(12L)).thenReturn(Map.of("case_status", "processing"));

        ServiceException error = assertThrows(ServiceException.class, () -> service.assign(command));

        assertEquals("只有待分案案件可以分配，办理中案件请走转案审批", error.getMessage());
        assertEquals("STATE_CONFLICT", error.getBusinessCode());
        verify(userService, never()).selectUserById(org.mockito.ArgumentMatchers.anyLong());
        verify(mapper, never()).updateCaseAssignment(command);
    }
}
