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

    record DirectoryEntry(long businessId,String businessNo,String businessName,String businessType) { }
    record DirectoryPage(List<DirectoryEntry> rows,long total)
    {
        public DirectoryPage{rows=rows==null?List.of():List.copyOf(rows);}
    }
}
