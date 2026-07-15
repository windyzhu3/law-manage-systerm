package com.law.business.contract.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
public class ContractSignCommand {
    @NotNull(message="请选择合同") private Long contractId;
    @NotBlank(message="请选择签署状态") @Pattern(regexp="^[12]$",message="签署状态不合法") private String signStatus;
    public Long getContractId(){return contractId;} public void setContractId(Long value){contractId=value;}
    public String getSignStatus(){return signStatus;} public void setSignStatus(String value){signStatus=value;}
}
