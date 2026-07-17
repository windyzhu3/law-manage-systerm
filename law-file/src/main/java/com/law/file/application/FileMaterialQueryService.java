package com.law.file.application;

import java.util.ArrayList;
import java.util.List;

import com.law.file.domain.FileObject.FileActor;
import com.law.file.domain.FileObject.FileBusinessRelation;
import com.law.file.infrastructure.internal.FilePersistenceModel.StoredVersion;
import com.law.file.repository.FileObjectRepository;
import com.law.file.security.FileAccessDeniedException;
import com.law.file.security.FileAccessPolicy;
import org.springframework.stereotype.Service;

/** Authorized read model for active file-center material relations. */
@Service
public class FileMaterialQueryService
{
    private final FileObjectRepository repository;
    private final FileAccessPolicy access;
    public FileMaterialQueryService(FileObjectRepository repository,FileAccessPolicy access)
    {this.repository=repository;this.access=access;}

    public List<FileMaterialView> list(String businessType,Long businessId,FileActor actor)
    {
        if(businessType==null||businessType.isBlank()||businessId==null||businessId<=0)return List.of();
        List<FileMaterialView> result=new ArrayList<>();
        for(FileBusinessRelation relation:repository.findActiveRelations(businessType,businessId))
        {
            List<FileBusinessRelation> allowed;
            try{allowed=access.requireCanRead(relation.fileObjectId(),actor);}
            catch(FileAccessDeniedException denied){continue;}
            boolean visible=allowed.stream().anyMatch(value->value.relationId().equals(relation.relationId())
                && value.businessType().equals(businessType)&&value.businessId().equals(businessId));
            if(!visible)continue;
            StoredVersion current=repository.findCurrentVersion(relation.fileObjectId());
            if(current==null||current.metadata()==null)continue;
            result.add(new FileMaterialView(relation.relationId(),relation.fileObjectId(),relation.materialType(),
                current.metadata().originalFileName(),relation.visibility()));
        }
        return List.copyOf(result);
    }

    public record FileMaterialView(Long relationId,Long fileObjectId,String materialType,String fileName,String visibility) { }
}
