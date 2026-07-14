package com.ruoyi.system.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.domain.BizLeadFollowup;
import com.ruoyi.system.domain.BizLeadSetting;

public interface BizLeadMapper
{
    public List<BizLead> selectLeadList(BizLead lead);
    public BizLead selectLeadById(Long leadId);
    public int insertLead(BizLead lead);
    public int updateLead(BizLead lead);
    public int softDeleteLead(@Param("leadIds") Long[] leadIds, @Param("updateBy") String updateBy);
    public int restoreLead(@Param("leadIds") Long[] leadIds, @Param("updateBy") String updateBy);
    public int purgeLeadFollowups(@Param("leadIds") Long[] leadIds);
    public int purgeLeadAssignmentLogs(@Param("leadIds") Long[] leadIds);
    public int purgeLead(@Param("leadIds") Long[] leadIds);
    public int assignLead(@Param("leadId") Long leadId, @Param("ownerId") Long ownerId, @Param("updateBy") String updateBy, @Param("expectedStatus") String expectedStatus);
    public int moveToPool(@Param("leadId") Long leadId, @Param("reason") String reason, @Param("updateBy") String updateBy, @Param("expectedStatus") String expectedStatus);
    public int claimLead(@Param("leadId") Long leadId, @Param("ownerId") Long ownerId, @Param("deptId") Long deptId, @Param("updateBy") String updateBy, @Param("expectedStatus") String expectedStatus);
    public int convertLead(@Param("leadId") Long leadId, @Param("updateBy") String updateBy);
    public int bindCustomer(@Param("leadId") Long leadId, @Param("customerId") Long customerId, @Param("updateBy") String updateBy);
    public int insertAssignmentLog(Map<String, Object> assignmentLog);
    public List<BizLeadFollowup> selectFollowupList(BizLeadFollowup followup);
    public int insertFollowup(BizLeadFollowup followup);
    public int updateFollowup(BizLeadFollowup followup);
    public int deleteFollowup(Long followupId);
    public int touchLeadFollowTime(@Param("leadId") Long leadId, @Param("nextFollowTime") java.util.Date nextFollowTime, @Param("updateBy") String updateBy);
    public int countLeadInDataScope(@Param("leadId") Long leadId, @Param("currentUserId") Long currentUserId, @Param("currentDeptId") Long currentDeptId, @Param("includeDeleted") Boolean includeDeleted);
    public List<BizLeadSetting> selectSettingList(BizLeadSetting setting);
    public int insertSetting(BizLeadSetting setting);
    public int updateSetting(BizLeadSetting setting);
    public int deleteSetting(Long settingId);
    public List<Map<String, Object>> selectDashboardCards(@Param("currentUserId") Long currentUserId, @Param("currentDeptId") Long currentDeptId, @Param("dataScope") Boolean dataScope);
    public List<Map<String, Object>> selectSourceStats(@Param("currentUserId") Long currentUserId, @Param("currentDeptId") Long currentDeptId, @Param("dataScope") Boolean dataScope);
    public List<Map<String, Object>> selectStatusStats(@Param("currentUserId") Long currentUserId, @Param("currentDeptId") Long currentDeptId, @Param("dataScope") Boolean dataScope);
}
