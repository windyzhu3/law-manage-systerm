package com.law.business.lawcase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CaseConfirmCommand
{
    @NotNull(message = "请选择确认信息") private Long confirmId;
    @NotBlank(message = "请选择确认结果") private String confirmResult;
    private String remark;
    public Long getConfirmId(){return confirmId;} public void setConfirmId(Long v){confirmId=v;}
    public String getConfirmResult(){return confirmResult;} public void setConfirmResult(String v){confirmResult=v;}
    public String getRemark(){return remark;} public void setRemark(String v){remark=v;}
}
