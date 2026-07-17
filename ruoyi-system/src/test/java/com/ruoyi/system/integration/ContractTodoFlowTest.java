package com.ruoyi.system.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.law.business.contract.dto.ContractTodoSignCommand;
import com.law.business.security.BusinessActor;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;
import com.ruoyi.system.domain.BizContract;
import com.ruoyi.system.mapper.BizContractMapper;
import com.ruoyi.system.service.contract.ContractLifecycleService;
import com.ruoyi.system.service.event.ContractReviewTodoHandler;
import com.ruoyi.system.service.event.ContractSignTodoHandler;
import com.ruoyi.system.service.event.ContractTodoValidator;

class ContractTodoFlowTest
{
    @Test void reviewHandlerUsesStableKeyAndKeepsHistoricalFallback()
    {
        ContractLifecycleService service=mock(ContractLifecycleService.class);
        ContractReviewTodoHandler handler=new ContractReviewTodoHandler(service);TodoInstance todo=todo("CONTRACT_REVIEW");
        handler.complete(todo,Map.of("reviewAction","pass","opinion","同意"),7L,"alice");
        verify(service).approve(eq(8L),eq("pass"),eq("同意"),any());
        handler.complete(todo,Map.of("action","back","opinion","补充"),7L,"alice");
        verify(service).approve(eq(8L),eq("back"),eq("补充"),any());
        todo.setTemplateCode("CONTRACT_SIGN");assertFalse(handler.supports(todo));
    }

    @Test void validatorRejectsStaleReviewAndAcceptsFileCenterBackedSignature()
    {
        BizContractMapper mapper=mock(BizContractMapper.class);BizContract contract=new BizContract();
        contract.setContractId(8L);contract.setAuditStatus("0");when(mapper.selectContractById(8L)).thenReturn(contract);
        ContractTodoValidator validator=new ContractTodoValidator(mapper);
        assertEquals("CONTRACT_AUDIT_STATE_STALE",assertThrows(TodoException.class,()->validator.validate(todo("CONTRACT_REVIEW"),Map.of("reviewAction","pass","opinion","ok"))).getBusinessCode());
        contract.setAuditStatus("2");
        assertDoesNotThrow(()->validator.validate(todo("CONTRACT_SIGN"),Map.of("signStatus","1","signMethod","offline","signDate","2026-07-12")));
    }

    @Test void signHandlerDoesNotRequireLegacyFileUrlWhenFileRelationWasValidated()
    {
        ContractLifecycleService service=mock(ContractLifecycleService.class);
        new ContractSignTodoHandler(service).complete(todo("CONTRACT_SIGN"),
                Map.of("signStatus","1","signMethod","offline","signDate","2026-07-12"),7L,"alice");
        verify(service).sign(argThat((ContractTodoSignCommand command)->command.signFileUrl()==null&&"1".equals(command.signStatus())),any(BusinessActor.class));
    }

    private TodoInstance todo(String code){TodoInstance value=new TodoInstance();value.setTemplateCode(code);value.setBusinessType("CONTRACT");value.setBusinessId(8L);value.setOwnerDeptId(3L);return value;}
}
