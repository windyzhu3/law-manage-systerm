package com.law.business.customer.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CustomerMergeCommand
{
    @NotNull(message = "请选择主客户")
    private Long mainCustomerId;

    @NotNull(message = "请选择待合并客户")
    private Long mergedCustomerId;

    @Size(max = 500, message = "合并说明不能超过500个字符")
    private String content;

    public Long getMainCustomerId() { return mainCustomerId; }
    public void setMainCustomerId(Long mainCustomerId) { this.mainCustomerId = mainCustomerId; }
    public Long getMergedCustomerId() { return mergedCustomerId; }
    public void setMergedCustomerId(Long mergedCustomerId) { this.mergedCustomerId = mergedCustomerId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
