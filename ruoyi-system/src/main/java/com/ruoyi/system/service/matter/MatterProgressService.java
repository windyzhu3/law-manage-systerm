package com.ruoyi.system.service.matter;

import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.mapper.BizMatterMapper;
import com.law.business.shared.error.BusinessErrorCode;

@Service
public class MatterProgressService
{
    private static final String PROCESSING = "processing";
    private static final String MATTER_PERMISSIONS =
            "matter:list,matter:query,matter:mine:list,matter:mine:query,matter:add,matter:edit,"
                    + "matter:progress:list,matter:progress:add,matter:progress:edit,matter:progress:remove";

    private final BizMatterMapper matterMapper;

    public MatterProgressService(BizMatterMapper matterMapper)
    {
        this.matterMapper = matterMapper;
    }

    @Transactional
    public int create(Map<String, Object> progress)
    {
        Long caseId = toLong(progress.get("caseId"), "请选择案件");
        requireProcessEditableMatter(caseId);
        requiredText(progress.get("content"), "请输入进展内容");
        progress.put("recordUserId", SecurityUtils.getUserId());
        progress.put("recordUserName", SecurityUtils.getLoginUser().getUser().getNickName());
        progress.put("createBy", SecurityUtils.getUsername());
        int rows = matterMapper.insertProgress(progress);
        assertRows(rows, "进度记录创建失败");
        touchMatter(caseId, progress.get("content"));
        insertStatusLog(caseId, "progress_add", "新增进度记录");
        return rows;
    }

    @Transactional
    public int update(Map<String, Object> progress)
    {
        Map<String, Object> existed = requireOwnedProgress(toLong(progress.get("progressId"), "请选择进度记录"));
        Long caseId = toLong(existed.get("case_id"), "请选择案件");
        requireProcessEditableMatter(caseId);
        requiredText(progress.get("content"), "请输入进展内容");
        progress.put("updateBy", SecurityUtils.getUsername());
        int rows = matterMapper.updateProgress(progress);
        assertRows(rows, "进度记录已变化，请刷新后重试");
        insertStatusLog(caseId, "progress_edit", "编辑进度记录");
        return rows;
    }

    @Transactional
    public int delete(Long progressId)
    {
        Map<String, Object> existed = requireOwnedProgress(progressId);
        Long caseId = toLong(existed.get("case_id"), "请选择案件");
        requireProcessEditableMatter(caseId);
        int rows = matterMapper.deleteProgress(progressId, SecurityUtils.getUsername());
        assertRows(rows, "进度记录已变化，请刷新后重试");
        insertStatusLog(caseId, "progress_remove", "删除进度记录");
        return rows;
    }

    private Map<String, Object> requireOwnedProgress(Long progressId)
    {
        Map<String, Object> row = matterMapper.selectProgressById(progressId);
        if (row == null || "2".equals(text(row.get("del_flag")))) throw error(BusinessErrorCode.DATA_NOT_FOUND, "进度记录不存在");
        requireMatter(toLong(row.get("case_id"), "请选择案件"));
        return row;
    }

    private void requireProcessEditableMatter(Long caseId)
    {
        Map<String, Object> matter = requireMatter(caseId);
        if (!PROCESSING.equals(text(matter.get("case_status")))) throw error(BusinessErrorCode.STATE_CONFLICT, "只有办理中案件可以发起该操作");
    }

    private Map<String, Object> requireMatter(Long caseId)
    {
        Map<String, Object> matter = matterMapper.selectMatterById(caseId);
        if (matter == null) throw error(BusinessErrorCode.DATA_NOT_FOUND, "案件不存在或已删除");
        if (!SecurityUtils.isAdmin() && matterMapper.countMatterInDataScope(caseId, SecurityUtils.getUserId(), SecurityUtils.getDeptId(), true, MATTER_PERMISSIONS) == 0)
            throw error(BusinessErrorCode.ACCESS_DENIED, "无权访问该案件");
        return matter;
    }

    private void touchMatter(Long caseId, Object content)
    {
        Map<String, Object> update = new HashMap<>();
        update.put("caseId", caseId); update.put("recentProgress", limitText(text(content), 300));
        update.put("currentNode", "办理中"); update.put("updateBy", SecurityUtils.getUsername());
        matterMapper.updateMatter(update);
    }

    private void insertStatusLog(Long caseId, String actionType, String content)
    {
        Map<String, Object> log = new HashMap<>();
        log.put("caseId", caseId); log.put("actionType", actionType); log.put("content", content); log.put("createBy", SecurityUtils.getUsername());
        assertRows(matterMapper.insertStatusLog(log), "案件状态记录创建失败");
    }

    private Long toLong(Object value,String message){if(value==null||StringUtils.isEmpty(String.valueOf(value)))throw new ServiceException(message);return Long.valueOf(String.valueOf(value));}
    private String requiredText(Object value,String message){String valueText=text(value);if(StringUtils.isEmpty(valueText))throw new ServiceException(message);return valueText;}
    private String text(Object value){return value==null||"null".equalsIgnoreCase(String.valueOf(value))?null:String.valueOf(value).trim();}
    private String limitText(String value,int max){return value==null||value.length()<=max?value:value.substring(0,max);}
    private void assertRows(int rows,String message){if(rows<=0)throw new ServiceException(message);}
    private ServiceException error(BusinessErrorCode code,String message){return new ServiceException(message,code.name());}
}
