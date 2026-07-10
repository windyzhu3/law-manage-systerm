package com.law.business.shared.status;

public enum LeadStatus implements CodeStatus
{
    UNASSIGNED("0"), WAIT_FOLLOW("1"), FOLLOWING("2"), CONVERTED("3"), INVALID("4"), CLOSED("5");

    private final String code;

    LeadStatus(String code) { this.code = code; }

    @Override
    public String code() { return code; }
}
