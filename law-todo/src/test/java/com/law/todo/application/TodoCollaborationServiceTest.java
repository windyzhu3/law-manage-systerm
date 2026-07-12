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
import com.law.todo.mapper.TodoMapper;

@ExtendWith(MockitoExtension.class)
class TodoCollaborationServiceTest
{
    @Mock TodoMapper mapper;
    @Test void addsTypedAttachment(){when(mapper.insertAttachment(anyMap())).thenReturn(1);new TodoCollaborationService(mapper).addAttachment(1L,"a-1","CONTACT_PROOF","call.mp3","/file/call.mp3",7L);verify(mapper).insertAttachment(anyMap());}
    @Test void rejectsBlankAttachmentUrl(){assertThrows(TodoException.class,()->new TodoCollaborationService(mapper).addAttachment(1L,"a-1","CONTACT_PROOF","call.mp3","",7L));}
    @Test void addsCcUser(){when(mapper.insertCc(1L,8L,"CC")).thenReturn(1);new TodoCollaborationService(mapper).addCc(1L,8L,"CC");verify(mapper).insertCc(1L,8L,"CC");}
}
