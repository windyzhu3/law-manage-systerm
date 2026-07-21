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
import com.law.todo.application.view.TodoConfigurationViews.BusinessObjectPage;
import com.law.todo.domain.TodoException;
import com.law.todo.mapper.TodoConfigurationMapper;
import com.law.todo.spi.TodoBusinessDirectoryAccess;
import com.law.todo.application.command.TodoActionCommands.Actor;

@ExtendWith(MockitoExtension.class)
class TodoConfigurationQueryServiceTest
{
    @Mock TodoConfigurationMapper mapper;
    @Mock TodoBusinessDirectoryAccess directory;

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
        when(mapper.selectReleaseRecords(anyMap())).thenReturn(List.of(Map.ofEntries(
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
        when(mapper.selectReleaseRecords(anyMap())).thenReturn(List.of(Map.ofEntries(
                Map.entry("version_id",9L),Map.entry("template_id",7L),Map.entry("template_code","T-7"),
                Map.entry("template_name","Template 7"),Map.entry("status","PUBLISHED"),Map.entry("published_time",timestamp),
                Map.entry("update_time",timestamp))));

        ReleaseRecord release=service().releases(Map.of()).get(0);

        assertEquals(timestamp.toLocalDateTime(),release.publishedTime());
        assertEquals(timestamp.toLocalDateTime(),release.updateTime());
    }

    @Test void releaseProjectionSuppliesTheDefaultPageSizeWhenOffsetIsRequested()
    {
        when(mapper.selectReleaseRecords(anyMap())).thenReturn(List.of());

        service().releases(Map.of("offset",20));

        ArgumentCaptor<Map<String,Object>> query=ArgumentCaptor.forClass(Map.class);
        verify(mapper).selectReleaseRecords(query.capture());
        assertEquals(20,query.getValue().get("offset"));
        assertEquals(20,query.getValue().get("limit"));
    }

    @Test void releasePaginationAlwaysSuppliesDeterministicDefaults()
    {
        when(mapper.selectReleaseRecords(anyMap())).thenReturn(List.of());

        service().releases(Map.of());
        Map<String,Object> nullOffset=new java.util.HashMap<>();nullOffset.put("offset",null);service().releases(nullOffset);

        ArgumentCaptor<Map<String,Object>> queries=ArgumentCaptor.forClass(Map.class);
        verify(mapper,org.mockito.Mockito.times(2)).selectReleaseRecords(queries.capture());
        assertEquals(0,queries.getAllValues().get(0).get("offset"));
        assertEquals(20,queries.getAllValues().get(0).get("limit"));
        assertEquals(0,queries.getAllValues().get(1).get("offset"));
        assertEquals(20,queries.getAllValues().get(1).get("limit"));
    }

    @Test void releasePaginationDefaultsAPresentNullLimitWhenOffsetIsProvided()
    {
        when(mapper.selectReleaseRecords(anyMap())).thenReturn(List.of());
        Map<String,Object> query=new java.util.HashMap<>();query.put("offset",20);query.put("limit",null);

        service().releases(query);

        ArgumentCaptor<Map<String,Object>> normalized=ArgumentCaptor.forClass(Map.class);verify(mapper).selectReleaseRecords(normalized.capture());
        assertEquals(20,normalized.getValue().get("limit"));
    }

    @Test void releasePaginationAcceptsZeroOffsetAndAddsTheDefaultLimit()
    {
        when(mapper.selectReleaseRecords(anyMap())).thenReturn(List.of());

        service().releases(Map.of("offset",0));

        ArgumentCaptor<Map<String,Object>> normalized=ArgumentCaptor.forClass(Map.class);verify(mapper).selectReleaseRecords(normalized.capture());
        assertEquals(0,normalized.getValue().get("offset"));
        assertEquals(20,normalized.getValue().get("limit"));
    }

    @Test void releasePaginationClampsOversizedLimits()
    {
        when(mapper.selectReleaseRecords(anyMap())).thenReturn(List.of());

        service().releases(Map.of("offset",0,"limit",700));

        ArgumentCaptor<Map<String,Object>> normalized=ArgumentCaptor.forClass(Map.class);verify(mapper).selectReleaseRecords(normalized.capture());
        assertEquals(500,normalized.getValue().get("limit"));
    }

    @Test void releasePaginationRejectsZeroLimitAndNegativeOffset()
    {
        TodoException zero=assertThrows(TodoException.class,()->service().releases(Map.of("limit",0)));
        TodoException negative=assertThrows(TodoException.class,()->service().releases(Map.of("offset",-1)));

        assertEquals("TODO_CONFIGURATION_QUERY_INVALID",zero.getBusinessCode());
        assertEquals("TODO_CONFIGURATION_QUERY_INVALID",negative.getBusinessCode());
    }

    @Test void releaseVersionCatalogContainsOnlyImmutableVersionsForTheSelectedRelease()
    {
        LocalDateTime published=LocalDateTime.of(2026,7,20,9,0);
        when(mapper.selectReleaseRecord(9L)).thenReturn(Map.ofEntries(Map.entry("version_id",9L),Map.entry("template_id",7L),
                Map.entry("template_code","T-7"),Map.entry("template_name","Template 7"),Map.entry("version_no",4),
                Map.entry("status","PUBLISHED"),Map.entry("published_time",published),Map.entry("update_time",published)));
        when(mapper.selectImmutableTemplateVersions(7L)).thenReturn(List.of(
                Map.of("version_id",9L,"version_no",4,"status","PUBLISHED"),
                Map.of("version_id",8L,"version_no",3,"status","RETIRED")));

        var versions=service().releaseVersions(9L);

        assertEquals(List.of("PUBLISHED","RETIRED"),versions.stream().map(item->item.status()).toList());
    }

    @Test void businessObjectLookupIsPagedAndProjectsOnlyStableIdentityFields()
    {
        Actor actor=new Actor(7L,"operator",2L);
        when(directory.supports("LEAD")).thenReturn(true);
        when(directory.search("LEAD","31",20,20,actor)).thenReturn(new TodoBusinessDirectoryAccess.DirectoryPage(
                List.of(new TodoBusinessDirectoryAccess.DirectoryEntry(31L,"L-31","Lead 31","LEAD")),1L));

        BusinessObjectPage page=serviceWithDirectory().businessObjects("LEAD","31",2,20,actor);

        assertEquals(1L,page.total());assertEquals("L-31",page.rows().get(0).businessNo());
        verify(directory).search("LEAD","31",20,20,actor);
    }

    @Test void unauthorizedBusinessObjectIsAbsentFromRowsAndTotalAndCannotBeLookedUp()
    {
        Actor actor=new Actor(7L,"operator",2L);
        when(directory.supports("CASE")).thenReturn(true);
        when(directory.search("CASE",null,0,20,actor)).thenReturn(new TodoBusinessDirectoryAccess.DirectoryPage(List.of(),0));

        BusinessObjectPage page=serviceWithDirectory().businessObjects("CASE",null,1,20,actor);
        TodoException error=assertThrows(TodoException.class,()->serviceWithDirectory().requireBusinessObject("CASE",99L,actor));

        assertEquals(0,page.total());assertEquals(List.of(),page.rows());
        assertEquals("TODO_SIMULATION_BUSINESS_OBJECT_NOT_FOUND",error.getBusinessCode());
    }

    @Test void allFiveBusinessTypesUseTheActorScopedDirectory()
    {
        Actor actor=new Actor(7L,"operator",2L);
        for(String type:List.of("LEAD","CUSTOMER","CONTRACT","CASE","MATTER"))
        {
            when(directory.supports(type)).thenReturn(true);
            when(directory.findVisible(type,9L,actor)).thenReturn(java.util.Optional.of(
                    new TodoBusinessDirectoryAccess.DirectoryEntry(9L,type+"-9",type+" 9",type)));
            assertEquals(type,serviceWithDirectory().requireBusinessObject(type,9L,actor).businessType());
        }
    }

    private TodoConfigurationQueryService service(){return new TodoConfigurationQueryService(mapper);}
    private TodoConfigurationQueryService serviceWithDirectory(){return new TodoConfigurationQueryService(mapper,List.of(directory));}
}
