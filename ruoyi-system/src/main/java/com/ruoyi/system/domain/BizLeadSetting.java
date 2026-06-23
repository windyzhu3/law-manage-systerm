package com.ruoyi.system.domain;

import com.ruoyi.common.core.domain.BaseEntity;

/**
 * Lead configurable option.
 */
public class BizLeadSetting extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long settingId;
    private String settingType;
    private String settingCode;
    private String settingName;
    private String color;
    private Integer orderNum;
    private String status;

    public Long getSettingId() { return settingId; }
    public void setSettingId(Long settingId) { this.settingId = settingId; }
    public String getSettingType() { return settingType; }
    public void setSettingType(String settingType) { this.settingType = settingType; }
    public String getSettingCode() { return settingCode; }
    public void setSettingCode(String settingCode) { this.settingCode = settingCode; }
    public String getSettingName() { return settingName; }
    public void setSettingName(String settingName) { this.settingName = settingName; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public Integer getOrderNum() { return orderNum; }
    public void setOrderNum(Integer orderNum) { this.orderNum = orderNum; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}

