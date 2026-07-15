package com.ruoyi.system.service.contract;

import static com.ruoyi.system.support.BusinessFixtures.actor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.law.business.security.BusinessActorProvider;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.BizContract;
import com.ruoyi.system.domain.BizCustomer;
import com.ruoyi.system.mapper.BizContractMapper;
import com.ruoyi.system.mapper.BizCustomerMapper;

@ExtendWith(MockitoExtension.class)
class ContractImportServiceTest
{
    @Mock private BizContractMapper contracts;
    @Mock private BizCustomerMapper customers;
    @Mock private ContractCommandService commands;
    @Mock private BusinessActorProvider actors;
    private ContractImportService service;

    @BeforeEach
    void setUp()
    {
        service = new ContractImportService(contracts, customers, commands, actors);
    }

    @Test
    void resolvesUniqueCustomerAndCreatesContract()
    {
        BizContract row = row("合同A", "甲客户");
        BizCustomer customer = new BizCustomer();
        customer.setCustomerId(7L);
        when(actors.current()).thenReturn(actor());
        when(customers.selectCustomersByExactNameInScope(eq("甲客户"), eq(8L), eq(3L), eq(true), anyString()))
                .thenReturn(List.of(customer));
        when(contracts.selectContractsByExactNameInScope(eq("合同A"), eq(8L), eq(3L), eq(true), anyString()))
                .thenReturn(List.of());

        String result = service.importContracts(new ContractImportCommand(List.of(row), false, "alice"));

        assertEquals("导入成功，共 1 条", result);
        verify(commands).create(any(com.law.business.contract.dto.ContractCreateCommand.class));
    }

    @Test
    void duplicateCustomerNameIsRejected()
    {
        BizCustomer first = new BizCustomer();
        first.setCustomerId(7L);
        BizCustomer second = new BizCustomer();
        second.setCustomerId(8L);
        when(actors.current()).thenReturn(actor());
        when(customers.selectCustomersByExactNameInScope(eq("甲客户"), eq(8L), eq(3L), eq(true), anyString()))
                .thenReturn(List.of(first, second));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.importContracts(new ContractImportCommand(
                        List.of(row("合同A", "甲客户")), false, "alice")));

        assertEquals("PRECONDITION_FAILED", exception.getBusinessCode());
        verify(commands, never()).create(any(com.law.business.contract.dto.ContractCreateCommand.class));
    }

    @Test
    void duplicateContractRequiresUpdateSupport()
    {
        BizContract row = row("合同A", null);
        row.setCustomerId(7L);
        BizContract duplicate = new BizContract();
        duplicate.setContractId(10L);
        when(actors.current()).thenReturn(actor());
        when(contracts.selectContractsByExactNameInScope(eq("合同A"), eq(8L), eq(3L), eq(true), anyString()))
                .thenReturn(List.of(duplicate));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.importContracts(new ContractImportCommand(List.of(row), false, "alice")));

        assertEquals("DUPLICATE_OPERATION", exception.getBusinessCode());
        verify(commands, never()).update(any(com.law.business.contract.dto.ContractUpdateCommand.class));
    }

    private BizContract row(String contractName, String customerName)
    {
        BizContract row = new BizContract();
        row.setContractName(contractName);
        row.setCustomerName(customerName);
        row.setCaseType("civil");
        row.setSignAmount(new BigDecimal("10000"));
        row.setFeeType("once");
        row.setSignMethod("online");
        row.setRiskLevel("1");
        return row;
    }
}
