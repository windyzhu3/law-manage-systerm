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
import com.ruoyi.system.service.matter.MatterArchiveService;

class ArchiveTodoFlowTest
{
    @Test void closeValidatorBlocksUnfinishedNodesAndUnpaidExpenses()
    {
        BizMatterMapper mapper=mock(BizMatterMapper.class);when(mapper.selectMatterById(8L)).thenReturn(Map.of("case_status","closing"));when(mapper.countUnfinishedNodes(8L)).thenReturn(1);TodoException error=assertThrows(TodoException.class,()->new ArchiveTodoValidator(mapper).validate(todo("CASE_CLOSE_CONFIRM"),Map.of("action","pass","opinion","同意","feeClearStatus","cleared")));assertEquals("CASE_NODES_UNFINISHED",error.getBusinessCode());
    }
    @Test void archiveValidatorRequiresReadyMaterialFiles()
    {
        BizMatterMapper mapper=mock(BizMatterMapper.class);when(mapper.selectMatterById(8L)).thenReturn(Map.of("case_status","closed"));TodoException error=assertThrows(TodoException.class,()->new ArchiveTodoValidator(mapper).validate(todo("CASE_ARCHIVE_CONFIRM"),Map.of("action","pass","opinion","同意","archiveNo","A1","materials",List.of(Map.of("materialName","卷宗","materialStatus","ready")))));assertEquals("ARCHIVE_MATERIAL_INCOMPLETE",error.getBusinessCode());
    }
    @Test void successfulArchiveCancelsOtherActiveTodos()
    {
        MatterArchiveService service=mock(MatterArchiveService.class);TodoMapper mapper=mock(TodoMapper.class);CaseArchiveTodoHandler handler=new CaseArchiveTodoHandler(service,mapper);TodoInstance todo=todo("CASE_ARCHIVE_CONFIRM");todo.setTodoId(44L);List<Map<String,Object>> materials=List.of(Map.of("materialName","卷宗","materialStatus","ready","fileUrl","/archive"));handler.complete(todo,Map.of("action","pass","opinion","同意","archiveNo","A1","materials",materials),1L,"admin");verify(service).archiveFromTodo(eq(8L),eq("pass"),eq("同意"),eq("A1"),eq(materials),any());verify(mapper).cancelActiveByBusiness("MATTER",8L,44L,"admin");
    }
    private TodoInstance todo(String code){TodoInstance value=new TodoInstance();value.setTemplateCode(code);value.setBusinessType("MATTER");value.setBusinessId(8L);value.setOwnerDeptId(3L);return value;}
}
