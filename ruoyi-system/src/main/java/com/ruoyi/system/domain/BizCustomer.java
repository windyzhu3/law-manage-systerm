package com.ruoyi.system.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.annotation.Excel.Type;
import com.ruoyi.common.core.domain.BaseEntity;

public class BizCustomer extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long customerId;
    @Excel(name = "客户编号", type = Type.EXPORT)
    private String customerNo;
    @Excel(name = "客户名称")
    private String customerName;
    @Excel(name = "客户类型", dictType = "law_customer_type", comboReadDict = true)
    private String customerType;
    @Excel(name = "手机号")
    private String mobile;
    @Excel(name = "微信")
    private String wechat;
    @Excel(name = "邮箱")
    private String email;
    @Excel(name = "公司名称")
    private String companyName;
    @Excel(name = "统一社会信用代码")
    private String creditCode;
    @Excel(name = "行业", dictType = "law_customer_industry", comboReadDict = true)
    private String industry;
    @Excel(name = "地区")
    private String region;
    @Excel(name = "来源")
    private String sourceCode;
    @Excel(name = "客户等级", dictType = "law_customer_level", comboReadDict = true)
    private String customerLevel;
    @Excel(name = "主要需求")
    private String mainDemand;
    private Long ownerId;
    @Excel(name = "负责人", type = Type.EXPORT)
    private String ownerName;
    private Long deptId;
    @Excel(name = "所属部门", type = Type.EXPORT)
    private String deptName;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "首次联系时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date firstContactTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "最近跟进时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss", type = Type.EXPORT)
    private Date lastFollowTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "下次跟进时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date nextFollowTime;
    private Long leadId;
    @Excel(name = "客户状态", dictType = "law_customer_status", comboReadDict = true, type = Type.EXPORT)
    private String status;
    private String delFlag;
    @Excel(name = "客户标签", type = Type.EXPORT)
    private String tagNames;
    private String tagColors;
    @Excel(name = "关联合同数", type = Type.EXPORT)
    private Long contractCount;
    private Long currentUserId;
    private Long currentDeptId;
    private Boolean dataScope;
    /** 标签筛选（仅查询条件，不入库） */
    private Long tagId;

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getCustomerNo() { return customerNo; }
    public void setCustomerNo(String customerNo) { this.customerNo = customerNo; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustomerType() { return customerType; }
    public void setCustomerType(String customerType) { this.customerType = customerType; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public String getWechat() { return wechat; }
    public void setWechat(String wechat) { this.wechat = wechat; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getCreditCode() { return creditCode; }
    public void setCreditCode(String creditCode) { this.creditCode = creditCode; }
    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    public String getCustomerLevel() { return customerLevel; }
    public void setCustomerLevel(String customerLevel) { this.customerLevel = customerLevel; }
    public String getMainDemand() { return mainDemand; }
    public void setMainDemand(String mainDemand) { this.mainDemand = mainDemand; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public Long getDeptId() { return deptId; }
    public void setDeptId(Long deptId) { this.deptId = deptId; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
    public Date getFirstContactTime() { return firstContactTime; }
    public void setFirstContactTime(Date firstContactTime) { this.firstContactTime = firstContactTime; }
    public Date getLastFollowTime() { return lastFollowTime; }
    public void setLastFollowTime(Date lastFollowTime) { this.lastFollowTime = lastFollowTime; }
    public Date getNextFollowTime() { return nextFollowTime; }
    public void setNextFollowTime(Date nextFollowTime) { this.nextFollowTime = nextFollowTime; }
    public Long getLeadId() { return leadId; }
    public void setLeadId(Long leadId) { this.leadId = leadId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }
    public String getTagNames() { return tagNames; }
    public void setTagNames(String tagNames) { this.tagNames = tagNames; }
    public String getTagColors() { return tagColors; }
    public void setTagColors(String tagColors) { this.tagColors = tagColors; }
    public Long getContractCount() { return contractCount; }
    public void setContractCount(Long contractCount) { this.contractCount = contractCount; }
    public Long getCurrentUserId() { return currentUserId; }
    public void setCurrentUserId(Long currentUserId) { this.currentUserId = currentUserId; }
    public Long getCurrentDeptId() { return currentDeptId; }
    public void setCurrentDeptId(Long currentDeptId) { this.currentDeptId = currentDeptId; }
    public Boolean getDataScope() { return dataScope; }
    public void setDataScope(Boolean dataScope) { this.dataScope = dataScope; }
    public Long getTagId() { return tagId; }
    public void setTagId(Long tagId) { this.tagId = tagId; }
}
