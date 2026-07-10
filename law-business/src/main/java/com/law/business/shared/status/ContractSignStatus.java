package com.law.business.shared.status;

public enum ContractSignStatus implements CodeStatus
{
    UNSIGNED("0"), SIGNED("1"), PARTIAL("2");

    private final String code;

    ContractSignStatus(String code) { this.code = code; }

    @Override
    public String code() { return code; }
}
