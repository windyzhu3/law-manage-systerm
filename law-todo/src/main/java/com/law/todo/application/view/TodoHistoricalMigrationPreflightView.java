package com.law.todo.application.view;

import java.time.Instant;
import java.util.List;

public record TodoHistoricalMigrationPreflightView(String gateCode,Instant generatedAt,
        long activeCaseCount,long deletedCaseCount,long historicalTodoCount,long orphanTodoVersionCount,
        long exceptionCandidateCount,List<HistoricalCaseGroupView> groups) { }
