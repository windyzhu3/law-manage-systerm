package com.ruoyi.system.service.casecenter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.SysNotice;
import com.ruoyi.system.mapper.BizCaseMapper;
import com.ruoyi.system.service.ISysDictTypeService;
import com.ruoyi.system.service.ISysNoticeService;

@Component
public class CaseWorkflowSupport
{
    @Autowired private BizCaseMapper mapper;
    @Autowired private ISysDictTypeService dictionaries;
    @Autowired private ISysNoticeService notices;
    public void statusLog(Long id,String from,String to,String action,String content){requireDict("law_case_status_action",action,"案件状态动作不合法");Map<String,Object> m=new HashMap<>();m.put("caseId",id);m.put("fromStatus",from);m.put("toStatus",to);m.put("actionType",action);m.put("content",content);m.put("createBy",SecurityUtils.getUsername());if(mapper.insertStatusLog(m)<=0)throw new ServiceException("案件状态记录创建失败");}
    public void notice(String title,String content){SysNotice n=new SysNotice();n.setNoticeTitle(title);n.setNoticeType("1");n.setNoticeContent(content);n.setStatus("0");n.setCreateBy(SecurityUtils.getUsername());n.setRemark("案管中心");notices.insertNotice(n);}
    public void requireDict(String type,Object value,String message){String v=value==null?null:String.valueOf(value).trim();List<SysDictData> xs=dictionaries.selectDictDataByType(type);if(StringUtils.isEmpty(v)||!contains(xs,v))throw new ServiceException(xs==null||xs.isEmpty()?"字典未初始化："+type:message);}
    private boolean contains(List<SysDictData> xs,String v){if(xs!=null)for(SysDictData x:xs)if(v.equals(x.getDictValue()))return true;return false;}
}
