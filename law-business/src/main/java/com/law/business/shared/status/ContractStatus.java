package com.law.business.shared.status;

public enum ContractStatus implements CodeStatus
{
    DRAFT("0"), PERFORMING("1"), ARCHIVED("2"), VOID("3"), TERMINATED("4");

    private final String code;

    ContractStatus(String code) { this.code = code; }

    @Override
    public String code() { return code; }

    public static ContractStatus fromCode(String code)
    {
        for (ContractStatus status : values()) if (status.code.equals(code)) return status;
        throw new IllegalArgumentException("Unknown contract status: " + code);
    }
}
