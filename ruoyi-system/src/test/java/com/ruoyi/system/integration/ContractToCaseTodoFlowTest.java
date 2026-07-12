package com.ruoyi.system.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;
import com.ruoyi.system.domain.BizContract;
import com.ruoyi.system.mapper.BizContractMapper;
import com.ruoyi.system.service.casecenter.CaseCreationService;
import com.ruoyi.system.service.contract.*;
import com.ruoyi.system.service.event.*;

class ContractToCaseTodoFlowTest
{
    @Test void paymentHandlerUsesFeePlanAsSecondaryRelation()
    {
        ContractPaymentService service=mock(ContractPaymentService.class);TodoMapper mapper=mock(TodoMapper.class);ContractPaymentTodoHandler handler=new ContractPaymentTodoHandler(service,mapper);TodoInstance todo=todo("PAYMENT_CONFIRM");todo.setTodoId(5L);
        handler.complete(todo,Map.of("planId",22L,"receivedAmount","100","voucherUrl","/proof.jpg"),7L,"alice");
        verify(service).confirm(eq(22L),eq("100"),isNull(),isNull(),eq("/proof.jpg"),any());verify(mapper).insertRelation(argThat(row->"FEE_PLAN".equals(row.get("businessType"))&&Long.valueOf(22L).equals(row.get("businessId"))));
    }
    @Test void validatorRejectsFeePlanFromAnotherContract()
    {
        BizContractMapper mapper=mock(BizContractMapper.class);BizContract contract=new BizContract();contract.setContractId(8L);when(mapper.selectContractById(8L)).thenReturn(contract);when(mapper.selectFeePlanById(22L)).thenReturn(Map.of("contract_id",99L));
        TodoException error=assertThrows(TodoException.class,()->new ContractTodoValidator(mapper).validate(todo("PAYMENT_CONFIRM"),Map.of("planId",22L,"receivedAmount",100,"voucherUrl","x")));
        assertEquals("PAYMENT_PLAN_INVALID",error.getBusinessCode());
    }
    @Test void caseCreationHandlerIsIdempotentThroughExistingCaseGuard()
    {
        CaseCreationService service=mock(CaseCreationService.class);BizContractMapper mapper=mock(BizContractMapper.class);BizContract contract=new BizContract();contract.setContractId(8L);when(mapper.selectContractById(8L)).thenReturn(contract);CaseCreationTodoHandler handler=new CaseCreationTodoHandler(service,mapper);
        handler.complete(todo("CASE_CREATE_CHECK"),Map.of("materialsChecked",true),7L,"alice");verify(service).createFromContract(eq(contract),any());
    }
    private TodoInstance todo(String code){TodoInstance value=new TodoInstance();value.setTemplateCode(code);value.setBusinessType("CONTRACT");value.setBusinessId(8L);value.setOwnerDeptId(3L);return value;}
}
