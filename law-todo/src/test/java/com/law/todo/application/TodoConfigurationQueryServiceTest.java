package com.law.todo.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import com.law.todo.application.view.TodoConfigurationViews.ConfigurationDashboard;
import com.law.todo.application.view.TodoConfigurationViews.ReleaseRecord;
import com.law.todo.application.view.TodoConfigurationViews.TemplateConfigurationDetail;
import com.law.todo.application.view.TodoConfigurationViews.TemplatePage;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoConfigurationMapper;

@ExtendWith(MockitoExtension.class)
class TodoConfigurationQueryServiceTest
{
    @Mock TodoConfigurationMapper mapper;

    @Test void dashboardUsesTheStableConfigurationCounters()
    {
        when(mapper.countPublishedTemplates()).thenReturn(3L);
        when(mapper.countDraftTemplates()).thenReturn(2L);
        when(mapper.countEnabledSlaRules()).thenReturn(4L);
        when(mapper.countTodayTriggeredTodos()).thenReturn(5L);

        ConfigurationDashboard dashboard=service().dashboard();

        assertEquals(new ConfigurationDashboard(3L,2L,4L,5L),dashboard);
    }

    @Test void templateProjectionUsesTheDraftVersionForOrderedRuleReferences()
    {
        when(mapper.selectTemplateConfiguration(7L)).thenReturn(Map.of("template_id",7L,"template_code","T-7",
                "template_name","Template 7","business_type","LEAD","current_version",4,
                "draft_version_id",9L,"draft_status","DRAFT"));
        when(mapper.selectDraftRuleRefs(9L)).thenReturn(List.of(Map.of("ref_type","DOD","sort_order",0),
                Map.of("ref_type","SLA","sort_order",1),Map.of("ref_type","DOD","sort_order",2)));

        TemplateConfigurationDetail detail=service().template(7L);

        assertEquals(7L,detail.templateId());
        assertEquals(9L,detail.draftVersionId());
        assertEquals(List.of("DOD","SLA","DOD"),detail.ruleReferences().stream().map(ref->ref.type()).toList());
    }

    @Test void templatePageUsesDatabasePaginationAndPreservesOnlySupportedFilters()
    {
        Map<String,Object> row=Map.ofEntries(Map.entry("template_id",7L),Map.entry("template_code","T-7"),
                Map.entry("template_name","Template 7"),Map.entry("business_type","LEAD"),Map.entry("status","0"),
                Map.entry("version",2),Map.entry("business_stage","LEAD"),Map.entry("template_type","STANDARD"),
                Map.entry("priority","HIGH"),Map.entry("publish_status","DRAFT"),Map.entry("draft_version_id",9L),
                Map.entry("draft_version_no",4),Map.entry("event_type","LEAD_ASSIGNED"),Map.entry("owner_summary","ROLE"));
        when(mapper.selectTemplateConfigurations(anyMap())).thenReturn(List.of(row));
        when(mapper.countTemplateConfigurations(anyMap())).thenReturn(23L);

        TemplatePage page=service().templatePage(Map.of("keyword","T-7","businessType","LEAD",
                "businessStage","LEAD","templateType","STANDARD","publishStatus","DRAFT","status","0",
                "offset",20,"limit",10));

        assertEquals(23L,page.total());assertEquals(1,page.rows().size());
        assertEquals("LEAD_ASSIGNED",page.rows().get(0).eventType());
        ArgumentCaptor<Map<String,Object>> query=ArgumentCaptor.forClass(Map.class);
        verify(mapper).selectTemplateConfigurations(query.capture());
        assertEquals(20,query.getValue().get("offset"));assertEquals(10,query.getValue().get("limit"));
        assertEquals("STANDARD",query.getValue().get("templateType"));
    }

    @Test void missingTemplateHasTheStableNotFoundError()
    {
        TodoException error=assertThrows(TodoException.class,()->service().template(8L));

        assertEquals("TODO_TEMPLATE_NOT_FOUND",error.getBusinessCode());
    }

    @Test void releaseProjectionReadsImmutableVersionMetadataAndLedgerAction()
    {
        LocalDateTime published=LocalDateTime.of(2026,7,20,9,0);
        when(mapper.selectReleaseRecords(Map.of("templateId",7L))).thenReturn(List.of(Map.ofEntries(
                Map.entry("version_id",9L),Map.entry("template_id",7L),Map.entry("template_code","T-7"),
                Map.entry("template_name","Template 7"),Map.entry("version_no",4),Map.entry("status","PUBLISHED"),
                Map.entry("change_summary","ready"),Map.entry("impact_scope","LEAD"),
                Map.entry("rollback_source_version_id",3L),Map.entry("published_by","alice"),
                Map.entry("published_time",published),Map.entry("update_time",published),Map.entry("action_id","publish-9"),
                Map.entry("action_type","PUBLISH_VERSION"),Map.entry("operator_id",7L),Map.entry("operator_name","alice"))));

        ReleaseRecord release=service().releases(Map.of("templateId",7L)).get(0);

        assertEquals("ready",release.changeSummary());
        assertEquals("LEAD",release.impactScope());
        assertEquals(3L,release.rollbackSourceVersionId());
        assertEquals("publish-9",release.action().get("actionId"));
    }

    @Test void releaseProjectionConvertsSqlTimestampToLocalDateTime()
    {
        java.sql.Timestamp timestamp=java.sql.Timestamp.valueOf(LocalDateTime.of(2026,7,21,10,15));
        when(mapper.selectReleaseRecords(Map.of())).thenReturn(List.of(Map.ofEntries(
                Map.entry("version_id",9L),Map.entry("template_id",7L),Map.entry("template_code","T-7"),
                Map.entry("template_name","Template 7"),Map.entry("status","PUBLISHED"),Map.entry("published_time",timestamp),
                Map.entry("update_time",timestamp))));

        ReleaseRecord release=service().releases(Map.of()).get(0);

        assertEquals(timestamp.toLocalDateTime(),release.publishedTime());
        assertEquals(timestamp.toLocalDateTime(),release.updateTime());
    }

    @Test void releaseProjectionSuppliesADeterministicLimitWhenOffsetIsRequested()
    {
        when(mapper.selectReleaseRecords(anyMap())).thenReturn(List.of());

        service().releases(Map.of("offset",20));

        ArgumentCaptor<Map<String,Object>> query=ArgumentCaptor.forClass(Map.class);
        verify(mapper).selectReleaseRecords(query.capture());
        assertEquals(20,query.getValue().get("offset"));
        assertEquals(100,query.getValue().get("limit"));
    }

    @Test void releasePaginationPreservesAnAbsentOrNullOffsetWithoutAddingALimit()
    {
        when(mapper.selectReleaseRecords(anyMap())).thenReturn(List.of());

        service().releases(Map.of());
        Map<String,Object> nullOffset=new java.util.HashMap<>();nullOffset.put("offset",null);service().releases(nullOffset);

        ArgumentCaptor<Map<String,Object>> queries=ArgumentCaptor.forClass(Map.class);
        verify(mapper,org.mockito.Mockito.times(2)).selectReleaseRecords(queries.capture());
        assertEquals(false,queries.getAllValues().get(0).containsKey("limit"));
        assertEquals(false,queries.getAllValues().get(1).containsKey("limit"));
    }

    @Test void releasePaginationDefaultsAPresentNullLimitWhenOffsetIsProvided()
    {
        when(mapper.selectReleaseRecords(anyMap())).thenReturn(List.of());
        Map<String,Object> query=new java.util.HashMap<>();query.put("offset",20);query.put("limit",null);

        service().releases(query);

        ArgumentCaptor<Map<String,Object>> normalized=ArgumentCaptor.forClass(Map.class);verify(mapper).selectReleaseRecords(normalized.capture());
        assertEquals(100,normalized.getValue().get("limit"));
    }

    @Test void releasePaginationAcceptsZeroOffsetAndAddsTheDefaultLimit()
    {
        when(mapper.selectReleaseRecords(anyMap())).thenReturn(List.of());

        service().releases(Map.of("offset",0));

        ArgumentCaptor<Map<String,Object>> normalized=ArgumentCaptor.forClass(Map.class);verify(mapper).selectReleaseRecords(normalized.capture());
        assertEquals(0,normalized.getValue().get("offset"));
        assertEquals(100,normalized.getValue().get("limit"));
    }

    @Test void releasePaginationRejectsZeroLimitAndNegativeOffset()
    {
        TodoException zero=assertThrows(TodoException.class,()->service().releases(Map.of("limit",0)));
        TodoException negative=assertThrows(TodoException.class,()->service().releases(Map.of("offset",-1)));

        assertEquals("TODO_CONFIGURATION_QUERY_INVALID",zero.getBusinessCode());
        assertEquals("TODO_CONFIGURATION_QUERY_INVALID",negative.getBusinessCode());
    }

    private TodoConfigurationQueryService service(){return new TodoConfigurationQueryService(mapper);}
}
