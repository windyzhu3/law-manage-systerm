package com.ruoyi.system.service.matter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.mapper.BizMatterMapper;
import com.ruoyi.system.service.ISysDictTypeService;

@Service
public class MatterQueryService
{
    private static final String PERMISSIONS=
            "matter:list,matter:query,matter:mine:list,matter:mine:query,matter:add,matter:edit,matter:import,matter:export,"
                    +"matter:progress:list,matter:progress:add,matter:progress:edit,matter:progress:remove,"
                    +"matter:node:list,matter:node:add,matter:node:edit,matter:node:remove,matter:node:remind,"
                    +"matter:expense:list,matter:expense:add,matter:expense:edit,matter:expense:remove,"
                    +"matter:document:list,matter:document:add,matter:document:remove,"
                    +"matter:archive:list,matter:archive:apply,matter:archive:confirm,matter:status:list";
    private final BizMatterMapper mapper; private final ISysDictTypeService dictService;
    public MatterQueryService(BizMatterMapper mapper,ISysDictTypeService dictService){this.mapper=mapper;this.dictService=dictService;}

    public Map<String,Object> dashboard(){Map<String,Object> params=scope(new HashMap<>()),data=new HashMap<>();data.put("cards",mapper.selectDashboardCards(params));data.put("types",mapper.selectCaseTypeStats(params));data.put("reminders",mapper.selectReminders(params));return data;}
    public List<Map<String,Object>> matters(Map<String,Object> params){return mapper.selectMatterList(scope(params));}
    public List<Map<String,Object>> progress(Map<String,Object> params){return mapper.selectProgressList(scope(params));}
    public List<Map<String,Object>> nodes(Map<String,Object> params){return fillNodeMaterials(mapper.selectNodeList(scope(params)));}
    public List<Map<String,Object>> expenses(Map<String,Object> params){return mapper.selectExpenseList(scope(params));}
    public List<Map<String,Object>> documents(Map<String,Object> params){return mapper.selectDocumentList(scope(params));}
    public List<Map<String,Object>> statusLogs(Map<String,Object> params){return mapper.selectStatusLogs(scope(params));}

    public List<Map<String,Object>> fieldConfigs(String caseType){assertDict("law_case_type",caseType,"案件类型不合法");return mapper.selectFieldConfigs(caseType);}

    public Map<String,Object> detail(Long caseId)
    {
        Map<String,Object> matter=requireMatter(caseId);List<Map<String,Object>> configs=mapper.selectFieldConfigs(text(matter.get("case_type")));
        matter.put("fieldConfigs",configs);matter.put("fieldValues",mergeFields(configs,mapper.selectFieldValues(caseId)));
        matter.put("progress",progress(Map.of("caseId",caseId,"pageSize",10)));matter.put("nodes",nodes(Map.of("caseId",caseId,"pageSize",20)));
        matter.put("expenses",expenses(Map.of("caseId",caseId,"pageSize",10)));matter.put("documents",documents(Map.of("caseId",caseId,"pageSize",10)));
        Map<String,Object> archive=mapper.selectArchiveByCaseId(caseId);if(archive!=null)archive.put("materials",mapper.selectArchiveMaterials(toLong(archive.get("archive_id"),"归档记录不存在")));matter.put("archive",archive);
        matter.put("statusLogs",statusLogs(Map.of("caseId",caseId,"pageSize",20)));return matter;
    }

    private List<Map<String,Object>> fillNodeMaterials(List<Map<String,Object>> nodes){if(nodes==null||nodes.isEmpty())return nodes;for(Map<String,Object> node:nodes)node.put("materials",mapper.selectNodeMaterials(toLong(node.get("node_id"),"关键节点不存在")));return nodes;}
    private List<Map<String,Object>> mergeFields(List<Map<String,Object>> configs,List<Map<String,Object>> values){Map<String,Map<String,Object>> valueMap=new HashMap<>();for(Map<String,Object> value:values)valueMap.put(text(value.get("field_code")),value);List<Map<String,Object>> result=new ArrayList<>();for(Map<String,Object> config:configs){String code=text(config.get("field_code"));Map<String,Object> merged=new HashMap<>(config),value=valueMap.get(code);if(value!=null)merged.putAll(value);else{merged.put("field_code",code);merged.put("field_name",config.get("field_name"));merged.put("field_value","");}result.add(merged);}return result;}
    private Map<String,Object> requireMatter(Long id){Map<String,Object> matter=mapper.selectMatterById(id);if(matter==null)throw new ServiceException("案件不存在或已删除");if(!SecurityUtils.isAdmin()&&mapper.countMatterInDataScope(id,SecurityUtils.getUserId(),SecurityUtils.getDeptId(),true,PERMISSIONS)==0)throw new ServiceException("无权访问该案件");return matter;}
    private Map<String,Object> scope(Map<String,Object> params){Map<String,Object> value=new HashMap<>();if(params!=null)value.putAll(params);value.put("currentUserId",SecurityUtils.getUserId());value.put("currentDeptId",SecurityUtils.getDeptId());value.put("dataScope",!SecurityUtils.isAdmin());value.put("permissions",PERMISSIONS);return value;}
    private void assertDict(String type,String target,String message){if(StringUtils.isEmpty(target))return;List<SysDictData> values=dictService.selectDictDataByType(type);if(contains(values,target))return;dictService.resetDictCache();values=dictService.selectDictDataByType(type);if(values==null||values.isEmpty())throw new ServiceException("字典未初始化："+type);if(!contains(values,target))throw new ServiceException(message);}
    private boolean contains(List<SysDictData> values,String target){if(values==null)return false;for(SysDictData item:values)if(target.equals(item.getDictValue()))return true;return false;}
    private Long toLong(Object value,String message){if(value==null||StringUtils.isEmpty(String.valueOf(value)))throw new ServiceException(message);return Long.valueOf(String.valueOf(value));}
    private String text(Object value){return value==null||"null".equalsIgnoreCase(String.valueOf(value))?null:String.valueOf(value).trim();}
}
