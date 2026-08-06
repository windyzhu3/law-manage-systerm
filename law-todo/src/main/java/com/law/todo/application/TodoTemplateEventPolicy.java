package com.law.todo.application;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.law.todo.domain.TodoException;

/** Immutable compatibility policy for governed templates whose entry event is part of the business contract. */
@Component
public class TodoTemplateEventPolicy
{
    private static final Map<String,TemplateEventPolicyView> GOVERNED=Map.of(
            "TD-001",governed("LEAD_ASSIGNED"),
            "TD-002",governed("LEAD_SUSPECT_INVALID_MARKED"),
            "TD-003",governed("LEAD_RETRY_WINDOW_DUE"),
            "TD-004",governed("LEAD_FIRST_CONTACT_VALID"));

    public TemplateEventPolicyView view(String templateCode,String businessType)
    {
        if(!"LEAD".equals(businessType))return TemplateEventPolicyView.unrestricted();
        return GOVERNED.getOrDefault(templateCode,TemplateEventPolicyView.unrestricted());
    }

    public void requireCompatible(String templateCode,String businessType,String eventType,int payloadVersion)
    {
        TemplateEventPolicyView policy=view(templateCode,businessType);
        if(!policy.locked())return;
        if(!policy.allowedEventTypes().contains(eventType)||policy.payloadVersion()!=payloadVersion)
            throw new TodoException("TODO_TEMPLATE_EVENT_INCOMPATIBLE",
                    "模板“"+templateCode+"”必须使用业务事件“"+policy.recommendedEventType()
                            +"” v"+policy.payloadVersion());
    }

    private static TemplateEventPolicyView governed(String eventType)
    {return new TemplateEventPolicyView(eventType,1,true,List.of(eventType));}

    public record TemplateEventPolicyView(String recommendedEventType,int payloadVersion,boolean locked,
            List<String> allowedEventTypes)
    {
        public TemplateEventPolicyView
        {allowedEventTypes=allowedEventTypes==null?List.of():List.copyOf(allowedEventTypes);}
        public static TemplateEventPolicyView unrestricted()
        {return new TemplateEventPolicyView(null,0,false,List.of());}
    }
}
