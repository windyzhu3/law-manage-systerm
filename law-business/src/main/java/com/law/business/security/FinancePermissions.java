package com.law.business.security;

public final class FinancePermissions
{
    public static final String DATA_SCOPE =
        "finance:overview,finance:receivable:list,finance:payment:list,finance:invoice:list," +
        "finance:expense:list,finance:report:list,contract:fee:list,matter:expense:list";

    private FinancePermissions() { }
}
