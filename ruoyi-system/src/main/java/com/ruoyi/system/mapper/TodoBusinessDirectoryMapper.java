package com.ruoyi.system.mapper;

import java.util.List;
import java.util.Map;

public interface TodoBusinessDirectoryMapper
{
    List<Map<String,Object>> selectVisibleBusinessObjects(Map<String,Object> query);
    long countVisibleBusinessObjects(Map<String,Object> query);
    long countUnscopedBusinessObjects(Map<String,Object> query);
    Map<String,Object> selectVisibleBusinessObject(Map<String,Object> query);
    Map<String,Object> selectVisibleBusinessPayload(Map<String,Object> query);
}
