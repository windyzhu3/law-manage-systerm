package com.law.business.matter.dto;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Typed HTTP commands for matter write operations. */
public final class MatterCommands
{
    private MatterCommands() { }

    public interface MapCommand { Map<String, Object> toPersistenceMap(); }

    public static class Matter implements MapCommand
    {
        private Long caseId;
        @NotNull(message="请选择来源合同") private Long contractId;
        @NotBlank(message="请输入案件名称") private String caseName;
        @NotBlank(message="请选择案件类型") private String caseType;
        private String caseStage; private String riskLevel; private String cause; private BigDecimal disputeAmount;
        private String courtName; private String caseSummary; private List<Map<String,Object>> fieldValues;
        public Map<String,Object> toPersistenceMap(){Map<String,Object> v=map("caseId",caseId,"contractId",contractId,"caseName",caseName,"caseType",caseType,"caseStage",caseStage,"riskLevel",riskLevel,"cause",cause,"disputeAmount",disputeAmount,"courtName",courtName,"caseSummary",caseSummary);v.put("fieldValues",fieldValues);return v;}
        public Long getCaseId(){return caseId;} public void setCaseId(Long x){caseId=x;} public Long getContractId(){return contractId;} public void setContractId(Long x){contractId=x;}
        public String getCaseName(){return caseName;} public void setCaseName(String x){caseName=x;} public String getCaseType(){return caseType;} public void setCaseType(String x){caseType=x;}
        public void setCaseStage(String x){caseStage=x;} public void setRiskLevel(String x){riskLevel=x;} public void setCause(String x){cause=x;} public void setDisputeAmount(BigDecimal x){disputeAmount=x;}
        public void setCourtName(String x){courtName=x;} public void setCaseSummary(String x){caseSummary=x;} public void setFieldValues(List<Map<String,Object>> x){fieldValues=x;}
    }

    public static class Progress implements MapCommand
    {
        private Long progressId; @NotNull(message="请选择案件") private Long caseId; @NotBlank(message="请输入进展内容") private String content;
        private String nextPlan; private String syncCustomer; private String attachmentUrl; private String attachmentName;
        public Map<String,Object> toPersistenceMap(){return map("progressId",progressId,"caseId",caseId,"content",content,"nextPlan",nextPlan,"syncCustomer",syncCustomer,"attachmentUrl",attachmentUrl,"attachmentName",attachmentName);}
        public void setProgressId(Long x){progressId=x;} public void setCaseId(Long x){caseId=x;} public void setContent(String x){content=x;} public void setNextPlan(String x){nextPlan=x;}
        public void setSyncCustomer(String x){syncCustomer=x;} public void setAttachmentUrl(String x){attachmentUrl=x;} public void setAttachmentName(String x){attachmentName=x;}
    }

    public static class Material implements MapCommand
    {
        private Long materialId; @NotBlank(message="请输入材料名称") private String materialName; private String materialStatus; private String fileName; private String fileUrl; private String remark;
        public Map<String,Object> toPersistenceMap(){return map("materialId",materialId,"materialName",materialName,"materialStatus",materialStatus,"fileName",fileName,"fileUrl",fileUrl,"remark",remark);}
        public void setMaterialId(Long x){materialId=x;} public void setMaterialName(String x){materialName=x;} public void setMaterialStatus(String x){materialStatus=x;}
        public void setFileName(String x){fileName=x;} public void setFileUrl(String x){fileUrl=x;} public void setRemark(String x){remark=x;}
    }

    public static class Node implements MapCommand
    {
        private Long nodeId; @NotNull(message="请选择案件") private Long caseId; @NotBlank(message="请输入节点名称") private String nodeName;
        @NotBlank(message="请选择节点类型") private String nodeType; @NotBlank(message="请选择节点状态") private String nodeStatus;
        @NotBlank(message="请选择计划日期") private String planDate; private String actualDate; private String courtPlace; private String courtRoom; private String remark; @Valid private List<Material> materials;
        public Map<String,Object> toPersistenceMap(){Map<String,Object> v=map("nodeId",nodeId,"caseId",caseId,"nodeName",nodeName,"nodeType",nodeType,"nodeStatus",nodeStatus,"planDate",planDate,"actualDate",actualDate,"courtPlace",courtPlace,"courtRoom",courtRoom,"remark",remark);v.put("materials",materials==null?null:materials.stream().map(Material::toPersistenceMap).toList());return v;}
        public void setNodeId(Long x){nodeId=x;} public void setCaseId(Long x){caseId=x;} public void setNodeName(String x){nodeName=x;} public void setNodeType(String x){nodeType=x;}
        public void setNodeStatus(String x){nodeStatus=x;} public void setPlanDate(String x){planDate=x;} public void setActualDate(String x){actualDate=x;} public void setCourtPlace(String x){courtPlace=x;}
        public void setCourtRoom(String x){courtRoom=x;} public void setRemark(String x){remark=x;} public void setMaterials(List<Material> x){materials=x;}
    }

    public static class Expense implements MapCommand
    {
        private Long expenseId; @NotNull(message="请选择案件") private Long caseId; @NotBlank(message="请选择费用类型") private String expenseType;
        @NotNull(message="请输入金额") @DecimalMin(value="0.01",message="金额必须大于0") private BigDecimal amount; @NotBlank(message="请选择发生日期") private String occurDate;
        private String payStatus; private String reimburseStatus; private String voucherStatus; private String voucherName; private String voucherUrl; private String remark;
        public Map<String,Object> toPersistenceMap(){return map("expenseId",expenseId,"caseId",caseId,"expenseType",expenseType,"amount",amount,"occurDate",occurDate,"payStatus",payStatus,"reimburseStatus",reimburseStatus,"voucherStatus",voucherStatus,"voucherName",voucherName,"voucherUrl",voucherUrl,"remark",remark);}
        public void setExpenseId(Long x){expenseId=x;} public void setCaseId(Long x){caseId=x;} public void setExpenseType(String x){expenseType=x;} public void setAmount(BigDecimal x){amount=x;}
        public void setOccurDate(String x){occurDate=x;} public void setPayStatus(String x){payStatus=x;} public void setReimburseStatus(String x){reimburseStatus=x;} public void setVoucherStatus(String x){voucherStatus=x;}
        public void setVoucherName(String x){voucherName=x;} public void setVoucherUrl(String x){voucherUrl=x;} public void setRemark(String x){remark=x;}
    }

    public static class Document implements MapCommand
    {
        @NotNull(message="请选择案件") private Long caseId; @NotBlank(message="请填写文件名称") private String fileName; @NotBlank(message="请选择文档类型") private String documentType;
        @NotBlank(message="请上传文件") private String fileUrl; private String remark;
        public Map<String,Object> toPersistenceMap(){return map("caseId",caseId,"fileName",fileName,"documentType",documentType,"fileUrl",fileUrl,"remark",remark);}
        public void setCaseId(Long x){caseId=x;} public void setFileName(String x){fileName=x;} public void setDocumentType(String x){documentType=x;} public void setFileUrl(String x){fileUrl=x;} public void setRemark(String x){remark=x;}
    }

    public static class Archive implements MapCommand
    {
        @NotNull(message="请选择案件") private Long caseId; @NotBlank(message="请选择结案结果") private String closeResult; @NotBlank(message="请选择结案日期") private String closeDate;
        private BigDecimal actualReceivedAmount; private String feeClearStatus; private Integer satisfaction; @NotBlank(message="请输入办案总结") private String summary;
        private String readonlyFlag; private String archiveNo; @Valid private List<Material> materials;
        public Map<String,Object> toPersistenceMap(){Map<String,Object> v=map("caseId",caseId,"closeResult",closeResult,"closeDate",closeDate,"actualReceivedAmount",actualReceivedAmount,"feeClearStatus",feeClearStatus,"satisfaction",satisfaction,"summary",summary,"readonlyFlag",readonlyFlag,"archiveNo",archiveNo);v.put("materials",materials==null?null:materials.stream().map(Material::toPersistenceMap).toList());return v;}
        public void setCaseId(Long x){caseId=x;} public void setCloseResult(String x){closeResult=x;} public void setCloseDate(String x){closeDate=x;} public void setActualReceivedAmount(BigDecimal x){actualReceivedAmount=x;}
        public void setFeeClearStatus(String x){feeClearStatus=x;} public void setSatisfaction(Integer x){satisfaction=x;} public void setSummary(String x){summary=x;} public void setReadonlyFlag(String x){readonlyFlag=x;}
        public void setArchiveNo(String x){archiveNo=x;} public void setMaterials(List<Material> x){materials=x;}
    }

    private static Map<String,Object> map(Object... values){Map<String,Object> result=new HashMap<>();for(int i=0;i<values.length;i+=2)result.put((String)values[i],values[i+1]);return result;}
}
