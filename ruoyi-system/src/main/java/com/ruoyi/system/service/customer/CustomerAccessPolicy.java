package com.ruoyi.system.service.customer;

import org.springframework.stereotype.Service;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.law.business.shared.error.BusinessErrorCode;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.BizCustomer;
import com.ruoyi.system.mapper.BizCustomerMapper;

@Service
public class CustomerAccessPolicy
{
    private static final String NORMAL = "0";
    private static final String MERGED = "2";
    private static final String DELETED = "2";
    private static final String DATA_SCOPE_PERMISSIONS =
            "customer:list,customer:query,customer:add,customer:edit,customer:remove,customer:import,customer:export,"
            + "customer:contact:list,customer:contact:add,customer:contact:edit,customer:contact:remove,"
            + "customer:followup:list,customer:followup:add,customer:followup:remove,"
            + "customer:tag:list,customer:tag:add,customer:tag:edit,customer:tag:remove,customer:tag:assign,"
            + "customer:merge:list,customer:merge:merge";

    private final BizCustomerMapper mapper;
    private final BusinessActorProvider actors;

    public CustomerAccessPolicy(BizCustomerMapper mapper, BusinessActorProvider actors)
    {
        this.mapper = mapper;
        this.actors = actors;
    }

    public BizCustomer requireReadable(Long customerId)
    {
        BizCustomer customer = mapper.selectCustomerById(customerId);
        if (customer == null || DELETED.equals(customer.getDelFlag()) || MERGED.equals(customer.getStatus()))
        {
            throw error(BusinessErrorCode.DATA_NOT_FOUND, "客户不存在、已删除或已合并");
        }
        BusinessActor actor = actors.current();
        if (!actor.administrator()
                && mapper.countCustomerInDataScope(customerId, actor.userId(), actor.deptId(), DATA_SCOPE_PERMISSIONS) == 0)
        {
            throw error(BusinessErrorCode.ACCESS_DENIED, "无权访问该客户");
        }
        return customer;
    }

    public BizCustomer requireOperable(Long customerId)
    {
        BizCustomer customer = requireReadable(customerId);
        if (!NORMAL.equals(customer.getStatus()))
        {
            throw error(BusinessErrorCode.STATE_CONFLICT, "当前客户状态不允许操作");
        }
        return customer;
    }

    private ServiceException error(BusinessErrorCode code, String message)
    {
        return new ServiceException(message, code.name());
    }
}
