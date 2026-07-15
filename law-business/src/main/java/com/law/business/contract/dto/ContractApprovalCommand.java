package com.law.business.contract.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
public class ContractApprovalCommand {
    @NotNull(message="请选择合同") private Long contractId;
    @NotBlank(message="请选择审批动作") @Pattern(regexp="^(pass|reject|back)$",message="审批动作不合法") private String action;
    @NotBlank(message="请输入审批意见") @Size(max=500,message="审批意见不能超过500个字符") private String opinion;
    public Long getContractId(){return contractId;} public void setContractId(Long value){contractId=value;}
    public String getAction(){return action;} public void setAction(String value){action=value;}
    public String getOpinion(){return opinion;} public void setOpinion(String value){opinion=value;}
}
