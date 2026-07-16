package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.todo.domain.TodoAccessPolicy;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.mapper.TodoMapper;

@ExtendWith(MockitoExtension.class)
class TodoQueryServiceTest
{
    @Mock TodoMapper mapper;@Mock TodoAccessPolicy access;
    @Test void rejectsInvisibleDetail(){TodoInstance t=new TodoInstance();t.setTodoId(1L);when(mapper.selectById(1L)).thenReturn(t);when(access.canView(t,7L,3L)).thenReturn(false);TodoException e=assertThrows(TodoException.class,()->new TodoQueryService(mapper,access).detail(1L,7L,3L));assertEquals("TODO_ACCESS_DENIED",e.getBusinessCode());}
    @Test void dashboardReturnsMapperMetrics(){when(mapper.selectDashboard(7L,3L)).thenReturn(Map.of("mine",4));assertEquals(4,new TodoQueryService(mapper,access).dashboard(7L,3L).get("mine"));}
    @Test void detailViewContainsAuditAndAttachments(){TodoInstance t=new TodoInstance();t.setTodoId(1L);when(mapper.selectById(1L)).thenReturn(t);when(access.canView(t,7L,3L)).thenReturn(true);when(mapper.selectActionTimeline(1L)).thenReturn(java.util.List.of(Map.of("action_type","CLAIM")));when(mapper.selectAttachments(1L)).thenReturn(java.util.List.of(Map.of("file_name","a.pdf")));Map<String,Object> view=new TodoQueryService(mapper,access).detailView(1L,7L,3L);assertEquals(1,((java.util.List<?>)view.get("actions")).size());assertEquals(1,((java.util.List<?>)view.get("attachments")).size());}
    @Test void formUsesPublishedDefinitionSnapshot()
    {
        TodoInstance t=new TodoInstance();t.setTodoId(2L);t.setStatus("SUBMITTED");t.setTemplateVersionId(9L);
        when(mapper.selectById(2L)).thenReturn(t);when(access.canView(t,7L,3L)).thenReturn(true);
        when(mapper.selectTemplateVersionById(9L)).thenReturn(Map.of(
                "compiled_json", "{\"schemaVersion\":1,\"templateCode\":\"T\",\"dod\":{\"config\":{\"requiredFields\":[\"result\"]}},\"ui\":{\"config\":{\"fields\":[\"result\"],\"defaults\":{\"result\":\"OK\"}}},\"autoActions\":[],\"decisionRefs\":[],\"acceptanceRefs\":[]}",
                "definition_hash", "abc"));

        var form=new TodoQueryService(mapper,access).form(2L,new Actor(7L,"alice",3L));

        assertEquals("COMPLETE",form.action());assertEquals("abc",form.definitionHash());
        assertEquals("OK",form.defaults().get("result"));
    }
}
