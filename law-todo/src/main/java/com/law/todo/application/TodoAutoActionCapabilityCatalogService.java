package com.law.todo.application;

import java.util.List;
import org.springframework.stereotype.Service;
import com.law.todo.spi.TodoAutoActionCapability;

@Service
public class TodoAutoActionCapabilityCatalogService
{
    private static final List<String> TRIGGER_AT=List.of("DUE","SLA_80","SLA_100","SLA_150");
    private final List<TodoAutoActionCapability> capabilities;
    public TodoAutoActionCapabilityCatalogService(List<TodoAutoActionCapability> capabilities){this.capabilities=capabilities==null?List.of():List.copyOf(capabilities);}
    public List<CapabilityView> list(){return capabilities.stream().map(capability->new CapabilityView(capability.actionType(),capability.actionType(),TRIGGER_AT,List.of(field("maxAttempts","number",false,1,3,"最大尝试次数"),field("retryDelayMinutes","number",false,1,5,"重试间隔分钟"),field("claimTimeoutMinutes","number",false,1,15,"认领超时分钟")),"TRANSFER".equals(capability.actionType())?List.of(field("targetOwnerId","number",true,1,null,"目标负责人")):List.of())).sorted(java.util.Comparator.comparing(CapabilityView::actionType)).toList();}
    private FieldView field(String name,String type,boolean required,Integer min,Integer defaultValue,String label){return new FieldView(name,type,required,min,defaultValue,label);}
    public record CapabilityView(String actionType,String capability,List<String> triggerAt,List<FieldView> retryFields,List<FieldView> requiredFields) { }
    public record FieldView(String name,String type,boolean required,Integer min,Integer defaultValue,String label) { }
}
