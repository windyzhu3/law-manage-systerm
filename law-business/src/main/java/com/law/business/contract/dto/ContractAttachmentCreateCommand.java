package com.law.business.contract.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public class ContractAttachmentCreateCommand
{
    @NotNull(message = "请选择合同") private Long contractId;
    @NotBlank(message = "请输入附件名称") @Size(max = 200) private String fileName;
    @NotBlank(message = "请上传附件") @Size(max = 500) private String fileUrl;
    @NotBlank(message = "请选择文件类型") @Size(max = 80) private String fileType;
    @PositiveOrZero(message = "文件大小不能小于0") private Long fileSize;
    @Size(max = 500) private String remark;

    public Long getContractId(){return contractId;} public void setContractId(Long v){contractId=v;}
    public String getFileName(){return fileName;} public void setFileName(String v){fileName=v;}
    public String getFileUrl(){return fileUrl;} public void setFileUrl(String v){fileUrl=v;}
    public String getFileType(){return fileType;} public void setFileType(String v){fileType=v;}
    public Long getFileSize(){return fileSize;} public void setFileSize(Long v){fileSize=v;}
    public String getRemark(){return remark;} public void setRemark(String v){remark=v;}
}
