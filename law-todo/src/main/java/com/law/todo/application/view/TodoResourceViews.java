package com.law.todo.application.view;

import java.time.LocalDateTime;
import java.util.List;

public final class TodoResourceViews
{
    private TodoResourceViews() { }

    public record EventResourceListItem(long eventCatalogId,String eventType,String eventName,String description,Integer payloadVersion,
            String businessObjectType,String sourceModule,String schemaStatus,String status,Integer version,
            long referenceCount,LocalDateTime updateTime)
    {
        public EventResourceListItem(long eventCatalogId,String eventType,String eventName,Integer payloadVersion,
                String businessObjectType,String sourceModule,String schemaStatus,String status,Integer version,
                long referenceCount,LocalDateTime updateTime)
        {this(eventCatalogId,eventType,eventName,null,payloadVersion,businessObjectType,sourceModule,schemaStatus,
                status,version,referenceCount,updateTime);}
    }

    public record EventResourceReference(String referenceType,Long referenceId,String referenceCode,
            String referenceName,String referenceStatus) { }

    public record EventResourceDetail(long eventCatalogId,String eventType,String eventName,String description,
            Integer payloadVersion,String businessObjectType,String sourceModule,String producer,
            String payloadSchemaJson,String samplePayloadJson,String schemaStatus,String status,Integer version,
            String createBy,LocalDateTime createTime,String updateBy,LocalDateTime updateTime,
            List<EventResourceReference> references)
    {
        public EventResourceDetail{references=references==null?List.of():List.copyOf(references);}
    }

    public record EventResourcePage(List<EventResourceListItem> rows,long total)
    {public EventResourcePage{rows=rows==null?List.of():List.copyOf(rows);}}
}
