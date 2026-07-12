package com.law.business.security;

public final class LeadPermissions
{
    public static final String QUERY_ALL = "lead:query";
    public static final String QUERY_MINE = "lead:mine:query";
    public static final String QUERY_POOL = "lead:pool:query";
    public static final String QUERY_RECYCLE = "lead:recycle:query";
    public static final String ASSIGN = "lead:assign";
    public static final String MOVE_POOL = "lead:pool:move";
    public static final String MOVE_MINE_POOL = "lead:mine:pool:move";
    public static final String CONVERT = "lead:convert";
    public static final String CONVERT_MINE = "lead:mine:convert";
    public static final String FOLLOW = "lead:followup:add";
    public static final String FOLLOW_MINE = "lead:mine:followup";

    private LeadPermissions() { }
}
