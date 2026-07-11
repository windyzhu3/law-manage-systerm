package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.BusinessEventRecord;

public interface BusinessEventMapper
{
    int insertBusinessEvent(BusinessEventRecord event);

    List<BusinessEventRecord> selectPendingEvents(@Param("limit") int limit);

    List<BusinessEventRecord> selectEventList(BusinessEventRecord query);

    int claimEvent(@Param("eventId") Long eventId);

    int markProcessed(@Param("eventId") Long eventId);

    int markFailed(@Param("eventId") Long eventId, @Param("errorMessage") String errorMessage);

    int requeueDead(@Param("eventId") Long eventId);
}
