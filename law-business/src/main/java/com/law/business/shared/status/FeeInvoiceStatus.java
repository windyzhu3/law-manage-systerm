package com.law.business.shared.status;

public enum FeeInvoiceStatus
{
    NONE("0"), INVOICED("1"), PARTIAL("2");

    private final String code;
    FeeInvoiceStatus(String code) { this.code = code; }
    public String code() { return code; }

    public boolean canTransitionTo(FeeInvoiceStatus target)
    {
        if (this == NONE) return target == PARTIAL || target == INVOICED;
        if (this == PARTIAL) return target == INVOICED;
        return false;
    }

    public static FeeInvoiceStatus fromCode(String code)
    {
        for (FeeInvoiceStatus value : values()) if (value.code.equals(code)) return value;
        throw new IllegalArgumentException("Unknown fee invoice status: " + code);
    }
}
