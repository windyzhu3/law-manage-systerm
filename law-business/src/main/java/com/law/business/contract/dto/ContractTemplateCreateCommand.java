package com.law.business.contract.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ContractTemplateCreateCommand
{
    @NotBlank(message = "请输入模板名称") @Size(max = 200) private String templateName;
    @NotBlank(message = "请选择案件类型") @Size(max = 50) private String caseType;
    @NotBlank(message = "请输入文件名称") @Size(max = 200) private String fileName;
    @NotBlank(message = "请上传模板文件") @Size(max = 500) private String fileUrl;
    @Size(max = 30) private String versionNo;
    @Pattern(regexp = "^[01]$", message = "模板状态不合法") private String status;
    @Size(max = 500) private String remark;

    public String getTemplateName(){return templateName;} public void setTemplateName(String v){templateName=v;}
    public String getCaseType(){return caseType;} public void setCaseType(String v){caseType=v;}
    public String getFileName(){return fileName;} public void setFileName(String v){fileName=v;}
    public String getFileUrl(){return fileUrl;} public void setFileUrl(String v){fileUrl=v;}
    public String getVersionNo(){return versionNo;} public void setVersionNo(String v){versionNo=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getRemark(){return remark;} public void setRemark(String v){remark=v;}
}
