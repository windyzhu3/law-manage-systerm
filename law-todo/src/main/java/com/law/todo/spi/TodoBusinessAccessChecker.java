package com.law.todo.spi;

public interface TodoBusinessAccessChecker
{
    boolean supports(String businessType);
    boolean canView(String businessType,Long businessId,Long userId,Long deptId);
}
