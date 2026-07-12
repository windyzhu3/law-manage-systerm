package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.todo.domain.TodoException;
import com.law.todo.domain.TodoAccessPolicy;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;

@ExtendWith(MockitoExtension.class)
class TodoCollaborationServiceTest
{
    @Mock TodoMapper mapper; @Mock TodoAccessPolicy access;
    private TodoCollaborationService serviceForOwner(){TodoInstance todo=new TodoInstance();todo.setTodoId(1L);when(mapper.selectById(1L)).thenReturn(todo);when(access.canOperate(todo,7L)).thenReturn(true);return new TodoCollaborationService(mapper,access);}
    @Test void addsTypedAttachment(){when(mapper.insertAttachment(anyMap())).thenReturn(1);serviceForOwner().addAttachment(1L,"a-1","CONTACT_PROOF","call.mp3","/file/call.mp3",7L);verify(mapper).insertAttachment(anyMap());}
    @Test void rejectsBlankAttachmentUrl(){assertThrows(TodoException.class,()->new TodoCollaborationService(mapper,access).addAttachment(1L,"a-1","CONTACT_PROOF","call.mp3","",7L));}
    @Test void addsCcUser(){when(mapper.insertCc(1L,8L,"CC")).thenReturn(1);serviceForOwner().addCc(1L,8L,"CC",7L);verify(mapper).insertCc(1L,8L,"CC");}
    @Test void rejectsCollaborationWriteByNonOwner(){TodoInstance todo=new TodoInstance();todo.setTodoId(1L);when(mapper.selectById(1L)).thenReturn(todo);when(access.canOperate(todo,9L)).thenReturn(false);assertThrows(TodoException.class,()->new TodoCollaborationService(mapper,access).addCandidate(1L,"USER",8L,9L));}
}
