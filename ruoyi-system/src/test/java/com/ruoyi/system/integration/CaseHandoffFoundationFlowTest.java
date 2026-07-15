package com.ruoyi.system.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import com.law.business.event.BusinessEventCommand;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.lawcase.dto.CaseAssignmentCommand;
import com.law.business.lawcase.dto.CaseConfirmCommand;
import com.law.business.lawcase.dto.CaseTransferApprovalCommand;
import com.law.business.lawcase.dto.CaseTransferCommand;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.law.business.shared.status.CaseStatus;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.system.domain.BizContract;
import com.ruoyi.system.mapper.BizCaseMapper;
import com.ruoyi.system.mapper.BizContractMapper;
import com.ruoyi.system.service.ISysDictTypeService;
import com.ruoyi.system.service.ISysNoticeService;
import com.ruoyi.system.service.ISysUserService;
import com.ruoyi.system.service.casecenter.CaseAccessPolicy;
import com.ruoyi.system.service.casecenter.CaseAssignmentService;
import com.ruoyi.system.service.casecenter.CaseConfirmContext;
import com.ruoyi.system.service.casecenter.CaseConfirmationService;
import com.ruoyi.system.service.casecenter.CaseCreationService;
import com.ruoyi.system.service.casecenter.CaseTransferContext;
import com.ruoyi.system.service.casecenter.CaseTransferService;
import com.ruoyi.system.service.casecenter.CaseWorkflowSupport;

class CaseHandoffFoundationFlowTest
{
    @Test
    void paidContractAssignmentAcceptanceAndTransferExposeStableFacts()
    {
        Fixture fixture = new Fixture();

        fixture.createCase();
        fixture.assign(12L);
        fixture.confirm(101L, true, fixture.lawyer(12L));
        fixture.requestTransfer(13L);
        fixture.approveTransfer();
        fixture.confirm(121L, true, fixture.lawyer(13L));

        assertEquals(List.of(
                "CASE_CREATED:81",
                "CASE_ASSIGNED:81:91",
                "CASE_ACCEPTED:81:101",
                "CASE_TRANSFER_REQUESTED:81:111",
                "CASE_TRANSFER_APPROVED:81:111",
                "CASE_ACCEPTED:81:121"), fixture.events.keys);
        assertEquals("processing", fixture.caseState.status);
        assertEquals(13L, fixture.caseState.mainLawyerId);
    }

    @Test
    void outboxFailureRollsBackTransferApprovalFacts()
    {
        Fixture fixture = new Fixture();
        fixture.createCase(); fixture.assign(12L); fixture.confirm(101L, true, fixture.lawyer(12L));
        fixture.requestTransfer(13L);
        int eventCount = fixture.events.keys.size();
        int logCount = fixture.actions.size();
        fixture.events.failPrefix = "CASE_TRANSFER_APPROVED";
        TransactionTemplate transaction = new TransactionTemplate(new SnapshotTransactionManager(fixture));

        assertThrows(IllegalStateException.class,
                () -> transaction.executeWithoutResult(status -> fixture.approveTransfer()));

        assertEquals("pending", fixture.transferStatus);
        assertEquals(CaseStatus.TRANSFERRING.code(), fixture.caseState.status);
        assertEquals(12L, fixture.caseState.mainLawyerId);
        assertEquals(List.of(), fixture.confirmations.keySet().stream().filter(id -> id > 101L).toList());
        assertEquals(logCount, fixture.actions.size());
        assertEquals(eventCount, fixture.events.keys.size());
    }

    private static final class Fixture
    {
        private final BizCaseMapper cases = mock(BizCaseMapper.class);
        private final BizContractMapper contracts = mock(BizContractMapper.class);
        private final ISysDictTypeService dictionaries = mock(ISysDictTypeService.class);
        private final ISysNoticeService notices = mock(ISysNoticeService.class);
        private final ISysUserService users = mock(ISysUserService.class);
        private final BusinessActorProvider actors = mock(BusinessActorProvider.class);
        private final CaseAccessPolicy access = mock(CaseAccessPolicy.class);
        private final CaseWorkflowSupport support = mock(CaseWorkflowSupport.class);
        private final CollectingPublisher events = new CollectingPublisher();
        private final MutableCase caseState = new MutableCase();
        private final Map<Long,String> confirmations = new LinkedHashMap<>();
        private final List<String> actions = new ArrayList<>();
        private String transferStatus;
        private Snapshot snapshot;
        private final CaseCreationService creation;
        private final CaseAssignmentService assignment;
        private final CaseTransferService transfer;
        private final CaseConfirmationService confirmation;

        private Fixture()
        {
            stubContract(); stubDictionaries(); stubUsers(); stubPersistence(); stubAccessAndSupport();
            creation = new CaseCreationService(cases, contracts, dictionaries, notices, events);
            assignment = new CaseAssignmentService(cases, access, dictionaries, users, notices, events, actors);
            transfer = new CaseTransferService(cases, access, users, support, events, actors);
            confirmation = new CaseConfirmationService(cases, access, support, events, actors);
        }

        private void createCase()
        {
            creation.createFromContract(10L, manager());
        }

        private void assign(Long lawyerId)
        {
            CaseAssignmentCommand command = new CaseAssignmentCommand();
            command.setCaseId(81L); command.setMainLawyerId(lawyerId); command.setAssignMethod("manual");
            command.setPriority("medium"); command.setAssignReason("normal");
            command.setEstimatedWorkload(new BigDecimal("24")); command.setNotifyFlag("Y");
            assignment.assign(command, manager());
        }

        private void confirm(Long confirmId, boolean accepted, BusinessActor actor)
        {
            CaseConfirmCommand command = new CaseConfirmCommand(); command.setConfirmId(confirmId);
            command.setConfirmResult(accepted ? "accepted" : "rejected"); command.setRemark("同意");
            confirmation.handle(command, actor);
        }

        private void requestTransfer(Long targetLawyerId)
        {
            CaseTransferCommand command = new CaseTransferCommand(); command.setCaseId(81L);
            command.setToLawyerId(targetLawyerId); command.setTransferReason("capacity");
            command.setRiskLevel("medium"); command.setDetail("调整主办律师");
            transfer.request(command, lawyer(12L));
        }

        private void approveTransfer()
        {
            CaseTransferApprovalCommand command = new CaseTransferApprovalCommand();
            command.setTransferId(111L); command.setAction("passed"); command.setOpinion("同意");
            transfer.approve(command, manager());
        }

        private void stubContract()
        {
            BizContract contract = new BizContract(); contract.setContractId(10L); contract.setContractNo("HT-10");
            contract.setContractName("法律服务合同"); contract.setCustomerId(31L); contract.setCustomerName("客户");
            contract.setCaseType("civil"); contract.setSignStatus("1"); contract.setOwnerId(8L); contract.setDeptId(3L);
            when(contracts.selectContractById(10L)).thenReturn(contract);
            when(contracts.countConfirmedFeePlans(10L)).thenReturn(1);
        }

        private void stubDictionaries()
        {
            when(dictionaries.selectDictDataByType(anyString())).thenAnswer(invocation -> {
                String type = invocation.getArgument(0);
                return switch (type)
                {
                    case "law_case_type" -> options("civil");
                    case "law_case_urgency" -> defaultOption("normal");
                    case "law_case_priority" -> defaultOption("medium");
                    case "law_case_assign_method" -> options("manual");
                    case "law_case_assign_reason" -> options("normal");
                    case "law_case_status_action" -> options("assign");
                    default -> List.of();
                };
            });
        }

        private void stubUsers()
        {
            when(users.selectUserById(anyLong())).thenAnswer(invocation -> {
                Long id = invocation.getArgument(0); SysUser user = new SysUser(); user.setUserId(id);
                user.setNickName(id == 12L ? "律师甲" : "律师乙"); user.setStatus("0"); return user;
            });
            when(cases.selectLawyerProfileByUserId(anyLong())).thenAnswer(invocation -> {
                Long id = invocation.getArgument(0);
                return Map.of("profileId", id, "assignEnabled", "Y", "lawyerRole", "lawyer");
            });
        }

        private void stubPersistence()
        {
            when(cases.selectCaseByContractId(10L)).thenAnswer(invocation -> caseState.id == null ? null : caseMap());
            when(cases.insertCase(anyMap())).thenAnswer(invocation -> {
                Map<String,Object> row = invocation.getArgument(0); row.put("caseId", 81L);
                caseState.id = 81L; caseState.status = String.valueOf(row.get("caseStatus"));
                caseState.mainLawyerId = null; caseState.mainLawyerName = null; return 1;
            });
            when(cases.insertStatusLog(anyMap())).thenAnswer(invocation -> {
                actions.add(String.valueOf(invocation.<Map<String,Object>>getArgument(0).get("actionType"))); return 1;
            });
            when(cases.updateCaseAssignment(anyMap())).thenAnswer(invocation -> {
                Map<String,Object> row = invocation.getArgument(0);
                if (!"pending".equals(caseState.status)) return 0;
                caseState.status = String.valueOf(row.get("caseStatus"));
                caseState.mainLawyerId = ((Number) row.get("mainLawyerId")).longValue();
                caseState.mainLawyerName = String.valueOf(row.get("mainLawyerName")); return 1;
            });
            when(cases.insertAssignment(anyMap())).thenAnswer(invocation -> {
                invocation.<Map<String,Object>>getArgument(0).put("assignmentId", 91L); return 1;
            });
            when(cases.insertConfirm(anyMap())).thenAnswer(invocation -> {
                Map<String,Object> row = invocation.getArgument(0); long id = confirmations.isEmpty() ? 101L : 121L;
                row.put("confirmId", id); confirmations.put(id, "pending"); return 1;
            });
            when(cases.updateConfirm(anyMap())).thenAnswer(invocation -> {
                Map<String,Object> row = invocation.getArgument(0); Long id = ((Number) row.get("confirmId")).longValue();
                if (!String.valueOf(row.get("expectedStatus")).equals(confirmations.get(id))) return 0;
                confirmations.put(id, String.valueOf(row.get("confirmResult"))); return 1;
            });
            when(cases.updateCaseConfirmResult(anyMap())).thenAnswer(invocation -> updateCase(invocation.getArgument(0)));
            when(cases.insertTransfer(anyMap())).thenAnswer(invocation -> {
                invocation.<Map<String,Object>>getArgument(0).put("transferId", 111L); transferStatus = "pending"; return 1;
            });
            when(cases.updateTransferApproval(anyMap())).thenAnswer(invocation -> {
                Map<String,Object> row = invocation.getArgument(0);
                if (!String.valueOf(row.get("expectedStatus")).equals(transferStatus)) return 0;
                transferStatus = String.valueOf(row.get("transferStatus")); return 1;
            });
            when(cases.updateCaseStatus(anyMap())).thenAnswer(invocation -> updateCase(invocation.getArgument(0)));
        }

        private void stubAccessAndSupport()
        {
            when(access.requireAssignable(anyLong(), any())).thenAnswer(invocation -> caseMap());
            when(access.requireTransferRequestable(anyLong(), any())).thenAnswer(invocation -> caseMap());
            when(access.requireTransferApprovable(anyLong(), any())).thenAnswer(invocation -> new CaseTransferContext(
                    111L, 81L, "CSHT-10", "TR-111", transferStatus, 12L, "律师甲", 13L, "律师乙"));
            when(access.requireConfirmable(anyLong(), any())).thenAnswer(invocation -> {
                Long id = invocation.getArgument(0); Long userId = id == 101L ? 12L : 13L;
                return new CaseConfirmContext(id, 81L, "CSHT-10", confirmations.get(id), userId,
                        userId == 12L ? "律师甲" : "律师乙", id == 101L ? "accept" : "transfer_accept");
            });
            doAnswer(invocation -> { String action = invocation.getArgument(3); actions.add(action); return null; })
                    .when(support).statusLog(anyLong(), anyString(), anyString(), anyString(), anyString(), any());
        }

        private int updateCase(Map<String,Object> row)
        {
            if (!String.valueOf(row.get("expectedStatus")).equals(caseState.status)) return 0;
            caseState.status = String.valueOf(row.get("caseStatus"));
            if (Boolean.TRUE.equals(row.get("clearAssignment")))
            {
                caseState.mainLawyerId = null; caseState.mainLawyerName = null;
            }
            if (row.get("mainLawyerId") != null)
            {
                caseState.mainLawyerId = ((Number) row.get("mainLawyerId")).longValue();
                caseState.mainLawyerName = String.valueOf(row.get("mainLawyerName"));
            }
            return 1;
        }

        private Map<String,Object> caseMap()
        {
            Map<String,Object> row = new HashMap<>(); row.put("case_id", 81L); row.put("case_no", "CSHT-10");
            row.put("case_status", caseState.status); row.put("main_lawyer_id", caseState.mainLawyerId);
            row.put("main_lawyer_name", caseState.mainLawyerName); row.put("owner_id", 8L); return row;
        }

        private void snapshot()
        {
            snapshot = new Snapshot(caseState.status, caseState.mainLawyerId, caseState.mainLawyerName,
                    transferStatus, new LinkedHashMap<>(confirmations), actions.size(), events.keys.size());
        }

        private void rollback()
        {
            caseState.status = snapshot.caseStatus; caseState.mainLawyerId = snapshot.mainLawyerId;
            caseState.mainLawyerName = snapshot.mainLawyerName; transferStatus = snapshot.transferStatus;
            confirmations.clear(); confirmations.putAll(snapshot.confirmations);
            trim(actions, snapshot.actionCount); trim(events.keys, snapshot.eventCount);
        }

        private void trim(List<?> values, int size)
        {
            while (values.size() > size) values.remove(values.size() - 1);
        }

        private BusinessActor manager() { return new BusinessActor(8L, "manager", "案管", 3L, false); }
        private BusinessActor lawyer(Long id) { return new BusinessActor(id, "lawyer" + id, "律师" + id, 3L, false); }
        private List<SysDictData> options(String value) { return List.of(option(value, false)); }
        private List<SysDictData> defaultOption(String value) { return List.of(option(value, true)); }
        private SysDictData option(String value, boolean selected)
        {
            SysDictData item = new SysDictData(); item.setDictValue(value); item.setIsDefault(selected ? "Y" : "N"); return item;
        }
    }

    private static final class MutableCase
    {
        private Long id; private String status; private Long mainLawyerId; private String mainLawyerName;
    }

    private record Snapshot(String caseStatus, Long mainLawyerId, String mainLawyerName,
            String transferStatus, Map<Long,String> confirmations, int actionCount, int eventCount) { }

    private static final class CollectingPublisher implements BusinessEventPublisher
    {
        private final List<String> keys = new ArrayList<>();
        private String failPrefix;

        @Override
        public void publish(BusinessEventCommand command)
        {
            if (failPrefix != null && command.getIdempotencyKey().startsWith(failPrefix))
                throw new IllegalStateException("outbox unavailable");
            keys.add(command.getIdempotencyKey());
        }
    }

    private static final class SnapshotTransactionManager extends AbstractPlatformTransactionManager
    {
        private final Fixture fixture;
        private SnapshotTransactionManager(Fixture fixture) { this.fixture = fixture; }
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { fixture.snapshot(); }
        @Override protected void doCommit(DefaultTransactionStatus status) { }
        @Override protected void doRollback(DefaultTransactionStatus status) { fixture.rollback(); }
    }
}
