package com.ruoyi.system.support;

import com.law.business.security.BusinessActor;
import com.ruoyi.system.domain.BizCustomer;
import com.ruoyi.system.domain.BizLead;

public final class BusinessFixtures
{
    private BusinessFixtures() { }

    public static BusinessActor actor()
    {
        return new BusinessActor(8L, "alice", "Alice", 3L, false);
    }

    public static BusinessActor administrator()
    {
        return new BusinessActor(1L, "admin", "Administrator", 1L, true);
    }

    public static BizLead lead(Long id, String status, String delFlag)
    {
        BizLead value = new BizLead();
        value.setLeadId(id);
        value.setStatus(status);
        value.setDelFlag(delFlag);
        return value;
    }

    public static BizCustomer customer(Long id, String status, String delFlag)
    {
        BizCustomer value = new BizCustomer();
        value.setCustomerId(id);
        value.setStatus(status);
        value.setDelFlag(delFlag);
        return value;
    }
}
