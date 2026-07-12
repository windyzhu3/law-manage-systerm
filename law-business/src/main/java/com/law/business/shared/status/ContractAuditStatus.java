package com.law.business.shared.status;

public enum ContractAuditStatus implements CodeStatus
{
    PENDING("0"), REVIEWING("1"), PASSED("2"), REJECTED("3"), BACK("4");

    private final String code;

    ContractAuditStatus(String code) { this.code = code; }

    @Override
    public String code() { return code; }
}
