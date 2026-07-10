package com.ruoyi.system.service.matter;

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
import com.ruoyi.system.domain.BizContract;
import com.ruoyi.system.mapper.BizContractMapper;
import com.ruoyi.system.mapper.BizCustomerMapper;
import com.ruoyi.system.mapper.BizMatterMapper;
import com.ruoyi.system.service.ISysDictTypeService;

@ExtendWith(MockitoExtension.class)
class MatterCommandServiceTest
{
    @Mock private BizMatterMapper matterMapper;
    @Mock private BizContractMapper contractMapper;
    @Mock private BizCustomerMapper customerMapper;
    @Mock private ISysDictTypeService dictTypeService;
    private MatterCommandService service;

    @BeforeEach
    void setUp()
    {
        service = new MatterCommandService(matterMapper, contractMapper, customerMapper, dictTypeService);
    }

    @Test
    void createRejectsContractThatAlreadyHasMatter()
    {
        Map<String, Object> command = new HashMap<>();
        command.put("contractId", 10L);
        when(matterMapper.selectMatterByContractId(10L)).thenReturn(Map.of("case_id", 99L));

        assertThrows(ServiceException.class, () -> service.create(command));

        verify(contractMapper, never()).selectContractById(10L);
    }

    @Test
    void createRejectsMissingSourceContract()
    {
        Map<String, Object> command = new HashMap<>();
        command.put("contractId", 10L);
        when(matterMapper.selectMatterByContractId(10L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.create(command));
        verify(contractMapper).selectContractById(10L);
    }

    @Test
    void createRejectsContractOutsideRequiredLifecycle()
    {
        Map<String, Object> command = new HashMap<>();
        command.put("contractId", 10L);
        BizContract contract = new BizContract();
        contract.setCustomerId(20L);
        contract.setAuditStatus("1");
        contract.setSignStatus("0");
        contract.setContractStatus("0");
        when(matterMapper.selectMatterByContractId(10L)).thenReturn(null);
        when(contractMapper.selectContractById(10L)).thenReturn(contract);

        assertThrows(ServiceException.class, () -> service.create(command));

        verify(contractMapper).selectContractById(10L);
        verify(customerMapper, never()).selectCustomerById(20L);
    }
}
