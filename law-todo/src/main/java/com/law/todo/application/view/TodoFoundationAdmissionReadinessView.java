package com.law.todo.application.view;

import java.util.List;

public record TodoFoundationAdmissionReadinessView(String overallStatus,boolean admitted,int readyGateCount,
        int totalGateCount,List<TodoFoundationAdmissionGateView> gates) { }
