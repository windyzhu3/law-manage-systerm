package com.law.business.lawcase.dto;

import java.util.HashMap;
import java.util.Map;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CaseConfirmCommand
{
    @NotNull(message = "请选择确认信息") private Long confirmId;
    @NotBlank(message = "请选择确认结果") private String confirmResult;
    private String remark;
    public Map<String,Object> toPersistenceMap(){Map<String,Object> v=new HashMap<>();v.put("confirmId",confirmId);v.put("confirmResult",confirmResult);v.put("remark",remark);return v;}
    public Long getConfirmId(){return confirmId;} public void setConfirmId(Long v){confirmId=v;}
    public String getConfirmResult(){return confirmResult;} public void setConfirmResult(String v){confirmResult=v;}
    public String getRemark(){return remark;} public void setRemark(String v){remark=v;}
}
