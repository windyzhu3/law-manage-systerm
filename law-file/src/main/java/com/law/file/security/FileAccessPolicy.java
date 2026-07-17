package com.law.file.security;

import java.util.List;

import com.law.file.domain.FileObject.FileActor;
import com.law.file.domain.FileObject.FileBusinessRelation;
import com.law.file.repository.FileObjectRepository;
import com.law.file.spi.FileBusinessAccessChecker;
import org.springframework.stereotype.Component;

@Component
public class FileAccessPolicy
{
    private final FileObjectRepository repository;
    private final List<FileBusinessAccessChecker> checkers;
    public FileAccessPolicy(FileObjectRepository repository,List<FileBusinessAccessChecker> checkers)
    {this.repository=repository;this.checkers=checkers==null?List.of():List.copyOf(checkers);}

    public void requireCanWrite(String businessType,Long businessId,FileActor actor)
    {
        FileBusinessAccessChecker checker=checker(businessType);
        if(!checker.canWrite(businessType,businessId,actor.userId(),actor.deptId()))deny();
    }
    public List<FileBusinessRelation> requireCanRead(Long fileObjectId,FileActor actor)
    {return require(fileObjectId,actor,false);}
    public FileBusinessRelation requireCanReadRelation(Long fileObjectId,Long relationId,FileActor actor)
    {
        return require(fileObjectId,actor,false).stream().filter(value->value.relationId().equals(relationId))
            .findFirst().orElseThrow(()->new FileAccessDeniedException("File relation is outside the actor's business scope"));
    }
    public FileBusinessRelation requireCanWriteRelation(Long fileObjectId,Long relationId,FileActor actor)
    {
        return require(fileObjectId,actor,true).stream().filter(value->value.relationId().equals(relationId))
            .findFirst().orElseThrow(()->new FileAccessDeniedException("File relation is outside the actor's business scope"));
    }
    public List<FileBusinessRelation> requireCanWrite(Long fileObjectId,FileActor actor)
    {return require(fileObjectId,actor,true);}
    private List<FileBusinessRelation> require(Long id,FileActor actor,boolean write)
    {
        List<FileBusinessRelation> relations=repository.findActiveRelations(id);
        if(relations==null||relations.isEmpty())deny();
        List<FileBusinessRelation> allowed=relations.stream().filter(r->visible(r,actor)&&authorized(r,actor,write)).toList();
        if(allowed.isEmpty())deny();
        return allowed;
    }
    private boolean authorized(FileBusinessRelation relation,FileActor actor,boolean write)
    {
        FileBusinessAccessChecker checker=checkers.stream().filter(c->c.supports(relation.businessType())).findFirst().orElse(null);
        return checker!=null&&(write?checker.canWrite(relation.businessType(),relation.businessId(),actor.userId(),actor.deptId())
            :checker.canRead(relation.businessType(),relation.businessId(),actor.userId(),actor.deptId()));
    }
    private boolean visible(FileBusinessRelation relation,FileActor actor)
    {
        return switch(relation.visibility()) {
            case "BUSINESS" -> true;
            case "DEPARTMENT" -> actor.deptId()!=null&&actor.deptId().equals(relation.scopeDeptId());
            case "PRIVATE" -> actor.userId().equals(relation.scopeUserId());
            default -> false;
        };
    }
    private FileBusinessAccessChecker checker(String type)
    {return checkers.stream().filter(c->c.supports(type)).findFirst().orElseThrow(()->new FileAccessDeniedException("No business access policy"));}
    private static void deny(){throw new FileAccessDeniedException("File access is outside the actor's business scope");}
}
