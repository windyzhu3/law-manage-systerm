package com.law.todo.application.view;
import java.util.List;
public record TodoFinanceReadinessView(String gateCode,int total,int ready,int sourceUnresolved,int runtimeMissing,boolean gateReady,int feePlanCoreColumns,int nodeFeeColumns,int financeSupportTables,int riskSchemaObjects,List<TodoFinanceRequirementView> requirements){}
