package com.law.todo.application;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;

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

    private static DirectoryEntry sample(long id,String no,String name,String type)
    {return new DirectoryEntry(id,no,name,type,"SAMPLE",true);}
}
