package com.law.todo.application.view;

import java.util.List;

public record TodoFoundationAdmissionGateView(String gateCode,String title,String status,boolean ready,
        boolean technicalReady,String evidenceStatus,int completed,int total,String summary,List<String> blockers) { }
