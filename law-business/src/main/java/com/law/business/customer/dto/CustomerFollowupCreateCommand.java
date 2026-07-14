package com.law.business.customer.dto;

import java.util.Date;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CustomerFollowupCreateCommand
{
    @NotNull(message = "客户不能为空")
    private Long customerId;

    @NotBlank(message = "跟进方式不能为空")
    private String followType;

    @Size(max = 200, message = "跟进结果不能超过200个字符")
    private String followResult;

    private Date followTime;

    @NotBlank(message = "跟进内容不能为空")
    @Size(max = 1000, message = "跟进内容不能超过1000个字符")
    private String content;

    private Date nextFollowTime;

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getFollowType() { return followType; }
    public void setFollowType(String followType) { this.followType = followType; }
    public String getFollowResult() { return followResult; }
    public void setFollowResult(String followResult) { this.followResult = followResult; }
    public Date getFollowTime() { return followTime; }
    public void setFollowTime(Date followTime) { this.followTime = followTime; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Date getNextFollowTime() { return nextFollowTime; }
    public void setNextFollowTime(Date nextFollowTime) { this.nextFollowTime = nextFollowTime; }
}
