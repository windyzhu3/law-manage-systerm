package com.ruoyi.system.service.lead;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.business.event.BusinessEventCommand;
import com.law.business.event.BusinessEventPublisher;
import com.law.business.event.BusinessEventType;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.law.business.shared.error.BusinessErrorCode;
import com.law.business.shared.status.LeadStatus;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.domain.BizLeadSetting;
import com.ruoyi.system.mapper.BizLeadMapper;
import com.ruoyi.system.service.ISysDictTypeService;

@Service
public class LeadCommandService
{
    private static final String DELETED = "2";
    private static final String POOL_NO = "0";
    private static final String POOL_YES = "1";

    private final BizLeadMapper mapper;
    private final ISysDictTypeService dictionaries;
    private final BusinessEventPublisher events;
    private final BusinessActorProvider actors;
    private final LeadAccessPolicy access;

    public LeadCommandService(BizLeadMapper mapper, ISysDictTypeService dictionaries,
            BusinessEventPublisher events, BusinessActorProvider actors, LeadAccessPolicy access)
    {
        this.mapper = mapper;
        this.dictionaries = dictionaries;
        this.events = events;
        this.actors = actors;
        this.access = access;
    }

    @Transactional
    public int create(BizLead lead)
    {
        validateLead(lead);
        BusinessActor actor = actors.current();
        lead.setCreateBy(actor.userName());
        lead.setLeadNo("XS" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")));
        if (lead.getOwnerId() == null)
        {
            lead.setStatus(LeadStatus.UNASSIGNED.code());
            lead.setPoolStatus(POOL_YES);
            lead.setDeptId(null);
        }
        else
        {
            lead.setStatus(LeadStatus.WAIT_FOLLOW.code());
            lead.setPoolStatus(POOL_NO);
        }
        int rows = mapper.insertLead(lead);
        changed(rows, "线索创建失败");
        mapper.insertSourceBusinessTagIfAbsent(lead.getSourceCode(), actor.userName());
        changed(mapper.insertLeadSourceTagRelationIfAbsent(lead.getLeadId(), lead.getSourceCode(),
                actor.userName()), "线索来源标签关系创建失败");
        Map<String, Object> payload = new HashMap<>();
        payload.put("schemaVersion", 1);
        payload.put("operatorId", actor.userId());
        payload.put("ownerId", lead.getOwnerId() == null ? "" : lead.getOwnerId());
        events.publish(new BusinessEventCommand(BusinessEventType.LEAD_CREATED, "LEAD", lead.getLeadId(),
                lead.getLeadNo(), "LEAD_CREATED:" + lead.getLeadId(), payload));
        return rows;
    }

    public int update(BizLead lead)
    {
        if (lead == null || lead.getLeadId() == null)
        {
            throw error(BusinessErrorCode.VALIDATION_FAILED, "线索ID不能为空");
        }
        BizLead persisted = access.requireOperable(lead.getLeadId());
        if (lead.getSourceCode() != null
                && !java.util.Objects.equals(persisted.getSourceCode(), lead.getSourceCode()))
        {
            throw error(BusinessErrorCode.STATE_CONFLICT, "线索来源创建后不可修改");
        }
        lead.setSourceCode(persisted.getSourceCode());
        validateLead(lead);
        clearServerManagedFields(lead);
        lead.setUpdateBy(actors.current().userName());
        int rows = mapper.updateLead(lead);
        changed(rows, "线索更新失败");
        return rows;
    }

    public int softDelete(Long[] leadIds)
    {
        requireIds(leadIds);
        for (Long leadId : leadIds)
        {
            BizLead lead = access.requireOperable(leadId);
            if (LeadStatus.CONVERTED.code().equals(lead.getStatus()))
            {
                throw error(BusinessErrorCode.STATE_CONFLICT, "已转化线索不能删除");
            }
        }
        int rows = mapper.softDeleteLead(leadIds, actors.current().userName());
        changed(rows, "线索删除失败");
        return rows;
    }

    public int restore(Long[] leadIds)
    {
        requireIds(leadIds);
        for (Long leadId : leadIds)
        {
            BizLead lead = access.requireReadable(leadId, true, false);
            if (!DELETED.equals(lead.getDelFlag()))
            {
                throw error(BusinessErrorCode.STATE_CONFLICT, "只有回收站线索可以恢复");
            }
        }
        int rows = mapper.restoreLead(leadIds, actors.current().userName());
        changed(rows, "线索恢复失败");
        return rows;
    }

    @Transactional
    public int purge(Long[] leadIds)
    {
        requireIds(leadIds);
        for (Long leadId : leadIds)
        {
            BizLead lead = access.requireReadable(leadId, true, false);
            if (!DELETED.equals(lead.getDelFlag()))
            {
                throw error(BusinessErrorCode.STATE_CONFLICT, "只有回收站线索可以彻底删除");
            }
        }
        mapper.purgeLeadCallRecords(leadIds);
        mapper.purgeLeadInvalidReviews(leadIds);
        mapper.purgeLeadRetryRecords(leadIds);
        mapper.purgeLeadQualityRecords(leadIds);
        mapper.purgeLeadDeadPoolLogs(leadIds);
        mapper.purgeLeadTagRelations(leadIds);
        mapper.purgeLeadFollowups(leadIds);
        mapper.purgeLeadAssignmentLogs(leadIds);
        int rows = mapper.purgeLead(leadIds);
        changed(rows, "线索彻底删除失败");
        return rows;
    }

    public int createSetting(BizLeadSetting setting)
    {
        validateSetting(setting);
        setting.setCreateBy(actors.current().userName());
        return mapper.insertSetting(setting);
    }

    public int updateSetting(BizLeadSetting setting)
    {
        validateSetting(setting);
        setting.setUpdateBy(actors.current().userName());
        return mapper.updateSetting(setting);
    }

    public int deleteSetting(Long settingId)
    {
        if (settingId == null) throw error(BusinessErrorCode.VALIDATION_FAILED, "线索配置ID不能为空");
        return mapper.deleteSetting(settingId);
    }

    private void validateLead(BizLead lead)
    {
        if (lead == null) throw error(BusinessErrorCode.VALIDATION_FAILED, "线索不能为空");
        required(lead.getLeadName(), "线索名称不能为空");
        required(lead.getContactName(), "联系人不能为空");
        required(lead.getMobile(), "手机号不能为空");
        required(lead.getSourceCode(), "线索来源不能为空");
        required(lead.getPriority(), "优先级不能为空");
        required(lead.getLegalDemand(), "法律需求不能为空");
        BizLeadSetting query = new BizLeadSetting();
        query.setSettingType("source");
        query.setSettingCode(lead.getSourceCode());
        query.setStatus("0");
        List<BizLeadSetting> settings = mapper.selectSettingList(query);
        if (settings == null || settings.isEmpty())
            throw error(BusinessErrorCode.PRECONDITION_FAILED, "线索来源不存在或已停用");
        requireDict("law_lead_priority", lead.getPriority(), "优先级不合法");
    }

    private void validateSetting(BizLeadSetting setting)
    {
        if (setting == null) throw error(BusinessErrorCode.VALIDATION_FAILED, "线索配置不能为空");
        required(setting.getSettingType(), "配置类型不能为空");
        required(setting.getSettingCode(), "配置编码不能为空");
        required(setting.getSettingName(), "配置名称不能为空");
        requireDict("law_lead_setting_type", setting.getSettingType(), "配置类型不合法");
    }

    private void requireDict(String type, String value, String message)
    {
        List<SysDictData> options = dictionaries.selectDictDataByType(type);
        if (options != null)
        {
            for (SysDictData option : options)
            {
                if (value.equals(option.getDictValue())) return;
            }
        }
        throw error(options == null || options.isEmpty() ? BusinessErrorCode.PRECONDITION_FAILED
                : BusinessErrorCode.VALIDATION_FAILED, options == null || options.isEmpty() ? "字典未初始化：" + type : message);
    }

    private void clearServerManagedFields(BizLead lead)
    {
        lead.setStatus(null); lead.setPoolStatus(null); lead.setOwnerId(null); lead.setDeptId(null);
        lead.setConvertedTime(null); lead.setLastFollowTime(null); lead.setInvalidReason(null); lead.setPoolReason(null);
    }

    private void requireIds(Long[] ids)
    {
        if (ids == null || ids.length == 0) throw error(BusinessErrorCode.VALIDATION_FAILED, "请选择线索");
    }

    private String required(Object value, String message)
    {
        String text = value == null ? null : String.valueOf(value).trim();
        if (StringUtils.isEmpty(text)) throw error(BusinessErrorCode.VALIDATION_FAILED, message);
        return text;
    }

    private void changed(int rows, String message)
    {
        if (rows <= 0) throw error(BusinessErrorCode.CONCURRENT_MODIFICATION, message);
    }

    private ServiceException error(BusinessErrorCode code, String message)
    {
        return new ServiceException(message, code.name());
    }
}
