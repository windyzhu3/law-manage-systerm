package com.law.todo.application.view;

import java.time.LocalDateTime;

public record TodoDecisionView(Long decisionId,String code,String title,String description,boolean blocking,
        String status,String conclusion,String resolution,String decidedBy,LocalDateTime decidedTime,
        String createBy,LocalDateTime createTime,String updateBy,LocalDateTime updateTime,int version) { }
