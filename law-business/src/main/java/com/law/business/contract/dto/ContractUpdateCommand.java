package com.law.business.contract.dto;

import jakarta.validation.constraints.NotNull;

public class ContractUpdateCommand extends ContractCreateCommand
{
    @NotNull(message = "请选择合同")
    private Long contractId;

    public Long getContractId() { return contractId; }
    public void setContractId(Long value) { contractId = value; }
}
