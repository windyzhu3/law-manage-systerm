package com.ruoyi.system.service.matter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.mapper.BizMatterMapper;
import com.ruoyi.system.service.ISysDictTypeService;
import com.law.business.shared.error.BusinessErrorCode;
import com.law.business.shared.status.CaseStatus;
import com.law.business.shared.status.CaseStatusTransitions;

@Service
public class MatterArchiveService
{
    private static final String PROCESSING="processing",CLOSING="closing",CLOSED="closed",ARCHIVED="archived";
    private static final String PERMISSIONS="matter:list,matter:query,matter:mine:list,matter:mine:query,matter:archive:list,matter:archive:apply,matter:archive:confirm";
    private final BizMatterMapper mapper; private final ISysDictTypeService dictService;
    public MatterArchiveService(BizMatterMapper mapper,ISysDictTypeService dictService){this.mapper=mapper;this.dictService=dictService;}

    public Map<String,Object> select(Long caseId){requireMatter(caseId);Map<String,Object> value=mapper.selectArchiveByCaseId(caseId);if(value!=null)value.put("materials",mapper.selectArchiveMaterials(toLong(value.get("archive_id"),"归档记录不存在")));return value;}

    @Transactional
    public int apply(Map<String,Object> archive)
    {
        Long caseId=toLong(archive.get("caseId"),"请选择案件");Map<String,Object> matter=requireStatus(caseId,PROCESSING,"只有办理中案件可以发起该操作");validateCloseReady(caseId);validate(archive);
        archive.put("archiveStatus","pending");archive.put("createBy",SecurityUtils.getUsername());Map<String,Object> existed=mapper.selectArchiveByCaseId(caseId);int rows;
        if(existed==null)rows=mapper.insertArchive(archive);else{archive.put("archiveId",existed.get("archive_id"));archive.put("updateBy",SecurityUtils.getUsername());rows=mapper.updateArchive(archive);}assertRows(rows,"结案申请保存失败");
        Long archiveId=toLong(existed==null?archive.get("archiveId"):existed.get("archive_id"),"归档记录不存在");saveMaterials(archiveId,listValue(archive.get("materials")));updateStatus(caseId,CLOSING,"pending","结案申请",matter.get("case_status"));log(caseId,text(matter.get("case_status")),CLOSING,"archive_apply","发起结案申请");return rows;
    }

    @Transactional
    public int close(Map<String,Object> archive)
    {
        Long caseId=toLong(archive.get("caseId"),"请选择案件");Map<String,Object> matter=requireStatus(caseId,CLOSING,"只有结案申请中的案件可以确认结案");Map<String,Object> existed=requireArchive(caseId);
        validate(archive);validateCloseReady(caseId);validateFeeMarked(archive);validateCaseFee(caseId);archive.put("archiveId",existed.get("archive_id"));archive.put("archiveStatus","pending");archive.put("updateBy",SecurityUtils.getUsername());
        int rows=mapper.updateArchive(archive);assertRows(rows,"确认结案失败");saveMaterials(toLong(existed.get("archive_id"),"归档记录不存在"),listValue(archive.get("materials")));updateStatus(caseId,CLOSED,"pending","已结案",matter.get("case_status"));log(caseId,text(matter.get("case_status")),CLOSED,"archive_close","确认结案");return rows;
    }

    @Transactional
    public int archive(Map<String,Object> command)
    {
        Long caseId=toLong(command.get("caseId"),"请选择案件");Map<String,Object> matter=requireStatus(caseId,CLOSED,"只有已结案案件可以归档");Map<String,Object> existed=requireArchive(caseId);validate(command);
        Long archiveId=toLong(existed.get("archive_id"),"归档记录不存在");command.put("archiveId",archiveId);command.put("archiveStatus",ARCHIVED);command.put("archiveNo",defaultText(command.get("archiveNo"),"JG"+LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))));command.put("archiverId",SecurityUtils.getUserId());command.put("archiverName",SecurityUtils.getLoginUser().getUser().getNickName());command.put("updateBy",SecurityUtils.getUsername());
        int rows=mapper.updateArchive(command);assertRows(rows,"归档失败");saveMaterials(archiveId,listValue(command.get("materials")));validateCaseFee(caseId);validateReady(archiveId,command);updateStatus(caseId,ARCHIVED,ARCHIVED,"已归档",matter.get("case_status"));log(caseId,text(matter.get("case_status")),ARCHIVED,"archive_confirm","确认归档");return rows;
    }

    private void validate(Map<String,Object> value){required(value.get("closeResult"),"请选择结案结果");required(value.get("closeDate"),"请选择结案日期");required(value.get("summary"),"请输入办案总结");assertDict("law_case_close_result",value.get("closeResult"),"结案结果不合法");assertDict("law_case_fee_clear_status",value.get("feeClearStatus"),"费用结清状态不合法");}
    private void validateCloseReady(Long id){if(mapper.countUnfinishedNodes(id)>0)throw error(BusinessErrorCode.PRECONDITION_FAILED,"仍有未完成或超期的关键节点，不能发起结案");}
    private void validateCaseFee(Long id){if(mapper.countUnpaidExpenseByCaseId(id)>0)throw error(BusinessErrorCode.PRECONDITION_FAILED,"仍有未付款的案件费用，不能确认结案或归档");}
    private void validateFeeMarked(Map<String,Object> value){if(!"cleared".equals(text(value.get("feeClearStatus"))))throw error(BusinessErrorCode.PRECONDITION_FAILED,"费用未结清，不能确认结案或归档");}
    private void validateReady(Long id,Map<String,Object> value){validateFeeMarked(value);if(mapper.countArchiveMaterials(id)==0)throw error(BusinessErrorCode.PRECONDITION_FAILED,"请维护归档资料清单后再确认归档");if(mapper.countNotReadyArchiveMaterials(id)>0)throw error(BusinessErrorCode.PRECONDITION_FAILED,"仍有未准备完成的归档资料，不能确认归档");}
    private Map<String,Object> requireArchive(Long id){Map<String,Object> value=mapper.selectArchiveByCaseId(id);if(value==null)throw error(BusinessErrorCode.PRECONDITION_FAILED,"请先提交结案申请");return value;}
    private Map<String,Object> requireStatus(Long id,String status,String message){Map<String,Object> matter=requireMatter(id);if(!status.equals(text(matter.get("case_status"))))throw error(BusinessErrorCode.STATE_CONFLICT,message);return matter;}
    private Map<String,Object> requireMatter(Long id){Map<String,Object> matter=mapper.selectMatterById(id);if(matter==null)throw error(BusinessErrorCode.DATA_NOT_FOUND,"案件不存在或已删除");if(!SecurityUtils.isAdmin()&&mapper.countMatterInDataScope(id,SecurityUtils.getUserId(),SecurityUtils.getDeptId(),true,PERMISSIONS)==0)throw error(BusinessErrorCode.ACCESS_DENIED,"无权访问该案件");return matter;}
    private void updateStatus(Long id,String caseStatus,String archiveStatus,String node,Object expected){requireTransition(text(expected),caseStatus);Map<String,Object> value=new HashMap<>();value.put("caseId",id);value.put("caseStatus",caseStatus);value.put("archiveStatus",archiveStatus);value.put("currentNode",node);value.put("expectedStatus",expected);value.put("updateBy",SecurityUtils.getUsername());assertRows(mapper.updateMatterStatus(value),"案件状态已变化，请刷新后重试");}
    private void saveMaterials(Long id,List<Map<String,Object>> materials){mapper.deleteArchiveMaterials(id);if(materials==null)return;for(Map<String,Object> item:materials){if(StringUtils.isEmpty(text(item.get("materialName"))))continue;item.put("archiveId",id);item.put("materialStatus",defaultText(item.get("materialStatus"),"pending"));assertDict("law_case_material_status",item.get("materialStatus"),"材料状态不合法");item.put("createBy",SecurityUtils.getUsername());mapper.insertArchiveMaterial(item);}}
    private void log(Long id,String from,String to,String action,String content){Map<String,Object> value=new HashMap<>();value.put("caseId",id);value.put("fromStatus",from);value.put("toStatus",to);value.put("actionType",action);value.put("content",content);value.put("createBy",SecurityUtils.getUsername());assertRows(mapper.insertStatusLog(value),"案件状态记录创建失败");}
    private void assertDict(String type,Object value,String message){String target=text(value);if(StringUtils.isEmpty(target))return;List<SysDictData> values=dictService.selectDictDataByType(type);if(contains(values,target))return;dictService.resetDictCache();values=dictService.selectDictDataByType(type);if(values==null||values.isEmpty())throw new ServiceException("字典未初始化："+type);if(!contains(values,target))throw new ServiceException(message);}
    private boolean contains(List<SysDictData> values,String target){if(values==null)return false;for(SysDictData item:values)if(target.equals(item.getDictValue()))return true;return false;}
    private Long toLong(Object value,String message){if(value==null||StringUtils.isEmpty(String.valueOf(value)))throw new ServiceException(message);return Long.valueOf(String.valueOf(value));}
    private String required(Object value,String message){String result=text(value);if(StringUtils.isEmpty(result))throw new ServiceException(message);return result;}
    private String defaultText(Object value,Object fallback){String result=text(value);return StringUtils.isEmpty(result)?text(fallback):result;}
    private String text(Object value){return value==null||"null".equalsIgnoreCase(String.valueOf(value))?null:String.valueOf(value).trim();}
    @SuppressWarnings("unchecked") private List<Map<String,Object>> listValue(Object value){return value instanceof List?(List<Map<String,Object>>)value:List.of();}
    private void assertRows(int rows,String message){if(rows<=0)throw new ServiceException(message);}
    private ServiceException error(BusinessErrorCode code,String message){return new ServiceException(message,code.name());}
    private void requireTransition(String from,String to){try{CaseStatusTransitions.requireAllowed(CaseStatus.fromCode(from),CaseStatus.fromCode(to));}catch(IllegalArgumentException|IllegalStateException e){throw error(BusinessErrorCode.STATE_CONFLICT,"案件状态不允许从"+from+"变更为"+to);}}
}
