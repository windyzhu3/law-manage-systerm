package com.ruoyi.system.service.casecenter;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.business.lawcase.dto.LawyerProfileSaveCommand;
import com.law.business.lawcase.dto.LawyerProfileStatusCommand;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.law.business.shared.error.BusinessErrorCode;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
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
    private final ISysUserService users;
    private final ISysDictTypeService dictionaries;
    private final BusinessActorProvider actors;

    public LawyerProfileService(BizCaseMapper mapper, ISysUserService users,
            ISysDictTypeService dictionaries, BusinessActorProvider actors)
    {
        this.mapper = mapper; this.users = users; this.dictionaries = dictionaries; this.actors = actors;
    }

    @Transactional
    public int save(LawyerProfileSaveCommand command)
    {
        if (command == null || command.getUserId() == null)
            throw error(BusinessErrorCode.VALIDATION_FAILED, "请选择律师");
        SysUser user = users.selectUserById(command.getUserId());
        if (user == null || "1".equals(user.getStatus()))
            throw error(BusinessErrorCode.DATA_NOT_FOUND, "律师不存在或已停用");
        String role = defaultText(command.getLawyerRole(), "lawyer");
        requireDict("law_lawyer_role", role, "律师角色不合法");
        String specialties = defaultText(command.getSpecialties(), "business");
        for (String specialty : specialties.split(","))
            if (!StringUtils.isEmpty(specialty.trim())) requireDict("law_case_type", specialty.trim(), "专业方向不合法");
        String assignEnabled = defaultText(command.getAssignEnabled(), DISABLED);
        requireAssignFlag(assignEnabled);

        Map<String,Object> existing = mapper.selectLawyerProfileByUserId(command.getUserId());
        if (existing == null) throw error(BusinessErrorCode.DATA_NOT_FOUND, "律师不存在或已停用");
        BusinessActor actor = actors.current();
        Map<String,Object> row = saveRow(command, role, specialties, assignEnabled, actor);
        Object profileId = value(existing, "profileId", "profile_id");
        return profileId == null || StringUtils.isEmpty(String.valueOf(profileId))
                ? mapper.insertLawyerProfile(row) : mapper.updateLawyerProfile(row);
    }

    @Transactional
    public int updateStatus(LawyerProfileStatusCommand command)
    {
        if (command == null || command.getUserId() == null)
            throw error(BusinessErrorCode.VALIDATION_FAILED, "请选择律师");
        requireAssignFlag(command.getAssignEnabled());
        Map<String,Object> existing = mapper.selectLawyerProfileByUserId(command.getUserId());
        if (existing == null) throw error(BusinessErrorCode.DATA_NOT_FOUND, "律师不存在或已停用");
        Object profileId = value(existing, "profileId", "profile_id");
        if (profileId == null || StringUtils.isEmpty(String.valueOf(profileId)))
        {
            if (ENABLED.equals(command.getAssignEnabled()))
                throw error(BusinessErrorCode.PRECONDITION_FAILED, "请先编辑并保存律师档案后再启用分案");
            return 1;
        }
        Map<String,Object> row = new HashMap<>();
        row.put("userId", command.getUserId()); row.put("assignEnabled", command.getAssignEnabled());
        row.put("updateBy", actors.current().userName());
        return mapper.updateLawyerProfileStatus(row);
    }

    private Map<String,Object> saveRow(LawyerProfileSaveCommand command, String role,
            String specialties, String assignEnabled, BusinessActor actor)
    {
        Map<String,Object> row = new HashMap<>();
        row.put("userId", command.getUserId()); row.put("lawyerRole", role); row.put("specialties", specialties);
        row.put("loadLimit", valueOr(command.getLoadLimit(), new BigDecimal("100")));
        row.put("avgResponseHours", valueOr(command.getAvgResponseHours(), new BigDecimal("4")));
        row.put("assignEnabled", assignEnabled); row.put("remark", command.getRemark());
        row.put("createBy", actor.userName()); row.put("updateBy", actor.userName());
        return row;
    }

    private BigDecimal valueOr(BigDecimal value, BigDecimal fallback)
    {
        return value == null ? fallback : value;
    }

    private void requireAssignFlag(String value)
    {
        if (!ENABLED.equals(value) && !DISABLED.equals(value))
            throw error(BusinessErrorCode.VALIDATION_FAILED, "可分案状态不合法");
    }

    private void requireDict(String type, String wanted, String message)
    {
        List<SysDictData> options = dictionaries.selectDictDataByType(type);
        if (options != null) for (SysDictData option : options) if (wanted.equals(option.getDictValue())) return;
        throw error(options == null || options.isEmpty() ? BusinessErrorCode.PRECONDITION_FAILED
                : BusinessErrorCode.VALIDATION_FAILED, options == null || options.isEmpty() ? "字典未初始化：" + type : message);
    }

    private Object value(Map<String,Object> map, String first, String second)
    {
        return map.containsKey(first) ? map.get(first) : map.get(second);
    }

    private String defaultText(String value, String fallback)
    {
        return StringUtils.isEmpty(value) ? fallback : value.trim();
    }

    private ServiceException error(BusinessErrorCode code, String message)
    {
        return new ServiceException(message, code.name());
    }
}
