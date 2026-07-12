package com.ruoyi.system.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;
import com.ruoyi.system.mapper.BizMatterMapper;
import com.ruoyi.system.service.event.*;
import com.ruoyi.system.service.matter.*;

class MatterWorkTodoFlowTest
{
    @Test void nodeHandlerPassesDynamicFieldsMaterialsAndRelation()
    {
        MatterNodeService service=mock(MatterNodeService.class);TodoMapper todos=mock(TodoMapper.class);MatterNodeTodoHandler handler=new MatterNodeTodoHandler(service,todos);TodoInstance todo=todo("MATTER_NODE_HANDLE");todo.setTodoId(4L);List<Map<String,Object>> materials=List.of(Map.of("materialName","起诉状","materialStatus","ready","fileUrl","/a"));handler.complete(todo,Map.of("nodeId",9L,"actualDate","2026-07-13","dynamicFields",Map.of("courtRoom","1"),"materials",materials),7L,"alice");verify(service).completeFromTodo(eq(9L),eq("2026-07-13"),eq(Map.of("courtRoom","1")),eq(materials),any());verify(todos).insertRelation(argThat(row->"MATTER_NODE".equals(row.get("businessType"))));
    }
    @Test void nodeValidatorRejectsIncompleteMaterial()
    {
        BizMatterMapper mapper=mock(BizMatterMapper.class);when(mapper.selectMatterById(8L)).thenReturn(Map.of("case_status","processing"));TodoException error=assertThrows(TodoException.class,()->new MatterTodoValidator(mapper).validate(todo("MATTER_NODE_HANDLE"),Map.of("nodeId",9L,"actualDate","2026-07-13","materials",List.of(Map.of("materialName","起诉状","materialStatus","pending")))));assertEquals("MATTER_NODE_MATERIAL_INCOMPLETE",error.getBusinessCode());
    }
    @Test void expenseAndDocumentHandlersUseTypedBusinessCommands()
    {
        MatterExpenseService expenses=mock(MatterExpenseService.class);new MatterExpenseTodoHandler(expenses,mock(TodoMapper.class)).complete(todo("MATTER_EXPENSE_REVIEW"),Map.of("expenseId",2L,"result","approved","voucherUrl","/v"),1L,"admin");verify(expenses).reviewFromTodo(eq(2L),eq("approved"),eq("/v"),isNull(),any());MatterDocumentService documents=mock(MatterDocumentService.class);new MatterDocumentTodoHandler(documents).complete(todo("MATTER_DOCUMENT_SUPPLY"),Map.of("documentType","pleading","fileName","a.pdf","fileUrl","/a"),7L,"alice");verify(documents).supplyFromTodo(eq(8L),eq("pleading"),eq("a.pdf"),eq("/a"),isNull(),any());
    }
    private TodoInstance todo(String code){TodoInstance value=new TodoInstance();value.setTemplateCode(code);value.setBusinessType("MATTER");value.setBusinessId(8L);value.setOwnerDeptId(3L);return value;}
}
