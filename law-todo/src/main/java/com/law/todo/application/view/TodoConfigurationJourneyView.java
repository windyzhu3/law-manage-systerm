package com.law.todo.application.view;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.law.todo.application.TodoConfigurationResourceCatalogService.DodRecipeResource;
import com.law.todo.application.TodoConfigurationResourceCatalogService.FieldResource;
import com.law.todo.application.TodoConfigurationResourceCatalogService.MaterialResource;
import com.law.todo.application.TodoConfigurationResourceCatalogService.ValidatorResource;
import com.law.todo.application.view.TodoConfigurationViews.OwnerCatalogEntry;
import com.law.todo.application.view.TodoConfigurationViews.RoutingTargetCatalogEntry;
import com.law.todo.application.view.TodoResourceViews.EventResourceListItem;

public record TodoConfigurationJourneyView(
        TemplateSummary template,
        List<JourneyStep> steps,
        CurrentResources resources,
        EmployeeTodoPreview employeePreview,
        List<JourneyIssue> issues,
        JourneyPermissions permissions)
{
    public TodoConfigurationJourneyView
    {
        steps=steps==null?List.of():List.copyOf(steps);
        issues=issues==null?List.of():List.copyOf(issues);
    }
    public record TemplateSummary(long templateId,long versionId,int versionNo,int lockVersion,
            String templateCode,String templateName,String businessType,String businessStage,String publishStatus,
            String definitionHash) { }
    public record JourneyStep(String code,String title,String state,int issueCount,Map<String,Object> value) { }
    public record CurrentResources(List<EventResourceListItem> events,List<FieldResource> fields,
            List<OwnerCatalogEntry> owners,List<MaterialResource> materials,List<ValidatorResource> validators,
            List<DodRecipeResource> recipes,List<Map<String,Object>> calendars,
            List<RoutingTargetCatalogEntry> routingTargets) { }
    public record EmployeeTodoPreview(String title,String assigneeSummary,List<PreviewField> fields,
            List<PreviewMaterial> materials,List<String> completionInstructions,String dueSummary) { }
    public record PreviewField(String code,String label,String type,boolean required) { }
    public record PreviewMaterial(String code,String label,boolean required) { }
    public record JourneyIssue(String code,String severity,String stepCode,String fieldPath,
            String message,String repairAction) { }
    public record JourneyPermissions(boolean canView,boolean canEdit,boolean canMaintainResources,
            boolean canSimulate,boolean canPublish,boolean canAudit) { }
    public record TemplateWorkbenchItem(long templateId,String templateCode,String templateName,String businessType,
            String businessStage,String journeyState,int completedSteps,int totalSteps,int blockerCount,
            int warningCount,String lastEditor,LocalDateTime updateTime,String primaryAction)
    {
        public TemplateWorkbenchItem(long templateId,String templateName,String businessType,String businessStage,
                String journeyState,int completedSteps,int totalSteps,int blockerCount,int warningCount,
                String lastEditor,LocalDateTime updateTime,String primaryAction)
        {this(templateId,null,templateName,businessType,businessStage,journeyState,completedSteps,totalSteps,
                blockerCount,warningCount,lastEditor,updateTime,primaryAction);}
    }
    public record TemplateWorkbenchPage(List<TemplateWorkbenchItem> rows,long total,
            int blockerTemplates,int warningTemplates,int readyTemplates)
    {
        public TemplateWorkbenchPage { rows=rows==null?List.of():List.copyOf(rows); }
        public TemplateWorkbenchPage(List<TemplateWorkbenchItem> rows,long total,int blockerTemplates,int warningTemplates)
        {this(rows,total,blockerTemplates,warningTemplates,
                Math.max(0,(int)Math.min(Integer.MAX_VALUE,total)-blockerTemplates-warningTemplates));}
    }
}
