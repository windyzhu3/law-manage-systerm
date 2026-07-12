package com.ruoyi.system.service.event;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.spi.TodoBusinessValidator;
import com.ruoyi.system.mapper.BizMatterMapper;

@Component
public class ArchiveTodoValidator implements TodoBusinessValidator
{
    private final BizMatterMapper mapper;public ArchiveTodoValidator(BizMatterMapper mapper){this.mapper=mapper;}
    @Override public boolean supports(String type){return "MATTER".equals(type);}
    @Override public void validate(TodoInstance todo,Map<String,Object> p){String code=todo.getTemplateCode();if(!"CASE_CLOSE_CONFIRM".equals(code)&&!"CASE_ARCHIVE_CONFIRM".equals(code))return;Map<String,Object> matter=mapper.selectMatterById(todo.getBusinessId());if(matter==null)fail("MATTER_NOT_FOUND","案件不存在");required(p,"action");required(p,"opinion");if("pass".equals(text(p.get("action")))){if(mapper.countUnfinishedNodes(todo.getBusinessId())>0)fail("CASE_NODES_UNFINISHED","仍有未完成节点");if(mapper.countUnpaidExpenseByCaseId(todo.getBusinessId())>0)fail("CASE_EXPENSE_UNPAID","仍有未付款费用");if("CASE_CLOSE_CONFIRM".equals(code)){required(p,"feeClearStatus");if(!"cleared".equals(text(p.get("feeClearStatus"))))fail("CASE_FEE_NOT_CLEARED","费用未结清");}else{required(p,"archiveNo");Object raw=p.get("materials");if(!(raw instanceof List<?>))fail("ARCHIVE_MATERIAL_REQUIRED","归档材料不能为空");List<?> list=(List<?>)raw;if(list.isEmpty())fail("ARCHIVE_MATERIAL_REQUIRED","归档材料不能为空");for(Object item:list)if(item instanceof Map<?,?> m&&(!"ready".equals(text(m.get("materialStatus")))||blank(m.get("fileUrl"))))fail("ARCHIVE_MATERIAL_INCOMPLETE","归档材料未准备完成");}}}
    private void required(Map<String,Object>p,String k){if(blank(p.get(k)))fail("TODO_DOD_FIELD_MISSING","缺少完成字段："+k);}private boolean blank(Object v){return v==null||text(v).isBlank();}private String text(Object v){return v==null?"":String.valueOf(v);}private void fail(String c,String m){throw new TodoException(c,m);}
}
