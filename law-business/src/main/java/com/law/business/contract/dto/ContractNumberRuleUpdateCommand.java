package com.law.business.contract.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ContractNumberRuleUpdateCommand
{
    @NotNull(message = "请选择编号规则") private Long ruleId;
    @NotBlank(message = "请输入规则名称") @Size(max = 80) private String ruleName;
    @NotBlank(message = "请输入编号前缀") @Size(max = 20) private String prefix;
    @NotBlank(message = "请输入日期格式") @Size(max = 20) private String datePattern;
    @NotNull(message = "请输入流水长度") @Min(3) @Max(12) private Integer serialLength;
    @NotBlank(message = "请选择规则状态") @Pattern(regexp = "^[01]$", message = "规则状态不合法") private String status;
    @Size(max = 500) private String remark;

    public Long getRuleId(){return ruleId;} public void setRuleId(Long v){ruleId=v;}
    public String getRuleName(){return ruleName;} public void setRuleName(String v){ruleName=v;}
    public String getPrefix(){return prefix;} public void setPrefix(String v){prefix=v;}
    public String getDatePattern(){return datePattern;} public void setDatePattern(String v){datePattern=v;}
    public Integer getSerialLength(){return serialLength;} public void setSerialLength(Integer v){serialLength=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getRemark(){return remark;} public void setRemark(String v){remark=v;}
}
