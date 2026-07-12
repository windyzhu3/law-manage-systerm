package com.law.business.security;

public final class CasePermissions
{
    public static final String DATA_SCOPE =
        "case:pending:list,case:pending:query,case:pending:assign,case:pending:batchAssign," +
        "case:assign:list,case:assign:query,case:transfer:list,case:transfer:query," +
        "case:transfer:add,case:transfer:approve,case:lawyer:list,case:lawyer:query," +
        "case:lawyer:config,case:lawyer:assign,case:confirm:list,case:confirm:query," +
        "case:confirm:handle,case:status:list";

    private CasePermissions() { }
}
