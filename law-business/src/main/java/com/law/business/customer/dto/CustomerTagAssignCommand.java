package com.law.business.customer.dto;

import jakarta.validation.constraints.NotNull;

public class CustomerTagAssignCommand
{
    @NotNull(message = "客户不能为空")
    private Long customerId;

    @NotNull(message = "标签集合不能为空")
    private Long[] tagIds;

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public Long[] getTagIds() { return tagIds; }
    public void setTagIds(Long[] tagIds) { this.tagIds = tagIds; }
}
