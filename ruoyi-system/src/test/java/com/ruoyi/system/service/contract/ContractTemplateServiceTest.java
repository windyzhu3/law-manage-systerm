package com.ruoyi.system.service.contract;

import static com.ruoyi.system.support.BusinessFixtures.actor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.business.contract.dto.ContractTemplateCreateCommand;
import com.law.business.security.BusinessActorProvider;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.mapper.BizContractMapper;
import com.ruoyi.system.service.ISysDictTypeService;

@ExtendWith(MockitoExtension.class)
class ContractTemplateServiceTest
{
    @Mock private BizContractMapper mapper;
    @Mock private ISysDictTypeService dictionaries;
    @Mock private BusinessActorProvider actors;

    @Test
    void activeTemplateCannotBeDeleted()
    {
        when(mapper.selectTemplateById(5L)).thenReturn(Map.of("templateId", 5L, "status", "0"));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service().delete(5L));

        assertEquals("STATE_CONFLICT", exception.getBusinessCode());
        verify(mapper, never()).deleteTemplate(5L);
    }

    @Test
    void createMapsTypedCommandAndSystemOperator()
    {
        when(actors.current()).thenReturn(actor());
        when(dictionaries.selectDictDataByType("law_contract_case_type")).thenReturn(List.of(dict("civil")));
        when(dictionaries.selectDictDataByType("sys_normal_disable")).thenReturn(List.of(dict("0"), dict("1")));
        when(mapper.insertTemplate(org.mockito.ArgumentMatchers.anyMap())).thenReturn(1);

        service().create(command());

        verify(mapper).insertTemplate(argThat(row -> "alice".equals(row.get("createBy"))
                && "v1".equals(row.get("versionNo")) && "0".equals(row.get("status"))));
    }

    private ContractTemplateService service()
    {
        return new ContractTemplateService(mapper, dictionaries, actors);
    }

    private ContractTemplateCreateCommand command()
    {
        ContractTemplateCreateCommand command = new ContractTemplateCreateCommand();
        command.setTemplateName("民事委托模板");
        command.setCaseType("civil");
        command.setFileName("civil.docx");
        command.setFileUrl("/templates/civil.docx");
        return command;
    }

    private SysDictData dict(String value)
    {
        SysDictData data = new SysDictData();
        data.setDictValue(value);
        return data;
    }
}
