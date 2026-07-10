package com.law.business.contract.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
public class ContractApprovalCommand {
    @NotNull(message="请选择合同") private Long contractId;
    @NotBlank(message="请选择审批动作") private String action;
    private String opinion;
    public Long getContractId(){return contractId;} public void setContractId(Long value){contractId=value;}
    public String getAction(){return action;} public void setAction(String value){action=value;}
    public String getOpinion(){return opinion;} public void setOpinion(String value){opinion=value;}
}
