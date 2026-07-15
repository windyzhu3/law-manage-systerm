package com.law.business.contract.dto;

import jakarta.validation.constraints.NotNull;

public class ContractTemplateUpdateCommand extends ContractTemplateCreateCommand
{
    @NotNull(message = "请选择合同模板") private Long templateId;
    public Long getTemplateId(){return templateId;} public void setTemplateId(Long v){templateId=v;}
}
