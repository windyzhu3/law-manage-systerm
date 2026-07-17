package com.law.file.application;

import java.util.List;
import com.law.file.domain.FileObject.FileMaterial;
import com.law.file.repository.FileObjectRepository;
import org.springframework.stereotype.Service;

@Service
public class FileMaterialQuery
{
    private final FileObjectRepository repository;
    public FileMaterialQuery(FileObjectRepository repository){this.repository=repository;}
    public List<FileMaterial> resolve(String businessType,Long businessId,List<Long> fileObjectIds)
    {return fileObjectIds==null||fileObjectIds.isEmpty()?List.of():repository.findMaterials(businessType,businessId,List.copyOf(fileObjectIds));}
}
