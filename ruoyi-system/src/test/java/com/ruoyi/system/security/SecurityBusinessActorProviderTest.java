package com.ruoyi.system.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.law.business.security.BusinessActor;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;

class SecurityBusinessActorProviderTest
{
    @Test void resolvesTypedActorAtSecurityBoundary()
    {
        SysUser user=new SysUser();user.setUserId(7L);user.setDeptId(3L);user.setUserName("alice");user.setNickName("张律师");
        LoginUser login=new LoginUser(7L,3L,user,java.util.Set.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(login,null,login.getAuthorities()));
        try{BusinessActor actor=new SecurityBusinessActorProvider().current();assertEquals(7L,actor.userId());assertEquals("alice",actor.userName());assertEquals("张律师",actor.displayName());assertEquals(3L,actor.deptId());}
        finally{SecurityContextHolder.clearContext();}
    }
}
