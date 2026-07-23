package com.ruoyi.web.controller.todo;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.law.todo.application.TodoConfigurationQueryService;
import com.law.todo.application.TodoConfigurationJourneyService;
import com.law.todo.application.TodoBusinessPayloadHydrationService;
import com.law.todo.application.TodoJourneySimulationService;
import com.law.todo.application.TodoConfigurationResourceManagementService;
import com.law.todo.application.TodoConfigurationSimulationService;
import com.law.todo.application.TodoDefinitionDiffService;
import com.law.todo.application.TodoDefinitionCatalogService;
import com.law.todo.application.TodoAutoActionCapabilityCatalogService;
import com.law.todo.application.TodoDefinitionService;
import com.law.todo.application.TodoDodRuleManagementService;
import com.law.todo.application.TodoEventResourceService;
import com.law.todo.application.TodoConfigurationResourceCatalogService;
import com.law.todo.application.TodoPublishedSimulationDiagnosticService;
import com.law.todo.application.TodoSlaRuleManagementService;
import com.law.todo.application.TodoTemplateService;
import com.law.todo.application.command.TodoActionCommands.Actor;
import com.law.todo.application.command.TodoConfigurationCommands.ConfigurationSimulationCommand;
import com.law.todo.application.command.TodoConfigurationCommands.ConfigurationResourceCommand;
import com.law.todo.application.command.TodoConfigurationCommands.JourneyPayloadCommand;
import com.law.todo.application.command.TodoConfigurationCommands.JourneySimulationCommand;
import com.law.todo.application.command.TodoConfigurationCommands.DodRuleCommand;
import com.law.todo.application.command.TodoConfigurationCommands.SlaRuleCommand;
import com.law.todo.application.command.TodoDefinitionCommands.CopyTemplateCommand;
import com.law.todo.application.command.TodoDefinitionCommands.CopyVersionCommand;
import com.law.todo.application.command.TodoDefinitionCommands.CreateTemplateCommand;
import com.law.todo.application.command.TodoDefinitionCommands.ImportTemplateCommand;
import com.law.todo.application.command.TodoDefinitionCommands.PublishDraftCommand;
import com.law.todo.application.command.TodoDefinitionCommands.RollbackDraftCommand;
import com.law.todo.application.command.TodoDefinitionCommands.ReleaseDraftCommand;
import com.law.todo.application.command.TodoDefinitionCommands.UpdateDraftCommand;
import com.law.todo.application.command.TodoResourceCommands.EventResourceCommand;
import com.law.todo.application.command.TodoResourceCommands.EventResourceStatusCommand;
import com.law.todo.application.command.TodoResourceCommands.SimpleDodRuleCommand;
import com.law.todo.application.command.TodoManagementCommands.TemplateMetadataCommand;
import com.law.todo.application.command.TodoManagementCommands.TemplateToggleCommand;
import com.law.todo.application.command.TodoManagementCommands.TriggerCommand;
import com.law.todo.application.command.TodoManagementCommands.TriggerSortCommand;
import com.law.todo.application.command.TodoManagementCommands.TriggerToggleCommand;
import com.law.todo.domain.TodoException;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.utils.SecurityUtils;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Typed HTTP boundary for the six configuration-centre pages. */
@RestController
@RequestMapping("/todo/config")
public class TodoConfigurationController extends BaseController
{
    private final TodoConfigurationQueryService query;
    private final TodoSlaRuleManagementService sla;
    private final TodoDodRuleManagementService dod;
    private final TodoTemplateService templates;
    private final TodoDefinitionService definitions;
    private final TodoDefinitionDiffService diff;
    private final TodoConfigurationSimulationService simulation;
    private final TodoDefinitionCatalogService catalogs;
    private final TodoAutoActionCapabilityCatalogService autoActions;
    private final TodoEventResourceService eventResources;
    private final TodoConfigurationResourceCatalogService resourceCatalog;
    private final TodoPublishedSimulationDiagnosticService publishedDiagnostics;
    private final TodoConfigurationJourneyService journeys;
    private final TodoBusinessPayloadHydrationService payloads;
    private final TodoJourneySimulationService journeySimulation;
    private final TodoConfigurationResourceManagementService resourceManagement;

    @Autowired
    public TodoConfigurationController(TodoConfigurationQueryService query,TodoSlaRuleManagementService sla,
            TodoDodRuleManagementService dod,TodoTemplateService templates,TodoDefinitionService definitions,
            TodoDefinitionDiffService diff,TodoConfigurationSimulationService simulation,
            TodoDefinitionCatalogService catalogs,TodoAutoActionCapabilityCatalogService autoActions,
            TodoEventResourceService eventResources,TodoConfigurationResourceCatalogService resourceCatalog,
            TodoPublishedSimulationDiagnosticService publishedDiagnostics,TodoConfigurationJourneyService journeys,
            TodoBusinessPayloadHydrationService payloads,TodoJourneySimulationService journeySimulation,
            TodoConfigurationResourceManagementService resourceManagement)
    {this.query=query;this.sla=sla;this.dod=dod;this.templates=templates;this.definitions=definitions;this.diff=diff;this.simulation=simulation;this.catalogs=catalogs;this.autoActions=autoActions;this.eventResources=eventResources;this.resourceCatalog=resourceCatalog;this.publishedDiagnostics=publishedDiagnostics;this.journeys=journeys;this.payloads=payloads;this.journeySimulation=journeySimulation;this.resourceManagement=resourceManagement;}

    public TodoConfigurationController(TodoConfigurationQueryService query,TodoSlaRuleManagementService sla,
            TodoDodRuleManagementService dod,TodoTemplateService templates,TodoDefinitionService definitions,
            TodoDefinitionDiffService diff,TodoConfigurationSimulationService simulation,
            TodoDefinitionCatalogService catalogs,TodoAutoActionCapabilityCatalogService autoActions,
            TodoEventResourceService eventResources,TodoConfigurationResourceCatalogService resourceCatalog)
    {this(query,sla,dod,templates,definitions,diff,simulation,catalogs,autoActions,eventResources,resourceCatalog,null,null,null,null,null);}

    public TodoConfigurationController(TodoConfigurationQueryService query,TodoSlaRuleManagementService sla,
            TodoDodRuleManagementService dod,TodoTemplateService templates,TodoDefinitionService definitions,
            TodoDefinitionDiffService diff,TodoConfigurationSimulationService simulation,
            TodoDefinitionCatalogService catalogs,TodoAutoActionCapabilityCatalogService autoActions,
            TodoEventResourceService eventResources,TodoConfigurationResourceCatalogService resourceCatalog,
            TodoPublishedSimulationDiagnosticService publishedDiagnostics)
    {this(query,sla,dod,templates,definitions,diff,simulation,catalogs,autoActions,eventResources,resourceCatalog,
            publishedDiagnostics,null,null,null,null);}

    /** Focused-test compatibility; production uses the fully injected catalogue constructor. */
    public TodoConfigurationController(TodoConfigurationQueryService query,TodoSlaRuleManagementService sla,
            TodoDodRuleManagementService dod,TodoTemplateService templates,TodoDefinitionService definitions,
            TodoDefinitionDiffService diff,TodoConfigurationSimulationService simulation)
    {this(query,sla,dod,templates,definitions,diff,simulation,null,null,null,null,null);}

    @PreAuthorize("@ss.hasPermi('todo:template:list')")
    @GetMapping("/dashboard") public AjaxResult dashboard(){return success(query.dashboard());}

    @PreAuthorize("@ss.hasAnyPermi('todo:template:list,todo:template:edit,todo:simulation:simulate,todo:release:publish')")
    @GetMapping("/templates/{id}/journey")
    public AjaxResult journey(@PathVariable long id){return success(journeys.load(id,actor()));}
    @PreAuthorize("@ss.hasAnyPermi('todo:template:list,todo:template:edit,todo:simulation:simulate,todo:release:publish')")
    @GetMapping("/templates/workbench")
    public TableDataInfo journeyWorkbench(@Valid @ModelAttribute TemplateListQuery value)
    {var page=journeys.workbench(value.toMap(),actor());return new TableDataInfo(page.rows(),page.total());}
    @PreAuthorize("@ss.hasPermi('todo:simulation:simulate')")
    @PostMapping("/templates/{id}/journey/payload")
    public AjaxResult journeyPayload(@PathVariable Long id,@Valid @RequestBody JourneyPayloadCommand command)
    {
        requireSame(id,command.templateId());
        return success(payloads.hydrate(command.eventType(),command.payloadVersion(),command.businessType(),
                command.businessId(),actor(),command.manualOverrides()));
    }
    @PreAuthorize("@ss.hasPermi('todo:simulation:simulate')")
    @PostMapping("/templates/{id}/journey/simulate")
    public AjaxResult journeySimulation(@PathVariable Long id,@Valid @RequestBody JourneySimulationCommand command)
    {requireSame(id,command.templateId());return success(journeySimulation.simulate(command,actor()));}

    @PreAuthorize("@ss.hasPermi('todo:resource:list')")
    @GetMapping("/resources/events") public TableDataInfo eventResources(@Valid @ModelAttribute EventResourceListQuery value)
    {var result=eventResources.list(value.toMap());return new TableDataInfo(result.rows(),result.total());}
    @PreAuthorize("@ss.hasAnyPermi('todo:resource:list,todo:resource:query')")
    @GetMapping("/resources/events/{id}") public AjaxResult eventResource(@PathVariable long id){return success(eventResources.detail(id));}
    @PreAuthorize("@ss.hasPermi('todo:resource:add')")
    @PostMapping("/resources/events") public AjaxResult createEventResource(@Valid @RequestBody EventResourceCommand value)
    {requireNew(value.eventCatalogId());return success(eventResources.save(value,actor()));}
    @PreAuthorize("@ss.hasPermi('todo:resource:edit')")
    @PutMapping("/resources/events/{id}") public AjaxResult updateEventResource(@PathVariable Long id,@Valid @RequestBody EventResourceCommand value)
    {requireSame(id,value.eventCatalogId());return success(eventResources.save(value,actor()));}
    @PreAuthorize("@ss.hasPermi('todo:resource:add')")
    @PostMapping("/resources/events/{id}/versions") public AjaxResult createEventResourceVersion(@PathVariable long id,@Valid @RequestBody ActionIdCommand value)
    {return success(eventResources.createNextVersion(id,value.actionId(),actor()));}
    @PreAuthorize("@ss.hasPermi('todo:resource:status')")
    @PostMapping("/resources/events/{id}/status") public AjaxResult changeEventResourceStatus(@PathVariable long id,@Valid @RequestBody EventResourceStatusCommand value)
    {eventResources.changeStatus(id,value,actor());return success();}
    @PreAuthorize("@ss.hasAnyPermi('todo:resource:list,todo:resource:query,todo:dod-rule:list,todo:dod-rule:create,todo:dod-rule:edit,todo:template:create,todo:template:edit,todo:simulation:list')")
    @GetMapping("/resources/validators") public AjaxResult validatorResources(@RequestParam(required=false) String businessType)
    {return success(resourceCatalog.validators(businessType));}
    @PreAuthorize("@ss.hasAnyPermi('todo:resource:list,todo:resource:query,todo:dod-rule:list,todo:dod-rule:create,todo:dod-rule:edit,todo:template:create,todo:template:edit,todo:simulation:list')")
    @GetMapping("/resources/fields") public AjaxResult fieldResources(@RequestParam String businessType)
    {return success(resourceCatalog.fields(businessType));}
    @PreAuthorize("@ss.hasAnyPermi('todo:resource:list,todo:resource:query,todo:dod-rule:list,todo:dod-rule:create,todo:dod-rule:edit,todo:template:create,todo:template:edit,todo:simulation:list')")
    @GetMapping("/resources/materials") public AjaxResult materialResources(@RequestParam String businessType)
    {return success(resourceCatalog.materials(businessType));}
    @PreAuthorize("@ss.hasAnyPermi('todo:resource:list,todo:resource:query,todo:dod-rule:list,todo:dod-rule:create,todo:dod-rule:edit,todo:template:create,todo:template:edit,todo:simulation:list')")
    @GetMapping("/resources/dod-recipes") public AjaxResult dodRecipeResources(@RequestParam String businessType)
    {return success(resourceCatalog.recipes(businessType));}
    @PreAuthorize("@ss.hasAnyPermi('todo:resource:add,todo:resource:edit')")
    @PostMapping("/resources/items")
    public AjaxResult createResourceItem(@Valid @RequestBody ConfigurationResourceCommand command)
    {requireNew(command.resourceItemId());return success(resourceManagement.save(command,actor()));}
    @PreAuthorize("@ss.hasPermi('todo:resource:edit')")
    @PutMapping("/resources/items/{id}")
    public AjaxResult updateResourceItem(@PathVariable Long id,@Valid @RequestBody ConfigurationResourceCommand command)
    {requireSame(id,command.resourceItemId());return success(resourceManagement.save(command,actor()));}
    @PreAuthorize("@ss.hasAnyPermi('todo:resource:list,todo:resource:query,todo:template:list,todo:simulation:simulate')")
    @GetMapping("/resources/data-sources")
    public AjaxResult resourceDataSources(){return success(payloads.dataSources());}

    @PreAuthorize("@ss.hasPermi('todo:sla-rule:list')")
    @GetMapping("/sla-rules") public TableDataInfo slaRules(@Valid @ModelAttribute RuleListQuery value){return page(sla.list(value.toMap()),value.pageNum(),value.pageSize());}
    @PreAuthorize("@ss.hasPermi('todo:sla-rule:list')")
    @GetMapping("/sla-rules/{id}") public AjaxResult slaRule(@PathVariable long id){return success(sla.detail(id));}
    @PreAuthorize("@ss.hasPermi('todo:sla-rule:create')")
    @PostMapping("/sla-rules") public AjaxResult createSlaRule(@Valid @RequestBody SlaRuleCommand value){requireNew(value.slaRuleId());return success(sla.save(value,actor()));}
    @PreAuthorize("@ss.hasPermi('todo:sla-rule:edit')")
    @PutMapping("/sla-rules/{id}") public AjaxResult updateSlaRule(@PathVariable Long id,@Valid @RequestBody SlaRuleCommand value){requireSame(id,value.slaRuleId());return success(sla.save(value,actor()));}
    @PreAuthorize("@ss.hasPermi('todo:sla-rule:copy')")
    @PostMapping("/sla-rules/{id}/copy") public AjaxResult copySlaRule(@PathVariable long id,@Valid @RequestBody RuleCopyCommand value){return success(sla.copy(id,value.newRuleCode(),value.actionId(),actor()));}
    @PreAuthorize("@ss.hasPermi('todo:sla-rule:toggle')")
    @PostMapping("/sla-rules/{id}/toggle") public AjaxResult toggleSlaRule(@PathVariable long id,@Valid @RequestBody RuleToggleCommand value){sla.toggle(id,value.status(),value.actionId(),value.expectedVersion(),actor());return success();}
    @PreAuthorize("@ss.hasPermi('todo:sla-rule:list')")
    @PostMapping("/sla-rules/{id}/test") public AjaxResult testSlaRule(@PathVariable long id,@Valid @RequestBody SlaTestCommand value){return success(sla.testCalculation(id,value.createdAt()));}

    @PreAuthorize("@ss.hasPermi('todo:dod-rule:list')")
    @GetMapping("/dod-rules") public TableDataInfo dodRules(@Valid @ModelAttribute RuleListQuery value){return page(dod.list(value.toMap()),value.pageNum(),value.pageSize());}
    @PreAuthorize("@ss.hasPermi('todo:dod-rule:list')")
    @GetMapping("/dod-rules/{id}") public AjaxResult dodRule(@PathVariable long id){return success(dod.detail(id));}
    @PreAuthorize("@ss.hasPermi('todo:dod-rule:create')")
    @PostMapping("/dod-rules") public AjaxResult createDodRule(@Valid @RequestBody DodRuleCommand value){requireNew(value.dodRuleId());return success(dod.save(value,actor()));}
    @PreAuthorize("@ss.hasPermi('todo:dod-rule:create')")
    @PostMapping("/dod-rules/simple") public AjaxResult createSimpleDodRule(@Valid @RequestBody SimpleDodRuleCommand value){requireNew(value.dodRuleId());return success(dod.saveSimple(value,actor()));}
    @PreAuthorize("@ss.hasPermi('todo:dod-rule:edit')")
    @PutMapping("/dod-rules/{id}") public AjaxResult updateDodRule(@PathVariable Long id,@Valid @RequestBody DodRuleCommand value){requireSame(id,value.dodRuleId());return success(dod.save(value,actor()));}
    @PreAuthorize("@ss.hasPermi('todo:dod-rule:edit')")
    @PutMapping("/dod-rules/{id}/simple") public AjaxResult updateSimpleDodRule(@PathVariable Long id,@Valid @RequestBody SimpleDodRuleCommand value){requireSame(id,value.dodRuleId());return success(dod.saveSimple(value,actor()));}
    @PreAuthorize("@ss.hasPermi('todo:dod-rule:copy')")
    @PostMapping("/dod-rules/{id}/copy") public AjaxResult copyDodRule(@PathVariable long id,@Valid @RequestBody RuleCopyCommand value){return success(dod.copy(id,value.newRuleCode(),value.actionId(),actor()));}
    @PreAuthorize("@ss.hasPermi('todo:dod-rule:toggle')")
    @PostMapping("/dod-rules/{id}/toggle") public AjaxResult toggleDodRule(@PathVariable long id,@Valid @RequestBody RuleToggleCommand value){dod.toggle(id,value.status(),value.actionId(),value.expectedVersion(),actor());return success();}
    @PreAuthorize("@ss.hasPermi('todo:dod-rule:list')")
    @GetMapping("/dod-rules/{id}/reference-count") public AjaxResult dodReferenceCount(@PathVariable long id){return success(dod.referenceCount(id));}
    @PreAuthorize("@ss.hasPermi('todo:dod-rule:list')")
    @PostMapping("/dod-rules/{id}/test") public AjaxResult testDodRule(@PathVariable long id,@Valid @RequestBody DodTestCommand value){return success(dod.test(id,value.payload(),value.attachments(),actor()));}

    @PreAuthorize("@ss.hasAnyPermi('todo:template:list,todo:simulation:list')")
    @GetMapping("/templates") public TableDataInfo templateList(@Valid @ModelAttribute TemplateListQuery value){var page=query.templatePage(value.toMap());return new TableDataInfo(page.rows(),page.total());}
    @PreAuthorize("@ss.hasAnyPermi('todo:template:list,todo:template:create,todo:template:copy,todo:template:edit,todo:simulation:list,todo:release:publish')")
    @GetMapping("/templates/{id}") public AjaxResult template(@PathVariable Long id){return success(query.template(id));}
    @PreAuthorize("@ss.hasPermi('todo:template:create')")
    @PostMapping("/templates") public AjaxResult createTemplate(@Valid @RequestBody CreateTemplateCommand value){return success(definitions.createTemplateDraft(value,actor()));}
    @PreAuthorize("@ss.hasPermi('todo:template:import')")
    @PostMapping("/templates/import") public AjaxResult importTemplate(@Valid @RequestBody ImportTemplateCommand value){return success(definitions.importTemplateDraft(value,actor()));}
    @PreAuthorize("@ss.hasPermi('todo:template:edit')")
    @PutMapping("/templates/{id}") public AjaxResult updateTemplate(@PathVariable Long id,@Valid @RequestBody TemplateMetadataCommand value){requireSame(id,value.templateId());return success(templates.updateTemplateMetadata(value,actor()));}
    @PreAuthorize("@ss.hasPermi('todo:template:copy')")
    @PostMapping("/templates/{id}/copy") public AjaxResult copyTemplate(@PathVariable Long id,@Valid @RequestBody CopyTemplateCommand value){return success(definitions.copyTemplateDraft(id,value,actor()));}
    @PreAuthorize("@ss.hasPermi('todo:template:toggle')")
    @PostMapping("/templates/{id}/toggle") public AjaxResult toggleTemplate(@PathVariable Long id,@Valid @RequestBody TemplateToggleCommand value){templates.toggleTemplate(id,value,actor());return success();}
    @PreAuthorize("@ss.hasAnyPermi('todo:template:edit,todo:template:create,todo:template:copy')")
    @PutMapping("/template-versions/{id}") public AjaxResult updateTemplateDraft(@PathVariable Long id,@Valid @RequestBody UpdateDraftCommand value){requireSame(id,value.versionId());return success(definitions.updateDraft(value,actor()));}
    @PreAuthorize("@ss.hasAnyPermi('todo:template:edit,todo:release:publish,todo:simulation:simulate')")
    @PostMapping("/template-versions/{id}/preflight") public AjaxResult preflightTemplateDraft(@PathVariable Long id){return success(definitions.preflight(id));}
    @PreAuthorize("@ss.hasAnyPermi('todo:template:list,todo:template:create,todo:template:copy,todo:template:edit,todo:simulation:list,todo:release:publish')")
    @GetMapping("/template-catalog/events") public AjaxResult templateEventCatalog(){return success(templates.listTemplateEventCatalog());}
    @PreAuthorize("@ss.hasAnyPermi('todo:template:list,todo:template:create,todo:template:copy,todo:template:edit,todo:simulation:simulate,todo:release:publish')")
    @GetMapping("/template-catalog/owners") public AjaxResult templateOwnerCatalog(){return success(query.ownerCatalog());}
    @PreAuthorize("@ss.hasAnyPermi('todo:template:list,todo:template:create,todo:template:copy,todo:template:edit,todo:simulation:simulate,todo:release:publish')")
    @GetMapping("/template-catalog/handlers") public AjaxResult templateHandlerCatalog(){return success(catalogs==null?List.of():catalogs.handlers().stream().map(item->new CapabilitySummary(item.code(),item.description(),item.simulatable())).toList());}
    @PreAuthorize("@ss.hasAnyPermi('todo:template:list,todo:template:create,todo:template:copy,todo:template:edit,todo:simulation:simulate,todo:release:publish,todo:dod-rule:list,todo:dod-rule:create,todo:dod-rule:edit,todo:dod-rule:copy')")
    @GetMapping("/template-catalog/validators") public AjaxResult templateValidatorCatalog(){return success(catalogs==null?List.of():catalogs.validators().stream().map(item->new CapabilitySummary(item.code(),item.description(),false)).toList());}
    @PreAuthorize("@ss.hasAnyPermi('todo:template:list,todo:template:create,todo:template:copy,todo:template:edit,todo:simulation:simulate,todo:release:publish')")
    @GetMapping("/template-catalog/auto-actions") public AjaxResult templateAutoActionCatalog(){return success(autoActions==null?List.of():autoActions.list());}
    @PreAuthorize("@ss.hasAnyPermi('todo:template:list,todo:template:create,todo:template:copy,todo:template:edit,todo:simulation:simulate,todo:release:publish')")
    @GetMapping("/template-catalog/routing-targets") public AjaxResult templateRoutingTargetCatalog(){return success(templates.listRoutingTargetCatalog());}
    @PreAuthorize("@ss.hasAnyPermi('todo:template:list,todo:template:create,todo:template:copy,todo:template:edit')")
    @GetMapping("/template-catalog/sla-rules") public AjaxResult templateSlaRuleCatalog(){var rows=sla.list(Map.of("status","0"));return success(rows==null?List.of():rows.stream().map(row->new TemplateRuleCatalogEntry(
            row.slaRuleId(),row.ruleCode(),row.ruleName(),row.status(),row.slaType(),row.durationValue(),row.durationUnit(),row.calendarCode(),null,null,null)).toList());}
    @PreAuthorize("@ss.hasAnyPermi('todo:template:list,todo:template:create,todo:template:copy,todo:template:edit')")
    @GetMapping("/template-catalog/dod-rules") public AjaxResult templateDodRuleCatalog(){var rows=dod.list(Map.of("status","0"));return success(rows==null?List.of():rows.stream().map(row->new TemplateRuleCatalogEntry(
            row.dodRuleId(),row.ruleCode(),row.ruleName(),row.status(),row.ruleType(),null,null,null,
            row.requiredFieldsJson(),row.requiredAttachmentsJson(),row.conditionalRulesJson())).toList());}
    @PreAuthorize("@ss.hasAnyPermi('todo:template:list,todo:template:create,todo:template:copy,todo:template:edit,todo:sla-rule:list,todo:sla-rule:create,todo:sla-rule:edit,todo:sla-rule:copy')")
    @GetMapping("/template-catalog/calendars") public AjaxResult templateCalendarCatalog(){return success(templates.listTemplateCalendarCatalog());}

    @PreAuthorize("@ss.hasPermi('todo:trigger:list')")
    @GetMapping("/trigger-rules") public TableDataInfo triggerRules(@Valid @ModelAttribute PageQuery value){return page(templates.listTriggers(value.keyword()),value.pageNum(),value.pageSize());}
    @PreAuthorize("@ss.hasPermi('todo:trigger:list')")
    @GetMapping("/trigger-catalog/events") public AjaxResult triggerEventCatalog(){return success(templates.listEventCatalogs());}
    @PreAuthorize("@ss.hasPermi('todo:trigger:list')")
    @GetMapping("/trigger-catalog/templates") public AjaxResult triggerTemplateCatalog(){return success(templates.listTemplates());}
    @PreAuthorize("@ss.hasPermi('todo:trigger:list')")
    @GetMapping("/trigger-catalog/templates/{id}/versions") public AjaxResult triggerTemplateVersions(@PathVariable Long id){return success(templates.listPublishedVersionCatalog(id));}
    @PreAuthorize("@ss.hasPermi('todo:trigger:create')")
    @PostMapping("/trigger-rules") public AjaxResult createTrigger(@Valid @RequestBody TriggerCommand value){requireNew(value.triggerRuleId());return success(templates.saveTrigger(value,actor()));}
    @PreAuthorize("@ss.hasPermi('todo:trigger:edit')")
    @PutMapping("/trigger-rules/{id}") public AjaxResult updateTrigger(@PathVariable Long id,@Valid @RequestBody TriggerCommand value){requireSame(id,value.triggerRuleId());return success(templates.saveTrigger(value,actor()));}
    @PreAuthorize("@ss.hasPermi('todo:trigger:toggle')")
    @PostMapping("/trigger-rules/{id}/toggle") public AjaxResult toggleTrigger(@PathVariable Long id,@Valid @RequestBody TriggerToggleCommand value){templates.toggleTrigger(id,value,actor());return success();}
    @PreAuthorize("@ss.hasPermi('todo:trigger:edit')")
    @PostMapping("/trigger-rules/sort") public AjaxResult sortTriggers(@Valid @RequestBody TriggerSortCommand value){templates.sortTriggers(value,actor());return success();}

    @PreAuthorize("@ss.hasPermi('todo:simulation:simulate')")
    @PostMapping({"/simulations","/trigger-rules/simulate"}) public AjaxResult simulate(@Valid @RequestBody ConfigurationSimulationCommand value){return success(simulation.simulate(value,actor()));}
    @PreAuthorize("@ss.hasPermi('todo:simulation:simulate')")
    @PostMapping("/simulations/published-diagnostics") public AjaxResult diagnosePublished(){return success(publishedDiagnostics.diagnose(actor()));}
    @PreAuthorize("@ss.hasPermi('todo:simulation:list')")
    @GetMapping("/business-objects") public TableDataInfo businessObjects(@Valid @ModelAttribute BusinessObjectQuery value){var page=query.businessObjects(value.businessType(),value.keyword(),value.pageNum(),value.pageSize(),actor());return new BusinessObjectTableDataInfo(page.rows(),page.total(),page.emptyReason(),page.sampleFallback());}

    @PreAuthorize("@ss.hasPermi('todo:release:list')")
    @GetMapping("/release-records") public TableDataInfo releases(@Valid @ModelAttribute ReleaseListQuery value){var page=query.releasePage(value.toMap());return new TableDataInfo(page.rows(),page.total());}
    @PreAuthorize("@ss.hasPermi('todo:release:list')")
    @GetMapping("/release-records/{id}") public AjaxResult release(@PathVariable long id){return success(query.release(id));}
    @PreAuthorize("@ss.hasPermi('todo:release:list')")
    @GetMapping("/release-records/{id}/versions") public AjaxResult releaseVersions(@PathVariable long id){return success(query.releaseVersions(id));}
    @PreAuthorize("@ss.hasAnyPermi('todo:release:list,todo:template:list,todo:template:create,todo:template:copy,todo:template:edit,todo:simulation:list,todo:release:publish')")
    @GetMapping("/templates/{id}/versions") public AjaxResult versions(@PathVariable Long id){return success(definitions.versions(id));}
    @PreAuthorize("@ss.hasPermi('todo:release:diff')")
    @GetMapping("/release-records/{left}/diff/{right}") public AjaxResult releaseDiff(@PathVariable long left,@PathVariable long right){return success(diff.diffImmutable(left,right));}
    @PreAuthorize("@ss.hasPermi('todo:template:copy')")
    @PostMapping("/release-records/{id}/copy-draft") public AjaxResult copyReleaseDraft(@PathVariable long id,@Valid @RequestBody ReleaseDraftCommand value){return success(definitions.copyReleaseDraft(id,value,actor()));}
    @PreAuthorize("@ss.hasPermi('todo:release:publish')")
    @PostMapping("/release-records/{id}/publish") public AjaxResult publish(@PathVariable Long id,@Valid @RequestBody PublishDraftCommand value){requireSame(id,value.versionId());return success(definitions.publish(value,actor()));}
    @PreAuthorize("@ss.hasPermi('todo:release:rollback')")
    @PostMapping("/release-records/{id}/rollback-draft") public AjaxResult rollbackDraft(@PathVariable Long id,@Valid @RequestBody ReleaseDraftCommand value){return success(definitions.rollbackReleaseDraft(id,value,actor()));}

    private TableDataInfo page(List<?> values,int pageNum,int pageSize)
    {
        long calculatedStart=((long)pageNum-1L)*pageSize;if(calculatedStart>=values.size())return new TableDataInfo(List.of(),values.size());int start=(int)calculatedStart;
        return new TableDataInfo(values.subList(start,Math.min(values.size(),start+pageSize)),values.size());
    }
    private void requireSame(Long path,Long body){if(path==null||body==null||!path.equals(body))throw new TodoException("TODO_CONFIGURATION_PATH_BODY_MISMATCH","Path and body identifiers must match");}
    private void requireNew(Long id){if(id!=null)throw new TodoException("TODO_CONFIGURATION_PATH_BODY_MISMATCH","Create requests must not include an identifier");}
    private Actor actor(){return new Actor(SecurityUtils.getUserId(),SecurityUtils.getUsername(),SecurityUtils.getDeptId());}

    public record PageQuery(@Min(1) Integer pageNum,@Min(1) @Max(500) Integer pageSize,String keyword)
    {public PageQuery(Integer pageNum,Integer pageSize){this(pageNum,pageSize,null);}public PageQuery{pageNum=pageNum==null?1:pageNum;pageSize=pageSize==null?20:pageSize;}}
    public record TemplateListQuery(String keyword,String businessType,String businessStage,String templateType,
            String publishStatus,String status,@Min(1) Integer pageNum,@Min(1) @Max(200) Integer pageSize)
    {public TemplateListQuery{pageNum=pageNum==null?1:pageNum;pageSize=pageSize==null?20:pageSize;}public Map<String,Object> toMap(){Map<String,Object> result=new LinkedHashMap<>();result.put("keyword",keyword);result.put("businessType",businessType);result.put("businessStage",businessStage);result.put("templateType",templateType);result.put("publishStatus",publishStatus);result.put("status",status);result.put("offset",(pageNum-1)*pageSize);result.put("limit",pageSize);return result;}}
    public record CapabilitySummary(String code,String description,boolean simulatable) { }
    public record TemplateRuleCatalogEntry(Long id,String ruleCode,String ruleName,String status,String ruleType,
            Object durationValue,String durationUnit,String calendarCode,String requiredFieldsJson,
            String requiredAttachmentsJson,String conditionalRulesJson) { }
    public record RuleListQuery(String status,String slaType,String ruleType,String keyword,LocalDateTime beginTime,LocalDateTime endTime,
            @Min(1) Integer pageNum,@Min(1) @Max(500) Integer pageSize)
    {public RuleListQuery{pageNum=pageNum==null?1:pageNum;pageSize=pageSize==null?20:pageSize;}public Map<String,Object> toMap(){Map<String,Object> result=new LinkedHashMap<>();result.put("status",status);result.put("slaType",slaType);result.put("ruleType",ruleType);result.put("keyword",keyword);result.put("beginTime",beginTime);result.put("endTime",endTime);return result;}}
    public record RuleCopyCommand(@NotBlank String newRuleCode,@NotBlank String actionId) { }
    public record RuleToggleCommand(@NotBlank String status,@NotBlank String actionId,@NotNull @Min(0) Integer expectedVersion) { }
    public record SlaTestCommand(@NotNull LocalDateTime createdAt) { }
    public record DodTestCommand(@NotNull Map<String,Object> payload,List<String> attachments) {public DodTestCommand{payload=payload==null?null:Map.copyOf(payload);attachments=attachments==null?List.of():List.copyOf(attachments);}}
    public record ReleaseListQuery(Long templateId,String templateCode,String keyword,String status,String publisher,LocalDateTime beginTime,LocalDateTime endTime,
            @Min(0) Integer offset,@Min(1) @Max(500) Integer limit)
    {public ReleaseListQuery{offset=offset==null?0:offset;limit=limit==null?20:limit;}public Map<String,Object> toMap(){Map<String,Object> result=new LinkedHashMap<>();result.put("templateId",templateId);result.put("templateCode",templateCode);result.put("keyword",keyword);result.put("status",status);result.put("publisher",publisher);result.put("beginTime",beginTime);result.put("endTime",endTime);result.put("offset",offset);result.put("limit",limit);return result;}}
    public record BusinessObjectQuery(@NotBlank String businessType,String keyword,@Min(1) Integer pageNum,@Min(1) @Max(100) Integer pageSize)
    {public BusinessObjectQuery{pageNum=pageNum==null?1:pageNum;pageSize=pageSize==null?20:pageSize;}}
    public record EventResourceListQuery(String keyword,String businessObjectType,String schemaStatus,String status,
            @Min(1) Integer pageNum,@Min(1) @Max(200) Integer pageSize)
    {public EventResourceListQuery{pageNum=pageNum==null?1:pageNum;pageSize=pageSize==null?20:pageSize;}
        public Map<String,Object> toMap(){Map<String,Object> result=new LinkedHashMap<>();result.put("keyword",keyword);
            result.put("businessObjectType",businessObjectType);result.put("schemaStatus",schemaStatus);result.put("status",status);
            result.put("offset",(pageNum-1)*pageSize);result.put("limit",pageSize);return result;}}
    public record ActionIdCommand(@NotBlank String actionId) { }

    public static final class BusinessObjectTableDataInfo extends TableDataInfo
    {
        private static final long serialVersionUID=1L;private final String emptyReason;private final boolean sampleFallback;
        public BusinessObjectTableDataInfo(List<?> rows,long total,String emptyReason,boolean sampleFallback)
        {super(rows,total);this.emptyReason=emptyReason;this.sampleFallback=sampleFallback;}
        public String getEmptyReason(){return emptyReason;}
        public boolean isSampleFallback(){return sampleFallback;}
    }
}
