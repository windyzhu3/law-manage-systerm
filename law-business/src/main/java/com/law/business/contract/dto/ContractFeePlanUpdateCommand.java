package com.law.business.contract.dto;

import jakarta.validation.constraints.NotNull;

public class ContractFeePlanUpdateCommand extends ContractFeePlanCreateCommand
{
    @NotNull(message = "请选择收费计划") private Long planId;
    public Long getPlanId(){return planId;} public void setPlanId(Long v){planId=v;}
}
