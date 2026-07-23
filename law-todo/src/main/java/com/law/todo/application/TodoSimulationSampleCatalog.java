package com.law.todo.application;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSON;
import com.law.todo.mapper.TodoConfigurationMapper;
import com.law.todo.domain.TodoException;
import com.law.todo.spi.TodoBusinessDirectoryAccess.DirectoryEntry;
import com.law.todo.spi.TodoBusinessDirectoryAccess.DirectoryPage;

/** Deterministic read-only identities used only by configuration simulation. */
@Component
public class TodoSimulationSampleCatalog
{
    private static final List<DirectoryEntry> SAMPLES=List.of(
            sample(-1001,"DEMO-L-001","示例线索－劳动争议咨询","LEAD"),
            sample(-1002,"DEMO-CU-001","示例客户－张女士","CUSTOMER"),
            sample(-1003,"DEMO-CT-001","示例合同－劳动争议委托","CONTRACT"),
            sample(-1004,"DEMO-CA-001","示例案件－劳动争议案","CASE"),
            sample(-1005,"DEMO-M-001","示例事项－一审办理","MATTER"));
    @Autowired(required=false)
    private TodoConfigurationMapper mapper;

    public TodoSimulationSampleCatalog() { }
    public TodoSimulationSampleCatalog(TodoConfigurationMapper mapper){this.mapper=mapper;}

    public DirectoryPage search(String businessType,String keyword,int offset,int limit)
    {
        String needle=keyword==null?null:keyword.trim().toLowerCase(Locale.ROOT);
        List<DirectoryEntry> matched=SAMPLES.stream().filter(row->row.businessType().equals(businessType))
                .filter(row->needle==null||needle.isBlank()||row.businessNo().toLowerCase(Locale.ROOT).contains(needle)
                        ||row.businessName().toLowerCase(Locale.ROOT).contains(needle)).toList();
        List<DirectoryEntry> page=matched.stream().skip(Math.max(0,offset)).limit(Math.max(0,limit)).toList();
        return new DirectoryPage(page,matched.size(),matched.isEmpty()?"NO_MATCH":null,"SAMPLE");
    }

    public Optional<DirectoryEntry> find(String businessType,long businessId)
    {return SAMPLES.stream().filter(row->row.businessType().equals(businessType)&&row.businessId()==businessId).findFirst();}

    public boolean contains(String businessType,long businessId){return find(businessType,businessId).isPresent();}
    public boolean hasSample(String businessType){return SAMPLES.stream().anyMatch(row->row.businessType().equals(businessType));}

    public Map<String,Object> samplePayload(String eventType,int payloadVersion,String businessType,long businessId)
    {
        return samplePayload(eventType,payloadVersion,businessType,businessType,businessId);
    }

    public Map<String,Object> samplePayload(String eventType,int payloadVersion,String logicalBusinessType,
            String physicalBusinessType,long businessId)
    {
        DirectoryEntry sample=find(physicalBusinessType,businessId).orElseThrow();
        Map<String,Object> payload=new LinkedHashMap<>();
        Map<String,Object> event=mapper==null?null:mapper.selectEventResourceByTypeVersion(eventType,payloadVersion);
        if(event==null||!logicalBusinessType.equals(text(event,"business_object_type","businessObjectType")))
            throw unsupported();
        Object value=value(event,"sample_payload_json","samplePayloadJson");
        if(value instanceof Map<?,?> map)map.forEach((key,item)->payload.put(String.valueOf(key),item));
        else if(value!=null&&JSON.isValidObject(String.valueOf(value)))
            JSON.parseObject(String.valueOf(value)).forEach(payload::put);
        else
        {
            throw unsupported();
        }
        payload.putIfAbsent(identityField(physicalBusinessType),sample.businessId());
        payload.putIfAbsent("businessNo",sample.businessNo());
        payload.putIfAbsent("businessName",sample.businessName());
        return Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }

    private TodoException unsupported()
    {
        return new TodoException("TODO_SIMULATION_PAYLOAD_MAPPING_UNSUPPORTED",
                "The selected event, business type, and payload version are not supported");
    }

    private String identityField(String businessType)
    {return switch(businessType){case "LEAD"->"leadId";case "CUSTOMER"->"customerId";case "CONTRACT"->"contractId";case "CASE"->"caseId";case "MATTER"->"matterId";default->"businessId";};}
    private Object value(Map<String,Object> row,String snake,String camel)
    {return row.containsKey(snake)?row.get(snake):row.get(camel);}
    private String text(Map<String,Object> row,String snake,String camel)
    {Object result=value(row,snake,camel);return result==null?null:String.valueOf(result);}

    private static DirectoryEntry sample(long id,String no,String name,String type)
    {return new DirectoryEntry(id,no,name,type,"SAMPLE",true);}
}
