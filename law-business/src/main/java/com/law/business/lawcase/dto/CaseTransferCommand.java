package com.law.business.lawcase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CaseTransferCommand
{
    @NotNull(message = "请选择案件") private Long caseId;
    @NotNull(message = "请选择拟转入律师") private Long toLawyerId;
    @NotBlank(message = "请选择转案原因") private String transferReason;
    @NotBlank(message = "请选择风险等级") private String riskLevel;
    @NotBlank(message = "转案详情不能为空") private String detail;

    public Long getCaseId(){return caseId;} public void setCaseId(Long v){caseId=v;}
    public Long getToLawyerId(){return toLawyerId;} public void setToLawyerId(Long v){toLawyerId=v;}
    public String getTransferReason(){return transferReason;} public void setTransferReason(String v){transferReason=v;}
    public String getRiskLevel(){return riskLevel;} public void setRiskLevel(String v){riskLevel=v;}
    public String getDetail(){return detail;} public void setDetail(String v){detail=v;}
}
