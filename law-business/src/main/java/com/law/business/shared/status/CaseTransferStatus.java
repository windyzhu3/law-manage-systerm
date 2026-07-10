package com.law.business.shared.status;

public enum CaseTransferStatus implements CodeStatus
{
    PENDING("pending"), PASSED("passed"), REJECTED("rejected"), SUPPLEMENT("supplement");

    private final String code;

    CaseTransferStatus(String code) { this.code = code; }

    @Override
    public String code() { return code; }
}
