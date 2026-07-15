package com.law.business.lawcase.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class LawyerProfileSaveCommand
{
    @NotNull(message = "请选择律师") private Long userId;
    @NotBlank(message = "请选择业务角色") private String lawyerRole;
    @NotBlank(message = "请选择专业方向") private String specialties;
    @DecimalMin(value = "0.01", message = "负载上限必须大于0") private BigDecimal loadLimit;
    @DecimalMin(value = "0.0", inclusive = true, message = "平均响应时长不能小于0") private BigDecimal avgResponseHours;
    @NotBlank(message = "请选择可分案状态") private String assignEnabled;
    private String remark;

    public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;}
    public String getLawyerRole(){return lawyerRole;} public void setLawyerRole(String v){lawyerRole=v;}
    public String getSpecialties(){return specialties;} public void setSpecialties(String v){specialties=v;}
    public BigDecimal getLoadLimit(){return loadLimit;} public void setLoadLimit(BigDecimal v){loadLimit=v;}
    public BigDecimal getAvgResponseHours(){return avgResponseHours;} public void setAvgResponseHours(BigDecimal v){avgResponseHours=v;}
    public String getAssignEnabled(){return assignEnabled;} public void setAssignEnabled(String v){assignEnabled=v;}
    public String getRemark(){return remark;} public void setRemark(String v){remark=v;}
}
