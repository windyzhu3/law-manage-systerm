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

    @Test void relation_specific_read_never_falls_back_to_another_visible_relation()
    {
        FileBusinessRelation privateRelation=relation(1L,"PRIVATE",8L,3L);
        FileBusinessRelation publicRelation=relation(2L,"BUSINESS",8L,4L);
        when(repository.findActiveRelations(10L)).thenReturn(List.of(privateRelation,publicRelation));
        when(checker.supports("CASE")).thenReturn(true);when(checker.canRead("CASE",9L,7L,3L)).thenReturn(true);

        assertThrows(FileAccessDeniedException.class,
            ()->new FileAccessPolicy(repository,List.of(checker)).requireCanReadRelation(10L,1L,actor));
    }

    private FileBusinessRelation relation(String visibility,Long creator,Long dept)
    {return relation(1L,visibility,creator,dept);}
    private FileBusinessRelation relation(Long id,String visibility,Long creator,Long dept)
    {
        Long scopeDept="DEPARTMENT".equals(visibility)?dept:0L;
        Long scopeUser="PRIVATE".equals(visibility)?creator:0L;
        return new FileBusinessRelation(id,10L,"CASE",9L,"PROOF",visibility,scopeDept,scopeUser,creator,dept,true);
    }
}
