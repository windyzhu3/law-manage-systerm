package com.law.todo.domain;

public enum TodoStatus
{
    CREATED, CLAIMED, IN_PROGRESS, SUBMITTED, RETURNED, COMPLETED, CANCELLED;

    public String code() { return name(); }

    public boolean isTerminal() { return this == COMPLETED || this == CANCELLED; }

    public static TodoStatus fromCode(String code)
    {
        for (TodoStatus status : values()) if (status.name().equals(code)) return status;
        throw new IllegalArgumentException("Unknown todo status: " + code);
    }
}
