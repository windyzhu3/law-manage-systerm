package com.ruoyi.system.service.matter;

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
import com.law.business.security.BusinessActor;
import com.law.business.event.BusinessEventCommand;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.event.BusinessEventType;
import com.ruoyi.common.utils.uuid.IdUtils;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class MatterDocumentService
{
    private static final String PROCESSING = "processing";
    private static final String PERMISSIONS = "matter:list,matter:query,matter:mine:list,matter:mine:query,matter:document:list,matter:document:add,matter:document:remove";
    private final BizMatterMapper matterMapper;
    private final ISysDictTypeService dictService;
    @Autowired private BusinessEventPublisher publisher;

    public MatterDocumentService(BizMatterMapper matterMapper, ISysDictTypeService dictService)
    {
        this.matterMapper = matterMapper;
        this.dictService = dictService;
    }

    @Transactional
    public int create(Map<String,Object> document)
    {
        Long caseId=toLong(document.get("caseId"),"请选择案件");
        requireEditable(caseId);
        required(document.get("documentType"),"请选择文档类型");
        required(document.get("fileName"),"请填写文件名称");
        required(document.get("fileUrl"),"请上传文件");
        assertDict("law_case_document_type",document.get("documentType"),"文档类型不合法");
        document.put("createBy",SecurityUtils.getUsername());
        int rows=matterMapper.insertDocument(document);
        assertRows(rows,"文档创建失败");
        log(caseId,"document_add","新增案件文档："+document.get("fileName"));
        return rows;
    }

    @Transactional
    public int delete(Long documentId)
    {
        Map<String,Object> existed=matterMapper.selectDocumentById(documentId);
        if(existed==null) throw error(BusinessErrorCode.DATA_NOT_FOUND,"文档不存在");
        Long caseId=toLong(existed.get("case_id"),"请选择案件");
        requireEditable(caseId);
        int rows=matterMapper.deleteDocument(documentId,SecurityUtils.getUsername());
        assertRows(rows,"文档已变化，请刷新后重试");
        log(caseId,"document_remove","删除案件文档");
        return rows;
    }

    @Transactional
    public int supplyFromTodo(Long caseId,String documentType,String fileName,String fileUrl,String remark,BusinessActor actor)
    {
        Map<String,Object> matter=matterMapper.selectMatterById(caseId);if(matter==null)throw error(BusinessErrorCode.DATA_NOT_FOUND,"案件不存在");if(!PROCESSING.equals(text(matter.get("case_status"))))throw error(BusinessErrorCode.STATE_CONFLICT,"案件已不处于办理中");required(documentType,"请选择文档类型");required(fileName,"请填写文件名称");required(fileUrl,"请上传文件");Map<String,Object> document=new HashMap<>();document.put("caseId",caseId);document.put("documentType",documentType);document.put("fileName",fileName);document.put("fileUrl",fileUrl);document.put("remark",remark);document.put("createBy",actor.userName());assertRows(matterMapper.insertDocument(document),"文档创建失败");logTyped(caseId,"document_supply","补充案件文档："+fileName,actor.userName());Map<String,Object> payload=new HashMap<>();payload.put("documentId",document.get("documentId"));payload.put("documentType",documentType);publish(BusinessEventType.MATTER_DOCUMENT_SUPPLIED,caseId,text(matter.get("case_no")),payload);return 1;
    }

    public void requireDocument(Long caseId,String documentType,Long ownerId,String reason,BusinessActor actor)
    {Map<String,Object> matter=matterMapper.selectMatterById(caseId);if(matter==null)throw error(BusinessErrorCode.DATA_NOT_FOUND,"案件不存在");Map<String,Object> payload=new HashMap<>();payload.put("documentType",documentType);payload.put("ownerId",ownerId);payload.put("reason",reason);publish(BusinessEventType.MATTER_DOCUMENT_REQUIRED,caseId,text(matter.get("case_no")),payload);}

    private void requireEditable(Long caseId)
    {
        Map<String,Object> matter=matterMapper.selectMatterById(caseId);
        if(matter==null) throw error(BusinessErrorCode.DATA_NOT_FOUND,"案件不存在或已删除");
        if(!SecurityUtils.isAdmin()&&matterMapper.countMatterInDataScope(caseId,SecurityUtils.getUserId(),SecurityUtils.getDeptId(),true,PERMISSIONS)==0) throw error(BusinessErrorCode.ACCESS_DENIED,"无权访问该案件");
        if(!PROCESSING.equals(text(matter.get("case_status")))) throw error(BusinessErrorCode.STATE_CONFLICT,"只有办理中案件可以发起该操作");
    }

    private void assertDict(String type,Object value,String message)
    {
        String target=text(value); List<SysDictData> options=dictService.selectDictDataByType(type);
        if(contains(options,target)) return;
        dictService.resetDictCache(); options=dictService.selectDictDataByType(type);
        if(options==null||options.isEmpty()) throw new ServiceException("字典未初始化："+type);
        if(!contains(options,target)) throw new ServiceException(message);
    }

    private boolean contains(List<SysDictData> options,String target){if(options==null)return false;for(SysDictData item:options)if(target.equals(item.getDictValue()))return true;return false;}

    private void log(Long caseId,String action,String content){Map<String,Object> value=new HashMap<>();value.put("caseId",caseId);value.put("actionType",action);value.put("content",content);value.put("createBy",SecurityUtils.getUsername());assertRows(matterMapper.insertStatusLog(value),"案件状态记录创建失败");}
    private Long toLong(Object value,String message){if(value==null||StringUtils.isEmpty(String.valueOf(value)))throw new ServiceException(message);return Long.valueOf(String.valueOf(value));}
    private String required(Object value,String message){String result=text(value);if(StringUtils.isEmpty(result))throw new ServiceException(message);return result;}
    private String text(Object value){return value==null||"null".equalsIgnoreCase(String.valueOf(value))?null:String.valueOf(value).trim();}
    private void assertRows(int rows,String message){if(rows<=0)throw new ServiceException(message);}
    private ServiceException error(BusinessErrorCode code,String message){return new ServiceException(message,code.name());}
    private void logTyped(Long id,String action,String content,String operator){Map<String,Object> value=new HashMap<>();value.put("caseId",id);value.put("actionType",action);value.put("content",content);value.put("createBy",operator);assertRows(matterMapper.insertStatusLog(value),"案件状态记录创建失败");}
    private void publish(BusinessEventType type,Long id,String no,Map<String,Object> payload){publisher.publish(new BusinessEventCommand(type,"MATTER",id,no,type.name()+":"+id+":"+IdUtils.fastUUID(),payload));}
}
