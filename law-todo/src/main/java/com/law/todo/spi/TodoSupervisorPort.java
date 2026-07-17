package com.law.todo.spi;

import java.util.List;

/** Resolves the governed supervisor recipients for an assigned Todo. */
public interface TodoSupervisorPort
{
    List<Long> supervisors(Long ownerId,Long ownerDeptId);
}
