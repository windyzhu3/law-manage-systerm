package com.ruoyi.system.service.contract;

import static com.ruoyi.system.support.BusinessFixtures.actor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.business.contract.dto.ContractCreateCommand;
import com.law.business.contract.dto.ContractUpdateCommand;
import com.law.business.security.BusinessActorProvider;
import com.law.business.shared.status.ContractAuditStatus;
import com.law.business.shared.status.ContractSignStatus;
import com.law.business.shared.status.ContractStatus;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.BizContract;
import com.ruoyi.system.domain.BizCustomer;
import com.ruoyi.system.mapper.BizContractMapper;
import com.ruoyi.system.service.ISysDictTypeService;
import com.ruoyi.system.service.customer.CustomerAccessPolicy;

@ExtendWith(MockitoExtension.class)
class ContractCommandServiceTest
{
    @Mock private BizContractMapper mapper;
    @Mock private CustomerAccessPolicy customers;
    @Mock private ContractAccessPolicy access;
    @Mock private ContractNumberService numberService;
    @Mock private ISysDictTypeService dictionaries;
    @Mock private BusinessActorProvider actors;
    @Mock private ContractActionLogService actionLogs;
    private ContractCommandService service;

    @BeforeEach
    void setUp()
    {
        service = new ContractCommandService(mapper, customers, access, numberService,
                dictionaries, actors, actionLogs);
    }

    @Test
    void createUsesCustomerPolicyAndForcesLifecycleStatuses()
    {
        when(actors.current()).thenReturn(actor());
        when(customers.requireOperable(7L)).thenReturn(customer(7L, "甲客户"));
        when(numberService.nextNumber()).thenReturn("HT-001");
        allowDictionaries();
        when(mapper.insertContract(any())).thenAnswer(invocation -> {
            invocation.<BizContract>getArgument(0).setContractId(10L);
            return 1;
        });

        service.create(createCommand(7L));

        verify(customers).requireOperable(7L);
        verify(mapper).insertContract(argThat(contract ->
                ContractStatus.DRAFT.code().equals(contract.getContractStatus())
                        && ContractAuditStatus.PENDING.code().equals(contract.getAuditStatus())
                        && ContractSignStatus.UNSIGNED.code().equals(contract.getSignStatus())
                        && "甲客户".equals(contract.getCustomerName())));
        verify(actionLogs).record(eq(10L), eq(null), eq(ContractStatus.DRAFT.code()),
                eq("create"), any(), eq(actor()));
    }

    @Test
    void updateUsesExpectedStatusesAndRejectsConcurrentChange()
    {
        when(actors.current()).thenReturn(actor());
        when(access.requireOperable(10L)).thenReturn(draftContract());
        when(customers.requireOperable(7L)).thenReturn(customer(7L, "甲客户"));
        allowDictionaries();
        when(mapper.updateContractConditionally(any(), eq("0"), eq("0"), eq("0"))).thenReturn(0);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.update(updateCommand()));

        assertEquals("CONCURRENT_MODIFICATION", exception.getBusinessCode());
    }

    @Test
    void deleteRejectsConcurrentStateChange()
    {
        when(actors.current()).thenReturn(actor());
        when(access.requireOperable(10L)).thenReturn(draftContract());
        when(mapper.deleteContractConditionally(10L, "0", "0", "alice")).thenReturn(0);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.delete(new Long[] { 10L }));

        assertEquals("CONCURRENT_MODIFICATION", exception.getBusinessCode());
    }

    @Test
    void deleteRequiresAtLeastOneContract()
    {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.delete(new Long[0]));
        assertEquals("VALIDATION_FAILED", exception.getBusinessCode());
        verify(mapper, never()).deleteContractConditionally(any(), any(), any(), any());
    }

    private ContractCreateCommand createCommand(Long customerId)
    {
        ContractCreateCommand command = new ContractCreateCommand();
        command.setContractName("委托合同");
        command.setCustomerId(customerId);
        command.setCaseType("civil");
        command.setSignAmount(new BigDecimal("10000"));
        command.setFeeType("once");
        command.setSignMethod("online");
        command.setRiskLevel("1");
        return command;
    }

    private ContractUpdateCommand updateCommand()
    {
        ContractUpdateCommand command = new ContractUpdateCommand();
        command.setContractId(10L);
        command.setContractName("委托合同（修订）");
        command.setCustomerId(7L);
        command.setCaseType("civil");
        command.setSignAmount(new BigDecimal("12000"));
        command.setFeeType("once");
        command.setSignMethod("online");
        command.setRiskLevel("1");
        return command;
    }

    private BizContract draftContract()
    {
        BizContract contract = new BizContract();
        contract.setContractId(10L);
        contract.setAuditStatus(ContractAuditStatus.PENDING.code());
        contract.setContractStatus(ContractStatus.DRAFT.code());
        contract.setDelFlag("0");
        return contract;
    }

    private BizCustomer customer(Long id, String name)
    {
        BizCustomer customer = new BizCustomer();
        customer.setCustomerId(id);
        customer.setCustomerName(name);
        customer.setStatus("0");
        customer.setDelFlag("0");
        return customer;
    }

    private void allowDictionaries()
    {
        when(dictionaries.selectDictDataByType("law_contract_case_type")).thenReturn(List.of(dict("civil")));
        when(dictionaries.selectDictDataByType("law_contract_fee_type")).thenReturn(List.of(dict("once")));
        when(dictionaries.selectDictDataByType("law_contract_sign_method")).thenReturn(List.of(dict("online")));
        when(dictionaries.selectDictDataByType("law_contract_risk_level")).thenReturn(List.of(dict("1")));
    }

    private SysDictData dict(String value)
    {
        SysDictData data = new SysDictData();
        data.setDictValue(value);
        return data;
    }
}
