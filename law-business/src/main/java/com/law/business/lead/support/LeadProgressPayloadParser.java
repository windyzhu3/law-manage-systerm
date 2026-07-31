package com.law.business.lead.support;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Map;

import com.law.business.lead.dto.LeadProgressCompleteCommand;

/** Pure TD-004 payload parser shared by read-only simulation and live completion. */
public final class LeadProgressPayloadParser
{
    public static final String ERROR_CODE="TODO_HANDLER_PAYLOAD_INVALID";
    private static final int REMARK_MAX_LENGTH=1000;

    private LeadProgressPayloadParser(){ }

    public static LeadProgressCompleteCommand parse(Map<String,?> payload,
            Long authoritativeLeadId,Long authoritativeTodoId)
    {
        Map<String,?> values=payload==null?Map.of():payload;
        LeadProgressCompleteCommand command=new LeadProgressCompleteCommand();
        command.setLeadId(identity(values,"leadId",authoritativeLeadId));
        command.setTodoId(identity(values,"todoId",authoritativeTodoId));
        command.setProgressType(requiredText(values.get("progressType"),"progressType"));
        command.setProgressAt(progressAt(values.get("progressAt")));
        command.setRemark(optionalRemark(values.get("remark")));
        return command;
    }

    private static Long identity(Map<String,?> values,String field,Long authoritative)
    {
        if(authoritative!=null&&authoritative<=0)throw invalid(field+" must be a positive integer");
        Long supplied=values.containsKey(field)?positiveLong(values.get(field),field):null;
        if(supplied!=null&&authoritative!=null&&!supplied.equals(authoritative))
            throw invalid(field+" must match the source Todo");
        return authoritative==null?supplied:authoritative;
    }

    private static Long positiveLong(Object value,String field)
    {
        long parsed;
        try
        {
            if(value instanceof Byte||value instanceof Short||value instanceof Integer
                    ||value instanceof Long)
                parsed=((Number)value).longValue();
            else if(value instanceof BigInteger integer)parsed=integer.longValueExact();
            else if(value instanceof String text&&!text.isBlank())parsed=Long.parseLong(text.trim());
            else throw new NumberFormatException();
        }
        catch(ArithmeticException|NumberFormatException invalid)
        {throw invalid(field+" must be a positive integer");}
        if(parsed<=0)throw invalid(field+" must be a positive integer");
        return parsed;
    }

    private static String requiredText(Object value,String field)
    {
        if(value!=null&&!(value instanceof String))throw invalid(field+" must be a string");
        String text=value==null?null:((String)value).trim();
        if(text==null||text.isBlank())throw invalid(field+" must not be blank");
        return text;
    }

    private static LocalDateTime progressAt(Object value)
    {
        if(value instanceof LocalDateTime supplied)return supplied.withNano(0);
        if(!(value instanceof String text)||text.isBlank())
            throw invalid("progressAt must be an ISO local date-time");
        try{return LocalDateTime.parse(text.trim()).withNano(0);}
        catch(RuntimeException invalid)
        {throw invalid("progressAt must be an ISO local date-time");}
    }

    private static String optionalRemark(Object value)
    {
        if(value==null)return null;
        if(!(value instanceof String text))throw invalid("remark must be a string");
        if(text.length()>REMARK_MAX_LENGTH)
            throw invalid("remark must not exceed 1000 characters");
        String normalized=text.trim();
        return normalized.isEmpty()?null:normalized;
    }

    private static PayloadValidationException invalid(String message)
    {return new PayloadValidationException(ERROR_CODE,message);}

    public static final class PayloadValidationException extends IllegalArgumentException
    {
        private final String businessCode;

        private PayloadValidationException(String businessCode,String message)
        {super(message);this.businessCode=businessCode;}

        public String getBusinessCode(){return businessCode;}
    }
}
