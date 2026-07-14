package com.law.business.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CustomerContactCreateCommand
{
    @NotNull(message = "客户不能为空")
    private Long customerId;

    @NotBlank(message = "联系人不能为空")
    @Size(max = 100, message = "联系人不能超过100个字符")
    private String contactName;

    @Size(max = 100, message = "职务不能超过100个字符")
    private String positionName;

    @NotBlank(message = "联系人手机号不能为空")
    @Pattern(regexp = "^[0-9+\\-\\s]{6,20}$", message = "联系人手机号格式不正确")
    private String mobile;

    @Size(max = 100, message = "微信号不能超过100个字符")
    private String wechat;

    @Email(message = "邮箱格式不正确")
    @Size(max = 255, message = "邮箱不能超过255个字符")
    private String email;

    @NotBlank(message = "联系人关系不能为空")
    private String relationType;

    private String keyContact;
    private Long ownerId;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public String getPositionName() { return positionName; }
    public void setPositionName(String positionName) { this.positionName = positionName; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public String getWechat() { return wechat; }
    public void setWechat(String wechat) { this.wechat = wechat; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getRelationType() { return relationType; }
    public void setRelationType(String relationType) { this.relationType = relationType; }
    public String getKeyContact() { return keyContact; }
    public void setKeyContact(String keyContact) { this.keyContact = keyContact; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
