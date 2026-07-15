package com.law.business.contract.dto;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public class ContractReasonCommand {
    @NotNull(message="请选择合同") private Long contractId;
    @NotBlank(message="请输入原因") @Size(max=500,message="原因不能超过500个字符") private String reason;
    public Long getContractId(){return contractId;} public void setContractId(Long value){contractId=value;}
    public String getReason(){return reason;} public void setReason(String value){reason=value;}
}
