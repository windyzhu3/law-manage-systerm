package com.ruoyi.web.controller.todo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.law.todo.application.TodoAutoActionCapabilityCatalogService;
import com.law.todo.application.TodoBusinessPayloadHydrationService;
import com.law.todo.application.TodoConfigurationJourneyService;
import com.law.todo.application.TodoConfigurationQueryService;
import com.law.todo.application.TodoConfigurationResourceCatalogService;
import com.law.todo.application.TodoConfigurationResourceManagementService;
import com.law.todo.application.TodoConfigurationSimulationService;
import com.law.todo.application.TodoDefinitionCatalogService;
import com.law.todo.application.TodoDefinitionDiffService;
import com.law.todo.application.TodoDefinitionService;
import com.law.todo.application.TodoDodRuleManagementService;
import com.law.todo.application.TodoEventResourceService;
import com.law.todo.application.TodoJourneySimulationService;
import com.law.todo.application.TodoPublishedSimulationDiagnosticService;
import com.law.todo.application.TodoSlaRuleManagementService;
import com.law.todo.application.TodoTemplateService;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoConfigurationCommands.ConfigurationResourceCommand;
import com.law.todo.application.command.TodoConfigurationCommands.JourneySimulationCommand;
import com.law.todo.application.view.TodoConfigurationJourneyView;
import com.law.todo.application.view.TodoConfigurationJourneyView.CurrentResources;
import com.law.todo.application.view.TodoConfigurationJourneyView.EmployeeTodoPreview;
import com.law.todo.application.view.TodoConfigurationJourneyView.JourneyPermissions;
import com.law.todo.application.view.TodoConfigurationJourneyView.JourneyStep;
import com.law.todo.application.view.TodoConfigurationJourneyView.TemplateSummary;
import com.law.todo.application.view.TodoConfigurationJourneyView.TemplateWorkbenchItem;
import com.law.todo.application.view.TodoConfigurationJourneyView.TemplateWorkbenchPage;
import com.law.todo.domain.TodoException;
import com.law.todo.spi.TodoBusinessPayloadAccess.DataSourceStatus;
import com.law.todo.spi.TodoBusinessPayloadAccess.PayloadHydration;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;

@SpringJUnitConfig(TodoConfigurationControllerJourneyTest.Config.class)
@WebAppConfiguration
class TodoConfigurationControllerJourneyTest
{
    @jakarta.annotation.Resource TodoConfigurationController controller;
    @jakarta.annotation.Resource TodoConfigurationJourneyService journeys;
    @jakarta.annotation.Resource TodoBusinessPayloadHydrationService payloads;
    @jakarta.annotation.Resource TodoJourneySimulationService simulations;
    @jakarta.annotation.Resource TodoConfigurationResourceManagementService resources;
    @jakarta.annotation.Resource RequestMappingHandlerMapping handlerMapping;

    @AfterEach void clear(){SecurityContextHolder.clearContext();}

    @Test
    void returnsJourneyAggregateAndPropagatesAuthenticatedActor() throws Exception
    {
        authenticate("todo:template:list");
        mvc().perform(get("/todo/config/templates/42/journey"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.steps.length()").value(7))
                .andExpect(jsonPath("$.data.template.templateId").value(42))
                .andExpect(jsonPath("$.data.template.definitionJson").doesNotExist())
                .andExpect(jsonPath("$.data.steps[0].code").value("EVENT"))
                .andExpect(jsonPath("$.data.steps[0].value.eventType").value("LEAD_ASSIGNED"))
                .andExpect(jsonPath("$.data.steps[0].value.payloadVersion").value(2))
                .andExpect(jsonPath("$.data.steps[1].code").value("TRIGGER"))
                .andExpect(jsonPath("$.data.steps[1].value.condition.all[0].field").value("lead.source"))
                .andExpect(jsonPath("$.data.steps[2].code").value("OWNER"))
                .andExpect(jsonPath("$.data.steps[2].value.config.fallback.type").value("SUPERVISOR"))
                .andExpect(jsonPath("$.data.steps[3].code").value("DOD"))
                .andExpect(jsonPath("$.data.steps[3].value.config.evidence.types[1]").value("FILE"))
                .andExpect(jsonPath("$.data.steps[4].code").value("SLA"))
                .andExpect(jsonPath("$.data.steps[4].value.config.reminders[1]").value(30))
                .andExpect(jsonPath("$.data.steps[5].code").value("ROUTING"))
                .andExpect(jsonPath("$.data.steps[5].value.config.nodes[0].key").value("review"))
                .andExpect(jsonPath("$.data.steps[6].code").value("SIMULATION_PUBLISH"))
                .andExpect(jsonPath("$.data.steps[6].value.config.panels[0].code").value("summary"));
        ArgumentCaptor<Actor> actor=ArgumentCaptor.forClass(Actor.class);
        verify(journeys).load(org.mockito.ArgumentMatchers.eq(42L),actor.capture());
        assertActor(actor.getValue());
    }

    @Test
    void returnsTaskCenteredWorkbenchPage() throws Exception
    {
        authenticate("todo:template:list");
        mvc().perform(get("/todo/config/templates/workbench").param("pageNum","1").param("pageSize","20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].completedSteps").value(4))
                .andExpect(jsonPath("$.rows[0].blockerCount").value(1))
                .andExpect(jsonPath("$.total").value(1));
        ArgumentCaptor<Actor> actor=ArgumentCaptor.forClass(Actor.class);
        verify(journeys).workbench(anyMap(),actor.capture());
        assertActor(actor.getValue());
    }

    @Test
    void payloadEndpointUnpacksTypedCommandAndNeverTrustsClientActor() throws Exception
    {
        authenticate("todo:simulation:simulate");
        mvc().perform(post("/todo/config/templates/42/journey/payload")
                        .contentType("application/json").content(payloadJson(42)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payload.ownerId").value(17));
        ArgumentCaptor<Actor> actor=ArgumentCaptor.forClass(Actor.class);
        verify(payloads).hydrate(org.mockito.ArgumentMatchers.eq("LEAD_ASSIGNED"),org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq("LEAD"),org.mockito.ArgumentMatchers.eq(3L),actor.capture(),
                org.mockito.ArgumentMatchers.eq(Map.of("ownerId",17)));
        assertActor(actor.getValue());
    }

    @Test
    void deniesSimulationWithoutExecutionPermissionAndAllowsExactPermission() throws Exception
    {
        authenticate("todo:template:list");
        mvc().perform(post("/todo/config/templates/42/journey/simulate")
                        .contentType("application/json").content(simulationJson(42)))
                .andExpect(status().isForbidden());
        authenticate("todo:simulation:simulate");
        mvc().perform(post("/todo/config/templates/42/journey/simulate")
                        .contentType("application/json").content(simulationJson(42)))
                .andExpect(status().isOk());
        ArgumentCaptor<JourneySimulationCommand> command=ArgumentCaptor.forClass(JourneySimulationCommand.class);
        ArgumentCaptor<Actor> actor=ArgumentCaptor.forClass(Actor.class);
        verify(simulations).simulate(command.capture(),actor.capture());
        assertEquals(42L,command.getValue().templateId());assertActor(actor.getValue());
    }

    @Test
    void enforcesPathBodyAndCreateUpdateResourceIdentity() throws Exception
    {
        authenticate("todo:simulation:simulate");
        TodoException payloadMismatch=assertThrows(TodoException.class,()->
                controller.journeyPayload(42L,payloadCommand(41L)));
        assertEquals("TODO_CONFIGURATION_PATH_BODY_MISMATCH",payloadMismatch.getBusinessCode());
        TodoException simulationMismatch=assertThrows(TodoException.class,()->
                controller.journeySimulation(42L,simulationCommand(41L)));
        assertEquals("TODO_CONFIGURATION_PATH_BODY_MISMATCH",simulationMismatch.getBusinessCode());

        authenticate("todo:resource:add");
        TodoException createWithId=assertThrows(TodoException.class,()->
                controller.createResourceItem(resourceCommand(9L)));
        assertEquals("TODO_CONFIGURATION_PATH_BODY_MISMATCH",createWithId.getBusinessCode());
        authenticate("todo:resource:edit");
        TodoException updateMismatch=assertThrows(TodoException.class,()->
                controller.updateResourceItem(8L,resourceCommand(9L)));
        assertEquals("TODO_CONFIGURATION_PATH_BODY_MISMATCH",updateMismatch.getBusinessCode());
    }

    @Test
    void securesResourceWritesAndDataSourceReadWithExistingPermissions() throws Exception
    {
        String create=resourceJson(null);
        authenticate("todo:resource:add");
        mvc().perform(post("/todo/config/resources/items").contentType("application/json").content(create))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(51));
        authenticate("todo:resource:list");
        mvc().perform(post("/todo/config/resources/items").contentType("application/json").content(create))
                .andExpect(status().isForbidden());
        mvc().perform(get("/todo/config/resources/data-sources")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].businessType").value("LEAD"));
        authenticate("todo:resource:edit");
        mvc().perform(put("/todo/config/resources/items/9").contentType("application/json").content(resourceJson(9L)))
                .andExpect(status().isOk());
    }

    @Test
    void registersEveryJourneyEndpointAsOneUniqueHandler()
    {
        long count=handlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry->entry.getValue().getBeanType()==TodoConfigurationController.class).count();
        long distinct=handlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry->entry.getValue().getBeanType()==TodoConfigurationController.class)
                .map(entry->entry.getKey().toString()).distinct().count();
        assertEquals(count,distinct);
    }

    private MockMvc mvc(){return MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new Denied()).build();}

    private void authenticate(String permission)
    {
        SysUser user=new SysUser();user.setUserId(7L);user.setDeptId(2L);user.setUserName("server-user");
        LoginUser principal=new LoginUser(7L,2L,user,Set.of(permission));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal,"x",Set.of(new SimpleGrantedAuthority(permission))));
    }

    private void assertActor(Actor actor)
    {assertEquals(7L,actor.userId());assertEquals("server-user",actor.userName());assertEquals(2L,actor.deptId());}

    private String payloadJson(long templateId)
    {
        return """
                {"templateId":%d,"versionId":9,"eventType":"LEAD_ASSIGNED","payloadVersion":1,
                 "businessType":"LEAD","businessId":3,"manualOverrides":{"ownerId":17},
                 "expectedDefinitionHash":"hash"}
                """.formatted(templateId);
    }

    private String simulationJson(long templateId)
    {
        return """
                {"templateId":%d,"versionId":9,"eventType":"LEAD_ASSIGNED","payloadVersion":1,
                 "businessType":"LEAD","businessId":3,"manualOverrides":{"ownerId":17},
                 "effectiveAt":"2026-07-23T09:00:00","taskCompletions":[],
                 "expectedDefinitionHash":"hash"}
                """.formatted(templateId);
    }

    private com.law.todo.application.command.TodoConfigurationCommands.JourneyPayloadCommand payloadCommand(long templateId)
    {
        return new com.law.todo.application.command.TodoConfigurationCommands.JourneyPayloadCommand(templateId,9L,
                "LEAD_ASSIGNED",1,"LEAD",3L,Map.of("ownerId",17),"hash");
    }

    private JourneySimulationCommand simulationCommand(long templateId)
    {
        return new JourneySimulationCommand(templateId,9L,"LEAD_ASSIGNED",1,"LEAD",3L,Map.of("ownerId",17),
                LocalDateTime.of(2026,7,23,9,0),List.of(),"hash");
    }

    private ConfigurationResourceCommand resourceCommand(Long id)
    {
        return new ConfigurationResourceCommand(id,"FIELD","contactedAt","联系时间","实际联系时间","LEAD",
                "{\"type\":\"string\"}","ACTIVE",10,"resource-action",0);
    }

    private String resourceJson(Long id)
    {
        return """
                {"resourceItemId":%s,"resourceType":"FIELD","resourceCode":"contactedAt",
                 "resourceName":"联系时间","description":"实际联系时间","businessType":"LEAD",
                 "valueJson":"{\\"type\\":\\"string\\"}","status":"ACTIVE","sortOrder":10,
                 "actionId":"resource-action","expectedVersion":0}
                """.formatted(id==null?"null":id.toString());
    }

    @ControllerAdvice
    static class Denied
    {
        @ExceptionHandler(AccessDeniedException.class) @ResponseStatus(HttpStatus.FORBIDDEN) void denied(){}
    }

    @Configuration
    @EnableWebMvc
    @EnableMethodSecurity
    static class Config
    {
        @Bean PermissionProbe ss(){return new PermissionProbe();}
        @Bean TodoConfigurationJourneyService journeys()
        {
            TodoConfigurationJourneyService service=org.mockito.Mockito.mock(TodoConfigurationJourneyService.class);
            when(service.load(anyLong(),any())).thenReturn(journey());
            when(service.workbench(anyMap(),any())).thenReturn(new TemplateWorkbenchPage(List.of(
                    new TemplateWorkbenchItem(42L,"首联待办","LEAD","FIRST_CONTACT","IN_PROGRESS",
                            4,7,1,0,"server-user",LocalDateTime.of(2026,7,23,9,0),"CONTINUE_CONFIGURATION")),
                    1,1,0));
            return service;
        }
        @Bean TodoBusinessPayloadHydrationService payloads()
        {
            TodoBusinessPayloadHydrationService service=org.mockito.Mockito.mock(TodoBusinessPayloadHydrationService.class);
            when(service.hydrate(anyString(),anyInt(),anyString(),anyLong(),any(),anyMap()))
                    .thenReturn(new PayloadHydration(Map.of("ownerId",17),List.of(),false));
            when(service.dataSources()).thenReturn(List.of(new DataSourceStatus("LEAD",true,true,true,"READY","ready")));
            return service;
        }
        @Bean TodoJourneySimulationService journeySimulation()
        {return org.mockito.Mockito.mock(TodoJourneySimulationService.class);}
        @Bean TodoConfigurationResourceManagementService resourceManagement()
        {
            TodoConfigurationResourceManagementService service=org.mockito.Mockito.mock(TodoConfigurationResourceManagementService.class);
            when(service.save(any(),any())).thenReturn(51L);return service;
        }
        @Bean TodoConfigurationController controller(TodoConfigurationJourneyService journeys,
                TodoBusinessPayloadHydrationService payloads,TodoJourneySimulationService journeySimulation,
                TodoConfigurationResourceManagementService resourceManagement)
        {
            return new TodoConfigurationController(mock(TodoConfigurationQueryService.class),
                    mock(TodoSlaRuleManagementService.class),mock(TodoDodRuleManagementService.class),
                    mock(TodoTemplateService.class),mock(TodoDefinitionService.class),
                    mock(TodoDefinitionDiffService.class),mock(TodoConfigurationSimulationService.class),
                    mock(TodoDefinitionCatalogService.class),mock(TodoAutoActionCapabilityCatalogService.class),
                    mock(TodoEventResourceService.class),mock(TodoConfigurationResourceCatalogService.class),
                    mock(TodoPublishedSimulationDiagnosticService.class),journeys,payloads,journeySimulation,
                    resourceManagement);
        }
        private static <T> T mock(Class<T> type){return org.mockito.Mockito.mock(type);}
    }

    static class PermissionProbe
    {
        public boolean hasPermi(String permission)
        {return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority->authority.getAuthority().equals(permission));}
        public boolean hasAnyPermi(String permissions)
        {return java.util.Arrays.stream(permissions.split(",")).anyMatch(this::hasPermi);}
    }

    private static TodoConfigurationJourneyView journey()
    {
        List<JourneyStep> steps=List.of("EVENT","TRIGGER","OWNER","DOD","SLA","ROUTING","SIMULATION_PUBLISH")
                .stream().map(code->new JourneyStep(code,code,"COMPLETED",0,stepValue(code))).toList();
        return new TodoConfigurationJourneyView(
                new TemplateSummary(42L,9L,1,4,"LEAD-FIRST","首联待办","LEAD","FIRST_CONTACT","DRAFT","hash"),
                steps,new CurrentResources(List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of()),
                new EmployeeTodoPreview("首联","张律师",List.of(),List.of(),List.of(),"30分钟"),
                List.of(),new JourneyPermissions(true,true,true,true,true,true));
    }

    private static Map<String,Object> stepValue(String code)
    {
        return switch(code)
        {
            case "EVENT" -> Map.of("eventType","LEAD_ASSIGNED","payloadVersion",2);
            case "TRIGGER" -> Map.of("condition",Map.of("all",List.of(
                    Map.of("field","lead.source","operator","EQ","value","WEB"))));
            case "OWNER" -> Map.of("config",Map.of("type","BUSINESS_OWNER","fallback",Map.of("type","SUPERVISOR")));
            case "DOD" -> Map.of("config",Map.of("requiredFields",List.of("contactedAt"),
                    "evidence",Map.of("types",List.of("NOTE","FILE"))));
            case "SLA" -> Map.of("config",Map.of("calendarCode","DEFAULT","minutes",60,"reminders",List.of(15,30)));
            case "ROUTING" -> Map.of("config",Map.of("start","review","nodes",List.of(Map.of("key","review")),"edges",List.of()));
            case "SIMULATION_PUBLISH" -> Map.of("config",Map.of("businessStage","QUALIFY",
                    "panels",List.of(Map.of("code","summary"))));
            default -> throw new IllegalArgumentException("Unknown journey step: "+code);
        };
    }
}
