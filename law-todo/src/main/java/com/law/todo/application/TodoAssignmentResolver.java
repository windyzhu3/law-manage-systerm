package com.law.todo.application;

import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class TodoAssignmentResolver
{
    public record Assignment(Long ownerId,String candidateType,Long candidateValue) { }
    public Assignment resolve(String rule,Map<String,Object> payload)
    {
        String value=rule==null?"":rule.trim();if(value.startsWith("\"")&&value.endsWith("\""))value=value.substring(1,value.length()-1);
        if(value.equals("OWNER")){Long id=longValue(payload.get("ownerId"));return new Assignment(id,id==null?null:"USER",id);}
        if(value.startsWith("PAYLOAD:")){Long id=longValue(payload.get(value.substring(8)));return new Assignment(id,id==null?null:"USER",id);}
        String[] parts=value.split(":",2);if(parts.length==2&&ListTypes.supports(parts[0]))return new Assignment("USER".equals(parts[0])?Long.valueOf(parts[1]):null,parts[0],Long.valueOf(parts[1]));
        return new Assignment(null,null,null);
    }
    private Long longValue(Object value){return value==null?null:Long.valueOf(String.valueOf(value));}
    private static final class ListTypes{static boolean supports(String value){return "USER".equals(value)||"ROLE".equals(value)||"DEPT".equals(value)||"POST".equals(value);}}
}
