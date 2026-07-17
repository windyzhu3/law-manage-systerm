package com.law.file.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import com.law.file.domain.FileObject.FileActor;
import com.law.file.domain.FileObject.FileBusinessRelation;
import com.law.file.repository.FileObjectRepository;
import com.law.file.spi.FileBusinessAccessChecker;
import org.junit.jupiter.api.Test;

class FileAccessPolicyTest
{
    private final FileObjectRepository repository=mock(FileObjectRepository.class);
    private final FileBusinessAccessChecker checker=mock(FileBusinessAccessChecker.class);
    private final FileActor actor=new FileActor(7L,"alice",3L);

    @Test void business_visibility_still_requires_the_underlying_business_data_scope()
    {
        when(repository.findActiveRelations(10L)).thenReturn(List.of(relation("BUSINESS",8L,4L)));
        when(checker.supports("CASE")).thenReturn(true);when(checker.canRead("CASE",9L,7L,3L)).thenReturn(false);
        assertThrows(FileAccessDeniedException.class,()->new FileAccessPolicy(repository,List.of(checker)).requireCanRead(10L,actor));
    }

    @Test void private_visibility_is_limited_to_relation_creator_after_business_authorization()
    {
        when(repository.findActiveRelations(10L)).thenReturn(List.of(relation("PRIVATE",8L,3L)));
        when(checker.supports("CASE")).thenReturn(true);when(checker.canRead("CASE",9L,7L,3L)).thenReturn(true);
        assertThrows(FileAccessDeniedException.class,()->new FileAccessPolicy(repository,List.of(checker)).requireCanRead(10L,actor));
    }

    @Test void department_visibility_allows_same_department_with_business_access()
    {
        FileBusinessRelation expected=relation("DEPARTMENT",8L,3L);
        when(repository.findActiveRelations(10L)).thenReturn(List.of(expected));
        when(checker.supports("CASE")).thenReturn(true);when(checker.canRead("CASE",9L,7L,3L)).thenReturn(true);
        assertEquals(List.of(expected),new FileAccessPolicy(repository,List.of(checker)).requireCanRead(10L,actor));
    }

    private FileBusinessRelation relation(String visibility,Long creator,Long dept)
    {return new FileBusinessRelation(1L,"rel-1",10L,"CASE",9L,"PROOF",visibility,creator,dept,true);}
}
