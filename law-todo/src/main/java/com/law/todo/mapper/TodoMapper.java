package com.law.todo.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.law.todo.domain.model.TodoInstance;

public interface TodoMapper
{
    int insertInstance(TodoInstance value);
    TodoInstance selectById(Long todoId);
    TodoInstance selectByTriggerKey(String key);
    TodoInstance selectByNextKey(String key);
    int updateStatusConditionally(@Param("todoId") Long todoId,@Param("fromStatus") String fromStatus,@Param("toStatus") String toStatus,@Param("ownerId") Long ownerId,@Param("operator") String operator);
    int insertActionIfAbsent(Map<String,Object> action);
    Map<String,Object> selectActionById(String actionId);
    int insertCandidate(Map<String,Object> candidate);
    List<Map<String,Object>> selectCandidates(Long todoId);
    int insertRelation(Map<String,Object> relation);
    Map<String,Object> selectTemplateVersion(@Param("templateId") Long templateId,@Param("versionNo") int versionNo);
    int insertTemplateVersion(Map<String,Object> version);
    int updateTemplateCurrentVersion(@Param("templateId") Long templateId,@Param("versionNo") int versionNo,@Param("operator") String operator);
}
