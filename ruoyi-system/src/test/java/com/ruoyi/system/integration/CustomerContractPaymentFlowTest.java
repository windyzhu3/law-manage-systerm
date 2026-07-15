package com.ruoyi.system.integration;

import static com.ruoyi.system.support.BusinessFixtures.actor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import com.law.business.contract.dto.ContractCreateCommand;
import com.law.business.event.BusinessEventCommand;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.security.BusinessActorProvider;
import com.law.business.shared.status.ContractAuditStatus;
import com.law.business.shared.status.ContractSignStatus;
import com.law.business.shared.status.ContractStatus;
import com.law.business.shared.status.FeeInvoiceStatus;
import com.law.business.shared.status.FeePaymentStatus;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.system.domain.BizContract;
import com.ruoyi.system.domain.BizCustomer;
import com.ruoyi.system.mapper.BizContractMapper;
import com.ruoyi.system.mapper.BizCustomerMapper;
import com.ruoyi.system.service.IBizCaseService;
import com.ruoyi.system.service.ISysDictTypeService;
import com.ruoyi.system.service.contract.ContractAccessPolicy;
import com.ruoyi.system.service.contract.ContractActionLogService;
import com.ruoyi.system.service.contract.ContractCommandService;
import com.ruoyi.system.service.contract.ContractLifecycleService;
import com.ruoyi.system.service.contract.ContractNumberService;
import com.ruoyi.system.service.contract.ContractPaymentService;
import com.ruoyi.system.service.contract.ContractStatusLogRecord;
import com.ruoyi.system.service.customer.CustomerAccessPolicy;

class CustomerContractPaymentFlowTest
{
    @Test
    void customerContractApprovalSigningAndPaymentExposeStableFacts()
    {
        Fixture fixture = new Fixture();

        fixture.createContract();
        fixture.lifecycle.submit(10L);
        fixture.lifecycle.approve(10L, "pass", "同意", actor());
        fixture.lifecycle.sign(10L, ContractSignStatus.SIGNED.code());
        fixture.payment.confirm(21L, "100.00", null, "bank", null, actor());

        assertEquals(List.of(
                "CONTRACT_SUBMITTED:10:91",
                "CONTRACT_APPROVED:10:72",
                "CONTRACT_SIGNED:10:92",
                "PAYMENT_CONFIRMED:10:21:93"), fixture.events.keys);
        assertEquals(ContractStatus.PERFORMING.code(), fixture.contract.get().getContractStatus());
        assertEquals(FeePaymentStatus.CONFIRMED.code(), fixture.confirmStatus.get());

        int actionCount = fixture.actions.size();
        int eventCount = fixture.events.keys.size();
        assertThrows(RuntimeException.class, () -> fixture.lifecycle.submit(10L));
        assertThrows(RuntimeException.class, () -> fixture.lifecycle.approve(10L, "pass", "同意", actor()));
        assertThrows(RuntimeException.class, () -> fixture.lifecycle.sign(10L, ContractSignStatus.SIGNED.code()));
        assertThrows(RuntimeException.class,
                () -> fixture.payment.confirm(21L, "1.00", null, "bank", null, actor()));
        assertEquals(actionCount, fixture.actions.size());
        assertEquals(eventCount, fixture.events.keys.size());
    }

    @Test
    void outboxFailureRollsBackBusinessUpdateAndActionLog()
    {
        Fixture fixture = new Fixture();
        fixture.createContract();
        int actionCount = fixture.actions.size();
        fixture.events.fail = true;
        TransactionTemplate transaction = new TransactionTemplate(new SnapshotTransactionManager(fixture));

        assertThrows(RuntimeException.class,
                () -> transaction.executeWithoutResult(status -> fixture.lifecycle.submit(10L)));

        assertEquals(ContractAuditStatus.PENDING.code(), fixture.contract.get().getAuditStatus());
        assertEquals(actionCount, fixture.actions.size());
        assertEquals(List.of(), fixture.events.keys);
    }

    private static final class Fixture
    {
        private final BizContractMapper contracts = mock(BizContractMapper.class);
        private final BizCustomerMapper customers = mock(BizCustomerMapper.class);
        private final ISysDictTypeService dictionaries = mock(ISysDictTypeService.class);
        private final BusinessActorProvider actors = mock(BusinessActorProvider.class);
        private final ContractNumberService numbers = mock(ContractNumberService.class);
        private final AtomicReference<BizContract> contract = new AtomicReference<>();
        private final AtomicReference<String> confirmStatus = new AtomicReference<>(FeePaymentStatus.PENDING.code());
        private final AtomicReference<BigDecimal> receivedAmount = new AtomicReference<>(BigDecimal.ZERO);
        private final List<String> actions = new ArrayList<>();
        private final CollectingPublisher events = new CollectingPublisher();
        private final ContractCommandService commands;
        private final ContractLifecycleService lifecycle;
        private final ContractPaymentService payment;
        private Snapshot snapshot;

        private Fixture()
        {
            when(actors.current()).thenReturn(actor());
            when(numbers.nextNumber()).thenReturn("HT-10");
            stubCustomer();
            stubDictionaries();
            stubContractPersistence();
            ContractAccessPolicy contractAccess = new ContractAccessPolicy(contracts, actors);
            CustomerAccessPolicy customerAccess = new CustomerAccessPolicy(customers, actors);
            ContractActionLogService logs = new ContractActionLogService(contracts);
            commands = new ContractCommandService(contracts, customerAccess, contractAccess, numbers,
                    dictionaries, actors, logs);
            lifecycle = new ContractLifecycleService(contracts, contractAccess, mock(IBizCaseService.class),
                    dictionaries, events, logs, actors);
            payment = new ContractPaymentService(contracts, contractAccess, dictionaries, events, logs);
        }

        private void createContract()
        {
            ContractCreateCommand command = new ContractCreateCommand();
            command.setContractName("常年法律顾问合同");
            command.setCustomerId(31L);
            command.setCaseType("civil");
            command.setSignAmount(new BigDecimal("100.00"));
            command.setFeeType("once");
            command.setSignMethod("online");
            command.setRiskLevel("1");
            command.setSignDate(LocalDate.of(2026, 7, 15));
            commands.create(command);
        }

        private void stubCustomer()
        {
            BizCustomer customer = new BizCustomer();
            customer.setCustomerId(31L);
            customer.setCustomerName("张三客户");
            customer.setStatus("0");
            customer.setDelFlag("0");
            when(customers.selectCustomerById(31L)).thenReturn(customer);
            when(customers.countCustomerInDataScope(anyLong(), anyLong(), anyLong(), anyString()))
                    .thenReturn(1);
        }

        private void stubDictionaries()
        {
            when(dictionaries.selectDictDataByType(anyString())).thenAnswer(invocation -> {
                String type = invocation.getArgument(0);
                return switch (type)
                {
                    case "law_contract_case_type" -> options("civil");
                    case "law_contract_fee_type" -> options("once");
                    case "law_contract_sign_method" -> options("online");
                    case "law_contract_risk_level" -> options("1");
                    case "law_contract_approval_action" -> options("pass");
                    case "law_contract_sign_status" -> options(ContractSignStatus.SIGNED.code());
                    case "law_finance_payment_method" -> options("bank");
                    case "law_contract_status_action" -> options("submit", "approval", "sign", "fee_confirm");
                    default -> List.of();
                };
            });
        }

        private void stubContractPersistence()
        {
            when(contracts.insertContract(any(BizContract.class))).thenAnswer(invocation -> {
                BizContract value = invocation.getArgument(0);
                value.setContractId(10L);
                contract.set(value);
                return 1;
            });
            when(contracts.selectContractById(10L)).thenAnswer(invocation -> contract.get());
            when(contracts.countContractInDataScope(anyLong(), anyLong(), anyLong(), anyString()))
                    .thenReturn(1);
            when(contracts.insertStatusLog(any(ContractStatusLogRecord.class))).thenAnswer(invocation -> {
                ContractStatusLogRecord record = invocation.getArgument(0);
                record.setLogId(logId(record.getActionType()));
                actions.add(record.getActionType());
                return 1;
            });
            when(contracts.updateAuditStatus(anyLong(), anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenAnswer(invocation -> updateAudit(invocation.getArgument(1), invocation.getArgument(2),
                            invocation.getArgument(3), invocation.getArgument(4)));
            when(contracts.insertApproval(any())).thenAnswer(invocation -> {
                invocation.<Map<String, Object>>getArgument(0).put("approvalId", 72L);
                return 1;
            });
            when(contracts.updateLifecycleStatus(anyLong(), any(), anyString(), anyString(), anyString(), anyString()))
                    .thenAnswer(invocation -> updateLifecycle(invocation.getArgument(1), invocation.getArgument(2),
                            invocation.getArgument(3), invocation.getArgument(4)));
            when(contracts.selectFeePlanById(21L)).thenAnswer(invocation -> feePlan());
            when(contracts.updateFeePlanStatus(any())).thenAnswer(invocation -> updateFee(invocation.getArgument(0)));
        }

        private int updateAudit(String nextAudit, String nextContract, String expectedAudit, String expectedContract)
        {
            BizContract value = contract.get();
            if (!expectedAudit.equals(value.getAuditStatus()) || !expectedContract.equals(value.getContractStatus()))
                return 0;
            value.setAuditStatus(nextAudit);
            value.setContractStatus(nextContract);
            return 1;
        }

        private int updateLifecycle(String nextSign, String nextContract, String expectedAudit, String expectedContract)
        {
            BizContract value = contract.get();
            if (!expectedAudit.equals(value.getAuditStatus()) || !expectedContract.equals(value.getContractStatus()))
                return 0;
            value.setSignStatus(nextSign);
            value.setContractStatus(nextContract);
            return 1;
        }

        private Map<String, Object> feePlan()
        {
            Map<String, Object> value = new HashMap<>();
            value.put("contract_id", 10L);
            value.put("contract_no", "HT-10");
            value.put("confirm_status", confirmStatus.get());
            value.put("invoice_status", FeeInvoiceStatus.NONE.code());
            value.put("contract_status", contract.get().getContractStatus());
            value.put("receivable_amount", new BigDecimal("100.00"));
            value.put("received_amount", receivedAmount.get());
            return value;
        }

        private int updateFee(Map<String, Object> update)
        {
            if (!confirmStatus.get().equals(update.get("expectedConfirmStatus"))) return 0;
            confirmStatus.set(String.valueOf(update.get("confirmStatus")));
            receivedAmount.set((BigDecimal) update.get("receivedAmount"));
            return 1;
        }

        private long logId(String action)
        {
            return switch (action)
            {
                case "create" -> 90L;
                case "submit" -> 91L;
                case "sign" -> 92L;
                case "fee_confirm" -> 93L;
                default -> 94L;
            };
        }

        private void snapshot()
        {
            BizContract value = contract.get();
            snapshot = new Snapshot(value.getAuditStatus(), value.getContractStatus(), value.getSignStatus(),
                    confirmStatus.get(), receivedAmount.get(), actions.size());
        }

        private void rollback()
        {
            contract.get().setAuditStatus(snapshot.auditStatus());
            contract.get().setContractStatus(snapshot.contractStatus());
            contract.get().setSignStatus(snapshot.signStatus());
            confirmStatus.set(snapshot.confirmStatus());
            receivedAmount.set(snapshot.receivedAmount());
            while (actions.size() > snapshot.actionCount()) actions.remove(actions.size() - 1);
        }

        private List<SysDictData> options(String... values)
        {
            List<SysDictData> result = new ArrayList<>();
            for (String value : values)
            {
                SysDictData option = new SysDictData();
                option.setDictValue(value);
                option.setStatus("0");
                result.add(option);
            }
            return result;
        }
    }

    private record Snapshot(String auditStatus, String contractStatus, String signStatus,
            String confirmStatus, BigDecimal receivedAmount, int actionCount) { }

    private static final class CollectingPublisher implements BusinessEventPublisher
    {
        private final List<String> keys = new ArrayList<>();
        private boolean fail;

        @Override
        public void publish(BusinessEventCommand command)
        {
            if (fail) throw new IllegalStateException("outbox unavailable");
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
