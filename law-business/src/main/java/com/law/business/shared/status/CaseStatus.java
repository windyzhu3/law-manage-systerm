package com.law.business.shared.status;

import java.util.EnumSet;
import java.util.Set;

public enum CaseStatus implements CodeStatus
{
    PENDING("pending"), CONFIRMING("confirming"), PROCESSING("processing"),
    TRANSFERRING("transfering"), CLOSING("closing"), CLOSED("closed"),
    ARCHIVED("archived"), TERMINATED("terminated");

    private static final Set<CaseStatus> FINAL_STATUSES = EnumSet.of(ARCHIVED, TERMINATED);
    private final String code;

    CaseStatus(String code) { this.code = code; }

    @Override
    public String code() { return code; }

    public boolean isFinalStatus() { return FINAL_STATUSES.contains(this); }

    public static CaseStatus fromCode(String code)
    {
        for (CaseStatus status : values())
        {
            if (status.code.equals(code)) return status;
        }
        throw new IllegalArgumentException("Unknown case status: " + code);
    }
}
