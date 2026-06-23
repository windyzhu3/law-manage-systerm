package com.ruoyi.system.service.impl;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.domain.BizLeadFollowup;
import com.ruoyi.system.domain.BizLeadSetting;
import com.ruoyi.system.mapper.BizLeadMapper;
import com.ruoyi.system.service.IBizCustomerService;
import com.ruoyi.system.service.IBizLeadService;
import com.ruoyi.system.service.ISysDictTypeService;

@Service
public class BizLeadServiceImpl implements IBizLeadService
{
    private static final String STATUS_UNASSIGNED = "0";
    private static final String STATUS_WAIT_FOLLOW = "1";
    private static final String STATUS_FOLLOWING = "2";
    private static final String STATUS_CONVERTED = "3";
    private static final String STATUS_INVALID = "4";
    private static final String STATUS_CLOSED = "5";
    private static final String POOL_NO = "0";
    private static final String POOL_YES = "1";

    @Autowired
    private BizLeadMapper leadMapper;

    @Autowired
    private IBizCustomerService customerService;

    @Autowired
    private ISysDictTypeService dictTypeService;

    @Override
    public List<BizLead> selectLeadList(BizLead lead)
    {
        applyListScope(lead);
        if ("mine".equals(lead.getListMode()))
        {
            lead.setCurrentUserId(SecurityUtils.getUserId());
        }
        return leadMapper.selectLeadList(lead);
    }

    @Override
    public BizLead selectLeadById(Long leadId)
    {
        BizLead lead = leadMapper.selectLeadById(leadId);
        if (lead == null || "2".equals(lead.getDelFlag()))
        {
            throw new ServiceException("线索不存在或已被删除");
        }
        if (!canAccessLead(lead, false))
        {
            throw new ServiceException("没有权限访问该线索");
        }
        return lead;
    }

    @Override
    public int insertLead(BizLead lead)
    {
        validateLead(lead);
        lead.setCreateBy(SecurityUtils.getUsername());
        lead.setLeadNo("XS" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")));
        if (lead.getOwnerId() == null)
        {
            lead.setStatus(STATUS_UNASSIGNED);
            lead.setPoolStatus(POOL_YES);
            lead.setDeptId(null);
        }
        else
        {
            lead.setStatus(STATUS_WAIT_FOLLOW);
            lead.setPoolStatus(POOL_NO);
        }
        return leadMapper.insertLead(lead);
    }

    @Override
    public int updateLead(BizLead lead)
    {
        if (lead.getLeadId() == null)
        {
            throw new ServiceException("线索ID不能为空");
        }
        requiredAccessibleLead(lead.getLeadId(), false, false);
        validateLead(lead);
        // 状态、归属和公海字段只能通过分配、领取、跟进、转化、入公海等动作变更。
        lead.setStatus(null);
        lead.setPoolStatus(null);
        lead.setOwnerId(null);
        lead.setDeptId(null);
        lead.setConvertedTime(null);
        lead.setLastFollowTime(null);
        lead.setInvalidReason(null);
        lead.setPoolReason(null);
        lead.setUpdateBy(SecurityUtils.getUsername());
        return leadMapper.updateLead(lead);
    }

    @Override
    public int softDeleteLead(Long[] leadIds)
    {
        for (Long leadId : leadIds)
        {
            requiredAccessibleLead(leadId, false, false);
        }
        return leadMapper.softDeleteLead(leadIds, SecurityUtils.getUsername());
    }

    @Override
    public int restoreLead(Long[] leadIds)
    {
        for (Long leadId : leadIds)
        {
            requiredAccessibleLead(leadId, true, false);
        }
        return leadMapper.restoreLead(leadIds, SecurityUtils.getUsername());
    }

    @Override
    @Transactional
    public int purgeLead(Long[] leadIds)
    {
        for (Long leadId : leadIds)
        {
            BizLead lead = requiredAccessibleLead(leadId, true, false);
            if (!"2".equals(lead.getDelFlag()))
            {
                throw new ServiceException("只能彻底删除回收站中的线索");
            }
        }
        leadMapper.purgeLeadFollowups(leadIds);
        leadMapper.purgeLeadAssignmentLogs(leadIds);
        return leadMapper.purgeLead(leadIds);
    }

    @Override
    @Transactional
    public int assignLead(Long leadId, Long ownerId, String reason)
    {
        if (ownerId == null)
        {
            throw new ServiceException("请选择负责人");
        }
        BizLead lead = requiredAccessibleLead(leadId, false, true);
        assertActiveLead(lead, "分配");
        String username = SecurityUtils.getUsername();
        int rows = leadMapper.assignLead(leadId, ownerId, null, username);
        if (rows == 0)
        {
            throw new ServiceException("当前线索状态不允许分配，请刷新后重试");
        }
        leadMapper.insertAssignmentLog(leadId, lead.getOwnerId(), ownerId, "assign", reason, username);
        return rows;
    }

    @Override
    @Transactional
    public int moveToPool(Long leadId, String reason)
    {
        BizLead lead = requiredAccessibleLead(leadId, false, false);
        assertActiveLead(lead, "进入公海");
        if (POOL_YES.equals(lead.getPoolStatus()))
        {
            throw new ServiceException("该线索已在公海中");
        }
        String username = SecurityUtils.getUsername();
        int rows = leadMapper.moveToPool(leadId, reason, username);
        if (rows == 0)
        {
            throw new ServiceException("当前线索状态不允许进入公海，请刷新后重试");
        }
        leadMapper.insertAssignmentLog(leadId, lead.getOwnerId(), null, "pool", reason, username);
        return rows;
    }

    @Override
    @Transactional
    public int claimLead(Long leadId)
    {
        BizLead lead = requiredLead(leadId);
        assertActiveLead(lead, "领取");
        if (!POOL_YES.equals(lead.getPoolStatus()))
        {
            throw new ServiceException("该线索已被领取，请刷新列表");
        }
        Long userId = SecurityUtils.getUserId();
        int rows = leadMapper.claimLead(leadId, userId, SecurityUtils.getDeptId(), SecurityUtils.getUsername());
        if (rows == 0)
        {
            throw new ServiceException("该线索已被领取，请刷新列表");
        }
        leadMapper.insertAssignmentLog(leadId, null, userId, "claim", "公海领取", SecurityUtils.getUsername());
        return rows;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public int convertLead(Long leadId)
    {
        BizLead lead = requiredAccessibleLead(leadId, false, false);
        assertActiveLead(lead, "转化");
        assertOwnedLead(lead, "转化");
        if (!STATUS_WAIT_FOLLOW.equals(lead.getStatus()) && !STATUS_FOLLOWING.equals(lead.getStatus()))
        {
            throw new ServiceException("待分配线索需先分配或领取后才能转化");
        }
        int rows = leadMapper.convertLead(leadId, SecurityUtils.getUsername());
        if (rows == 0)
        {
            throw new ServiceException("当前线索状态不允许转化，请刷新后重试");
        }
        customerService.convertLeadToCustomer(lead);
        return rows;
    }

    @Override
    public List<BizLeadFollowup> selectFollowupList(BizLeadFollowup followup)
    {
        if ("mine".equals(followup.getListMode()))
        {
            followup.setCurrentUserId(SecurityUtils.getUserId());
        }
        return leadMapper.selectFollowupList(followup);
    }

    @Override
    @Transactional
    public int insertFollowup(BizLeadFollowup followup)
    {
        validateFollowup(followup);
        BizLead lead = requiredAccessibleLead(followup.getLeadId(), false, false);
        assertActiveLead(lead, "跟进");
        assertOwnedLead(lead, "跟进");
        followup.setFollowUserId(SecurityUtils.getUserId());
        followup.setCreateBy(SecurityUtils.getUsername());
        int rows = leadMapper.insertFollowup(followup);
        int touched = leadMapper.touchLeadFollowTime(followup.getLeadId(), followup.getNextFollowTime(), SecurityUtils.getUsername());
        if (touched == 0)
        {
            throw new ServiceException("当前线索状态不允许跟进，请刷新后重试");
        }
        return rows;
    }

    @Override
    public int updateFollowup(BizLeadFollowup followup)
    {
        followup.setUpdateBy(SecurityUtils.getUsername());
        return leadMapper.updateFollowup(followup);
    }

    @Override
    public int deleteFollowup(Long followupId)
    {
        return leadMapper.deleteFollowup(followupId);
    }

    @Override
    public List<BizLeadSetting> selectSettingList(BizLeadSetting setting)
    {
        return leadMapper.selectSettingList(setting);
    }

    @Override
    public int insertSetting(BizLeadSetting setting)
    {
        validateSetting(setting);
        setting.setCreateBy(SecurityUtils.getUsername());
        return leadMapper.insertSetting(setting);
    }

    @Override
    public int updateSetting(BizLeadSetting setting)
    {
        validateSetting(setting);
        setting.setUpdateBy(SecurityUtils.getUsername());
        return leadMapper.updateSetting(setting);
    }

    @Override
    public int deleteSetting(Long settingId)
    {
        return leadMapper.deleteSetting(settingId);
    }

    @Override
    public Map<String, Object> selectDashboard()
    {
        Long currentUserId = SecurityUtils.getUserId();
        Long currentDeptId = SecurityUtils.getDeptId();
        Boolean dataScope = !SecurityUtils.isAdmin();
        Map<String, Object> data = new HashMap<>();
        data.put("cards", leadMapper.selectDashboardCards(currentUserId, currentDeptId, dataScope));
        data.put("sources", leadMapper.selectSourceStats(currentUserId, currentDeptId, dataScope));
        data.put("statuses", leadMapper.selectStatusStats(currentUserId, currentDeptId, dataScope));
        return data;
    }

    private void validateLead(BizLead lead)
    {
        if (lead == null)
        {
            throw new ServiceException("线索不能为空");
        }
        requiredText(lead.getLeadName(), "线索名称不能为空");
        requiredText(lead.getContactName(), "联系人不能为空");
        requiredText(lead.getMobile(), "手机号不能为空");
        requiredText(lead.getSourceCode(), "线索来源不能为空");
        requiredText(lead.getPriority(), "优先级不能为空");
        requiredText(lead.getLegalDemand(), "法律需求不能为空");
        assertEnabledSetting("source", lead.getSourceCode(), "线索来源不存在或已停用");
        assertDictValue("law_lead_priority", lead.getPriority(), "线索优先级不合法");
    }

    private void validateFollowup(BizLeadFollowup followup)
    {
        if (followup == null)
        {
            throw new ServiceException("跟进记录不能为空");
        }
        requiredText(followup.getFollowType(), "跟进方式不能为空");
        requiredText(followup.getContent(), "跟进内容不能为空");
        assertDictValue("law_lead_follow_type", followup.getFollowType(), "跟进方式不合法");
    }

    private void validateSetting(BizLeadSetting setting)
    {
        if (setting == null)
        {
            throw new ServiceException("线索配置不能为空");
        }
        requiredText(setting.getSettingType(), "配置类型不能为空");
        requiredText(setting.getSettingCode(), "配置编码不能为空");
        requiredText(setting.getSettingName(), "配置名称不能为空");
        assertDictValue("law_lead_setting_type", setting.getSettingType(), "配置类型不合法");
    }

    private String requiredText(Object value, String message)
    {
        String text = value == null ? null : String.valueOf(value).trim();
        if (StringUtils.isEmpty(text))
        {
            throw new ServiceException(message);
        }
        return text;
    }

    private void assertDictValue(String dictType, Object value, String message)
    {
        String valueText = requiredText(value, message);
        List<SysDictData> options = dictTypeService.selectDictDataByType(dictType);
        if (options == null || options.isEmpty())
        {
            throw new ServiceException("字典未初始化：" + dictType);
        }
        for (SysDictData item : options)
        {
            if (valueText.equals(item.getDictValue()))
            {
                return;
            }
        }
        throw new ServiceException(message);
    }

    private void assertEnabledSetting(String settingType, Object value, String message)
    {
        String valueText = requiredText(value, message);
        BizLeadSetting query = new BizLeadSetting();
        query.setSettingType(settingType);
        query.setSettingCode(valueText);
        query.setStatus("0");
        List<BizLeadSetting> settings = leadMapper.selectSettingList(query);
        if (settings == null || settings.isEmpty())
        {
            throw new ServiceException(message);
        }
    }

    private void applyListScope(BizLead lead)
    {
        lead.setCurrentUserId(SecurityUtils.getUserId());
        lead.setCurrentDeptId(SecurityUtils.getDeptId());
        // 公海是共享池，是否可见由 lead:pool:list 控制；其他列表遵循若依角色数据范围。
        lead.setDataScope(!SecurityUtils.isAdmin() && !"pool".equals(lead.getListMode()));
    }

    private BizLead requiredLead(Long leadId)
    {
        BizLead lead = leadMapper.selectLeadById(leadId);
        if (lead == null || "2".equals(lead.getDelFlag()))
        {
            throw new ServiceException("线索不存在或已被删除");
        }
        return lead;
    }

    private BizLead requiredAccessibleLead(Long leadId, boolean includeDeleted, boolean allowPool)
    {
        BizLead lead = leadMapper.selectLeadById(leadId);
        if (lead == null || (!includeDeleted && "2".equals(lead.getDelFlag())))
        {
            throw new ServiceException("线索不存在或已被删除");
        }
        if (!canAccessLead(lead, allowPool))
        {
            throw new ServiceException("没有权限操作该线索");
        }
        return lead;
    }

    private boolean canAccessLead(BizLead lead, boolean allowPool)
    {
        if (SecurityUtils.isAdmin())
        {
            return true;
        }
        if (allowPool && POOL_YES.equals(lead.getPoolStatus()) && SecurityUtils.hasPermi("lead:pool:list"))
        {
            return true;
        }
        return leadMapper.countLeadInDataScope(lead.getLeadId(), SecurityUtils.getUserId(), SecurityUtils.getDeptId(), "2".equals(lead.getDelFlag())) > 0;
    }

    private void assertActiveLead(BizLead lead, String action)
    {
        if (STATUS_CONVERTED.equals(lead.getStatus()) || STATUS_INVALID.equals(lead.getStatus()) || STATUS_CLOSED.equals(lead.getStatus()))
        {
            throw new ServiceException("已转化、无效或关闭的线索不能" + action);
        }
    }

    private void assertOwnedLead(BizLead lead, String action)
    {
        if (POOL_YES.equals(lead.getPoolStatus()) || lead.getOwnerId() == null)
        {
            throw new ServiceException("公海或未分配线索需先领取/分配后才能" + action);
        }
    }
}
