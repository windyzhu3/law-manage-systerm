package com.law.todo.application;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.law.todo.application.TodoConfigurationResourceCatalogService.FieldResource;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.domain.TodoException;
import com.law.todo.spi.TodoBusinessDirectoryAccess;
import com.law.todo.spi.TodoBusinessPayloadAccess;
import com.law.todo.spi.TodoBusinessPayloadAccess.DataSourceStatus;
import com.law.todo.spi.TodoBusinessPayloadAccess.PayloadFieldSource;
import com.law.todo.spi.TodoBusinessPayloadAccess.PayloadHydration;

/** Builds a business-friendly simulation payload without ever widening the current actor's data scope. */
@Service
public class TodoBusinessPayloadHydrationService
{
    private static final List<String> BUSINESS_TYPES=List.of("LEAD","CUSTOMER","CONTRACT","CASE","MATTER");
    private static final String REDACTED="[REDACTED]";
    private final List<TodoBusinessPayloadAccess> accesses;
    private final List<TodoBusinessDirectoryAccess> directories;
    private final TodoConfigurationResourceCatalogService resources;
    private final TodoSimulationSampleCatalog samples;

    public TodoBusinessPayloadHydrationService(List<TodoBusinessPayloadAccess> accesses,
            TodoConfigurationResourceCatalogService resources,TodoSimulationSampleCatalog samples)
    {
        this(accesses,List.of(),resources,samples);
    }

    @Autowired
    public TodoBusinessPayloadHydrationService(List<TodoBusinessPayloadAccess> accesses,
            List<TodoBusinessDirectoryAccess> directories,
            TodoConfigurationResourceCatalogService resources,TodoSimulationSampleCatalog samples)
    {
        this.accesses=accesses==null?List.of():List.copyOf(accesses);
        this.directories=directories==null?List.of():List.copyOf(directories);
        this.resources=resources;this.samples=samples;
    }

    @Transactional(readOnly=true)
    public PayloadHydration hydrate(String eventType,int payloadVersion,String businessType,long businessId,Actor actor)
    {return hydrate(eventType,payloadVersion,businessType,businessId,actor,Map.of());}

    @Transactional(readOnly=true)
    public PayloadHydration hydrate(String eventType,int payloadVersion,String businessType,long businessId,Actor actor,
            Map<String,Object> manualOverrides)
    {
        return hydrateForExecution(eventType,payloadVersion,businessType,businessId,actor,manualOverrides).publicView();
    }

    ExecutionHydration hydrateForExecution(String eventType,int payloadVersion,String businessType,long businessId,
            Actor actor,Map<String,Object> manualOverrides)
    {
        if(businessId==0)throw new TodoException("TODO_SIMULATION_BUSINESS_ID_INVALID",
                "Simulation business ID must not be zero");
        String physicalType=physicalType(eventType,businessType);
        PayloadHydration base=businessId<0?sample(eventType,payloadVersion,businessType,physicalType,businessId):
                adapter(physicalType).hydrate(eventType,payloadVersion,physicalType,businessId,actor);
        return normalize(eventType,businessType,base,manualOverrides,actor);
    }

    @Transactional(readOnly=true)
    public List<DataSourceStatus> dataSources()
    {
        List<DataSourceStatus> result=new ArrayList<>();
        for(String type:BUSINESS_TYPES)
        {
            long adapterCount=accesses.stream().filter(access->access.supports(type)).count();
            long directoryCount=directories.stream().filter(directory->directory.supports(type)).count();
            boolean payload=adapterCount==1;boolean directory=directoryCount==1;
            boolean sample=samples!=null&&samples.hasSample(type);
            String status=payload&&directory&&sample?"READY":
                    adapterCount>1||directoryCount>1?"CONFLICT":"PARTIAL";
            String message=adapterCount>1?"Multiple payload adapters support this business type":
                    directoryCount>1?"Multiple directories support this business type":
                    payload&&directory&&sample?"Business directory, payload data and read-only samples are available":
                    !directory?"Business directory is unavailable":
                    !payload?"Business payload adapter is unavailable":"Read-only sample is unavailable";
            result.add(new DataSourceStatus(type,directory,payload,sample,status,message));
        }
        return List.copyOf(result);
    }

    private PayloadHydration sample(String eventType,int payloadVersion,String logicalType,String physicalType,
            long businessId)
    {
        if(samples==null||!samples.contains(physicalType,businessId))
            throw new TodoException("TODO_SIMULATION_BUSINESS_OBJECT_NOT_FOUND",
                    "Simulation sample business object does not exist");
        Map<String,Object> values=deepMutable(
                samples.samplePayload(eventType,payloadVersion,logicalType,physicalType,businessId));
        List<PayloadFieldSource> fields=flatten(values).entrySet().stream()
                .map(entry->new PayloadFieldSource(entry.getKey(),entry.getValue(),"EVENT_SAMPLE",
                        false,false,null,false)).toList();
        return new PayloadHydration(values,fields,true);
    }

    private TodoBusinessPayloadAccess adapter(String businessType)
    {
        List<TodoBusinessPayloadAccess> supported=accesses.stream().filter(access->access.supports(businessType)).toList();
        if(supported.isEmpty())throw new TodoException("TODO_SIMULATION_PAYLOAD_ADAPTER_UNAVAILABLE",
                "No business payload adapter supports the selected business type");
        if(supported.size()>1)throw new TodoException("TODO_SIMULATION_PAYLOAD_ADAPTER_AMBIGUOUS",
                "Multiple business payload adapters support the selected business type");
        return supported.get(0);
    }

    private ExecutionHydration normalize(String eventType,String businessType,PayloadHydration base,
            Map<String,Object> manualOverrides,Actor actor)
    {
        Map<String,Object> executionValues=deepMutable(base.payload());
        List<FieldResource> descriptors=resources==null?List.of():resources.fields(businessType,eventType);
        if(base.sample()&&actor!=null&&actor.userId()!=null)
            descriptors.stream().filter(field->field.ownerEligible()||"USER_ID".equals(field.semanticType()))
                    .forEach(field->putPath(executionValues,field.code(),actor.userId()));
        Map<String,Object> overrides=flatten(manualOverrides==null?Map.of():manualOverrides);
        overrides.forEach((path,value)->putPath(executionValues,path,value));
        Map<String,Object> publicValues=deepMutable(executionValues);
        Map<String,PayloadFieldSource> provided=new LinkedHashMap<>();
        for(PayloadFieldSource field:base.fields())provided.put(field.path(),field);

        Set<String> paths=new LinkedHashSet<>();
        descriptors.forEach(field->paths.add(field.code()));paths.addAll(provided.keySet());paths.addAll(overrides.keySet());
        Map<String,FieldResource> descriptorByPath=new LinkedHashMap<>();
        descriptors.forEach(field->descriptorByPath.put(field.code(),field));

        List<PayloadFieldSource> fields=new ArrayList<>();
        for(String path:paths)
        {
            FieldResource descriptor=descriptorByPath.get(path);PayloadFieldSource original=provided.get(path);
            Object value=valueAt(executionValues,path);boolean required=descriptor!=null?descriptor.required():
                    original!=null&&original.required();
            boolean sensitive=(descriptor!=null&&descriptor.sensitive())||(original!=null&&original.sensitive());
            String source=overrides.containsKey(path)?"MANUAL_OVERRIDE":
                    original==null?(present(value)?"SYSTEM_DEFAULT":"MISSING"):original.source();
            boolean missing=!present(value);
            if(missing)source="MISSING";
            Object responseValue=sensitive&&!missing?REDACTED:value;
            if(sensitive&&!missing)putPath(publicValues,path,REDACTED);
            fields.add(new PayloadFieldSource(path,responseValue,source,required,missing,
                    missing?"No value is available from the selected business object or event sample":null,sensitive));
        }
        PayloadHydration result=new PayloadHydration(publicValues,fields,base.sample());
        result.coveragePercent();
        return new ExecutionHydration(deepImmutable(executionValues),result);
    }

    private String physicalType(String eventType,String logicalType)
    {
        if(BUSINESS_TYPES.contains(logicalType))return logicalType;
        return switch(logicalType)
        {
            case "NON_LITIGATION" -> switch(eventType)
            {
                case "CASE_CLASSIFIED_NON_LITIGATION" -> "CASE";
                case "NON_LITIGATION_WORK_COMPLETED" -> "MATTER";
                default -> throw unsupported();
            };
            case "ENFORCEMENT" -> switch(eventType)
            {
                case "CASE_CLASSIFIED_ENFORCEMENT" -> "CASE";
                case "ENFORCEMENT_ORDER_ACCEPTED","ENFORCEMENT_SERVICE_NODE_READY" -> "MATTER";
                default -> throw unsupported();
            };
            default -> throw unsupported();
        };
    }

    private TodoException unsupported()
    {
        return new TodoException("TODO_SIMULATION_PAYLOAD_MAPPING_UNSUPPORTED",
                "The selected event, business type, and payload version are not supported");
    }

    private Map<String,Object> deepImmutable(Map<String,Object> source)
    {
        Map<String,Object> result=new LinkedHashMap<>();
        source.forEach((key,value)->result.put(key,value instanceof Map<?,?> map?
                deepImmutable(stringMap(map)):value));
        return Collections.unmodifiableMap(result);
    }

    /**
     * Internal-only execution carrier. It deliberately has no public payload accessor and its string form
     * never renders raw values, so it cannot be used as an HTTP response or leak secrets through logging.
     */
    static final class ExecutionHydration
    {
        private final Map<String,Object> payload;
        private final PayloadHydration publicView;

        private ExecutionHydration(Map<String,Object> payload,PayloadHydration publicView)
        {this.payload=payload;this.publicView=publicView;}

        Map<String,Object> payload(){return payload;}
        PayloadHydration publicView(){return publicView;}
        @Override public String toString(){return "ExecutionHydration[REDACTED]";}
    }

    private boolean present(Object value)
    {
        if(value==null)return false;
        if(value instanceof String text)return !text.isBlank();
        if(value instanceof Map<?,?> map)return !map.isEmpty();
        if(value instanceof Iterable<?> values)return values.iterator().hasNext();
        return true;
    }

    private Map<String,Object> deepMutable(Map<String,Object> source)
    {
        Map<String,Object> result=new LinkedHashMap<>();
        if(source==null)return result;
        source.forEach((key,value)->result.put(key,value instanceof Map<?,?> map?deepMutable(stringMap(map)):value));
        return result;
    }

    private Map<String,Object> stringMap(Map<?,?> source)
    {
        Map<String,Object> result=new LinkedHashMap<>();
        source.forEach((key,value)->result.put(String.valueOf(key),value));
        return result;
    }

    private Map<String,Object> flatten(Map<String,Object> source)
    {
        Map<String,Object> result=new LinkedHashMap<>();
        flattenInto(result,"",source==null?Map.of():source);
        return result;
    }

    private void flattenInto(Map<String,Object> result,String prefix,Map<String,Object> source)
    {
        source.forEach((key,value)->
        {
            String path=prefix.isEmpty()?key:prefix+"."+key;
            if(value instanceof Map<?,?> nested)flattenInto(result,path,stringMap(nested));
            else result.put(path,value);
        });
    }

    @SuppressWarnings("unchecked")
    private void putPath(Map<String,Object> values,String path,Object value)
    {
        if(value==null)
        {
            removePath(values,path);
            return;
        }
        String[] segments=path.split("\\.");Map<String,Object> cursor=values;
        for(int index=0;index<segments.length-1;index++)
        {
            Object nested=cursor.get(segments[index]);
            if(!(nested instanceof Map<?,?>))
            {
                Map<String,Object> created=new LinkedHashMap<>();cursor.put(segments[index],created);cursor=created;
            }
            else cursor=(Map<String,Object>)nested;
        }
        cursor.put(segments[segments.length-1],value);
    }

    @SuppressWarnings("unchecked")
    private void removePath(Map<String,Object> values,String path)
    {
        String[] segments=path.split("\\.");Map<String,Object> cursor=values;
        for(int index=0;index<segments.length-1;index++)
        {
            Object nested=cursor.get(segments[index]);
            if(!(nested instanceof Map<?,?>))return;
            cursor=(Map<String,Object>)nested;
        }
        cursor.remove(segments[segments.length-1]);
    }

    private Object valueAt(Map<String,Object> values,String path)
    {
        Object cursor=values;
        for(String segment:path.split("\\."))
        {
            if(!(cursor instanceof Map<?,?> map))return null;
            cursor=map.get(segment);
        }
        return cursor;
    }
}
