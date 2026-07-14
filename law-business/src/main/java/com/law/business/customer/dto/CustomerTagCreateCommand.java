package com.law.business.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CustomerTagCreateCommand
{
    @NotBlank(message = "标签名称不能为空")
    @Size(max = 100, message = "标签名称不能超过100个字符")
    private String tagName;

    @Pattern(regexp = "^$|^#[0-9A-Fa-f]{6}$", message = "标签颜色格式不正确")
    private String tagColor;

    private Integer orderNum;

    @NotBlank(message = "标签状态不能为空")
    private String status;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;

    public String getTagName() { return tagName; }
    public void setTagName(String tagName) { this.tagName = tagName; }
    public String getTagColor() { return tagColor; }
    public void setTagColor(String tagColor) { this.tagColor = tagColor; }
    public Integer getOrderNum() { return orderNum; }
    public void setOrderNum(Integer orderNum) { this.orderNum = orderNum; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
