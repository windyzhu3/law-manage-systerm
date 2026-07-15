package com.ruoyi.system.service.casecenter;

public record CaseTransferContext(Long transferId, Long caseId, String caseNo, String transferNo,
        String transferStatus, Long fromLawyerId, String fromLawyerName,
        Long toLawyerId, String toLawyerName) { }
