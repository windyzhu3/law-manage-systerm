package com.ruoyi.system.service.customer;

import static com.ruoyi.system.service.customer.CustomerAccessPolicy.DATA_SCOPE_PERMISSIONS;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.ruoyi.common.annotation.DataScope;
import com.ruoyi.system.domain.BizCustomer;
import com.ruoyi.system.mapper.BizCustomerMapper;

@Service
public class CustomerQueryService
{
    private final BizCustomerMapper mapper;
    private final CustomerAccessPolicy access;
    private final BusinessActorProvider actors;

    public CustomerQueryService(BizCustomerMapper mapper, CustomerAccessPolicy access,
            BusinessActorProvider actors)
    {
        this.mapper = mapper;
        this.access = access;
        this.actors = actors;
    }

    @DataScope(deptAlias = "c", userAlias = "c", userField = "owner_id")
    public List<BizCustomer> list(BizCustomer query)
    {
        return mapper.selectCustomerList(query);
    }

    public BizCustomer detail(Long customerId)
    {
        return access.requireReadable(customerId);
    }

    public Map<String, Object> dashboard()
    {
        BusinessActor actor = actors.current();
        boolean dataScope = !actor.administrator();
        Map<String, Object> data = new HashMap<>();
        data.put("cards", mapper.selectDashboardCards(actor.userId(), actor.deptId(), dataScope,
                DATA_SCOPE_PERMISSIONS, Collections.emptyMap()));
        data.put("types", mapper.selectTypeStats(actor.userId(), actor.deptId(), dataScope,
                DATA_SCOPE_PERMISSIONS, Collections.emptyMap()));
        return data;
    }
}
