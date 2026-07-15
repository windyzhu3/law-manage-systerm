package com.ruoyi.system.service.casecenter;

public record CaseConfirmContext(Long confirmId, Long caseId, String caseNo, String confirmStatus,
        Long confirmUserId, String confirmUserName, String confirmType) { }
