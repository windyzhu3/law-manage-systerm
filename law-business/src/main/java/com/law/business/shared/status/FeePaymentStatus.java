package com.law.business.shared.status;

public enum FeePaymentStatus
{
    PENDING("0"), CONFIRMED("1"), REJECTED("2");

    private final String code;
    FeePaymentStatus(String code) { this.code = code; }
    public String code() { return code; }

    public boolean canTransitionTo(FeePaymentStatus target)
    {
        if (this == PENDING) return target == CONFIRMED || target == REJECTED;
        if (this == CONFIRMED) return target == CONFIRMED;
        return target == PENDING;
    }

    public static FeePaymentStatus fromCode(String code)
    {
        for (FeePaymentStatus value : values()) if (value.code.equals(code)) return value;
        throw new IllegalArgumentException("Unknown fee payment status: " + code);
    }
}
