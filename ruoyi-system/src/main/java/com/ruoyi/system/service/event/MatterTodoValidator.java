package com.ruoyi.system.service.event;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.spi.TodoBusinessValidator;
import com.ruoyi.system.mapper.BizMatterMapper;

@Component
public class MatterTodoValidator implements TodoBusinessValidator
{
    private final BizMatterMapper mapper;public MatterTodoValidator(BizMatterMapper mapper){this.mapper=mapper;}
    @Override public boolean supports(String type){return "MATTER".equals(type);}
    @Override public void validate(TodoInstance todo,Map<String,Object> payload){Map<String,Object> matter=mapper.selectMatterById(todo.getBusinessId());if(matter==null)fail("MATTER_NOT_FOUND","案件不存在");String code=todo.getTemplateCode();if("MATTER_NODE_HANDLE".equals(code)){required(payload,"nodeId");required(payload,"actualDate");Object raw=payload.get("materials");if(raw instanceof List<?> list)for(Object item:list)if(item instanceof Map<?,?> material&&(!"ready".equals(text(material.get("materialStatus")))||blank(material.get("fileUrl"))))fail("MATTER_NODE_MATERIAL_INCOMPLETE","节点材料未准备完成");}if("MATTER_EXPENSE_REVIEW".equals(code)){required(payload,"expenseId");required(payload,"result");if("approved".equals(text(payload.get("result"))))required(payload,"voucherUrl");}if("MATTER_DOCUMENT_SUPPLY".equals(code)){required(payload,"documentType");required(payload,"fileName");required(payload,"fileUrl");}}
    private void required(Map<String,Object> p,String k){if(blank(p.get(k)))fail("TODO_DOD_FIELD_MISSING","缺少完成字段："+k);}private boolean blank(Object v){return v==null||text(v).isBlank();}private String text(Object v){return v==null?"":String.valueOf(v);}private void fail(String c,String m){throw new TodoException(c,m);}
}
