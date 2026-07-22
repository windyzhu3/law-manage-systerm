package com.law.todo.spi;

import java.util.List;
import java.util.Optional;

import com.law.todo.application.command.TodoActionCommands.Actor;

/** Actor-scoped business directory. Implementations must filter before count, paging, and projection. */
public interface TodoBusinessDirectoryAccess
{
    boolean supports(String businessType);
    DirectoryPage search(String businessType,String keyword,int offset,int limit,Actor actor);
    Optional<DirectoryEntry> findVisible(String businessType,Long businessId,Actor actor);

    record DirectoryEntry(long businessId,String businessNo,String businessName,String businessType,String source,boolean sample)
    {public DirectoryEntry(long businessId,String businessNo,String businessName,String businessType)
        {this(businessId,businessNo,businessName,businessType,"BUSINESS_DATA",false);}}
    record DirectoryPage(List<DirectoryEntry> rows,long total,String emptyReason,String source)
    {
        public DirectoryPage{rows=rows==null?List.of():List.copyOf(rows);}
        public DirectoryPage(List<DirectoryEntry> rows,long total){this(rows,total,null,"BUSINESS_DATA");}
    }
}
