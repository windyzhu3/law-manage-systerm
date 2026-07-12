package com.ruoyi.system.service.event;

import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.spi.TodoBusinessValidator;
import com.ruoyi.system.mapper.BizCaseMapper;

@Component
public class CaseTodoValidator implements TodoBusinessValidator
{
    private final BizCaseMapper mapper;public CaseTodoValidator(BizCaseMapper mapper){this.mapper=mapper;}
    @Override public boolean supports(String type){return "CASE".equals(type);}
    @Override public void validate(TodoInstance todo,Map<String,Object> payload){Map<String,Object> value=mapper.selectCaseById(todo.getBusinessId());if(value==null)fail("CASE_NOT_FOUND","案件不存在");String code=todo.getTemplateCode();if(Set.of("CASE_ASSIGN","CASE_REASSIGN").contains(code)){required(payload,"lawyerId");if(!"pending".equals(text(value.get("case_status"))))fail("CASE_STATE_STALE","案件已不处于待分案状态");}if("CASE_ACCEPT".equals(code)){required(payload,"confirmId");required(payload,"accepted");if(!"confirming".equals(text(value.get("case_status"))))fail("CASE_STATE_STALE","案件已不处于待确认状态");}if("CASE_TRANSFER_REVIEW".equals(code)){required(payload,"transferId");required(payload,"action");required(payload,"opinion");}}
    private void required(Map<String,Object> p,String k){if(p.get(k)==null||text(p.get(k)).isBlank())fail("TODO_DOD_FIELD_MISSING","缺少完成字段："+k);}private String text(Object v){return v==null?"":String.valueOf(v);}private void fail(String c,String m){throw new TodoException(c,m);}
}
