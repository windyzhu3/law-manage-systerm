package com.law.todo.spi;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.definition.model.TodoDefinitionDocument.AutoActionRule;
import com.law.todo.domain.model.TodoInstance;

/** A deliberately small, code-owned capability surface for scheduled actions. */
public interface TodoAutoActionCapability
{
    String actionType();

    /** The executable bean owns the complete definition-time contract for its action type. */
    default Descriptor descriptor()
    {
        return new Descriptor(actionType(), List.of("DUE", "SLA_80", "SLA_100", "SLA_150"),
                Descriptor.commonRetryFields(), List.of());
    }

    AutoActionResult execute(TodoInstance todo, AutoActionRule rule, Actor serviceActor);

    enum AutoActionStatus { SUCCESS, RETRY, DEAD }

    record Field(String name,String type,boolean required,Integer min,Integer defaultValue,String label,
            String invalidCode)
    {
        public Field { Objects.requireNonNull(name,"name");Objects.requireNonNull(type,"type"); }
    }

    record ValidationError(String code,String field,String message) { }

    record Descriptor(String actionType,List<String> triggerAt,List<Field> retryFields,List<Field> requiredFields)
    {
        public Descriptor
        {
            Objects.requireNonNull(actionType,"actionType");triggerAt=List.copyOf(triggerAt);
            retryFields=normalizeRetryFields(retryFields);requiredFields=List.copyOf(requiredFields);
        }
        public static List<Field> commonRetryFields()
        {
            return List.of(new Field("maxAttempts","number",false,1,3,"Maximum attempts","TODO_AUTO_ACTION_NUMBER_INVALID"),
                    new Field("retryDelayMinutes","number",false,1,5,"Retry delay minutes","TODO_AUTO_ACTION_NUMBER_INVALID"),
                    new Field("claimTimeoutMinutes","number",false,1,15,"Claim timeout minutes","TODO_AUTO_ACTION_NUMBER_INVALID"));
        }
        private static List<Field> normalizeRetryFields(List<Field> supplied)
        {
            Map<String,Field> declared=new java.util.LinkedHashMap<>();
            for(Field field:supplied==null?List.<Field>of():supplied)
                if(declared.putIfAbsent(field.name(),field)!=null)throw new IllegalArgumentException("Duplicate retry field: "+field.name());
            List<Field> normalized=new java.util.ArrayList<>();
            for(Field standard:commonRetryFields())
            {
                Field value=declared.remove(standard.name());
                if(value!=null)validateRetryField(value);
                normalized.add(value==null?standard:value);
            }
            normalized.addAll(declared.values());return List.copyOf(normalized);
        }
        private static void validateRetryField(Field field)
        {
            if(!"number".equals(field.type())||field.min()==null||field.min()<1||field.defaultValue()==null||field.defaultValue()<1)
                throw new IllegalArgumentException("Retry field must be numeric with a positive minimum and default: "+field.name());
        }
        public Field retryField(String name)
        {
            return retryFields.stream().filter(field->field.name().equals(name)).findFirst()
                    .orElseThrow(()->new IllegalArgumentException("Unknown retry field: "+name));
        }
        public List<ValidationError> validate(Map<String,Object> config)
        {
            java.util.ArrayList<ValidationError> errors=new java.util.ArrayList<>();
            String trigger=config.get("triggerAt")==null?null:String.valueOf(config.get("triggerAt"));
            if(!triggerAt.contains(trigger))errors.add(new ValidationError("TODO_AUTO_ACTION_TRIGGER_INVALID","triggerAt","triggerAt is not supported by this capability"));
            for(Field field:retryFields)validateField(config.get(field.name()),field,false,errors);
            for(Field field:requiredFields)validateField(config.get(field.name()),field,true,errors);
            return errors;
        }
        private static void validateField(Object value,Field field,boolean enforceRequired,List<ValidationError> errors)
        {
            if(value==null){if(enforceRequired&&field.required())errors.add(error(field));return;}
            if("number".equals(field.type()))try{if(Long.parseLong(String.valueOf(value))<(field.min()==null?Long.MIN_VALUE:field.min()))throw new NumberFormatException();}
            catch(NumberFormatException invalid){errors.add(error(field));}
        }
        private static ValidationError error(Field field)
        {
            return new ValidationError(field.invalidCode()==null?"TODO_AUTO_ACTION_FIELD_INVALID":field.invalidCode(),field.name(),field.name()+" is invalid");
        }
    }
    record AutoActionResult(AutoActionStatus status,String errorCode,String errorMessage)
    {
        public AutoActionResult { Objects.requireNonNull(status,"status"); }
        public static AutoActionResult success(){return new AutoActionResult(AutoActionStatus.SUCCESS,null,null);}
        public static AutoActionResult retry(String code,String message){return new AutoActionResult(AutoActionStatus.RETRY,code,message);}
        public static AutoActionResult dead(String code,String message){return new AutoActionResult(AutoActionStatus.DEAD,code,message);}
    }
}
