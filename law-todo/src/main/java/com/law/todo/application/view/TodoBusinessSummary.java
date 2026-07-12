package com.law.todo.application.view;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record TodoBusinessSummary(String businessType,Long businessId,long activeCount,long overdueCount,
    LocalDateTime nearestDueAt,List<Long> ownerIds,Map<String,Object> recentAction) {}
