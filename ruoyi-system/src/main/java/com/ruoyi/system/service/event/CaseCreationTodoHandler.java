package com.ruoyi.system.service.event;

import java.util.Map;
import org.springframework.stereotype.Component;
import com.law.business.security.BusinessActor;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.spi.TodoCompletionHandler;
import com.ruoyi.system.domain.BizContract;
import com.ruoyi.system.mapper.BizContractMapper;
import com.ruoyi.system.service.casecenter.CaseCreationService;

@Component
public class CaseCreationTodoHandler implements TodoCompletionHandler
{
    private final CaseCreationService service;private final BizContractMapper contracts;public CaseCreationTodoHandler(CaseCreationService service,BizContractMapper contracts){this.service=service;this.contracts=contracts;}
    @Override public boolean supports(TodoInstance todo){return "CASE_CREATE_CHECK".equals(todo.getTemplateCode());}
    @Override public void complete(TodoInstance todo,Map<String,Object> payload,Long userId,String userName){BizContract contract=contracts.selectContractById(todo.getBusinessId());if(contract==null)throw new TodoException("CONTRACT_NOT_FOUND","合同不存在");service.createFromContract(contract,new BusinessActor(userId,userName,userName,todo.getOwnerDeptId(),userId==1));}
}
