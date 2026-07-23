package com.law.todo.application;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.law.todo.spi.TodoBusinessPayloadAccess.PayloadFieldSource;

/**
 * One governed redaction policy shared by simulation responses and audit persistence.
 * Sensitive paths protect their original location; captured raw nodes protect values after
 * the engine projects them into neutral trace/result keys.
 */
public final class TodoSensitiveDataPolicy
{
    public static final String REDACTED="[REDACTED]";
    private static final int MAX_TEXT=512;
    private static final Pattern EMAIL=Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern PHONE=Pattern.compile("(?<!\\d)(?:\\+?\\d[ -]?){8,15}(?!\\d)");
    private static final Pattern IDENTITY=Pattern.compile("(?i)\\b(?:\\d{15}|\\d{17}[0-9X]|\\d{3}-\\d{2}-\\d{4})\\b");
    private static final Pattern FILE_URL=Pattern.compile("(?i)(?:file://|https?://\\S*(?:/file(?:/|[?#]|$)|fileurl))");
    private static final Pattern SECRET_VALUE=Pattern.compile(
            "(?i)(?:bearer\\s+|(?:secret|token|password|passwd|credential|authorization|api[-_ ]?key)\\s*[=:]?\\s*)\\S+");

    private final Set<String> sensitivePaths;
    private final Set<String> stringValues;
    private final Set<BigDecimal> numberValues;
    private final Set<Boolean> booleanValues;
    private final Set<String> structuredValues;

    private TodoSensitiveDataPolicy(Set<String> sensitivePaths,Set<String> stringValues,
            Set<BigDecimal> numberValues,Set<Boolean> booleanValues,Set<String> structuredValues)
    {
        this.sensitivePaths=Set.copyOf(sensitivePaths);this.stringValues=Set.copyOf(stringValues);
        this.numberValues=Set.copyOf(numberValues);this.booleanValues=Set.copyOf(booleanValues);
        this.structuredValues=Set.copyOf(structuredValues);
    }

    public static TodoSensitiveDataPolicy from(Map<String,Object> raw,List<PayloadFieldSource> fields)
    {
        Set<String> paths=new LinkedHashSet<>();Set<String> strings=new LinkedHashSet<>();
        Set<BigDecimal> numbers=new LinkedHashSet<>();Set<Boolean> booleans=new LinkedHashSet<>();
        Set<String> structured=new LinkedHashSet<>();
        for(PayloadFieldSource field:fields==null?List.<PayloadFieldSource>of():fields)
        {
            if(field==null||!field.sensitive()||field.path()==null||field.path().isBlank())continue;
            paths.add(field.path());collect(valueAt(raw,field.path()),strings,numbers,booleans,structured);
        }
        collectHeuristic(raw==null?Map.of():raw,"",paths,strings,numbers,booleans,structured);
        return new TodoSensitiveDataPolicy(paths,strings,numbers,booleans,structured);
    }

    public static TodoSensitiveDataPolicy heuristicOnly()
    {return from(Map.of(),List.of());}

    public Object redact(Object value){return redact(value,"");}
    public boolean protects(Object value){return matches(value);}

    public Map<String,Object> redactMap(Map<String,Object> source)
    {
        if(source==null)return Map.of();
        Map<String,Object> result=new LinkedHashMap<>();
        source.forEach((key,value)->result.put(key,redact(value,key)));
        return java.util.Collections.unmodifiableMap(result);
    }

    public List<PayloadFieldSource> redactFields(List<PayloadFieldSource> fields)
    {
        if(fields==null)return List.of();
        return fields.stream().map(field->
        {
            boolean governed=field.sensitive()||isSensitivePath(field.path())
                    ||matches(field.value());
            if(!governed||field.missing())return field;
            return new PayloadFieldSource(field.path(),REDACTED,field.source(),field.required(),
                    false,null,true);
        }).toList();
    }

    public String redactText(String value)
    {
        if(value==null)return null;
        String result=value;
        for(String sensitive:stringValues)
            if(!sensitive.isBlank())result=result.replace(sensitive,REDACTED);
        for(BigDecimal sensitive:numberValues)
            result=result.replace(sensitive.stripTrailingZeros().toPlainString(),REDACTED);
        for(Boolean sensitive:booleanValues)
            result=result.replace(String.valueOf(sensitive),REDACTED);
        if(EMAIL.matcher(result).find()||PHONE.matcher(result).find()||IDENTITY.matcher(result).find()
                ||FILE_URL.matcher(result).find()||SECRET_VALUE.matcher(result).find())return REDACTED;
        return bounded(result);
    }

    private Object redact(Object value,String path)
    {
        if(value==null)return null;
        if(isSensitivePath(path)||matches(value))return REDACTED;
        if(value instanceof Map<?,?> source)
        {
            Map<String,Object> result=new LinkedHashMap<>();
            source.forEach((key,nested)->
            {
                String name=String.valueOf(key);String child=path.isBlank()?name:path+"."+name;
                result.put(bounded(name),sensitiveKey(name)?REDACTED:redact(nested,child));
            });
            return result;
        }
        if(value instanceof Iterable<?> source)
        {
            List<Object> result=new ArrayList<>();int index=0;
            for(Object nested:source)result.add(redact(nested,path+"["+(index++)+"]"));
            return result;
        }
        if(value.getClass().isArray())
        {
            List<Object> result=new ArrayList<>();
            for(int index=0;index<Array.getLength(value);index++)
                result.add(redact(Array.get(value,index),path+"["+index+"]"));
            return result;
        }
        if(value instanceof String text)return redactText(text);
        return value;
    }

    private boolean matches(Object value)
    {
        if(value==null)return false;
        if(value instanceof String text)return stringValues.contains(text);
        if(value instanceof Number number)
        {
            try{return numberValues.contains(new BigDecimal(String.valueOf(number)).stripTrailingZeros());}
            catch(NumberFormatException invalid){return false;}
        }
        if(value instanceof Boolean bool)return booleanValues.contains(bool);
        if(value instanceof Map<?,?>||value instanceof Iterable<?>||value.getClass().isArray())
            return structuredValues.contains(canonical(value));
        return false;
    }

    private boolean isSensitivePath(String path)
    {
        if(path==null)return false;
        String normalized=path.replaceAll("\\[\\d+\\]","");
        return sensitivePaths.stream().anyMatch(root->normalized.equals(root)
                ||normalized.startsWith(root+"."));
    }

    private static void collect(Object value,Set<String> strings,Set<BigDecimal> numbers,
            Set<Boolean> booleans,Set<String> structured)
    {
        if(value==null)return;
        if(value instanceof String text){strings.add(text);return;}
        if(value instanceof Number number)
        {
            try{numbers.add(new BigDecimal(String.valueOf(number)).stripTrailingZeros());}
            catch(NumberFormatException ignored){ }
            return;
        }
        if(value instanceof Boolean bool){booleans.add(bool);return;}
        if(value instanceof Map<?,?> map)
        {
            structured.add(canonical(value));map.values().forEach(nested->collect(nested,strings,numbers,booleans,structured));return;
        }
        if(value instanceof Iterable<?> values)
        {
            structured.add(canonical(value));values.forEach(nested->collect(nested,strings,numbers,booleans,structured));return;
        }
        if(value.getClass().isArray())
        {
            structured.add(canonical(value));
            for(int index=0;index<Array.getLength(value);index++)
                collect(Array.get(value,index),strings,numbers,booleans,structured);
        }
    }

    private static void collectHeuristic(Map<String,Object> source,String prefix,Set<String> paths,
            Set<String> strings,Set<BigDecimal> numbers,Set<Boolean> booleans,Set<String> structured)
    {
        source.forEach((key,value)->
        {
            String path=prefix.isBlank()?key:prefix+"."+key;
            if(sensitiveKey(key)){paths.add(path);collect(value,strings,numbers,booleans,structured);}
            if(value instanceof Map<?,?> nested)
            {
                Map<String,Object> child=new LinkedHashMap<>();
                nested.forEach((nestedKey,nestedValue)->child.put(String.valueOf(nestedKey),nestedValue));
                collectHeuristic(child,path,paths,strings,numbers,booleans,structured);
            }
        });
    }

    private static Object valueAt(Map<String,Object> source,String path)
    {
        Object cursor=source;
        for(String segment:path.split("\\."))
        {
            if(!(cursor instanceof Map<?,?> map))return null;
            cursor=map.get(segment);
        }
        return cursor;
    }

    private static String canonical(Object value)
    {return JSON.toJSONString(value,JSONWriter.Feature.SortMapEntriesByKeys);}

    static boolean sensitiveKey(String value)
    {
        String key=value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");
        return key.contains("secret")||key.contains("token")||key.contains("password")||key.contains("passwd")
                ||key.contains("credential")||key.contains("authorization")||key.contains("apikey")
                ||key.contains("accesskey")||key.contains("fileurl")||key.contains("phone")
                ||key.contains("mobile")||key.contains("email")||key.contains("idcard")
                ||key.contains("identity")||key.contains("ssn")||key.contains("passport")
                ||key.contains("naturalperson")||key.contains("personname")||key.contains("fullname");
    }

    private static String bounded(String value)
    {return value.length()<=MAX_TEXT?value:value.substring(0,MAX_TEXT)+"[TRUNCATED]";}
}
