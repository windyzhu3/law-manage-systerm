package com.ruoyi.system.service.lead;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.business.lead.dto.LeadFollowupCommand;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.law.business.shared.error.BusinessErrorCode;
import com.law.business.shared.status.LeadStatus;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.BizLead;
import com.ruoyi.system.domain.BizLeadFollowup;
import com.ruoyi.system.mapper.BizLeadMapper;
import com.ruoyi.system.service.ISysDictTypeService;

@Service
public class LeadFollowupService
{
    private static final String IN_POOL = "1";

    private final BizLeadMapper mapper;
    private final LeadAccessPolicy access;
    private final BusinessActorProvider actors;
    private final ISysDictTypeService dictionaries;

    public LeadFollowupService(BizLeadMapper mapper, LeadAccessPolicy access,
            BusinessActorProvider actors, ISysDictTypeService dictionaries)
    {
        this.mapper = mapper;
        this.access = access;
        this.actors = actors;
        this.dictionaries = dictionaries;
    }

    public List<BizLeadFollowup> list(BizLeadFollowup query)
    {
        if ("mine".equals(query.getListMode()))
        {
            query.setCurrentUserId(actors.current().userId());
        }
        return mapper.selectFollowupList(query);
    }

    @Transactional
    public int add(LeadFollowupCommand command, boolean ownerOnly)
    {
        validate(command, false);
        BizLead lead = access.requireOperable(command.getLeadId());
        BusinessActor actor = actors.current();
        requireFollowable(lead, actor, ownerOnly);

        BizLeadFollowup followup = toFollowup(command);
        followup.setFollowUserId(actor.userId());
        followup.setFollowUserName(actor.displayName());
        followup.setCreateBy(actor.userName());
        if (mapper.insertFollowup(followup) <= 0)
        {
            throw error(BusinessErrorCode.CONCURRENT_MODIFICATION, "跟进记录创建失败");
        }
        int touched = mapper.touchLeadFollowTimeConditionally(lead.getLeadId(), command.getNextFollowTime(),
                actor.userName(), lead.getStatus());
        if (touched <= 0)
        {
            throw error(BusinessErrorCode.CONCURRENT_MODIFICATION, "线索状态已变化，请刷新后重试");
        }
        return 1;
    }

    @Transactional
    public int update(LeadFollowupCommand command, boolean ownerOnly)
    {
        validate(command, true);
        BizLeadFollowup stored = requireFollowup(command.getFollowupId());
        if (!stored.getLeadId().equals(command.getLeadId()))
        {
            throw error(BusinessErrorCode.VALIDATION_FAILED, "跟进记录与线索不匹配");
        }
        BizLead lead = access.requireOperable(stored.getLeadId());
        BusinessActor actor = actors.current();
        requireFollowable(lead, actor, ownerOnly);
        BizLeadFollowup followup = toFollowup(command);
        followup.setUpdateBy(actor.userName());
        if (mapper.updateFollowup(followup) <= 0)
        {
            throw error(BusinessErrorCode.CONCURRENT_MODIFICATION, "跟进记录已变化，请刷新后重试");
        }
        return 1;
    }

    @Transactional
    public int remove(Long followupId, boolean ownerOnly)
    {
        BizLeadFollowup stored = requireFollowup(followupId);
        BizLead lead = access.requireOperable(stored.getLeadId());
        requireFollowable(lead, actors.current(), ownerOnly);
        if (mapper.deleteFollowup(followupId, stored.getLeadId()) <= 0)
        {
            throw error(BusinessErrorCode.CONCURRENT_MODIFICATION, "跟进记录已变化，请刷新后重试");
        }
        return 1;
    }

    private BizLeadFollowup requireFollowup(Long followupId)
    {
        if (followupId == null)
        {
            throw error(BusinessErrorCode.VALIDATION_FAILED, "跟进记录不能为空");
        }
        BizLeadFollowup followup = mapper.selectFollowupById(followupId);
        if (followup == null)
        {
            throw error(BusinessErrorCode.DATA_NOT_FOUND, "跟进记录不存在");
        }
        return followup;
    }

    private void validate(LeadFollowupCommand command, boolean update)
    {
        if (command == null || command.getLeadId() == null)
        {
            throw error(BusinessErrorCode.VALIDATION_FAILED, "线索不能为空");
        }
        if (update && command.getFollowupId() == null)
        {
            throw error(BusinessErrorCode.VALIDATION_FAILED, "跟进记录不能为空");
        }
        required(command.getFollowType(), "跟进方式不能为空");
        required(command.getFollowResult(), "跟进结果不能为空");
        required(command.getContent(), "跟进内容不能为空");
        if (command.getContent().length() > 1000)
        {
            throw error(BusinessErrorCode.VALIDATION_FAILED, "跟进内容不能超过1000个字符");
        }
        List<SysDictData> options = dictionaries.selectDictDataByType("law_lead_follow_type");
        boolean valid = options != null && options.stream()
                .anyMatch(item -> command.getFollowType().equals(item.getDictValue()));
        if (!valid)
        {
            throw error(BusinessErrorCode.VALIDATION_FAILED, "跟进方式无效");
        }
    }

    private void requireFollowable(BizLead lead, BusinessActor actor, boolean ownerOnly)
    {
        boolean following = LeadStatus.WAIT_FOLLOW.code().equals(lead.getStatus())
                || LeadStatus.FOLLOWING.code().equals(lead.getStatus());
        if (!following)
        {
            throw error(BusinessErrorCode.STATE_CONFLICT, "当前线索状态不允许跟进");
        }
        if (IN_POOL.equals(lead.getPoolStatus()) || lead.getOwnerId() == null)
        {
            throw error(BusinessErrorCode.PRECONDITION_FAILED, "线索需先分配或领取后才能跟进");
        }
        if (ownerOnly && !actor.userId().equals(lead.getOwnerId()))
        {
            throw error(BusinessErrorCode.ACCESS_DENIED, "只有当前负责人可以跟进该线索");
        }
    }

    private BizLeadFollowup toFollowup(LeadFollowupCommand command)
    {
        BizLeadFollowup followup = new BizLeadFollowup();
        followup.setFollowupId(command.getFollowupId());
        followup.setLeadId(command.getLeadId());
        followup.setFollowType(command.getFollowType().trim());
        followup.setFollowResult(command.getFollowResult().trim());
        followup.setContent(command.getContent().trim());
        followup.setNextFollowTime(command.getNextFollowTime());
        followup.setTaskStatus(StringUtils.isEmpty(command.getTaskStatus()) ? "1" : command.getTaskStatus());
        return followup;
    }

    private void required(String value, String message)
    {
        if (StringUtils.isEmpty(value == null ? null : value.trim()))
        {
            throw error(BusinessErrorCode.VALIDATION_FAILED, message);
        }
    }

    private ServiceException error(BusinessErrorCode code, String message)
    {
        return new ServiceException(message, code.name());
    }
}
