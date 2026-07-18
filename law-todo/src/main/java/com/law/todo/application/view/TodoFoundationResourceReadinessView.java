package com.law.todo.application.view;

import java.util.List;

public record TodoFoundationResourceReadinessView(
        String gateCode,
        int total,
        int ready,
        int sourceUnresolved,
        int runtimeMissing,
        int runtimeIncomplete,
        boolean gateReady,
        List<TodoFoundationResourceView> resources)
{
}
