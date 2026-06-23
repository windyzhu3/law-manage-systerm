package com.ruoyi.system.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.domain.BizLeadFollowup;
import com.ruoyi.system.domain.BizLeadSetting;

public interface IBizLeadService
{
    public List<BizLead> selectLeadList(BizLead lead);
    public BizLead selectLeadById(Long leadId);
    public int insertLead(BizLead lead);
    public int updateLead(BizLead lead);
    public int softDeleteLead(Long[] leadIds);
    public int restoreLead(Long[] leadIds);
    public int purgeLead(Long[] leadIds);
    public int assignLead(Long leadId, Long ownerId, String reason);
    public int moveToPool(Long leadId, String reason);
    public int claimLead(Long leadId);
    public int convertLead(Long leadId);
    public List<BizLeadFollowup> selectFollowupList(BizLeadFollowup followup);
    public int insertFollowup(BizLeadFollowup followup);
    public int updateFollowup(BizLeadFollowup followup);
    public int deleteFollowup(Long followupId);
    public List<BizLeadSetting> selectSettingList(BizLeadSetting setting);
    public int insertSetting(BizLeadSetting setting);
    public int updateSetting(BizLeadSetting setting);
    public int deleteSetting(Long settingId);
    public Map<String, Object> selectDashboard();
}

