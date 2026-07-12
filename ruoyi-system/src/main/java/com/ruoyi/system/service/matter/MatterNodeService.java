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

@Service
public class MatterNodeService
{
    private static final String PROCESSING="processing";
    private static final String PERMISSIONS="matter:list,matter:query,matter:mine:list,matter:mine:query,matter:node:list,matter:node:add,matter:node:edit,matter:node:remove,matter:node:remind";
    private final BizMatterMapper mapper;
    private final ISysDictTypeService dictService;

    public MatterNodeService(BizMatterMapper mapper,ISysDictTypeService dictService){this.mapper=mapper;this.dictService=dictService;}

    @Transactional
    public int create(Map<String,Object> node)
    {
        Long caseId=toLong(node.get("caseId"),"请选择案件"); requireEditable(caseId); validate(node); node.put("createBy",SecurityUtils.getUsername());
        int rows=mapper.insertNode(node); assertRows(rows,"关键节点创建失败"); saveMaterialsInternal(toLong(node.get("nodeId"),"关键节点创建失败"),listValue(node.get("materials")));
        syncMatter(caseId); log(caseId,"node_add","新增关键节点："+node.get("nodeName")); return rows;
    }

    @Transactional
    public int update(Map<String,Object> node)
    {
        Map<String,Object> existed=requireNode(toLong(node.get("nodeId"),"请选择关键节点")); Long caseId=toLong(existed.get("case_id"),"请选择案件"); requireEditable(caseId);
        validate(node); node.put("updateBy",SecurityUtils.getUsername()); int rows=mapper.updateNode(node); assertRows(rows,"关键节点已变化，请刷新后重试");
        saveMaterialsInternal(toLong(node.get("nodeId"),"请选择关键节点"),listValue(node.get("materials"))); syncMatter(caseId); log(caseId,"node_edit","编辑关键节点："+node.get("nodeName")); return rows;
    }

    @Transactional
    public int delete(Long nodeId)
    {
        Map<String,Object> existed=requireNode(nodeId); Long caseId=toLong(existed.get("case_id"),"请选择案件"); requireEditable(caseId);
        mapper.deleteNodeMaterials(nodeId); int rows=mapper.deleteNode(nodeId,SecurityUtils.getUsername()); assertRows(rows,"关键节点已变化，请刷新后重试");
        syncMatter(caseId); log(caseId,"node_remove","删除关键节点"); return rows;
    }

    @Transactional
    public int saveMaterials(Long nodeId,List<Map<String,Object>> materials)
    {
        Map<String,Object> node=requireNode(nodeId); requireEditable(toLong(node.get("case_id"),"请选择案件")); saveMaterialsInternal(nodeId,materials); return 1;
    }

    private void saveMaterialsInternal(Long nodeId,List<Map<String,Object>> materials)
    {
        mapper.deleteNodeMaterials(nodeId); if(materials==null)return;
        for(Map<String,Object> material:materials){if(StringUtils.isEmpty(text(material.get("materialName"))))continue;material.put("nodeId",nodeId);material.put("materialStatus",defaultText(material.get("materialStatus"),"pending"));assertDict("law_case_material_status",material.get("materialStatus"),"材料状态不合法");material.put("createBy",SecurityUtils.getUsername());mapper.insertNodeMaterial(material);}
    }

    private void validate(Map<String,Object> node)
    {
        required(node.get("nodeName"),"请输入节点名称"); required(node.get("planDate"),"请选择计划日期"); assertDict("law_case_node_status",node.get("nodeStatus"),"节点状态不合法"); assertDict("law_case_node_type",node.get("nodeType"),"节点类型不合法");
        String status=text(node.get("nodeStatus")),actual=text(node.get("actualDate")); if("done".equals(status)&&StringUtils.isEmpty(actual))throw new ServiceException("已完成节点必须填写实际日期");
        if(!StringUtils.isEmpty(actual)&&("pending".equals(status)||"current".equals(status)))throw new ServiceException("已填写实际日期的节点不能保持待开始或当前节点状态");
    }

    private void syncMatter(Long caseId)
    {
        Map<String,Object> current=mapper.selectCurrentOpenNode(caseId),update=new HashMap<>(); update.put("caseId",caseId);update.put("updateBy",SecurityUtils.getUsername());update.put("refreshNextDate",true);
        if(current==null)update.put("currentNode","办理中");else{update.put("currentNode",current.get("node_name"));update.put("caseStage",stage(text(current.get("node_type"))));} mapper.updateMatter(update);
    }

    private String stage(String type){if("evidence".equals(type))return "evidence";if("hearing".equals(type)||"judgment".equals(type))return "hearing";if("execution".equals(type))return "execution";if("archive".equals(type))return "archive";return "opening";}
    private Map<String,Object> requireNode(Long id){Map<String,Object> row=mapper.selectNodeById(id);if(row==null||"2".equals(text(row.get("del_flag"))))throw error(BusinessErrorCode.DATA_NOT_FOUND,"关键节点不存在");requireAccess(toLong(row.get("case_id"),"请选择案件"));return row;}
    private void requireEditable(Long id){Map<String,Object> matter=requireAccess(id);if(!PROCESSING.equals(text(matter.get("case_status"))))throw error(BusinessErrorCode.STATE_CONFLICT,"只有办理中案件可以发起该操作");}
    private Map<String,Object> requireAccess(Long id){Map<String,Object> matter=mapper.selectMatterById(id);if(matter==null)throw error(BusinessErrorCode.DATA_NOT_FOUND,"案件不存在或已删除");if(!SecurityUtils.isAdmin()&&mapper.countMatterInDataScope(id,SecurityUtils.getUserId(),SecurityUtils.getDeptId(),true,PERMISSIONS)==0)throw error(BusinessErrorCode.ACCESS_DENIED,"无权访问该案件");return matter;}
    private void log(Long id,String action,String content){Map<String,Object> value=new HashMap<>();value.put("caseId",id);value.put("actionType",action);value.put("content",content);value.put("createBy",SecurityUtils.getUsername());assertRows(mapper.insertStatusLog(value),"案件状态记录创建失败");}
    private void assertDict(String type,Object value,String message){String target=text(value);if(StringUtils.isEmpty(target))return;List<SysDictData> values=dictService.selectDictDataByType(type);if(contains(values,target))return;dictService.resetDictCache();values=dictService.selectDictDataByType(type);if(values==null||values.isEmpty())throw new ServiceException("字典未初始化："+type);if(!contains(values,target))throw new ServiceException(message);}
    private boolean contains(List<SysDictData> values,String target){if(values==null)return false;for(SysDictData item:values)if(target.equals(item.getDictValue()))return true;return false;}
    private Long toLong(Object value,String message){if(value==null||StringUtils.isEmpty(String.valueOf(value)))throw new ServiceException(message);return Long.valueOf(String.valueOf(value));}
    private String required(Object value,String message){String result=text(value);if(StringUtils.isEmpty(result))throw new ServiceException(message);return result;}
    private String defaultText(Object value,Object fallback){String result=text(value);return StringUtils.isEmpty(result)?text(fallback):result;}
    private String text(Object value){return value==null||"null".equalsIgnoreCase(String.valueOf(value))?null:String.valueOf(value).trim();}
    @SuppressWarnings("unchecked") private List<Map<String,Object>> listValue(Object value){return value instanceof List?(List<Map<String,Object>>)value:List.of();}
    private void assertRows(int rows,String message){if(rows<=0)throw new ServiceException(message);}
    private ServiceException error(BusinessErrorCode code,String message){return new ServiceException(message,code.name());}
}
