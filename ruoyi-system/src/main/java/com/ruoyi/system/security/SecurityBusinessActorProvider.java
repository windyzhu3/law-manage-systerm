package com.ruoyi.system.security;

import org.springframework.stereotype.Component;
import com.law.business.security.BusinessActor;
import com.law.business.security.BusinessActorProvider;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.utils.SecurityUtils;

@Component
public class SecurityBusinessActorProvider implements BusinessActorProvider
{
    @Override public BusinessActor current()
    {
        LoginUser login=SecurityUtils.getLoginUser();
        return new BusinessActor(login.getUserId(),login.getUsername(),login.getUser().getNickName(),login.getDeptId(),SecurityUtils.isAdmin(login.getUserId()));
    }
}
