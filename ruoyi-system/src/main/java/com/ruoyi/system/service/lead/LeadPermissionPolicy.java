package com.ruoyi.system.service.lead;

import org.springframework.stereotype.Component;
import com.law.business.shared.error.BusinessErrorCode;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;

@Component
public class LeadPermissionPolicy
{
    public void require(String permission)
    {
        if(permission==null||permission.isBlank()||!SecurityUtils.hasPermi(permission))
            throw new ServiceException("Required lead permission is missing",
                    BusinessErrorCode.ACCESS_DENIED.name());
    }
}
