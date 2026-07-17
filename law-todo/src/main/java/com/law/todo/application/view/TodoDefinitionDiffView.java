package com.law.todo.application.view;

import java.util.List;

public record TodoDefinitionDiffView(long leftVersionId,long rightVersionId,List<Change> changes,Risk overallRisk)
{
    public TodoDefinitionDiffView { changes=changes==null?List.of():List.copyOf(changes); }
    public enum ChangeType { ADDED,REMOVED,MODIFIED }
    public enum Risk { NONE,LOW,MEDIUM,HIGH,BLOCKING }
    public record Change(String section,String path,ChangeType type,Object before,Object after,Risk risk,String reason) { }
}
