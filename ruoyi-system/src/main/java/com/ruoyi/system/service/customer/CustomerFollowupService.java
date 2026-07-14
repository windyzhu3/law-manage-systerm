package com.ruoyi.system.service.customer;

import static com.ruoyi.system.service.customer.CustomerAccessPolicy.DATA_SCOPE_PERMISSIONS;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.law.business.customer.dto.CustomerFollowupCreateCommand;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.law.business.shared.error.BusinessErrorCode;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.mapper.BizCustomerMapper;
import com.ruoyi.system.service.ISysDictTypeService;

@Service
public class CustomerFollowupService
{
    private final BizCustomerMapper mapper;
    private final CustomerAccessPolicy access;
    private final BusinessActorProvider actors;
    private final ISysDictTypeService dictionaries;

    public CustomerFollowupService(BizCustomerMapper mapper, CustomerAccessPolicy access,
            BusinessActorProvider actors, ISysDictTypeService dictionaries)
    {
        this.mapper = mapper;
        this.access = access;
        this.actors = actors;
        this.dictionaries = dictionaries;
    }

    public List<Map<String, Object>> list(Map<String, Object> params)
    {
        BusinessActor actor = actors.current();
        params.put("currentUserId", actor.userId());
        params.put("currentDeptId", actor.deptId());
        params.put("dataScope", !actor.administrator());
        params.put("permissions", DATA_SCOPE_PERMISSIONS);
        return mapper.selectFollowups(params);
    }

    @Transactional
    public int create(CustomerFollowupCreateCommand command)
    {
        if (command == null || command.getCustomerId() == null)
            throw error(BusinessErrorCode.VALIDATION_FAILED, "客户不能为空");
        access.requireOperable(command.getCustomerId());
        required(command.getFollowType(), "跟进方式不能为空");
        required(command.getContent(), "跟进内容不能为空");
        requireDict("law_customer_follow_type", command.getFollowType(), "跟进方式不合法");
        BusinessActor actor = actors.current();
        Map<String, Object> followup = new HashMap<>();
        followup.put("customerId", command.getCustomerId());
        followup.put("followType", command.getFollowType());
        followup.put("followResult", command.getFollowResult());
        followup.put("followTime", command.getFollowTime());
        followup.put("content", command.getContent());
        followup.put("nextFollowTime", command.getNextFollowTime());
        followup.put("followUserId", actor.userId());
        followup.put("createBy", actor.userName());
        changed(mapper.insertFollowup(followup), "客户跟进创建失败");
        changed(mapper.touchCustomerFollowTime(followup), "客户跟进时间更新失败");
        return 1;
    }

    @Transactional
    public int delete(Long followupId)
    {
        if (followupId == null) throw error(BusinessErrorCode.VALIDATION_FAILED, "跟进记录不能为空");
        Long customerId = mapper.selectFollowupCustomerId(followupId);
        if (customerId == null) throw error(BusinessErrorCode.DATA_NOT_FOUND, "跟进记录不存在");
        access.requireOperable(customerId);
        changed(mapper.deleteFollowup(followupId, actors.current().userName()), "跟进记录已变化，请刷新后重试");
        return 1;
    }

    private void requireDict(String type, String value, String message)
    {
        List<SysDictData> options = dictionaries.selectDictDataByType(type);
        if (options != null && options.stream().anyMatch(item -> value.equals(item.getDictValue()))) return;
        throw error(options == null || options.isEmpty() ? BusinessErrorCode.PRECONDITION_FAILED
                : BusinessErrorCode.VALIDATION_FAILED,
                options == null || options.isEmpty() ? "字典未初始化：" + type : message);
    }

    private void required(String value, String message)
    {
        if (StringUtils.isEmpty(value == null ? null : value.trim()))
            throw error(BusinessErrorCode.VALIDATION_FAILED, message);
    }

    private void changed(int rows, String message) { if (rows <= 0) throw error(BusinessErrorCode.CONCURRENT_MODIFICATION, message); }
    private ServiceException error(BusinessErrorCode code, String message) { return new ServiceException(message, code.name()); }
}
