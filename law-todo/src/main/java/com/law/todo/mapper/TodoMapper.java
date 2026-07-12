package com.law.todo.mapper;

import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
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
    List<Map<String,Object>> selectTriggerRules(@Param("eventType") String eventType,@Param("businessType") String businessType);
    List<Map<String,Object>> selectSlaScanItems(@Param("now") LocalDateTime now);
    int markSlaThreshold(@Param("todoId") Long todoId,@Param("threshold") String threshold,@Param("now") LocalDateTime now);
    int pauseSla(@Param("todoId") Long todoId,@Param("now") LocalDateTime now);
    int resumeSla(@Param("todoId") Long todoId,@Param("now") LocalDateTime now);
    Map<String,Object> selectDashboard(@Param("userId") Long userId,@Param("deptId") Long deptId);
    List<Map<String,Object>> selectTodoList(Map<String,Object> query);
    Map<String,Object> selectTemplateVersionById(Long versionId);
    List<String> selectAttachmentTypes(Long todoId);
    int insertAttachment(Map<String,Object> attachment);
    int insertCc(@Param("todoId") Long todoId,@Param("userId") Long userId,@Param("ccType") String ccType);
    List<Map<String,Object>> selectCalendars();
    int insertCalendar(Map<String,Object> calendar);
    int updateCalendar(Map<String,Object> calendar);
    Map<String,Object> selectCalendarByCode(String code);
    int insertSlaRecord(Map<String,Object> record);
}
