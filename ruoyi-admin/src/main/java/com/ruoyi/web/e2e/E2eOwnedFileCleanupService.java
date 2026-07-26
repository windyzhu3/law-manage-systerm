package com.ruoyi.web.e2e;

import com.law.file.application.FileObjectService;
import com.law.file.application.FileObjectService.RetireFileObjectCommand;
import com.law.file.application.FileObjectService.RetireFileObjectView;
import com.law.file.domain.FileObject.FileActor;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionOperations;

/**
 * E2E-only adapter around the production file lifecycle. It does not grant a
 * business role any file-governance permission: the exact fixture ownership is
 * checked here and the real relation-scoped retirement service remains the
 * implementation boundary.
 */
public class E2eOwnedFileCleanupService
{
    static final String OWNERSHIP_SQL="""
        select l.lead_id,l.owner_id,l.dept_id
        from file_object f
        join file_business_relation r on r.file_object_id=f.file_object_id
        join biz_lead l on r.business_type='LEAD' and l.lead_id=r.business_id
        where f.file_object_id=? and r.relation_id=?
          and l.lead_no like ? escape '\\\\'
          and l.create_by='lead-e2e'
          and f.created_by=? and r.created_by=?
        for update
        """;
    static final String TEMPORARY_ACCESS_SQL="""
        update biz_lead set owner_id=?,dept_id=?
        where lead_id=? and create_by='lead-e2e'
        """;
    static final String RESTORE_ACCESS_SQL="""
        update biz_lead set owner_id=?,dept_id=?
        where lead_id=? and create_by='lead-e2e'
        """;
    private static final Pattern RUN_ID=Pattern.compile("[A-Za-z0-9_]{1,10}");

    private final JdbcTemplate jdbc;
    private final FileObjectService files;
    private final TransactionOperations transactions;

    public E2eOwnedFileCleanupService(JdbcTemplate jdbc,FileObjectService files,
        TransactionOperations transactions)
    {
        this.jdbc=jdbc;
        this.files=files;
        this.transactions=transactions;
    }

    public RetireFileObjectView retire(Long fileObjectId,CleanupCommand command,FileActor actor)
    {
        if(fileObjectId==null||fileObjectId<=0||command==null||command.relationId()==null
            ||command.relationId()<=0||command.actionId()==null||command.actionId().isBlank()
            ||command.actionId().length()>128||command.runId()==null
            ||!RUN_ID.matcher(command.runId()).matches())
            throw new IllegalArgumentException("Exact fileObjectId, relationId, actionId and runId are required");
        return transactions.execute(status->retireInTransaction(fileObjectId,command,actor));
    }

    private RetireFileObjectView retireInTransaction(Long fileObjectId,CleanupCommand command,
        FileActor actor)
    {
        String prefix=("LEAD_E2E_"+command.runId()+"_")
            .replace("\\","\\\\").replace("_","\\_").replace("%","\\%");
        var fixtures=jdbc.query(OWNERSHIP_SQL,(rs,row)->new FixtureAccess(
            rs.getLong("lead_id"),(Long)rs.getObject("owner_id"),(Long)rs.getObject("dept_id")),
            fileObjectId,command.relationId(),prefix+"%",actor.userId(),actor.userId());
        if(fixtures.size()!=1)
            throw new AccessDeniedException("File relation is outside the verified E2E fixture");
        FixtureAccess fixture=fixtures.get(0);

        // A completed fixture may legitimately have moved to a pool and lost its
        // owner. Restore only this locked E2E lead long enough for the production
        // business-scope policy and real file lifecycle to run, then put the
        // asserted business state back before the transaction commits.
        if(jdbc.update(TEMPORARY_ACCESS_SQL,actor.userId(),actor.deptId(),fixture.leadId())!=1)
            throw new AccessDeniedException("Verified E2E fixture changed concurrently");
        try
        {
            return files.retire(fileObjectId,
                new RetireFileObjectCommand(command.actionId(),command.relationId(),true),actor);
        }
        finally
        {
            if(jdbc.update(RESTORE_ACCESS_SQL,fixture.ownerId(),fixture.deptId(),fixture.leadId())!=1)
                throw new IllegalStateException("Unable to restore verified E2E fixture access state");
        }
    }

    public record CleanupCommand(String actionId,Long relationId,String runId){}
    private record FixtureAccess(Long leadId,Long ownerId,Long deptId){}
}
