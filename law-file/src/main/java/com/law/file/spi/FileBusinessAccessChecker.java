package com.law.file.spi;

public interface FileBusinessAccessChecker
{
    boolean supports(String businessType);
    boolean canRead(String businessType,Long businessId,Long userId,Long deptId);
    default boolean canWrite(String businessType,Long businessId,Long userId,Long deptId)
    { return canRead(businessType,businessId,userId,deptId); }
}
