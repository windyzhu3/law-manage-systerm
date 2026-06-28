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
            throw new ServiceException("Lead does not exist or has been deleted");
        }
        if (!canAccessLead(lead, false))
        {
            throw new ServiceException("No permission to access this lead");
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
            throw new ServiceException("Lead ID is required");
        }
        requiredAccessibleLead(lead.getLeadId(), false, false);
        validateLead(lead);
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
                throw new ServiceException("Only recycle-bin leads can be permanently deleted");
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
            throw new ServiceException("Owner is required");
        }
        BizLead lead = requiredAccessibleLead(leadId, false, true);
        assertActiveLead(lead, "assign");
        String username = SecurityUtils.getUsername();
        int rows = leadMapper.assignLead(leadId, ownerId, null, username);
        if (rows == 0)
        {
            throw new ServiceException("Lead assignment failed, please refresh and try again");
        }
        leadMapper.insertAssignmentLog(leadId, lead.getOwnerId(), ownerId, "assign", reason, username);
        return rows;
    }

    @Override
    @Transactional
    public int moveToPool(Long leadId, String reason)
    {
        BizLead lead = requiredAccessibleLead(leadId, false, false);
        if (!SecurityUtils.hasPermi("lead:pool:move") && SecurityUtils.hasPermi("lead:mine:pool:move"))
        {
            assertMineLead(lead, "move to pool");
        }
        assertActiveLead(lead, "move to pool");
        if (POOL_YES.equals(lead.getPoolStatus()))
        {
            throw new ServiceException("Lead is already in public pool");
        }
        String username = SecurityUtils.getUsername();
        int rows = leadMapper.moveToPool(leadId, reason, username);
        if (rows == 0)
        {
            throw new ServiceException("Move to public pool failed, please refresh and try again");
        }
        leadMapper.insertAssignmentLog(leadId, lead.getOwnerId(), null, "pool", reason, username);
        return rows;
    }

    @Override
    @Transactional
    public int claimLead(Long leadId)
    {
        BizLead lead = requiredLead(leadId);
        assertActiveLead(lead, "claim");
        if (!POOL_YES.equals(lead.getPoolStatus()))
        {
            throw new ServiceException("Lead has already been claimed, please refresh the list");
        }
        Long userId = SecurityUtils.getUserId();
        int rows = leadMapper.claimLead(leadId, userId, SecurityUtils.getDeptId(), SecurityUtils.getUsername());
        if (rows == 0)
        {
            throw new ServiceException("Lead has already been claimed, please refresh the list");
        }
        leadMapper.insertAssignmentLog(leadId, null, userId, "claim", "claim from public pool", SecurityUtils.getUsername());
        return rows;
    }

    @Override
    @Transactional
    public int convertLead(Long leadId)
    {
        BizLead lead = requiredAccessibleLead(leadId, false, false);
        if (!SecurityUtils.hasPermi("lead:convert") && SecurityUtils.hasPermi("lead:mine:convert"))
        {
            assertMineLead(lead, "convert");
        }
        assertActiveLead(lead, "convert");
        assertOwnedLead(lead, "convert");
        if (!STATUS_WAIT_FOLLOW.equals(lead.getStatus()) && !STATUS_FOLLOWING.equals(lead.getStatus()))
        {
            throw new ServiceException("Only assigned leads in follow-up status can be converted");
        }
        int rows = leadMapper.convertLead(leadId, SecurityUtils.getUsername());
        if (rows == 0)
        {
            throw new ServiceException("Lead conversion failed, please refresh and try again");
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
        if (!SecurityUtils.hasPermi("lead:followup:add") && SecurityUtils.hasPermi("lead:mine:followup"))
        {
            assertMineLead(lead, "follow up");
        }
        assertActiveLead(lead, "follow up");
        assertOwnedLead(lead, "follow up");
        followup.setFollowUserId(SecurityUtils.getUserId());
        followup.setCreateBy(SecurityUtils.getUsername());
        int rows = leadMapper.insertFollowup(followup);
        int touched = leadMapper.touchLeadFollowTime(followup.getLeadId(), followup.getNextFollowTime(), SecurityUtils.getUsername());
        if (touched == 0)
        {
            throw new ServiceException("Lead follow-up failed, please refresh and try again");
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
            throw new ServiceException("Lead is required");
        }
        requiredText(lead.getLeadName(), "Lead name is required");
        requiredText(lead.getContactName(), "Contact name is required");
        requiredText(lead.getMobile(), "Mobile is required");
        requiredText(lead.getSourceCode(), "Lead source is required");
        requiredText(lead.getPriority(), "Lead priority is required");
        requiredText(lead.getLegalDemand(), "Legal demand is required");
        assertEnabledSetting("source", lead.getSourceCode(), "Lead source does not exist or has been disabled");
        assertDictValue("law_lead_priority", lead.getPriority(), "Lead priority is invalid");
    }

    private void validateFollowup(BizLeadFollowup followup)
    {
        if (followup == null)
        {
            throw new ServiceException("Follow-up record is required");
        }
        requiredText(followup.getFollowType(), "Follow-up type is required");
        requiredText(followup.getContent(), "Follow-up content is required");
        assertDictValue("law_lead_follow_type", followup.getFollowType(), "Follow-up type is invalid");
    }

    private void validateSetting(BizLeadSetting setting)
    {
        if (setting == null)
        {
            throw new ServiceException("Lead setting is required");
        }
        requiredText(setting.getSettingType(), "Setting type is required");
        requiredText(setting.getSettingCode(), "Setting code is required");
        requiredText(setting.getSettingName(), "Setting name is required");
        assertDictValue("law_lead_setting_type", setting.getSettingType(), "Setting type is invalid");
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
            throw new ServiceException("Dictionary is not initialized: " + dictType);
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
        lead.setDataScope(!SecurityUtils.isAdmin() && !"pool".equals(lead.getListMode()));
    }

    private BizLead requiredLead(Long leadId)
    {
        BizLead lead = leadMapper.selectLeadById(leadId);
        if (lead == null || "2".equals(lead.getDelFlag()))
        {
            throw new ServiceException("Lead does not exist or has been deleted");
        }
        return lead;
    }

    private BizLead requiredAccessibleLead(Long leadId, boolean includeDeleted, boolean allowPool)
    {
        BizLead lead = leadMapper.selectLeadById(leadId);
        if (lead == null || (!includeDeleted && "2".equals(lead.getDelFlag())))
        {
            throw new ServiceException("Lead does not exist or has been deleted");
        }
        if (!canAccessLead(lead, allowPool))
        {
            throw new ServiceException("No permission to operate this lead");
        }
        return lead;
    }

    private boolean canAccessLead(BizLead lead, boolean allowPool)
    {
        if (SecurityUtils.isAdmin())
        {
            return true;
        }
        Long currentUserId = SecurityUtils.getUserId();
        if (SecurityUtils.hasPermi("lead:query")
                && leadMapper.countLeadInDataScope(lead.getLeadId(), currentUserId, SecurityUtils.getDeptId(), "2".equals(lead.getDelFlag())) > 0)
        {
            return true;
        }
        if (SecurityUtils.hasPermi("lead:mine:query")
                && !"2".equals(lead.getDelFlag())
                && POOL_NO.equals(lead.getPoolStatus())
                && currentUserId != null
                && currentUserId.equals(lead.getOwnerId()))
        {
            return true;
        }
        if (SecurityUtils.hasPermi("lead:pool:query")
                && !"2".equals(lead.getDelFlag())
                && POOL_YES.equals(lead.getPoolStatus()))
        {
            return true;
        }
        if (SecurityUtils.hasPermi("lead:recycle:query")
                && "2".equals(lead.getDelFlag())
                && leadMapper.countLeadInDataScope(lead.getLeadId(), currentUserId, SecurityUtils.getDeptId(), true) > 0)
        {
            return true;
        }
        if (allowPool && POOL_YES.equals(lead.getPoolStatus()) && SecurityUtils.hasPermi("lead:pool:list"))
        {
            return true;
        }
        return hasLeadOperationPerm()
                && leadMapper.countLeadInDataScope(lead.getLeadId(), currentUserId, SecurityUtils.getDeptId(), "2".equals(lead.getDelFlag())) > 0;
    }

    private boolean hasLeadOperationPerm()
    {
        return SecurityUtils.hasPermi("lead:edit")
                || SecurityUtils.hasPermi("lead:remove")
                || SecurityUtils.hasPermi("lead:assign")
                || SecurityUtils.hasPermi("lead:pool:move")
                || SecurityUtils.hasPermi("lead:mine:pool:move")
                || SecurityUtils.hasPermi("lead:convert")
                || SecurityUtils.hasPermi("lead:mine:convert")
                || SecurityUtils.hasPermi("lead:followup:add")
                || SecurityUtils.hasPermi("lead:mine:followup")
                || SecurityUtils.hasPermi("lead:recycle:restore")
                || SecurityUtils.hasPermi("lead:recycle:purge");
    }

    private void assertActiveLead(BizLead lead, String action)
    {
        if (STATUS_CONVERTED.equals(lead.getStatus()) || STATUS_INVALID.equals(lead.getStatus()) || STATUS_CLOSED.equals(lead.getStatus()))
        {
            throw new ServiceException("Converted, invalid, or closed leads cannot " + action);
        }
    }

    private void assertOwnedLead(BizLead lead, String action)
    {
        if (POOL_YES.equals(lead.getPoolStatus()) || lead.getOwnerId() == null)
        {
            throw new ServiceException("Public pool or unassigned leads must be claimed or assigned before " + action);
        }
    }

    private void assertMineLead(BizLead lead, String action)
    {
        if (lead == null
                || "2".equals(lead.getDelFlag())
                || POOL_YES.equals(lead.getPoolStatus())
                || lead.getOwnerId() == null
                || !lead.getOwnerId().equals(SecurityUtils.getUserId()))
        {
            throw new ServiceException("Only assigned owner can " + action + " this lead");
        }
    }
}
