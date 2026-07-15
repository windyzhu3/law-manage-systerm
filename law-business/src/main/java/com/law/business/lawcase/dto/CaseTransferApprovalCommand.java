package com.law.business.lawcase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CaseTransferApprovalCommand
{
    @NotNull(message = "请选择转案申请") private Long transferId;
    @NotBlank(message = "请选择审批动作") private String action;
    @NotBlank(message = "审批意见必填") private String opinion;
    public Long getTransferId(){return transferId;} public void setTransferId(Long v){transferId=v;}
    public String getAction(){return action;} public void setAction(String v){action=v;}
    public String getOpinion(){return opinion;} public void setOpinion(String v){opinion=v;}
}
