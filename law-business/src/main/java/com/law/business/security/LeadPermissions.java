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
    public static final String TAG_CONFIRM = "lead:tag:confirm";
    public static final String FIRST_CONTACT_HANDLE = "lead:first-contact:handle";
    public static final String INVALID_REVIEW_LIST = "lead:invalid-review:list";
    public static final String INVALID_REVIEW_HANDLE = "lead:invalid-review:handle";
    public static final String RETRY_LIST = "lead:retry:list";
    public static final String RETRY_HANDLE = "lead:retry:handle";
    public static final String DEAD_POOL_LIST = "lead:dead-pool:list";
    public static final String DEAD_POOL_RESTORE = "lead:dead-pool:restore";
    public static final String ASSIGNMENT_POLICY_LIST = "lead:assignment-policy:list";
    public static final String ASSIGNMENT_POLICY_EDIT = "lead:assignment-policy:edit";
    public static final String CALL_RECORD_ADD = "lead:call-record:add";
    public static final String CALL_RECORD_VIEW = "lead:call-record:view";

    private LeadPermissions() { }
}
