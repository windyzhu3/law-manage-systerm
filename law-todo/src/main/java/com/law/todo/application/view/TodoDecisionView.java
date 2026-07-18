package com.law.todo.application.view;

import java.time.LocalDateTime;
import java.util.List;

public record TodoDecisionView(Long decisionId,String code,String title,String description,boolean blocking,
        String status,String conclusion,String resolution,String decidedBy,LocalDateTime decidedTime,
        Long ownerUserId,String ownerUserName,String ownerNickName,String ownerRoleKey,LocalDateTime dueAt,String deliveryPhase,
        String createBy,LocalDateTime createTime,String updateBy,LocalDateTime updateTime,int version,List<String> impactedTemplateCodes) { }
