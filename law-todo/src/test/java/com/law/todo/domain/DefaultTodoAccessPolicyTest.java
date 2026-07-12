package com.law.todo.domain;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.law.todo.domain.model.TodoInstance;
import com.law.todo.mapper.TodoMapper;

@ExtendWith(MockitoExtension.class)
class DefaultTodoAccessPolicyTest
{
    @Mock TodoMapper mapper;
    @Test void roleDepartmentOrPostCandidateCanClaim(){TodoInstance t=new TodoInstance();t.setTodoId(1L);when(mapper.countCandidateAccess(1L,7L,3L)).thenReturn(1);assertTrue(new DefaultTodoAccessPolicy(mapper).canClaim(t,7L,3L));}
    @Test void copiedUserCanView(){TodoInstance t=new TodoInstance();t.setTodoId(1L);when(mapper.countCcAccess(1L,7L)).thenReturn(1);assertTrue(new DefaultTodoAccessPolicy(mapper).canView(t,7L,3L));}
    @Test void departmentSupervisorCanViewSubordinateTodo(){TodoInstance t=new TodoInstance();t.setTodoId(1L);when(mapper.countSupervisorAccess(1L,7L)).thenReturn(1);assertTrue(new DefaultTodoAccessPolicy(mapper).canView(t,7L,3L));}
    @Test void copiedUserCannotReview(){TodoInstance t=new TodoInstance();t.setTodoId(1L);when(mapper.countSupervisorAccess(1L,7L)).thenReturn(0);org.junit.jupiter.api.Assertions.assertFalse(new DefaultTodoAccessPolicy(mapper).canReview(t,7L));}
    @Test void supervisorCanReview(){TodoInstance t=new TodoInstance();t.setTodoId(1L);when(mapper.countSupervisorAccess(1L,7L)).thenReturn(1);assertTrue(new DefaultTodoAccessPolicy(mapper).canReview(t,7L));}
}
