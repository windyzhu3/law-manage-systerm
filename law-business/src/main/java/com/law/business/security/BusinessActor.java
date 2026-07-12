package com.law.business.security;

public record BusinessActor(Long userId,String userName,String displayName,Long deptId,boolean administrator)
{
    public BusinessActor
    {
        if(userId==null)throw new IllegalArgumentException("userId is required");
        if(userName==null||userName.isBlank())throw new IllegalArgumentException("userName is required");
        displayName=displayName==null||displayName.isBlank()?userName:displayName;
    }
}
