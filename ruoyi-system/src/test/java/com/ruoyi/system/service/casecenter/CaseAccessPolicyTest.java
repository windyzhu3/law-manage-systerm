package com.ruoyi.system.service.casecenter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.law.business.security.CasePermissions;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.mapper.BizCaseMapper;

@ExtendWith(MockitoExtension.class)
class CaseAccessPolicyTest
{
    @Mock private BizCaseMapper mapper;
    @Mock private BusinessActorProvider actors;
    private CaseAccessPolicy policy;

    @BeforeEach
    void setUp()
    {
        policy = new CaseAccessPolicy(mapper, actors);
    }

    @Test
    void onlyAssignedLawyerOrOwnerCanRequestTransfer()
    {
        when(mapper.selectCaseById(8L)).thenReturn(Map.of(
                "case_id", 8L, "case_status", "processing",
                "owner_id", 3L, "main_lawyer_id", 12L));
        when(mapper.countCaseInDataScope(8L, 99L, 4L, true, CasePermissions.DATA_SCOPE))
                .thenReturn(1);

        ServiceException error = assertThrows(ServiceException.class,
                () -> policy.requireTransferRequestable(8L,
                        new BusinessActor(99L, "other", "other", 4L, false)));

        assertEquals("ACCESS_DENIED", error.getBusinessCode());
    }

    @Test
    void ownerCanRequestTransferForProcessingCase()
    {
        BusinessActor owner = new BusinessActor(3L, "owner", "Owner", 4L, false);
        when(mapper.selectCaseById(8L)).thenReturn(Map.of(
                "case_id", 8L, "case_status", "processing",
                "owner_id", 3L, "main_lawyer_id", 12L));
        when(mapper.countCaseInDataScope(8L, 3L, 4L, true, CasePermissions.DATA_SCOPE))
                .thenReturn(1);

        assertEquals(8L, policy.requireTransferRequestable(8L, owner).get("case_id"));
    }

    @Test
    void onlyNamedRecipientCanConfirm()
    {
        when(mapper.selectConfirmById(31L)).thenReturn(Map.of(
                "confirm_id", 31L, "confirm_status", "pending", "confirm_user_id", 12L,
                "case_id", 8L, "caseStatus", "confirming", "caseNo", "CS-8"));
        when(mapper.selectCaseById(8L)).thenReturn(Map.of(
                "case_id", 8L, "case_no", "CS-8", "case_status", "confirming"));
        when(mapper.countCaseInDataScope(8L, 13L, 4L, true, CasePermissions.DATA_SCOPE))
                .thenReturn(1);

        ServiceException error = assertThrows(ServiceException.class,
                () -> policy.requireConfirmable(31L,
                        new BusinessActor(13L, "bob", "Bob", 4L, false)));

        assertEquals("ACCESS_DENIED", error.getBusinessCode());
    }

    @Test
    void namedRecipientReceivesTypedConfirmationContext()
    {
        when(mapper.selectConfirmById(31L)).thenReturn(Map.of(
                "confirm_id", 31L, "confirm_status", "pending", "confirm_user_id", 12L,
                "confirm_user_name", "Alice", "confirm_type", "accept",
                "case_id", 8L, "caseStatus", "confirming", "caseNo", "CS-8"));
        when(mapper.selectCaseById(8L)).thenReturn(Map.of(
                "case_id", 8L, "case_no", "CS-8", "case_status", "confirming"));
        when(mapper.countCaseInDataScope(8L, 12L, 4L, true, CasePermissions.DATA_SCOPE))
                .thenReturn(1);

        CaseConfirmContext context = policy.requireConfirmable(31L,
                new BusinessActor(12L, "alice", "Alice", 4L, false));

        assertEquals(8L, context.caseId());
        assertEquals("CS-8", context.caseNo());
        assertEquals(12L, context.confirmUserId());
    }
}
