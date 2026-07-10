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

@Service
public class MatterDocumentService
{
    private static final String PROCESSING = "processing";
    private static final String PERMISSIONS = "matter:list,matter:query,matter:mine:list,matter:mine:query,matter:document:list,matter:document:add,matter:document:remove";
    private final BizMatterMapper matterMapper;
    private final ISysDictTypeService dictService;

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
        if(existed==null) throw new ServiceException("文档不存在");
        Long caseId=toLong(existed.get("case_id"),"请选择案件");
        requireEditable(caseId);
        int rows=matterMapper.deleteDocument(documentId,SecurityUtils.getUsername());
        assertRows(rows,"文档已变化，请刷新后重试");
        log(caseId,"document_remove","删除案件文档");
        return rows;
    }

    private void requireEditable(Long caseId)
    {
        Map<String,Object> matter=matterMapper.selectMatterById(caseId);
        if(matter==null) throw new ServiceException("案件不存在或已删除");
        if(!SecurityUtils.isAdmin()&&matterMapper.countMatterInDataScope(caseId,SecurityUtils.getUserId(),SecurityUtils.getDeptId(),true,PERMISSIONS)==0) throw new ServiceException("无权访问该案件");
        if(!PROCESSING.equals(text(matter.get("case_status")))) throw new ServiceException("只有办理中案件可以发起该操作");
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
}
