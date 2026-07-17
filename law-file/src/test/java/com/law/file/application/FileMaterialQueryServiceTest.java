package com.law.file.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import com.law.file.domain.FileObject.FileActor;
import com.law.file.domain.FileObject.FileBusinessRelation;
import com.law.file.domain.FileObject.FileVersion;
import com.law.file.infrastructure.internal.FilePersistenceModel.StoredVersion;
import com.law.file.repository.FileObjectRepository;
import com.law.file.security.FileAccessDeniedException;
import com.law.file.security.FileAccessPolicy;
import org.junit.jupiter.api.Test;

class FileMaterialQueryServiceTest
{
    @Test void lists_only_current_materials_visible_through_the_requested_business_context()
    {
        FileObjectRepository repository=mock(FileObjectRepository.class);
        FileAccessPolicy access=mock(FileAccessPolicy.class);
        FileActor actor=new FileActor(7L,"alice",3L);
        FileBusinessRelation visible=relation(1L,41L,"CONTRACT",77L,"SIGNED_CONTRACT");
        FileBusinessRelation hidden=relation(2L,42L,"CONTRACT",77L,"PRIVATE_NOTE");
        when(repository.findActiveRelations("CONTRACT",77L)).thenReturn(List.of(visible,hidden));
        when(access.requireCanRead(41L,actor)).thenReturn(List.of(visible));
        when(access.requireCanRead(42L,actor)).thenThrow(new FileAccessDeniedException("hidden"));
        when(repository.findCurrentVersion(41L)).thenReturn(new StoredVersion(
            new FileVersion(91L,41L,1,"signed.pdf","application/pdf",3L,"a".repeat(64),"Initial",7L,Instant.EPOCH),"secret-key"));

        var values=new FileMaterialQueryService(repository,access).list("CONTRACT",77L,actor);

        assertEquals(List.of(new FileMaterialQueryService.FileMaterialView(
            1L,41L,"SIGNED_CONTRACT","signed.pdf","BUSINESS")),values);
    }

    private FileBusinessRelation relation(Long relationId,Long fileId,String type,Long businessId,String material)
    {return new FileBusinessRelation(relationId,fileId,type,businessId,material,"BUSINESS",0L,0L,7L,3L,true);}
}
