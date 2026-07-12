package com.ruoyi.system.service.casecenter;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.mapper.BizCaseMapper;
import com.ruoyi.system.service.ISysDictTypeService;
import com.ruoyi.system.service.ISysUserService;

@Service
public class LawyerProfileService
{
    private static final String ENABLED = "Y";
    private static final String DISABLED = "N";
    private final BizCaseMapper mapper;
    private final ISysUserService userService;
    private final ISysDictTypeService dictService;

    public LawyerProfileService(BizCaseMapper mapper, ISysUserService userService, ISysDictTypeService dictService)
    {
        this.mapper = mapper;
        this.userService = userService;
        this.dictService = dictService;
    }

    @Transactional
    public int save(Map<String, Object> profile)
    {
        Long userId = toLong(profile == null ? null : profile.get("userId"));
        SysUser user = userService.selectUserById(userId);
        if (user == null || "1".equals(user.getStatus())) throw error("DATA_NOT_FOUND", "律师不存在或已停用");
        String role = text(profile.get("lawyerRole"), "lawyer");
        assertDict("law_lawyer_role", role, "律师角色不合法");
        String specialties = text(profile.get("specialties"), "business");
        for (String specialty : specialties.split(",")) if (!StringUtils.isEmpty(specialty)) assertDict("law_case_type", specialty.trim(), "专业方向不合法");
        String assignEnabled = text(profile.get("assignEnabled"), DISABLED);
        requireAssignFlag(assignEnabled);
        profile.put("userId", userId); profile.put("lawyerRole", role); profile.put("specialties", specialties);
        profile.put("loadLimit", number(profile.get("loadLimit"), 100));
        profile.put("avgResponseHours", number(profile.get("avgResponseHours"), 4));
        profile.put("assignEnabled", assignEnabled); profile.put("createBy", SecurityUtils.getUsername()); profile.put("updateBy", SecurityUtils.getUsername());
        Map<String, Object> existed = mapper.selectLawyerProfileByUserId(userId);
        if (existed == null) throw error("DATA_NOT_FOUND", "律师不存在或已停用");
        Object profileId = existed.get("profileId");
        return profileId == null || StringUtils.isEmpty(String.valueOf(profileId)) ? mapper.insertLawyerProfile(profile) : mapper.updateLawyerProfile(profile);
    }

    @Transactional
    public int updateStatus(Map<String, Object> profile)
    {
        Long userId = toLong(profile == null ? null : profile.get("userId"));
        String enabled = text(profile.get("assignEnabled"), "");
        requireAssignFlag(enabled);
        Map<String, Object> existed = mapper.selectLawyerProfileByUserId(userId);
        if (existed == null) throw error("DATA_NOT_FOUND", "律师不存在或已停用");
        Object profileId = existed.get("profileId");
        if (profileId == null || StringUtils.isEmpty(String.valueOf(profileId)))
        {
            if (ENABLED.equals(enabled)) throw error("PRECONDITION_FAILED", "请先编辑并保存律师档案后再启用分案");
            return 1;
        }
        profile.put("updateBy", SecurityUtils.getUsername());
        return mapper.updateLawyerProfileStatus(profile);
    }

    private void requireAssignFlag(String value) { if (!ENABLED.equals(value) && !DISABLED.equals(value)) throw error("VALIDATION_FAILED", "可分案状态不合法"); }
    private void assertDict(String type, String value, String message) { List<SysDictData> values = dictService.selectDictDataByType(type); if (values != null) for (SysDictData item : values) if (value.equals(item.getDictValue())) return; throw error("VALIDATION_FAILED", values == null || values.isEmpty() ? "字典未初始化：" + type : message); }
    private Long toLong(Object value) { try { return Long.valueOf(String.valueOf(value)); } catch (RuntimeException e) { throw error("VALIDATION_FAILED", "请选择律师"); } }
    private Number number(Object value, Number fallback) { if (value == null || StringUtils.isEmpty(String.valueOf(value))) return fallback; try { return new java.math.BigDecimal(String.valueOf(value)); } catch (NumberFormatException e) { throw error("VALIDATION_FAILED", "数值格式不合法"); } }
    private String text(Object value, String fallback) { return value == null || StringUtils.isEmpty(String.valueOf(value)) ? fallback : String.valueOf(value).trim(); }
    private ServiceException error(String code, String message) { return new ServiceException(message, code); }
}
