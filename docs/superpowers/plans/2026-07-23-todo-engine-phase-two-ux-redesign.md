# Todo Engine Phase Two High-Fidelity UX Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the approved seven-step, workflow-centered Todo Engine configuration experience so a business administrator can configure, simulate, and publish a simple template in ten minutes without editing JSON or internal codes.

**Architecture:** Keep the existing immutable template-version, event, Owner, DoD, SLA, routing, simulation, and Outbox runtime semantics. Add a page-oriented `TodoConfigurationJourneyService`, backend-derived step health and employee preview, governed resource metadata, actor-scoped payload hydration, and an explainable simulation/publish boundary. The Vue 2 client consumes one aggregate journey model, saves through the existing draft definition contract with optimistic locking, and keeps old routes and permission codes as compatibility entries.

**Tech Stack:** Java 17, Spring Boot, Jakarta Validation, MyBatis, Flyway, MySQL 8, Maven, Vue 2.6, Element UI 2.15, CommonJS model tests, Playwright 1.52 using the user-approved Chrome channel.

## Global Constraints

- Implement against branch `runtime/startup-wiring-fix` and preserve existing unrelated changes in `.superpowers/sdd/task-7-report.md`, `ruoyi-ui/vue.config.js`, `.playwright-cli/`, `.runtime-logs/`, and `output/`.
- Use the approved visual source `docs/superpowers/specs/assets/2026-07-23-todo-engine-phase-two-ux-selected.png` (1487×1058, SHA-256 `4A0319D2B894856E90C2C5B00F6A00B926074682300D84C29B6C00724DFD0FBE`) as the only visual target.
- Preserve published template immutability, current runtime event-consumption semantics, existing menu URLs, and existing permission codes.
- Do not rewrite the Todo Engine runtime, migrate to Vue 3, introduce microservices, or add an arbitrary drag-and-drop low-code canvas.
- Default business mode must not expose JSON, Java class names, database IDs, or technical codes; technical identifiers may appear only in secondary or advanced details.
- Configuration states are `NOT_STARTED`, `IN_PROGRESS`, `COMPLETED`, `WARNING`, and `BLOCKED`; every state must have icon, text, and color.
- Visual language: `#0B2A55` primary, `#C89A3D` restrained accent, white/cool-gray backgrounds, 4–10px radii, no large gradients, no nested decorative cards, and shadows only on floating layers.
- Autosave after field blur or structural action, visibly report `SAVING`, `SAVED`, or `FAILED`, retain local edits after failure, and warn on navigation only when unsaved changes exist.
- Server-side optimistic locking is authoritative; conflicts must show differences and allow refresh/merge or save-copy recovery.
- Blocking issues prevent publish; warnings require a non-blank publication explanation; publish always reruns full server preflight.
- Sample business objects are read-only and must never create runtime tasks, relations, or business writes.
- Sensitive payload fields must be redacted in UI responses, audit records, simulation records, and logs.
- Primary visual QA viewports are 1440×1024 and 1920×1080, with reference and implementation combined into one side-by-side comparison image at the same state.
- Completion requires backend tests, real MySQL Flyway/integration tests, frontend contract checks, production build, Chrome E2E, and `design-qa.md` ending exactly with `final result: passed` after all P0/P1/P2 differences are closed.

## File and Interface Map

Backend ownership:

- `TodoConfigurationJourneyService`: page aggregate orchestration only.
- `TodoConfigurationJourneyEvaluator`: seven-step state and issue calculation only.
- `TodoEmployeeTodoPreviewProjector`: employee-visible task projection only.
- `TodoPayloadSchemaDescriptor`: JSON Schema to business field descriptors only.
- `TodoDodRecipeMatcher`: contextual recipe ranking only.
- `TodoBusinessPayloadAccess`: actor-scoped business-object-to-payload SPI.
- `TodoJourneySimulationService`: hydration, manual overrides, simulation trace, and publish gate presentation only.
- Existing `TodoDefinitionService`: remains authoritative for draft persistence, preflight, immutable publish, and definition hashes.

Frontend ownership:

- `journey-model.js`: pure hydration, patching, navigation, save merge, and simulation command functions.
- `journey/index.vue`: shell/orchestration only; steps own only their section form state.
- `JourneyStepNav`: status/navigation only.
- `ConfigurationHealthPanel`: warnings/blockers and repair actions only.
- `EmployeeTodoPreview`: read-only employee-visible projection only.
- `ContextResourceDrawer`: contextual maintenance and return-to-editor only.
- `BusinessObjectPayloadEditor`: object search, source labels, manual overrides, and missing values only.
- `SimulationTrace`: ordered trace rendering only.
- `PublishPreflightPanel`: publish gate, reason, diff, and result only.

---

### Task 1: Journey Aggregate Contract and Page Service

**Files:**
- Create: `law-todo/src/main/java/com/law/todo/application/view/TodoConfigurationJourneyView.java`
- Create: `law-todo/src/main/java/com/law/todo/application/TodoConfigurationJourneyService.java`
- Create: `law-todo/src/main/java/com/law/todo/application/TodoConfigurationJourneyEvaluator.java`
- Create: `law-todo/src/main/java/com/law/todo/application/TodoEmployeeTodoPreviewProjector.java`
- Modify: `law-todo/src/main/java/com/law/todo/mapper/TodoConfigurationMapper.java`
- Modify: `law-todo/src/main/resources/mapper/todo/TodoConfigurationMapper.xml`
- Create: `law-todo/src/test/java/com/law/todo/application/TodoConfigurationJourneyServiceTest.java`

**Interfaces:**
- Consumes: `TodoConfigurationQueryService.template(Long)`, `TodoDefinitionCodec.read(String)`, `TodoConfigurationResourceCatalogService`, and `Actor(Long userId,String userName,Long deptId)`.
- Produces: `TodoConfigurationJourneyView load(long templateId, Actor actor)`, `TemplateWorkbenchPage workbench(Map<String,Object> query,Actor actor)`, and the stable step codes `EVENT`, `TRIGGER`, `OWNER`, `DOD`, `SLA`, `ROUTING`, `SIMULATION_PUBLISH`.

- [ ] **Step 1: Write the failing aggregate contract test**

```java
@Test
void loadsSevenOrderedStepsAndKeepsOptimisticLockVersion()
{
    when(query.template(42L)).thenReturn(fixture.templateDetail());
    TodoConfigurationJourneyView view=service.load(42L,new Actor(7L,"配置管理员",3L));
    assertThat(view.template().templateId()).isEqualTo(42L);
    assertThat(view.template().lockVersion()).isEqualTo(4);
    assertThat(view.steps()).extracting(TodoConfigurationJourneyView.JourneyStep::code)
            .containsExactly("EVENT","TRIGGER","OWNER","DOD","SLA","ROUTING","SIMULATION_PUBLISH");
    assertThat(view.permissions().canEdit()).isTrue();
}

@Test
void returnsWorkbenchProgressAndIssuesWithoutPerRowQueries()
{
    when(mapper.selectTemplateJourneySummaries(anyMap())).thenReturn(fixture.workbenchRows());
    TemplateWorkbenchPage page=service.workbench(Map.of("offset",0,"limit",20),actor);
    assertThat(page.rows()).extracting(TemplateWorkbenchItem::primaryAction)
            .containsExactly("CONTINUE_CONFIGURATION","VIEW_PUBLISHED");
    verify(mapper,times(1)).selectTemplateJourneySummaries(anyMap());
}
```

- [ ] **Step 2: Run the focused test and verify the missing-type failure**

Run: `mvn -pl law-todo -am -Dtest=TodoConfigurationJourneyServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because `TodoConfigurationJourneyView` and `TodoConfigurationJourneyService` do not exist.

- [ ] **Step 3: Add the immutable page contract**

```java
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
    public record TemplateWorkbenchItem(long templateId,String templateName,String businessType,
            String businessStage,String journeyState,int completedSteps,int totalSteps,int blockerCount,
            int warningCount,String lastEditor,LocalDateTime updateTime,String primaryAction) { }
    public record TemplateWorkbenchPage(List<TemplateWorkbenchItem> rows,long total,
            int blockerTemplates,int warningTemplates)
    {
        public TemplateWorkbenchPage { rows=rows==null?List.of():List.copyOf(rows); }
    }
}
```

- [ ] **Step 4: Implement page-service orchestration without duplicating engine rules**

```java
@Service
public class TodoConfigurationJourneyService
{
    private final TodoConfigurationQueryService query;
    private final TodoDefinitionCodec codec;
    private final TodoConfigurationJourneyEvaluator evaluator;
    private final TodoEmployeeTodoPreviewProjector preview;

    public TodoConfigurationJourneyView load(long templateId,Actor actor)
    {
        TemplateConfigurationDetail detail=query.template(templateId);
        TemplateVersionDetail version=Objects.requireNonNull(detail.editableVersion(),"editableVersion");
        TodoDefinitionDocument definition=codec.read(version.definitionJson());
        TodoConfigurationJourneyEvaluator.Evaluation evaluation=evaluator.evaluate(detail,definition);
        return new TodoConfigurationJourneyView(summary(detail,version),evaluation.steps(),
                resources(detail,definition),preview.project(detail,definition),evaluation.issues(),permissions(actor));
    }
}
```

Add `TodoConfigurationMapper.selectTemplateJourneySummaries(Map<String,Object>)` and `countTemplateJourneySummaries(Map<String,Object>)`. The XML must join each template to its editable draft once, return `definition_json`, `validation_report_json`, editor/time, and pagination fields in one query. `workbench` decodes each returned definition in memory and never calls `load` per row.

For this task, create minimal `TodoConfigurationJourneyEvaluator` and `TodoEmployeeTodoPreviewProjector` implementations that return the seven ordered `NOT_STARTED`/`IN_PROGRESS` steps and an empty preview. Task 2 replaces this baseline behavior with authoritative health and employee projections; the types exist now so Task 1 compiles independently.

- [ ] **Step 5: Run the test and commit the contract**

Run: `mvn -pl law-todo -am -Dtest=TodoConfigurationJourneyServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS with one journey aggregate test.

```bash
git add law-todo/src/main/java/com/law/todo/application/view/TodoConfigurationJourneyView.java law-todo/src/main/java/com/law/todo/application/TodoConfigurationJourneyService.java law-todo/src/main/java/com/law/todo/application/TodoConfigurationJourneyEvaluator.java law-todo/src/main/java/com/law/todo/application/TodoEmployeeTodoPreviewProjector.java law-todo/src/main/java/com/law/todo/mapper/TodoConfigurationMapper.java law-todo/src/main/resources/mapper/todo/TodoConfigurationMapper.xml law-todo/src/test/java/com/law/todo/application/TodoConfigurationJourneyServiceTest.java
git commit -m "feat(todo-config): add journey aggregate contract"
```

---

### Task 2: Seven-Step Health Evaluator and Employee Preview

**Files:**
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoConfigurationJourneyEvaluator.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoEmployeeTodoPreviewProjector.java`
- Create: `law-todo/src/test/java/com/law/todo/application/TodoConfigurationJourneyEvaluatorTest.java`
- Create: `law-todo/src/test/java/com/law/todo/application/TodoEmployeeTodoPreviewProjectorTest.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoConfigurationJourneyService.java`

**Interfaces:**
- Consumes: `TemplateConfigurationDetail`, `TodoDefinitionDocument`, governed catalogs, and the records from Task 1.
- Produces: `Evaluation evaluate(TemplateConfigurationDetail,TodoDefinitionDocument)`, `EmployeeTodoPreview project(TemplateConfigurationDetail,TodoDefinitionDocument)`, and stable issue codes prefixed `TODO_JOURNEY_`.

- [ ] **Step 1: Write failing evaluator and preview tests**

```java
@Test
void blocksEventStepWhenActiveEventHasNoUsableSchema()
{
    Evaluation result=evaluator.evaluate(fixture.detail(),fixture.definition("LEAD_ASSIGNED",Map.of()));
    assertThat(result.step("EVENT").state()).isEqualTo("BLOCKED");
    assertThat(result.issues()).extracting(JourneyIssue::code)
            .contains("TODO_JOURNEY_EVENT_SCHEMA_REQUIRED");
}

@Test
void previewUsesBusinessLabelsInsteadOfTechnicalCodes()
{
    EmployeeTodoPreview view=projector.project(fixture.detail(),fixture.definitionWithDod());
    assertThat(view.fields()).extracting(PreviewField::label).contains("联系时间","跟进结果");
    assertThat(view.completionInstructions()).contains("记录联系时间并填写跟进结果");
}
```

- [ ] **Step 2: Run the focused tests and verify they fail**

Run: `mvn -pl law-todo -am -Dtest=TodoConfigurationJourneyEvaluatorTest,TodoEmployeeTodoPreviewProjectorTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because evaluator/projector classes do not exist.

- [ ] **Step 3: Implement deterministic state precedence**

```java
public final class TodoConfigurationJourneyEvaluator
{
    public enum JourneyState
    {
        NOT_STARTED,IN_PROGRESS,COMPLETED,WARNING,BLOCKED;
        static JourneyState from(List<JourneyIssue> issues,boolean started,boolean complete)
        {
            if(issues.stream().anyMatch(issue->"BLOCKER".equals(issue.severity())))return BLOCKED;
            if(issues.stream().anyMatch(issue->"WARNING".equals(issue.severity())))return WARNING;
            if(complete)return COMPLETED;
            return started?IN_PROGRESS:NOT_STARTED;
        }
    }

    public record Evaluation(List<JourneyStep> steps,List<JourneyIssue> issues)
    {
        public Evaluation
        {
            steps=steps==null?List.of():List.copyOf(steps);
            issues=issues==null?List.of():List.copyOf(issues);
        }
        public JourneyStep step(String code)
        {
            return steps.stream().filter(step->step.code().equals(code)).findFirst()
                    .orElseThrow(()->new IllegalArgumentException("Unknown journey step: "+code));
        }
    }
}
```

Define each step evaluator as a private focused method and apply the same precedence. An unresolved Owner with no fallback, missing event schema, unusable calendar, invalid route, or missing successful simulation must emit a blocker. Optional recommendations emit warnings.

- [ ] **Step 4: Implement preview projection from the same definition snapshot**

```java
public EmployeeTodoPreview project(TemplateConfigurationDetail detail,TodoDefinitionDocument definition)
{
    Map<String,Object> ui=definition.ui().config();
    Set<String> requiredFields=Set.copyOf(strings(definition.dod().config().get("requiredFields")));
    Set<String> requiredMaterials=Set.copyOf(strings(definition.dod().config().get("requiredAttachments")));
    List<PreviewField> fields=resources.fields(detail.businessType()).stream()
            .filter(field->requiredFields.contains(field.code()))
            .map(field->new PreviewField(field.code(),field.name(),field.type(),true)).toList();
    List<PreviewMaterial> materials=resources.materials(detail.businessType()).stream()
            .filter(material->requiredMaterials.contains(material.code()))
            .map(material->new PreviewMaterial(material.code(),material.name(),true)).toList();
    return new EmployeeTodoPreview(
            display(ui,"employeeTitle",detail.templateName()),
            describeOwner(definition.owner().config()),fields,materials,
            strings(definition.dod().config().get("employeeInstructions")),
            describeSla(definition.sla().config()));
}
```

`TodoEmployeeTodoPreviewProjector` injects only `TodoConfigurationResourceCatalogService`. Its private helpers have exact signatures `List<String> strings(Object value)`, `String display(Map<String,Object> value,String key,String fallback)`, `String describeOwner(Map<String,Object> owner)`, and `String describeSla(Map<String,Object> sla)`.

- [ ] **Step 5: Run tests and commit evaluator/preview**

Run: `mvn -pl law-todo -am -Dtest=TodoConfigurationJourneyEvaluatorTest,TodoEmployeeTodoPreviewProjectorTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS; issue order is step order then blocker-before-warning.

```bash
git add law-todo/src/main/java/com/law/todo/application/TodoConfigurationJourneyEvaluator.java law-todo/src/main/java/com/law/todo/application/TodoEmployeeTodoPreviewProjector.java law-todo/src/main/java/com/law/todo/application/TodoConfigurationJourneyService.java law-todo/src/test/java/com/law/todo/application/TodoConfigurationJourneyEvaluatorTest.java law-todo/src/test/java/com/law/todo/application/TodoEmployeeTodoPreviewProjectorTest.java
git commit -m "feat(todo-config): evaluate journey health and preview"
```

---

### Task 3: Payload Schema Descriptor and Context Resource Model

**Files:**
- Create: `law-todo/src/main/java/com/law/todo/application/TodoPayloadSchemaDescriptor.java`
- Create: `law-todo/src/test/java/com/law/todo/application/TodoPayloadSchemaDescriptorTest.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoConfigurationResourceCatalogService.java`
- Modify: `law-todo/src/test/java/com/law/todo/application/TodoConfigurationResourceCatalogServiceTest.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoConfigurationJourneyService.java`

**Interfaces:**
- Consumes: active `todo_event_catalog.payload_schema_json`, configured field metadata, and event type/payload version.
- Produces: `List<PayloadFieldDescriptor> describe(String schemaJson,String sourceObject)` with `path,label,type,required,example,sourceObject,sensitive,operators,options`.

- [ ] **Step 1: Write a failing nested-schema descriptor test**

```java
@Test
void flattensTwoLevelsAndPreservesLabelsExamplesSensitivityAndOptions()
{
    List<PayloadFieldDescriptor> fields=descriptor.describe("""
      {"type":"object","required":["ownerId"],"properties":{
        "ownerId":{"type":"integer","title":"负责人","examples":[7]},
        "customer":{"type":"object","properties":{
          "mobile":{"type":"string","title":"手机号","x-sensitive":true},
          "level":{"type":"string","title":"客户等级","enum":["A","B"]}
        }}
      }}""","LEAD");
    assertThat(fields).extracting(PayloadFieldDescriptor::path)
            .containsExactly("ownerId","customer.mobile","customer.level");
    assertThat(fields.get(1).sensitive()).isTrue();
    assertThat(fields.get(2).options()).containsExactly("A","B");
}
```

- [ ] **Step 2: Run the descriptor test and verify failure**

Run: `mvn -pl law-todo -am -Dtest=TodoPayloadSchemaDescriptorTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because `TodoPayloadSchemaDescriptor` is missing.

- [ ] **Step 3: Implement bounded schema flattening and typed operators**

```java
public record PayloadFieldDescriptor(String path,String label,String type,boolean required,
        Object example,String sourceObject,boolean sensitive,List<String> operators,List<Object> options) { }

private List<String> operators(String type)
{
    return switch(type)
    {
        case "integer","number" -> List.of("EQ","NE","GT","GTE","LT","LTE","IN","NOT_IN","PRESENT");
        case "boolean" -> List.of("EQ","NE","PRESENT");
        default -> List.of("EQ","NE","IN","NOT_IN","CONTAINS","PRESENT");
    };
}
```

Reject invalid JSON with `TODO_EVENT_SCHEMA_INVALID`; return an empty list only when the stored schema is blank and let the evaluator emit `TODO_JOURNEY_EVENT_SCHEMA_REQUIRED`.

- [ ] **Step 4: Replace the old minimal field record with the descriptor**

`TodoConfigurationResourceCatalogService.fields(businessType)` must merge governed field rows with active event schemas by path, prefer governed Chinese names, union source events, and never leak sensitive examples.

```java
public record FieldResource(String code,String name,String type,boolean required,Object example,
        boolean sensitive,List<String> operators,List<String> sourceEvents,List<Object> options) { }
```

- [ ] **Step 5: Run resource tests and commit schema descriptors**

Run: `mvn -pl law-todo -am -Dtest=TodoPayloadSchemaDescriptorTest,TodoConfigurationResourceCatalogServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS, including blank-schema and sensitive-field cases.

```bash
git add law-todo/src/main/java/com/law/todo/application/TodoPayloadSchemaDescriptor.java law-todo/src/main/java/com/law/todo/application/TodoConfigurationResourceCatalogService.java law-todo/src/main/java/com/law/todo/application/TodoConfigurationJourneyService.java law-todo/src/test/java/com/law/todo/application/TodoPayloadSchemaDescriptorTest.java law-todo/src/test/java/com/law/todo/application/TodoConfigurationResourceCatalogServiceTest.java
git commit -m "feat(todo-config): describe payload fields for business editors"
```

---

### Task 4: Contextual DoD Recipes and Journey Metadata Migration

**Files:**
- Create: `law-todo/src/main/java/com/law/todo/application/TodoDodRecipeMatcher.java`
- Create: `law-todo/src/main/java/com/law/todo/application/TodoConfigurationResourceManagementService.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/command/TodoConfigurationCommands.java`
- Modify: `law-todo/src/main/java/com/law/todo/mapper/TodoConfigurationMapper.java`
- Modify: `law-todo/src/main/resources/mapper/todo/TodoConfigurationMapper.xml`
- Create: `law-todo/src/test/java/com/law/todo/application/TodoDodRecipeMatcherTest.java`
- Create: `law-todo/src/test/java/com/law/todo/application/TodoConfigurationResourceManagementServiceTest.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoConfigurationResourceCatalogService.java`
- Create: `ruoyi-admin/src/main/resources/db/migration/V0_20_42__todo_phase_two_journey_metadata.sql`
- Create: `ruoyi-admin/src/test/java/com/ruoyi/web/migration/TodoPhaseTwoJourneyMetadataMigrationContractTest.java`

**Interfaces:**
- Consumes: existing `todo_configuration_resource_item` rows of type `DOD_RECIPE`; no second recipe table.
- Produces: `List<DodRecipeResource> match(List<DodRecipeResource>,String businessType,String businessAction,String templateStage)` ordered by contextual score and governed CRUD `save(ConfigurationResourceCommand,Actor)` for `FIELD`, `MATERIAL`, and `DOD_RECIPE` rows.

- [ ] **Step 1: Write the failing recipe ranking test**

```java
@Test
void ranksExactActionThenStageThenConfiguredPriority()
{
    List<DodRecipeResource> result=matcher.match(fixture.recipes(),"LEAD","FIRST_CONTACT","LEAD_FOLLOWUP");
    assertThat(result).extracting(DodRecipeResource::code)
            .containsExactly("LEAD_FIRST_CONTACT","LEAD_GENERIC","GLOBAL_GENERIC");
    assertThat(result.get(0).employeeInstructions()).contains("记录联系时间");
}
```

- [ ] **Step 2: Run the test and verify failure**

Run: `mvn -pl law-todo -am -Dtest=TodoDodRecipeMatcherTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because contextual recipe fields and matcher are absent.

- [ ] **Step 3: Extend the governed recipe record and matcher**

```java
public record DodRecipeResource(String code,String name,String description,String businessType,
        List<String> businessActions,List<String> templateStages,int recommendationPriority,
        List<String> requiredFields,List<String> requiredAttachments,List<String> validatorRefs,
        List<Map<String,Object>> conditionalRules,List<String> employeeInstructions) { }

private int score(DodRecipeResource recipe,String action,String stage)
{
    int score=recipe.businessActions().isEmpty()?0:recipe.businessActions().contains(action)?100:-1000;
    score+=recipe.templateStages().isEmpty()?0:recipe.templateStages().contains(stage)?20:-1000;
    return score+recipe.recommendationPriority();
}
```

Filter scores below zero. Empty action/stage lists are wildcards. Resolve ties by recipe name then code.

- [ ] **Step 4: Add optimistic resource-item maintenance**

First add this failing service test and rerun the Task 4 focused Maven command:

```java
@Test
void rejectsRecipeThatReferencesUnknownBusinessField()
{
    ConfigurationResourceCommand command=fixture.recipe("{\"requiredFields\":[\"unknownField\"],\"requiredAttachments\":[],\"validatorRefs\":[],\"conditionalRules\":[]}");
    assertThatThrownBy(()->resources.save(command,actor))
            .hasMessageContaining("TODO_CONFIGURATION_RESOURCE_REFERENCE_UNKNOWN");
}
```

```java
public record ConfigurationResourceCommand(Long resourceItemId,
        @NotBlank @Pattern(regexp="FIELD|MATERIAL|DOD_RECIPE") String resourceType,
        @NotBlank String resourceCode,@NotBlank String resourceName,String description,
        @NotBlank @Pattern(regexp="LEAD|CUSTOMER|CONTRACT|CASE|MATTER") String businessType,
        @NotBlank String valueJson,@NotBlank @Pattern(regexp="ACTIVE|DISABLED") String status,
        @NotNull @PositiveOrZero Integer sortOrder,@NotBlank String actionId,
        @NotNull @PositiveOrZero Integer expectedVersion)
{
    @AssertTrue(message="Configuration resource value must be a JSON object")
    public boolean isValueJsonValid(){return JSON.isValidObject(valueJson);}
}
```

`TodoConfigurationResourceManagementService.save` validates type-specific JSON keys, rejects unknown field/material/validator references in recipes, claims `actionId`, inserts or conditionally updates by `expectedVersion`, and emits `TODO_CONFIGURATION_RESOURCE_VERSION_CONFLICT` when another editor wins. Add exact mapper methods `selectConfigurationResourceItem(long)`, `insertConfigurationResourceItem(Map<String,Object>)`, and `updateConfigurationResourceItemConditionally(Map<String,Object>)`.

- [ ] **Step 5: Seed business-readable recipe metadata in Flyway**

The migration must update existing `DOD_RECIPE` JSON with these keys and seed at least the five v0.2 business families (`LEAD`, `CUSTOMER`, `CONTRACT`, `CASE`, `MATTER`). The lead first-contact resource must contain this canonical shape:

```json
{
  "businessActions":["FIRST_CONTACT"],
  "templateStages":["LEAD_FOLLOWUP"],
  "recommendationPriority":100,
  "requiredFields":["contactedAt","contactResult"],
  "requiredAttachments":[],
  "conditionalRules":[],
  "validatorRefs":[],
  "employeeInstructions":["记录联系时间并填写跟进结果"]
}
```

Use idempotent MySQL `ON DUPLICATE KEY UPDATE` upserts and preserve resource codes already referenced by drafts.

- [ ] **Step 6: Verify matcher, resource writes, and migration contract, then commit**

Run: `mvn -pl law-todo -am -Dtest=TodoDodRecipeMatcherTest,TodoConfigurationResourceCatalogServiceTest,TodoConfigurationResourceManagementServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS.

Run: `mvn -pl ruoyi-admin -am -Dtest=TodoPhaseTwoJourneyMetadataMigrationContractTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS after verifying the migration is idempotent, preserves existing resource codes, and contains metadata for all five business types. Real MySQL application is covered by Task 14.

```bash
git add law-todo/src/main/java/com/law/todo/application/TodoDodRecipeMatcher.java law-todo/src/main/java/com/law/todo/application/TodoConfigurationResourceCatalogService.java law-todo/src/main/java/com/law/todo/application/TodoConfigurationResourceManagementService.java law-todo/src/main/java/com/law/todo/application/command/TodoConfigurationCommands.java law-todo/src/main/java/com/law/todo/mapper/TodoConfigurationMapper.java law-todo/src/main/resources/mapper/todo/TodoConfigurationMapper.xml law-todo/src/test/java/com/law/todo/application/TodoDodRecipeMatcherTest.java law-todo/src/test/java/com/law/todo/application/TodoConfigurationResourceManagementServiceTest.java ruoyi-admin/src/main/resources/db/migration/V0_20_42__todo_phase_two_journey_metadata.sql ruoyi-admin/src/test/java/com/ruoyi/web/migration/TodoPhaseTwoJourneyMetadataMigrationContractTest.java
git commit -m "feat(todo-config): recommend contextual dod recipes"
```

---

### Task 5: Actor-Scoped Business Payload Hydration with Provenance

**Files:**
- Create: `law-todo/src/main/java/com/law/todo/spi/TodoBusinessPayloadAccess.java`
- Create: `law-todo/src/main/java/com/law/todo/application/TodoBusinessPayloadHydrationService.java`
- Create: `law-todo/src/test/java/com/law/todo/application/TodoBusinessPayloadHydrationServiceTest.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/todo/RuoYiTodoBusinessPayloadAccess.java`
- Create: `ruoyi-system/src/test/java/com/ruoyi/system/service/todo/RuoYiTodoBusinessPayloadAccessTest.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/mapper/TodoBusinessDirectoryMapper.java`
- Modify: `ruoyi-system/src/main/resources/mapper/system/TodoBusinessDirectoryMapper.xml`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoSimulationSampleCatalog.java`

**Interfaces:**
- Consumes: event type/version, business type/id, `Actor`, payload field descriptors, existing data-scope SQL, and read-only event samples.
- Produces: `PayloadHydration hydrate(String eventType,int payloadVersion,String businessType,long businessId,Actor actor)`, `List<DataSourceStatus> dataSources()`, and field source values `BUSINESS_OBJECT`, `EVENT_SAMPLE`, `SYSTEM_DEFAULT`, `MANUAL_OVERRIDE`, or `MISSING`.

- [ ] **Step 1: Write failing hydration tests for real and sample objects**

```java
@Test
void hydratesRequiredFieldsAndMarksMissingValues()
{
    PayloadHydration result=service.hydrate("LEAD_ASSIGNED",1,"LEAD",81L,actor);
    assertThat(result.payload()).containsEntry("leadId",81L).containsEntry("ownerId",7L);
    assertThat(result.fields()).extracting(PayloadFieldSource::source)
            .contains("BUSINESS_OBJECT","MISSING");
}

@Test
void sampleObjectUsesEventSampleAndRemainsReadOnly()
{
    PayloadHydration result=service.hydrate("LEAD_ASSIGNED",1,"LEAD",-1001L,actor);
    assertThat(result.sample()).isTrue();
    assertThat(result.fields()).allMatch(field->!"BUSINESS_OBJECT".equals(field.source()));
}
```

- [ ] **Step 2: Run focused tests and verify missing SPI failure**

Run: `mvn -pl law-todo -am -Dtest=TodoBusinessPayloadHydrationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because `TodoBusinessPayloadAccess` is missing.

- [ ] **Step 3: Add the hydration SPI and merge contract**

```java
public interface TodoBusinessPayloadAccess
{
    boolean supports(String businessType);
    PayloadHydration hydrate(String eventType,int payloadVersion,String businessType,long businessId,Actor actor);

    record PayloadHydration(Map<String,Object> payload,List<PayloadFieldSource> fields,boolean sample)
    {
        public PayloadHydration
        {
            payload=payload==null?Map.of():Map.copyOf(payload);
            fields=fields==null?List.of():List.copyOf(fields);
        }
    }
    record PayloadFieldSource(String path,Object value,String source,boolean required,
            boolean missing,String missingReason,boolean sensitive) { }
    record DataSourceStatus(String businessType,boolean directoryAvailable,boolean payloadAvailable,
            boolean sampleAvailable,String status,String message) { }
}
```

The application service selects exactly one supporting adapter, overlays explicit manual values last, redacts sensitive response values, and calculates required-field coverage as `presentRequired / totalRequired`.

- [ ] **Step 4: Implement five actor-scoped SQL mappings**

`RuoYiTodoBusinessPayloadAccess` must call `TodoBusinessDirectoryMapper.selectVisibleBusinessPayload(query)` using the same actor/data-scope parameters as `RuoYiTodoBusinessDirectoryAccess`. The MyBatis XML must have explicit branches for `LEAD`, `CUSTOMER`, `CONTRACT`, `CASE`, and `MATTER`, returning stable aliases such as `business_id`, `business_no`, `business_name`, `owner_id`, `dept_id`, and the event-specific values already present in each domain table.

```java
@Override
public PayloadHydration hydrate(String eventType,int payloadVersion,String businessType,long businessId,Actor actor)
{
    Map<String,Object> row=mapper.selectVisibleBusinessPayload(query(businessType,businessId,actor));
    if(row==null||row.isEmpty())throw new TodoException("TODO_SIMULATION_BUSINESS_OBJECT_NOT_FOUND",
            "Simulation business object does not exist or is not accessible");
    Map<String,Object> payload=payloadFor(eventType,payloadVersion,businessType,row);
    return new PayloadHydration(payload,fieldSources(eventType,payloadVersion,businessType,payload),false);
}
```

Use private methods with exact signatures `Map<String,Object> payloadFor(String eventType,int payloadVersion,String businessType,Map<String,Object> row)` and `List<PayloadFieldSource> fieldSources(String eventType,int payloadVersion,String businessType,Map<String,Object> payload)`. Each uses an exhaustive `switch` on the five supported business types and throws `TODO_SIMULATION_PAYLOAD_MAPPING_UNSUPPORTED` for an unsupported event/type/version tuple.

- [ ] **Step 5: Run law-todo and ruoyi-system tests, then commit**

Run: `mvn -pl law-todo -am -Dtest=TodoBusinessPayloadHydrationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS with coverage and provenance assertions.

Run: `mvn -pl ruoyi-system -am -Dtest=RuoYiTodoBusinessPayloadAccessTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS for all five business types and a no-permission case.

```bash
git add law-todo/src/main/java/com/law/todo/spi/TodoBusinessPayloadAccess.java law-todo/src/main/java/com/law/todo/application/TodoBusinessPayloadHydrationService.java law-todo/src/test/java/com/law/todo/application/TodoBusinessPayloadHydrationServiceTest.java law-todo/src/main/java/com/law/todo/application/TodoSimulationSampleCatalog.java ruoyi-system/src/main/java/com/ruoyi/system/service/todo/RuoYiTodoBusinessPayloadAccess.java ruoyi-system/src/test/java/com/ruoyi/system/service/todo/RuoYiTodoBusinessPayloadAccessTest.java ruoyi-system/src/main/java/com/ruoyi/system/mapper/TodoBusinessDirectoryMapper.java ruoyi-system/src/main/resources/mapper/system/TodoBusinessDirectoryMapper.xml
git commit -m "feat(todo-config): hydrate simulation payloads from business data"
```

---

### Task 6: Explainable Journey Simulation and Publish Gates

**Files:**
- Create: `law-todo/src/main/java/com/law/todo/application/TodoJourneySimulationService.java`
- Create: `law-todo/src/main/java/com/law/todo/application/view/TodoJourneySimulationResult.java`
- Create: `law-todo/src/test/java/com/law/todo/application/TodoJourneySimulationServiceTest.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoConfigurationSimulationService.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoDefinitionService.java`
- Modify: `law-todo/src/test/java/com/law/todo/application/TodoDefinitionServiceTest.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/command/TodoConfigurationCommands.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/command/TodoDefinitionCommands.java`

**Interfaces:**
- Consumes: Task 5 hydration, `TodoConfigurationSimulationService.simulate`, Task 2 journey issues, existing `TodoSimulationView`, and immutable definition hashes.
- Produces: `TodoJourneySimulationResult simulate(JourneySimulationCommand,Actor)`, `JourneyPayloadCommand`, `JourneySimulationCommand`, and publish rule `warningReason` required when warnings exist.

- [ ] **Step 1: Write failing trace and publish-gate tests**

```java
@Test
void returnsHydratedPayloadCoverageAndOrderedTrace()
{
    TodoJourneySimulationResult result=service.simulate(fixture.command(),actor);
    assertThat(result.payload().coveragePercent()).isGreaterThanOrEqualTo(90);
    assertThat(result.trace()).extracting(TraceSection::code)
            .containsExactly("EVENT","OWNER","DOD","SLA","ROUTING","TODO_PREVIEW");
}

@Test
void requiresReasonForWarningsAndRejectsBlockers()
{
    assertThatThrownBy(()->definitions.publish(fixture.publishWithBlocker(),actor))
            .hasMessageContaining("TODO_PUBLISH_BLOCKED");
    assertThatThrownBy(()->definitions.publish(fixture.publishWithWarningAndBlankReason(),actor))
            .hasMessageContaining("TODO_PUBLISH_WARNING_REASON_REQUIRED");
}
```

- [ ] **Step 2: Run the tests and verify failure**

Run: `mvn -pl law-todo -am -Dtest=TodoJourneySimulationServiceTest,TodoDefinitionServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because journey simulation result and warning reason are absent.

- [ ] **Step 3: Add the page-oriented simulation result without breaking the old endpoint**

```java
public record TodoJourneySimulationResult(
        HydratedPayload payload,
        TodoSimulationView engine,
        List<TraceSection> trace,
        EmployeeTodoPreview employeePreview,
        List<JourneyIssue> issues,
        boolean publishEligible)
{
    public record HydratedPayload(Map<String,Object> values,List<PayloadFieldSource> fields,int coveragePercent) { }
    public record TraceSection(String code,String title,String status,String summary,List<TraceDetail> details) { }
    public record TraceDetail(String label,String value,String source,String status) { }
}
```

Keep `POST /todo/config/simulations` and `ConfigurationSimulationResult` unchanged. The new journey endpoint will call `TodoJourneySimulationService` and receive the expanded view.

Add explicit page commands. A negative business ID is valid only for a read-only sample; zero is always invalid.

```java
public record JourneyPayloadCommand(@NotNull @Positive Long templateId,@NotNull @Positive Long versionId,
        @NotBlank String eventType,@NotNull @Positive Integer payloadVersion,@NotBlank String businessType,
        @NotNull Long businessId,Map<String,Object> manualOverrides,@NotBlank String expectedDefinitionHash)
{
    @AssertTrue(message="Simulation business ID must not be zero")
    public boolean isBusinessIdValid(){return businessId!=null&&businessId.longValue()!=0L;}
}

public record JourneySimulationCommand(@NotNull @Positive Long templateId,@NotNull @Positive Long versionId,
        @NotBlank String eventType,@NotNull @Positive Integer payloadVersion,@NotBlank String businessType,
        @NotNull Long businessId,Map<String,Object> manualOverrides,@NotNull LocalDateTime effectiveAt,
        List<@Valid TodoDefinitionCommands.VirtualTaskCompletionSample> taskCompletions,
        @NotBlank String expectedDefinitionHash)
{
    @AssertTrue(message="Simulation business ID must not be zero")
    public boolean isBusinessIdValid(){return businessId!=null&&businessId.longValue()!=0L;}
}
```

- [ ] **Step 4: Extend publish command and rerun full preflight server-side**

```java
public record PublishDraftCommand(@NotBlank String actionId,@NotNull @Positive Long versionId,
        String expectedDefinitionHash,String warningReason)
{
    public PublishDraftCommand(String actionId,Long versionId){this(actionId,versionId,null,null);}
    public PublishDraftCommand(String actionId,Long versionId,String expectedDefinitionHash)
    {this(actionId,versionId,expectedDefinitionHash,null);}
}

private void assertPublishable(DefinitionValidationReport report,String warningReason)
{
    if(!report.errors().isEmpty())throw new TodoException("TODO_PUBLISH_BLOCKED","Resolve blocking configuration issues before publishing");
    if(!report.warnings().isEmpty()&&(warningReason==null||warningReason.isBlank()))
        throw new TodoException("TODO_PUBLISH_WARNING_REASON_REQUIRED","Publication explanation is required for warnings");
}
```

The publish transaction must persist the full preflight report before creating the immutable snapshot and must not leave a half-published version on failure.

- [ ] **Step 5: Run tests and commit simulation/publish gates**

Run: `mvn -pl law-todo -am -Dtest=TodoJourneySimulationServiceTest,TodoDefinitionServiceTest,TodoConfigurationSimulationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS; legacy simulation compatibility test remains green.

```bash
git add law-todo/src/main/java/com/law/todo/application/TodoJourneySimulationService.java law-todo/src/main/java/com/law/todo/application/view/TodoJourneySimulationResult.java law-todo/src/main/java/com/law/todo/application/TodoConfigurationSimulationService.java law-todo/src/main/java/com/law/todo/application/TodoDefinitionService.java law-todo/src/main/java/com/law/todo/application/command/TodoConfigurationCommands.java law-todo/src/main/java/com/law/todo/application/command/TodoDefinitionCommands.java law-todo/src/test/java/com/law/todo/application/TodoJourneySimulationServiceTest.java law-todo/src/test/java/com/law/todo/application/TodoDefinitionServiceTest.java
git commit -m "feat(todo-config): explain simulation and enforce publish gates"
```

---

### Task 7: Journey HTTP API, Security, and Compatibility Navigation

**Files:**
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/todo/TodoConfigurationController.java`
- Create: `ruoyi-admin/src/test/java/com/ruoyi/web/controller/todo/TodoConfigurationControllerJourneyTest.java`
- Create: `ruoyi-admin/src/main/resources/db/migration/V0_20_43__todo_phase_two_journey_navigation.sql`
- Modify: `ruoyi-ui/src/api/todo-config.js`
- Modify: `ruoyi-ui/src/api/todo-resources.js`

**Interfaces:**
- Consumes: `TodoConfigurationJourneyService`, `TodoBusinessPayloadHydrationService`, `TodoJourneySimulationService`, and `TodoConfigurationResourceManagementService`.
- Produces: `GET /todo/config/templates/workbench`, `GET /todo/config/templates/{id}/journey`, `POST /todo/config/templates/{id}/journey/payload`, `POST /todo/config/templates/{id}/journey/simulate`, `POST/PUT /todo/config/resources/items`, and `GET /todo/config/resources/data-sources`.

- [ ] **Step 1: Write failing MVC contract and permission tests**

```java
@Test
@WithMockUser(authorities="todo:template:list")
void returnsJourneyAggregate() throws Exception
{
    mvc.perform(get("/todo/config/templates/42/journey"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.steps.length()").value(7));
}

@Test
@WithMockUser(authorities="todo:template:list")
void returnsTaskCenteredWorkbenchPage() throws Exception
{
    mvc.perform(get("/todo/config/templates/workbench").param("pageNum","1").param("pageSize","20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rows[0].completedSteps").isNumber())
            .andExpect(jsonPath("$.rows[0].blockerCount").isNumber());
}

@Test
@WithMockUser(authorities="todo:template:list")
void deniesSimulationWithoutSimulationPermission() throws Exception
{
    mvc.perform(post("/todo/config/templates/42/journey/simulate")
            .contentType(APPLICATION_JSON).content(fixture.simulationJson()))
            .andExpect(status().isForbidden());
}
```

- [ ] **Step 2: Run MVC tests and verify 404/missing-handler failure**

Run: `mvn -pl ruoyi-admin -am -Dtest=TodoConfigurationControllerJourneyTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because journey mappings are absent.

- [ ] **Step 3: Add controller endpoints with existing permission codes**

```java
@PreAuthorize("@ss.hasAnyPermi('todo:template:list,todo:template:edit,todo:simulation:simulate,todo:release:publish')")
@GetMapping("/templates/{id}/journey")
public AjaxResult journey(@PathVariable long id){return success(journeys.load(id,actor()));}

@PreAuthorize("@ss.hasAnyPermi('todo:template:list,todo:template:edit,todo:simulation:simulate,todo:release:publish')")
@GetMapping("/templates/workbench")
public TableDataInfo journeyWorkbench(@Valid @ModelAttribute TemplateListQuery value)
{
    var page=journeys.workbench(value.toMap(),actor());
    return new TableDataInfo(page.rows(),page.total());
}

@PreAuthorize("@ss.hasPermi('todo:simulation:simulate')")
@PostMapping("/templates/{id}/journey/payload")
public AjaxResult journeyPayload(@PathVariable long id,@Valid @RequestBody JourneyPayloadCommand command)
{requireSame(id,command.templateId());return success(payloads.hydrate(command,actor()));}

@PreAuthorize("@ss.hasPermi('todo:simulation:simulate')")
@PostMapping("/templates/{id}/journey/simulate")
public AjaxResult journeySimulation(@PathVariable long id,@Valid @RequestBody JourneySimulationCommand command)
{requireSame(id,command.templateId());return success(journeySimulation.simulate(command,actor()));}

@PreAuthorize("@ss.hasAnyPermi('todo:resource:add,todo:resource:edit')")
@PostMapping("/resources/items")
public AjaxResult createResourceItem(@Valid @RequestBody ConfigurationResourceCommand command)
{requireNew(command.resourceItemId());return success(resourceManagement.save(command,actor()));}

@PreAuthorize("@ss.hasPermi('todo:resource:edit')")
@PutMapping("/resources/items/{id}")
public AjaxResult updateResourceItem(@PathVariable long id,@Valid @RequestBody ConfigurationResourceCommand command)
{requireSame(id,command.resourceItemId());return success(resourceManagement.save(command,actor()));}

@PreAuthorize("@ss.hasAnyPermi('todo:resource:list,todo:resource:query,todo:template:list,todo:simulation:simulate')")
@GetMapping("/resources/data-sources")
public AjaxResult resourceDataSources(){return success(payloads.dataSources());}
```

- [ ] **Step 4: Add frontend API functions and hidden compatibility navigation**

```js
export function getTodoTemplateJourney(id) { return request({ url: `/todo/config/templates/${id}/journey`, method: 'get' }) }
export function listTodoTemplateWorkbench(params) { return request({ url: '/todo/config/templates/workbench', method: 'get', params }) }
export function hydrateTodoJourneyPayload(id, data) { return request({ url: `/todo/config/templates/${id}/journey/payload`, method: 'post', data }) }
export function simulateTodoJourney(id, data) { return request({ url: `/todo/config/templates/${id}/journey/simulate`, method: 'post', data }) }
```

Add to `todo-resources.js`:

```js
export function createConfigurationResource(data) { return request({ url: '/todo/config/resources/items', method: 'post', data }) }
export function updateConfigurationResource(id, data) { return request({ url: `/todo/config/resources/items/${id}`, method: 'put', data }) }
export function listConfigurationDataSources() { return request({ url: '/todo/config/resources/data-sources', method: 'get' }) }
```

`V0_20_43` must add a hidden child route `todo-template-journey` with component `todo/config/journey/index`, reuse `todo:template:list`, and leave `/todo-engine/todo-template` plus all old resource routes intact.

- [ ] **Step 5: Run MVC and Flyway tests, then commit**

Run: `mvn -pl ruoyi-admin -am -Dtest=TodoConfigurationControllerJourneyTest,TodoPhaseTwoJourneyMetadataMigrationContractTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS with authenticated actor propagation and no permission widening.

```bash
git add ruoyi-admin/src/main/java/com/ruoyi/web/controller/todo/TodoConfigurationController.java ruoyi-admin/src/test/java/com/ruoyi/web/controller/todo/TodoConfigurationControllerJourneyTest.java ruoyi-admin/src/main/resources/db/migration/V0_20_43__todo_phase_two_journey_navigation.sql ruoyi-ui/src/api/todo-config.js ruoyi-ui/src/api/todo-resources.js
git commit -m "feat(todo-config): expose secured journey endpoints"
```

---

### Task 8: Pure Frontend Journey Model and Contract Checks

**Files:**
- Create: `ruoyi-ui/src/views/todo/config/journey/journey-model.js`
- Create: `ruoyi-ui/scripts/check-todo-journey-model.js`
- Modify: `ruoyi-ui/package.json`

**Interfaces:**
- Consumes: Task 7 structured journey JSON (`steps[].value`) and existing `template-draft-model.js` draft payload contract; the API does not return raw `definitionJson`.
- Produces: `hydrateJourney`, `applyStepPatch`, `derivePrimaryAction`, `mergeSaveResult`, `toSimulationCommand`, and `canLeave`.

- [ ] **Step 1: Write the failing CommonJS model check**

```js
const assert = require('assert')
const model = require('../src/views/todo/config/journey/journey-model')
const journey = model.hydrateJourney(fixture())
assert.deepStrictEqual(journey.steps.map(step => step.code), [
  'EVENT', 'TRIGGER', 'OWNER', 'DOD', 'SLA', 'ROUTING', 'SIMULATION_PUBLISH'
])
const changed = model.applyStepPatch(journey, 'OWNER', { config: { type: 'BUSINESS_OWNER' } })
assert.strictEqual(changed.dirty, true)
assert.strictEqual(model.derivePrimaryAction(changed).label, '保存并继续')
assert.strictEqual(model.canLeave(changed), false)
```

- [ ] **Step 2: Add the script entry and verify failure**

```json
"test:todo-phase-two": "node scripts/check-todo-journey-model.js && node scripts/check-todo-phase-two-ux.js"
```

Run: `npm run test:todo-phase-two`

Expected: FAIL because the model and UX contract script do not exist.

- [ ] **Step 3: Implement immutable model transformations**

```js
function hydrateJourney(payload) {
  const byCode = new Map((payload.steps || []).map(step => [step.code, step]))
  const definition = {
    schemaVersion: 1,
    templateCode: payload.template.templateCode,
    event: clone((byCode.get('EVENT') || {}).value || { eventType: '', payloadVersion: 1 }),
    owner: clone((byCode.get('OWNER') || {}).value || { config: {} }),
    dod: clone((byCode.get('DOD') || {}).value || { config: {} }),
    sla: clone((byCode.get('SLA') || {}).value || { config: {} }),
    routing: clone((byCode.get('ROUTING') || {}).value || { config: { nodes: [], edges: [] } })
  }
  return { ...clone(payload), definition, dirty: false, saveState: 'SAVED', payload: { manualOverrides: {} } }
}

function applyStepPatch(journey, stepCode, value) {
  const steps = journey.steps.map(step => step.code === stepCode ? { ...step, value: clone(value), state: 'IN_PROGRESS' } : step)
  return { ...journey, steps, dirty: true, saveState: 'IDLE', preflightGate: null }
}

function toSimulationCommand(journey, object, effectiveAt) {
  return {
    requestId: `journey-${Date.now()}`, templateId: Number(journey.template.templateId),
    versionId: Number(journey.template.versionId), eventType: journey.definition.event.eventType,
    payloadVersion: Number(journey.definition.event.payloadVersion), businessType: journey.template.businessType,
    businessId: Number(object.businessId), manualOverrides: clone(journey.payload.manualOverrides || {}),
    effectiveAt, expectedDefinitionHash: journey.template.definitionHash, taskCompletions: []
  }
}

function canLeave(journey) { return !journey.dirty || journey.saveState === 'SAVED' }
```

- [ ] **Step 4: Cover conflict merge and save-state behavior**

The check script must assert: successful save updates lock version/hash; failed save retains local edits; conflict creates `conflict.server` and `conflict.local`; simulation command contains no sample runtime-write flag; `derivePrimaryAction` returns `继续配置`, `试运行`, or `发布预检` according to current step/state.

- [ ] **Step 5: Run model checks and commit**

Run: `npm run test:todo-phase-two`

Expected: PASS with `todo phase two journey model contract passed`.

```bash
git add ruoyi-ui/src/views/todo/config/journey/journey-model.js ruoyi-ui/scripts/check-todo-journey-model.js ruoyi-ui/package.json
git commit -m "feat(todo-config): add journey client model"
```

---

### Task 9: High-Fidelity Template Configuration Workbench

**Files:**
- Modify: `ruoyi-ui/src/views/todo/config/template/index.vue`
- Create: `ruoyi-ui/src/views/todo/config/template/TemplateProblemSummary.vue`
- Create: `ruoyi-ui/src/views/todo/config/template/TemplateProgressCell.vue`
- Modify: `ruoyi-ui/src/views/todo/config/styles/config-center.scss`
- Create: `ruoyi-ui/scripts/check-todo-phase-two-ux.js`

**Interfaces:**
- Consumes: `listTodoTemplateWorkbench(params)` from Task 7; no client-side per-row journey requests.
- Produces: `TemplateConfigurationWorkbench` behavior: business scenario/stage, progress, blockers/warnings, last editor/time, and one primary continue/view action.

- [ ] **Step 1: Write the failing source contract check**

```js
const assert = require('assert')
const fs = require('fs')
const source = fs.readFileSync('src/views/todo/config/template/index.vue', 'utf8')
for (const token of ['TemplateProblemSummary', 'TemplateProgressCell', '继续配置', '查看已发布版本', 'data-testid="template-workbench"'])
  assert(source.includes(token), `missing workbench token: ${token}`)
assert(!source.includes('todayTriggeredTodoCount'), 'generic dashboard cards must be removed from the workbench')
```

- [ ] **Step 2: Run the source contract and verify failure**

Run: `npm run test:todo-phase-two`

Expected: FAIL with `missing workbench token`.

- [ ] **Step 3: Replace generic cards with the task-centered table**

```vue
<section class="template-workbench" data-testid="template-workbench">
  <header class="workbench-header">
    <div><h1>待办配置工作台</h1><p>按业务旅程完成配置、模拟与发布</p></div>
    <el-button type="primary" icon="el-icon-plus" @click="createDraft">新建配置</el-button>
  </header>
  <template-problem-summary :summary="problemSummary" @filter="applyIssueFilter" />
  <el-table :data="rows" @row-click="continueConfiguration">
    <el-table-column label="业务场景" min-width="260">
      <template slot-scope="{ row }"><strong>{{ row.templateName }}</strong><small>{{ row.businessTypeLabel }} · {{ row.businessStageLabel }}</small></template>
    </el-table-column>
    <el-table-column label="配置进度" width="220">
      <template slot-scope="{ row }"><template-progress-cell :row="row" /></template>
    </el-table-column>
    <el-table-column label="健康状态" width="180">
      <template slot-scope="{ row }"><el-tag v-if="row.blockerCount" type="danger">{{ row.blockerCount }} 项阻塞</el-tag><el-tag v-else-if="row.warningCount" type="warning">{{ row.warningCount }} 项警告</el-tag><el-tag v-else type="success">可继续</el-tag></template>
    </el-table-column>
    <el-table-column label="最近修改" width="190">
      <template slot-scope="{ row }">{{ row.lastEditor }} · {{ parseTime(row.updateTime) }}</template>
    </el-table-column>
    <el-table-column label="操作" width="150">
      <template slot-scope="{ row }"><el-button type="text" @click.stop="continueConfiguration(row)">{{ row.primaryAction === 'VIEW_PUBLISHED' ? '查看已发布版本' : '继续配置' }}</el-button></template>
    </el-table-column>
  </el-table>
</section>
```

Use business type/stage/status/issue filters. Keep template code only as muted secondary search text. The row primary action navigates to the hidden journey route with `templateId`.

- [ ] **Step 4: Apply approved visual tokens**

Add CSS variables `--todo-navy:#0B2A55`, `--todo-gold:#C89A3D`, `--todo-surface:#FFFFFF`, `--todo-bg:#F4F7FA`, `--todo-border:#D9E1EA`; use 8px radii, 16/24px spacing, no gradient, and no shadow on table sections.

- [ ] **Step 5: Run frontend contracts/build and commit**

Run: `npm run test:todo-phase-two && npm run test:todo-config && npm run build:prod`

Expected: all contract checks pass and Vue production build completes successfully.

```bash
git add ruoyi-ui/src/views/todo/config/template/index.vue ruoyi-ui/src/views/todo/config/template/TemplateProblemSummary.vue ruoyi-ui/src/views/todo/config/template/TemplateProgressCell.vue ruoyi-ui/src/views/todo/config/styles/config-center.scss ruoyi-ui/scripts/check-todo-phase-two-ux.js
git commit -m "feat(todo-config): redesign template workbench"
```

---

### Task 10: Journey Shell, Navigation, Autosave, and Conflict Recovery

**Files:**
- Create: `ruoyi-ui/src/views/todo/config/journey/index.vue`
- Create: `ruoyi-ui/src/views/todo/config/journey/components/JourneyStepNav.vue`
- Create: `ruoyi-ui/src/views/todo/config/journey/components/ConfigurationHealthPanel.vue`
- Create: `ruoyi-ui/src/views/todo/config/journey/components/EmployeeTodoPreview.vue`
- Create: `ruoyi-ui/src/views/todo/config/journey/components/JourneySaveStatus.vue`
- Create: `ruoyi-ui/src/views/todo/config/journey/components/JourneyConflictDialog.vue`
- Modify: `ruoyi-ui/scripts/check-todo-phase-two-ux.js`

**Interfaces:**
- Consumes: Task 8 model and Task 7 journey/draft APIs.
- Produces: the fixed seven-step shell, right-hand preview/health rail, debounced autosave, retry, navigation guard, and conflict recovery.

- [ ] **Step 1: Extend the failing source contract**

```js
const journey = fs.readFileSync('src/views/todo/config/journey/index.vue', 'utf8')
for (const token of ['JourneyStepNav', 'ConfigurationHealthPanel', 'EmployeeTodoPreview',
  'JourneySaveStatus', 'JourneyConflictDialog', 'beforeRouteLeave', 'scheduleAutosave'])
  assert(journey.includes(token), `missing journey shell token: ${token}`)
```

- [ ] **Step 2: Run the contract and verify missing-file failure**

Run: `npm run test:todo-phase-two`

Expected: FAIL because `journey/index.vue` is absent.

- [ ] **Step 3: Build the responsive shell**

```vue
<div class="journey-shell" data-testid="template-journey-shell">
  <journey-step-nav :steps="journey.steps" :active-code="activeStep" @select="selectStep" />
  <main class="journey-body">
    <section class="journey-editor"><component :is="activeComponent" :value="activeValue" @change="onStepChange" /></section>
    <aside class="journey-aside">
      <configuration-health-panel :issues="activeIssues" @repair="repair" />
      <employee-todo-preview :value="journey.employeePreview" />
    </aside>
  </main>
  <footer class="journey-footer">
    <journey-save-status :state="journey.saveState" @retry="saveNow" />
    <el-button @click="saveNow">保存</el-button>
    <el-button type="primary" @click="runPrimaryAction">{{ primaryAction.label }}</el-button>
  </footer>
</div>
```

- [ ] **Step 4: Implement autosave and conflict recovery exactly once in the shell**

Use a 600ms debounce. On successful save call `mergeSaveResult`; on network failure set `FAILED` without replacing local steps; on HTTP 409 open `JourneyConflictDialog` with server/local definition summaries and actions `刷新并合并` and `另存副本`. `beforeRouteLeave` prompts only when `canLeave(journey)` is false.

- [ ] **Step 5: Run contracts/build and commit**

Run: `npm run test:todo-phase-two && npm run build:prod`

Expected: PASS; compiler reports no unknown custom elements.

```bash
git add ruoyi-ui/src/views/todo/config/journey/index.vue ruoyi-ui/src/views/todo/config/journey/components/JourneyStepNav.vue ruoyi-ui/src/views/todo/config/journey/components/ConfigurationHealthPanel.vue ruoyi-ui/src/views/todo/config/journey/components/EmployeeTodoPreview.vue ruoyi-ui/src/views/todo/config/journey/components/JourneySaveStatus.vue ruoyi-ui/src/views/todo/config/journey/components/JourneyConflictDialog.vue ruoyi-ui/scripts/check-todo-phase-two-ux.js
git commit -m "feat(todo-config): build seven-step journey shell"
```

---

### Task 11: Business Event, Trigger, and Owner Steps with Context Resources

**Files:**
- Create: `ruoyi-ui/src/views/todo/config/journey/steps/EventStep.vue`
- Create: `ruoyi-ui/src/views/todo/config/journey/steps/TriggerStep.vue`
- Create: `ruoyi-ui/src/views/todo/config/journey/steps/OwnerStep.vue`
- Create: `ruoyi-ui/src/views/todo/config/journey/components/ContextResourceDrawer.vue`
- Create: `ruoyi-ui/src/views/todo/config/journey/components/TypedConditionBuilder.vue`
- Modify: `ruoyi-ui/src/views/todo/config/resource/index.vue`
- Modify: `ruoyi-ui/src/views/todo/config/resource/BusinessResourcePanel.vue`
- Modify: `ruoyi-ui/src/views/todo/config/resource/ValidatorCatalogPanel.vue`
- Create: `ruoyi-ui/src/views/todo/config/resource/ResourceItemDrawer.vue`
- Create: `ruoyi-ui/src/views/todo/config/resource/BusinessDataSourcePanel.vue`
- Modify: `ruoyi-ui/src/views/todo/config/journey/index.vue`
- Modify: `ruoyi-ui/scripts/check-todo-phase-two-ux.js`

**Interfaces:**
- Consumes: governed events, Task 3 field descriptors/operators/options, owner catalog, health issues, and resource-maintenance permissions.
- Produces: definition patches for `event`, `event.condition`, and `owner.config`; emits `repair-resource` with `{ type, eventType, payloadVersion, returnStep }`; provides governed maintenance for fields/materials/recipes/calendars and read-only validator/data-source status.

- [ ] **Step 1: Extend source contracts for business-first controls**

```js
const files = ['EventStep.vue', 'TriggerStep.vue', 'OwnerStep.vue']
for (const name of files) assert(fs.existsSync(`src/views/todo/config/journey/steps/${name}`), `missing ${name}`)
const trigger = fs.readFileSync('src/views/todo/config/journey/steps/TriggerStep.vue', 'utf8')
assert(trigger.includes('TypedConditionBuilder'))
assert(trigger.includes('规则说明'))
assert(!trigger.includes('payload JSON'))
const resourceCenter = fs.readFileSync('src/views/todo/config/resource/index.vue', 'utf8')
for (const label of ['事件目录','校验器目录','业务字段','材料类型','DoD 配方','工作日历','模拟数据源'])
  assert(resourceCenter.includes(label), `missing resource center section: ${label}`)
```

- [ ] **Step 2: Run the contract and verify failure**

Run: `npm run test:todo-phase-two`

Expected: FAIL listing the missing step files.

- [ ] **Step 3: Implement Event and Trigger steps**

`EventStep` groups searchable events by business type/source/status and displays Chinese name, production timing, source business, payload version, description, sample object, and payload summary. A blank/incomplete schema disables continue and emits `repair-resource` with label `维护事件字段`.

`TypedConditionBuilder` limits normal mode to two nested groups, obtains operators from the selected field descriptor, and chooses boolean/select/date/number/text controls from field type/options. It emits the existing condition JSON shape, never asks the user to type a path, operator code, or JSON.

- [ ] **Step 4: Implement Owner business strategies and live explanation**

```js
const strategies = [
  { value: 'EVENT_OWNER', label: '事件中的负责人' },
  { value: 'BUSINESS_OWNER', label: '业务对象负责人' },
  { value: 'ROLE', label: '指定角色' },
  { value: 'USER', label: '指定人员' },
  { value: 'CANDIDATE_POOL', label: '候选池' }
]
```

Show resolution order and fallback in natural language. Use the current simulation object preview when available. No resolution plus no fallback must visibly block publish.

- [ ] **Step 5: Implement context resource drawer return semantics**

The drawer loads only the requested event/schema/resource, requires resource permission for writes, retains the journey draft, closes after a successful save, reloads journey resources, restores `returnStep`, and focuses the repaired field.

- [ ] **Step 6: Complete the unified configuration resource center**

Keep the existing event and validator tabs. Split `业务字段与材料` into governed `业务字段`, `材料类型`, and `DoD 配方` sub-tabs using `ResourceItemDrawer`; add `工作日历` using the existing `WorkCalendarDialog`; add `模拟数据源` using `BusinessDataSourcePanel`. Validator implementation/class names remain read-only advanced details and cannot be uploaded or executed from the UI. Resource saves return to the originating journey step without losing the draft.

- [ ] **Step 7: Run contracts/build and commit**

Run: `npm run test:todo-phase-two && npm run build:prod`

Expected: PASS and no plain JSON editor in the three normal-mode steps.

```bash
git add ruoyi-ui/src/views/todo/config/journey/steps/EventStep.vue ruoyi-ui/src/views/todo/config/journey/steps/TriggerStep.vue ruoyi-ui/src/views/todo/config/journey/steps/OwnerStep.vue ruoyi-ui/src/views/todo/config/journey/components/ContextResourceDrawer.vue ruoyi-ui/src/views/todo/config/journey/components/TypedConditionBuilder.vue ruoyi-ui/src/views/todo/config/journey/index.vue ruoyi-ui/src/views/todo/config/resource/index.vue ruoyi-ui/src/views/todo/config/resource/BusinessResourcePanel.vue ruoyi-ui/src/views/todo/config/resource/ValidatorCatalogPanel.vue ruoyi-ui/src/views/todo/config/resource/ResourceItemDrawer.vue ruoyi-ui/src/views/todo/config/resource/BusinessDataSourcePanel.vue ruoyi-ui/scripts/check-todo-phase-two-ux.js
git commit -m "feat(todo-config): add event trigger and owner journey steps"
```

---

### Task 12: DoD, SLA, Routing, and Employee-Visible Preview Steps

**Files:**
- Create: `ruoyi-ui/src/views/todo/config/journey/steps/DodStep.vue`
- Create: `ruoyi-ui/src/views/todo/config/journey/steps/SlaStep.vue`
- Create: `ruoyi-ui/src/views/todo/config/journey/steps/RoutingStep.vue`
- Create: `ruoyi-ui/src/views/todo/config/journey/components/DodRecipePicker.vue`
- Create: `ruoyi-ui/src/views/todo/config/journey/components/SlaTimelinePreview.vue`
- Create: `ruoyi-ui/src/views/todo/config/journey/components/BusinessRoutingEditor.vue`
- Modify: `ruoyi-ui/src/views/todo/config/journey/components/EmployeeTodoPreview.vue`
- Modify: `ruoyi-ui/src/views/todo/config/journey/index.vue`
- Modify: `ruoyi-ui/scripts/check-todo-phase-two-ux.js`

**Interfaces:**
- Consumes: ranked recipes from Task 4, field/material/validator catalogs, SLA/calendar catalogs, routing targets, and Task 2 preview.
- Produces: definition patches for `dod.config`, `sla.config`, and `routing.config` while keeping technical validator and graph editors behind advanced mode.

- [ ] **Step 1: Add failing contracts for recipe, timeline, and business routing**

```js
for (const file of ['DodRecipePicker.vue', 'SlaTimelinePreview.vue', 'BusinessRoutingEditor.vue'])
  assert(fs.existsSync(`src/views/todo/config/journey/components/${file}`), `missing ${file}`)
const dod = fs.readFileSync('src/views/todo/config/journey/steps/DodStep.vue', 'utf8')
assert(dod.includes('推荐完成标准'))
assert(dod.includes('高级设置'))
const sla = fs.readFileSync('src/views/todo/config/journey/components/SlaTimelinePreview.vue', 'utf8')
for (const marker of ['80%', '100%', '150%']) assert(sla.includes(marker))
```

- [ ] **Step 2: Run the contract and verify failure**

Run: `npm run test:todo-phase-two`

Expected: FAIL listing missing components.

- [ ] **Step 3: Implement DoD recipe selection and controlled customization**

Display contextual recipes first, with business descriptions such as `已记录联系时间`. Applying a recipe updates only the current draft and materializes required fields, materials, conditional rules, validator refs, and employee instructions. Normal mode can add/remove governed fields/materials/conditions. Validator codes and parameter schemas appear only after `高级设置` is expanded.

- [ ] **Step 4: Implement natural-language SLA and 80/100/150 timeline**

Provide duration, unit, calendar, start strategy, and pause rules as labeled controls. `SlaTimelinePreview` draws four semantic points using Element UI icons and real text: created, 80% reminder, 100% overdue, 150% escalation. List the reminder/escalation/transfer/return-to-pool action under each point. Missing calendar or calculation error produces a blocker with a repair action.

- [ ] **Step 5: Implement business routing and advanced graph compatibility**

Normal mode edits an ordered business result list with branch result, next template business name, or `结束`. Support parallel/join via explicit business controls. Advanced mode embeds the existing graph editor. Validate missing targets, no-exit cycles, invalid versions, and unreachable branches through backend issues rather than client-only inference.

- [ ] **Step 6: Run contracts/build and commit**

Run: `npm run test:todo-phase-two && npm run test:todo-config && npm run build:prod`

Expected: PASS; employee preview fields/materials/instructions match the selected recipe.

```bash
git add ruoyi-ui/src/views/todo/config/journey/steps/DodStep.vue ruoyi-ui/src/views/todo/config/journey/steps/SlaStep.vue ruoyi-ui/src/views/todo/config/journey/steps/RoutingStep.vue ruoyi-ui/src/views/todo/config/journey/components/DodRecipePicker.vue ruoyi-ui/src/views/todo/config/journey/components/SlaTimelinePreview.vue ruoyi-ui/src/views/todo/config/journey/components/BusinessRoutingEditor.vue ruoyi-ui/src/views/todo/config/journey/components/EmployeeTodoPreview.vue ruoyi-ui/src/views/todo/config/journey/index.vue ruoyi-ui/scripts/check-todo-phase-two-ux.js
git commit -m "feat(todo-config): add dod sla and routing journey steps"
```

---

### Task 13: Business Object Payload Editor, Full Trace, and Publish Step

**Files:**
- Create: `ruoyi-ui/src/views/todo/config/journey/steps/SimulationPublishStep.vue`
- Create: `ruoyi-ui/src/views/todo/config/journey/components/BusinessObjectPayloadEditor.vue`
- Create: `ruoyi-ui/src/views/todo/config/journey/components/SimulationTrace.vue`
- Create: `ruoyi-ui/src/views/todo/config/journey/components/PublishPreflightPanel.vue`
- Modify: `ruoyi-ui/src/views/todo/config/journey/index.vue`
- Create: `ruoyi-ui/tests/e2e/todo-config-journey.spec.js`
- Modify: `ruoyi-ui/tests/e2e/bootstrap/todo-config-admin.sql`

**Interfaces:**
- Consumes: Task 7 business-object search/hydration/simulation APIs, Task 6 ordered trace and publish gates, and existing release APIs.
- Produces: read-only real/sample object simulation, provenance-aware payload editor, full trace, warning reason, version diff, and publish result.

- [ ] **Step 1: Write the failing Chrome journey test**

```js
test('configures, hydrates, simulates and publishes through the seven-step journey', async ({ page }) => {
  await loginAs(page, 'todo_config_admin', process.env.TODO_CONFIG_E2E_PASSWORD)
  await page.goto('/todo-engine/todo-template')
  await page.getByRole('button', { name: '新建配置' }).click()
  await expect(page.getByTestId('template-journey-shell')).toBeVisible()
  await completeEventTriggerOwnerDodSlaRouting(page)
  await page.getByRole('tab', { name: /模拟发布/ }).click()
  await selectBusinessObject(page, process.env.TODO_CONFIG_E2E_LEAD_NO)
  await expect(page.getByText(/Payload 自动填充率/)).toContainText(/9\d%|100%/)
  await page.getByRole('button', { name: '运行模拟' }).click()
  for (const title of ['事件命中','负责人解析','完成标准','SLA 截止','后续路由','待办预览'])
    await expect(page.getByText(title, { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '发布预检' }).click()
  await page.getByRole('button', { name: '发布版本' }).click()
  await expect(page.getByText('模板已发布')).toBeVisible()
})
```

- [ ] **Step 2: Build production assets and run the E2E test to verify failure**

Run: `npm run build:prod; $env:TODO_E2E_BROWSER='chrome'; npm run test:e2e -- tests/e2e/todo-config-journey.spec.js`

Expected: FAIL because simulation/publish step components and journey selectors are missing.

- [ ] **Step 3: Implement object selection and payload provenance**

`BusinessObjectPayloadEditor` searches actor-visible real objects, visually labels sample rows `只读样例`, requests hydration on selection, displays coverage, and shows each field source as `对象读取`, `事件样例`, `系统默认`, `人工覆盖`, or `仍缺失`. Manual edits update `manualOverrides` only. Sensitive values render as `••••••`.

- [ ] **Step 4: Implement ordered trace and publish preflight**

`SimulationTrace` renders Task 6 sections in fixed order and each error links to its `stepCode/fieldPath`. `PublishPreflightPanel` requires a successful current-hash simulation, reloads full server preflight, blocks on blockers, requires a reason on warnings, displays draft-versus-published diff, and calls the existing immutable publish endpoint.

- [ ] **Step 5: Cover failure repair, permissions, samples, and warnings in E2E**

Add tests for: schema repair drawer and return; applying a recommended recipe; real object automatic hydration; sample object never causing runtime rows; failed simulation repair and rerun; blocker preventing publish; warning requiring explanation; business admin/resource admin/publisher/auditor visibility and write boundaries.

- [ ] **Step 6: Run Chrome E2E and commit**

Run: `$env:TODO_E2E_REAL_BACKEND='true'; $env:TODO_E2E_BROWSER='chrome'; npm run test:e2e -- tests/e2e/todo-config-journey.spec.js`

Expected: PASS for all seven required phase-two E2E scenarios using the disposable real backend and MySQL database.

```bash
git add ruoyi-ui/src/views/todo/config/journey/steps/SimulationPublishStep.vue ruoyi-ui/src/views/todo/config/journey/components/BusinessObjectPayloadEditor.vue ruoyi-ui/src/views/todo/config/journey/components/SimulationTrace.vue ruoyi-ui/src/views/todo/config/journey/components/PublishPreflightPanel.vue ruoyi-ui/src/views/todo/config/journey/index.vue ruoyi-ui/tests/e2e/todo-config-journey.spec.js ruoyi-ui/tests/e2e/bootstrap/todo-config-admin.sql
git commit -m "feat(todo-config): close journey simulation and publish loop"
```

---

### Task 14: Compatibility, Full Regression, Visual QA, and Acceptance Report

**Files:**
- Modify: `ruoyi-ui/tests/e2e/todo-config-center.spec.js`
- Create: `ruoyi-ui/scripts/compose-todo-phase-two-design-qa.py`
- Create: `docs/superpowers/reports/2026-07-23-todo-engine-phase-two-acceptance.md`
- Create: `docs/superpowers/reports/2026-07-23-todo-engine-phase-two-design-qa.md`
- Create: `output/playwright/todo-phase-two-ux/1440x1024-implementation.png`
- Create: `output/playwright/todo-phase-two-ux/1920x1080-implementation.png`
- Create: `output/playwright/todo-phase-two-ux/1440x1024-comparison.png`
- Create: `output/playwright/todo-phase-two-ux/1920x1080-comparison.png`

**Interfaces:**
- Consumes: all prior tasks, the approved reference asset, legacy routes/permissions, disposable MySQL environment, and user-approved Chrome/Playwright.
- Produces: regression evidence, same-state comparison images, design QA report, and phase-two acceptance decision.

- [ ] **Step 1: Add legacy route and immutable-version regression assertions**

```js
test('legacy template and resource routes preserve context', async ({ page }) => {
  await loginAs(page, 'todo_config_admin', process.env.TODO_CONFIG_E2E_PASSWORD)
  for (const route of ['/todo-engine/todo-template','/todo-engine/todo-trigger-rule',
    '/todo-engine/todo-dod-rule','/todo-engine/todo-sla-rule','/todo-engine/todo-release-record']) {
    await page.goto(route)
    await expect(page.locator('.app-main')).toBeVisible()
    await expect(page.getByText(/无权限|页面不存在/)).toHaveCount(0)
  }
})
```

- [ ] **Step 2: Run the complete backend and MySQL suite**

Run: `mvn test`

Expected: BUILD SUCCESS with all module tests green.

Run: `$env:TODO_MIGRATION_DB_URL='jdbc:mysql://127.0.0.1:3306/law_todo_config_e2e?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&connectionCollation=utf8mb4_unicode_ci'; $env:TODO_MIGRATION_DB_USER='root'; $env:TODO_MIGRATION_DB_PASSWORD='root'; mvn -pl ruoyi-admin -am -Dtest=FlywayMigrationTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: BUILD SUCCESS; Flyway applies through `V0_20_43` and transaction/immutability tests pass against MySQL 8.

- [ ] **Step 3: Run complete frontend checks and production build**

Run: `npm run test:todo-phase-two && npm run test:todo-config && npm run test:todo && npm run test:encoding && npm run build:prod`

Expected: all contract scripts pass and production build completes.

- [ ] **Step 4: Run the complete real-backend Chrome E2E suite**

Run: `$env:TODO_E2E_REAL_BACKEND='true'; $env:TODO_E2E_BROWSER='chrome'; npm run test:e2e -- tests/e2e/todo-config-center.spec.js tests/e2e/todo-config-journey.spec.js`

Expected: PASS with traces retained only for failures and no runtime task created from sample simulation.

- [ ] **Step 5: Capture same-state visual evidence**

At 1440×1024 and 1920×1080, open the Owner step for the same populated template and business object, keep the left editor/right employee preview visible, and save screenshots under `output/playwright/todo-phase-two-ux/`. Do not compare a list page to the approved journey-editor reference.

- [ ] **Step 6: Compose side-by-side comparison inputs**

`compose-todo-phase-two-design-qa.py` must use Pillow from the bundled workspace Python to place the approved reference on the left and the implementation screenshot on the right, preserve aspect ratio, label each half, and output the two `*-comparison.png` files. It must fail if either source image is missing or has zero dimensions.

Run: `python scripts/compose-todo-phase-two-design-qa.py`

Expected: two non-empty comparison PNG files are created.

- [ ] **Step 7: Perform visual QA and close every P0/P1/P2 difference**

Inspect each combined comparison image for layout, spacing, typography, color, borders, radii, hierarchy, overflow, focus, error, loading, and disabled states. Record each finding with severity, screenshot, component, expected correction, and verification. Repeat implementation capture and comparison until no P0/P1/P2 findings remain.

The design report must end with this exact line only after closure:

```text
final result: passed
```

- [ ] **Step 8: Write the acceptance report with measurable evidence**

The acceptance report must record: elapsed time for a normal administrator to configure and simulate a simple template; active-event schema coverage; DoD recipe coverage percentage; payload auto-fill percentage; trace section coverage; publish blocker/warning behavior; employee preview parity; route/permission/version compatibility; exact backend/frontend/E2E commands and results; links to both comparison images; and the final design-QA result.

- [ ] **Step 9: Run final cleanliness checks and commit**

Run: `git diff --check && git status --short`

Expected: no whitespace errors; only intentional product/report changes plus the preserved user-owned files listed in Global Constraints.

```bash
git add ruoyi-ui/tests/e2e/todo-config-center.spec.js ruoyi-ui/scripts/compose-todo-phase-two-design-qa.py docs/superpowers/reports/2026-07-23-todo-engine-phase-two-acceptance.md docs/superpowers/reports/2026-07-23-todo-engine-phase-two-design-qa.md
git commit -m "test(todo-config): complete phase two acceptance"
```

## Delivery Checkpoints

1. **Batch 1 — Journey shell and trigger closure:** Tasks 1–4 and 7–11. Demo the workbench, seven-step shell, event schema repair, typed trigger builder, health states, and autosave before continuing.
2. **Batch 2 — Owner, DoD, SLA, and employee preview:** Tasks 2, 4, and 12. Verify recipe coverage and preview parity with representative LEAD/CONTRACT/CASE definitions.
3. **Batch 3 — Routing, payload, simulation, and publish:** Tasks 5–7 and 13. Verify real/sample payload provenance, full trace, blockers, warning reason, diff, and immutable publish.
4. **Batch 4 — Compatibility and acceptance:** Task 14. No completion claim until MySQL, Chrome E2E, both visual viewports, and design QA all pass.

## Phase-Two Acceptance Matrix

| Requirement | Evidence task |
|---|---:|
| Simple template configured and simulated within 10 minutes | 13, 14 |
| No JSON/internal IDs/codes in default mode | 9–13 |
| All active events expose understandable selectable payload fields | 3, 7, 11, 14 |
| At least 80% common DoD through recipes | 4, 12, 14 |
| Selected object auto-fills at least 90% payload | 5, 6, 13, 14 |
| Trace covers event, Owner, DoD, SLA, route, next Todo | 6, 13, 14 |
| Blockers prevent publish; warnings require reason | 2, 6, 13, 14 |
| Employee preview matches actual task form/materials/instructions | 2, 12, 14 |
| Old routes, permissions, published versions remain compatible | 7, 14 |
| Backend, MySQL, frontend, Chrome E2E, visual QA pass | 14 |
