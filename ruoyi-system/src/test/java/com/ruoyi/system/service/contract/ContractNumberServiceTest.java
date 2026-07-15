package com.ruoyi.system.service.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.business.contract.dto.ContractNumberRuleUpdateCommand;
import com.law.business.security.BusinessActorProvider;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.mapper.BizContractMapper;
import com.ruoyi.system.service.ISysDictTypeService;

@ExtendWith(MockitoExtension.class)
class ContractNumberServiceTest
{
    @Mock private BizContractMapper mapper;
    @Mock private ISysDictTypeService dictionaries;
    @Mock private BusinessActorProvider actors;

    @Test
    void disablingLastEnabledRuleIsRejected()
    {
        ContractNumberRuleUpdateCommand command = command("1");
        when(dictionaries.selectDictDataByType("sys_normal_disable")).thenReturn(List.of(dict("0"), dict("1")));
        when(mapper.countOtherEnabledNoRules(3L)).thenReturn(0);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service().updateRule(command));

        assertEquals("PRECONDITION_FAILED", exception.getBusinessCode());
        verify(mapper, never()).updateRule(org.mockito.ArgumentMatchers.anyMap());
    }

    private ContractNumberService service()
    {
        return new ContractNumberService(mapper, dictionaries, actors);
    }

    private ContractNumberRuleUpdateCommand command(String status)
    {
        ContractNumberRuleUpdateCommand command = new ContractNumberRuleUpdateCommand();
        command.setRuleId(3L);
        command.setRuleName("默认规则");
        command.setPrefix("HT");
        command.setDatePattern("yyyyMMdd");
        command.setSerialLength(4);
        command.setStatus(status);
        return command;
    }

    private SysDictData dict(String value)
    {
        SysDictData data = new SysDictData();
        data.setDictValue(value);
        return data;
    }
}
