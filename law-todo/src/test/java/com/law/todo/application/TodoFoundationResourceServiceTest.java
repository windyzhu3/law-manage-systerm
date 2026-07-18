package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.law.todo.mapper.TodoFoundationResourceMapper;

@ExtendWith(MockitoExtension.class)
class TodoFoundationResourceServiceTest
{
    @Mock TodoFoundationResourceMapper mapper;

    @Test void reportsRepositorySourceAndRuntimeReadinessSeparately()
    {
        when(mapper.selectResourceReadiness("G-02")).thenReturn(List.of(
            row(1L,"DICTIONARY","law_business_line","CONFIRMED",true,3,3,3),
            row(2L,"DICTIONARY","law_lead_invalid_level","NEEDS_DECISION",true,2,0,0),
            row(3L,"ROLE","sales","CONFIRMED",false,0,0,0),
            row(4L,"DICTIONARY","law_followup_progress_type","CONFIRMED",true,4,6,4),
            row(5L,"ROLE","case_manager","CONFIRMED",true,1,0,0)));

        var report=service().readiness("G-02");

        assertEquals(5,report.total());
        assertEquals(2,report.ready());
        assertEquals(1,report.sourceUnresolved());
        assertEquals(1,report.runtimeMissing());
        assertEquals(1,report.runtimeIncomplete());
        assertFalse(report.gateReady());
        assertEquals("READY",report.resources().get(0).readinessStatus());
        assertEquals("SOURCE_UNRESOLVED",report.resources().get(1).readinessStatus());
        assertEquals("RUNTIME_MISSING",report.resources().get(2).readinessStatus());
        assertEquals("RUNTIME_INCOMPLETE",report.resources().get(3).readinessStatus());
    }

    @Test void gateIsReadyOnlyWhenEveryCatalogResourceIsReady()
    {
        when(mapper.selectResourceReadiness("G-02")).thenReturn(List.of(
            row(1L,"DICTIONARY","law_business_line","CONFIRMED",true,3,3,3),
            row(2L,"ROLE","case_manager","CONFIRMED",true,1,0,0)));

        assertTrue(service().gateReady("G-02"));
        assertTrue(service().readiness("G-02").gateReady());
    }

    private TodoFoundationResourceService service(){return new TodoFoundationResourceService(mapper);}

    private Map<String,Object> row(Long id,String type,String code,String sourceStatus,boolean exists,
                                   int active,int expected,int matched)
    {
        Map<String,Object> row=new HashMap<>();row.put("resource_id",id);row.put("gate_code","G-02");
        row.put("resource_type",type);row.put("resource_code",code);row.put("domain_code","FOUNDATION");
        row.put("delivery_phase","PHASE_ONE");row.put("source_ref","doc/v0.2-prd-readiness-gap-analysis.md");
        row.put("source_status",sourceStatus);row.put("expected_values_json","[]");row.put("minimum_active_items",1);
        row.put("resource_exists",exists?1:0);row.put("active_item_count",active);
        row.put("expected_item_count",expected);row.put("matched_expected_item_count",matched);
        return row;
    }
}
