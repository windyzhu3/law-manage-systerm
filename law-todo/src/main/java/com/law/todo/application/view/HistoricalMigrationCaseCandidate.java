package com.law.todo.application.view;

public record HistoricalMigrationCaseCandidate(Long caseId,String caseNo,String caseName,String caseType,
        String caseStatus,Long contractId,Long mainLawyerId,Long deptId) { }
