package com.law.business.contract.dto;
import jakarta.validation.constraints.NotNull;
public class ContractReasonCommand {
    @NotNull(message="请选择合同") private Long contractId;
    private String reason;
    public Long getContractId(){return contractId;} public void setContractId(Long value){contractId=value;}
    public String getReason(){return reason;} public void setReason(String value){reason=value;}
}
