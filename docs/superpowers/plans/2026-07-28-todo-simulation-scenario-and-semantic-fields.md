# Todo Simulation Scenarios and Semantic Fields Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Todo template simulation show only fields used by the current template, render IDs and codes as governed business labels, and prove TD-001 creation plus its three completion branches before publication.

**Architecture:** Keep raw IDs and dictionary codes in canonical definitions, but add template-scoped field usages and an actor-scoped display-resolution boundary around payload hydration. Model completion scenarios as governed configuration resources, execute them through the existing read-only journey simulator, and persist only hash-bound, redacted evidence for the publication gate.

**Tech Stack:** Java 17, Spring Boot, Jakarta Validation, MyBatis, Flyway, MySQL 8, Vue 2.6, Element UI, Node contract tests, Playwright, Maven

## Global Constraints

- Use branch `runtime/startup-wiring-fix`; preserve unrelated local changes and runtime artifacts.
- Do not expose a raw user, department, post, role, dictionary, or business-reference ID in the default UI.
- Canonical definitions continue to store stable IDs or codes; labels are resolved at read time.
- Only fields referenced by the current template appear in the default simulation workbench.
- A missing optional field is not a blocker and is not labelled “待补充”.
- TD-001 must support `TD001_VALID`, `TD001_SUSPECT_INVALID`, and `TD001_UNREACHABLE`.
- Simulation must not insert or update runtime Todo, business data, or business-event Outbox rows.
- Evidence stores hashes and redacted summaries, never full customer payloads.
- A definition hash or scenario version change invalidates prior evidence.
- Publication is denied until all required scenarios for the current definition hash pass.
- Existing routes, permissions, published immutable versions, and legacy simulation APIs remain compatible.
- Every database change is a new Flyway migration; never edit an already-applied migration.

---

## File Structure

### Backend domain and application

- `law-todo/src/main/java/com/law/todo/application/TodoTemplateFieldUsageService.java` — extracts field usages from the canonical template definition.
- `law-todo/src/main/java/com/law/todo/application/TodoFieldDisplayResolutionService.java` — batches actor-scoped label resolution.
- `law-todo/src/main/java/com/law/todo/application/TodoJourneyPayloadPreparationService.java` — orchestrates hydration, scoping, requiredness, and display values.
- `law-todo/src/main/java/com/law/todo/application/TodoSimulationScenarioService.java` — lists governed scenarios and executes one or all scenarios.
- `law-todo/src/main/java/com/law/todo/application/TodoSimulationEvidenceService.java` — writes and evaluates redacted hash-bound evidence.
- `law-todo/src/main/java/com/law/todo/spi/TodoFieldReferenceDirectory.java` — RuoYi-independent label and option boundary.
- `law-todo/src/main/java/com/law/todo/application/view/TodoJourneyPayloadView.java` — grouped creation/completion/routing payload contract.
- `law-todo/src/main/java/com/law/todo/application/view/TodoSimulationScenarioViews.java` — scenario, result, batch, and evidence views.

### RuoYi integration

- `ruoyi-system/src/main/java/com/ruoyi/system/service/todo/RuoYiTodoFieldReferenceDirectory.java` — resolves users, departments, posts, roles, dictionaries, and business references.
- `ruoyi-system/src/main/java/com/ruoyi/system/mapper/TodoBusinessDirectoryMapper.java` — batch reference queries.
- `ruoyi-system/src/main/resources/mapper/system/TodoBusinessDirectoryMapper.xml` — actor-scoped SQL for reference labels and choices.
- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/todo/TodoConfigurationController.java` — payload, option, scenario, and batch endpoints.

### Database

- `ruoyi-admin/src/main/resources/db/migration/V0_20_61__todo_semantic_field_metadata.sql` — semantic metadata and required dictionaries.
- `ruoyi-admin/src/main/resources/db/migration/V0_20_62__todo_simulation_scenarios_and_evidence.sql` — scenario resources, TD-001 seeds, evidence table, indexes.

### Frontend

- `ruoyi-ui/src/views/todo/config/journey/components/SemanticValueRenderer.vue`
- `ruoyi-ui/src/views/todo/config/journey/components/SemanticOptionSelector.vue`
- `ruoyi-ui/src/views/todo/config/journey/components/CreationValidationPanel.vue`
- `ruoyi-ui/src/views/todo/config/journey/components/EventInputPanel.vue`
- `ruoyi-ui/src/views/todo/config/journey/components/TodoCreationPreview.vue`
- `ruoyi-ui/src/views/todo/config/journey/components/ScenarioSelector.vue`
- `ruoyi-ui/src/views/todo/config/journey/components/CompletionFormRenderer.vue`
- `ruoyi-ui/src/views/todo/config/journey/components/BatchScenarioGate.vue`
- `ruoyi-ui/src/views/todo/config/journey/components/AdvancedPayloadOverride.vue`
- `ruoyi-ui/src/views/todo/config/journey/steps/SimulationPublishStep.vue` — orchestration only.
- `ruoyi-ui/src/views/todo/config/journey/simulation-workbench-model.js` — pure grouping, labels, invalidation, and gate helpers.
- `ruoyi-ui/src/api/todo-config.js` — new option and scenario requests.

---

### Task 1: Template-Scoped Field Usage and Semantic Metadata

**Files:**
- Create: `law-todo/src/main/java/com/law/todo/application/TodoTemplateFieldUsageService.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoPayloadSchemaDescriptor.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoConfigurationResourceCatalogService.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/command/TodoConfigurationCommands.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoConfigurationResourceManagementService.java`
- Create: `ruoyi-admin/src/main/resources/db/migration/V0_20_61__todo_semantic_field_metadata.sql`
- Test: `law-todo/src/test/java/com/law/todo/application/TodoTemplateFieldUsageServiceTest.java`
- Test: `law-todo/src/test/java/com/law/todo/application/TodoConfigurationResourceCatalogServiceTest.java`
- Test: `ruoyi-admin/src/test/java/com/ruoyi/web/migration/TodoSemanticFieldMetadataMigrationContractTest.java`

**Interfaces:**
- Consumes: canonical definition JSON with `event.condition`, `owner.config`, `dod.config`, `sla.config`, `routing.config`, and `ui.config.fields`.
- Produces:

```java
public record FieldUsage(
        String path,
        String stage,
        String stepCode,
        boolean required,
        String reason) { }

public Map<String,List<FieldUsage>> usages(String definitionJson);
```

- Extends `FieldResource` with `semanticType`, `optionSource`, `dictType`, and `displayPattern`.

- [ ] **Step 1: Write field-usage tests**

Add tests that compile the TD-001 shape and assert:

```java
assertThat(result.get("ownerId"))
        .extracting(FieldUsage::stage)
        .contains("EVENT_INPUT", "OWNER_INPUT");
assertThat(result.get("contactResult"))
        .extracting(FieldUsage::stage)
        .contains("COMPLETION_INPUT", "ROUTING_INPUT");
assertThat(result).doesNotContainKeys("reviewResult", "reviewOpinion", "attemptStage", "attemptCount");
```

Also assert that `name`, `city`, `demand`, and `visited` are conditionally required only for `contactResult=VALID`.

- [ ] **Step 2: Run the tests and confirm failure**

Run:

```powershell
mvn -pl law-todo -am "-Dtest=TodoTemplateFieldUsageServiceTest,TodoConfigurationResourceCatalogServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: compilation fails because `TodoTemplateFieldUsageService` and semantic properties do not exist.

- [ ] **Step 3: Implement recursive reference extraction**

Implement explicit visitors:

```java
public Map<String,List<FieldUsage>> usages(String definitionJson)
{
    JSONObject definition=JSON.parseObject(definitionJson);
    Map<String,List<FieldUsage>> result=new LinkedHashMap<>();
    addEventSchemaFields(result,definition);
    addConditionFields(result,definition.getJSONObject("event"),"TRIGGER_INPUT","TRIGGER");
    addOwnerFields(result,definition.getJSONObject("owner"));
    addDodAndUiFields(result,definition);
    addSlaFields(result,definition.getJSONObject("sla"));
    addRoutingFields(result,definition.getJSONObject("routing"));
    return immutable(result);
}
```

Only recognized field operands are accepted. Do not treat arbitrary JSON keys, labels, node keys, template codes, or constants as field paths.

- [ ] **Step 4: Extend governed field metadata**

Parse these keys from `value_json` and event Schema:

```text
semanticType / x-semantic-type
optionSource / x-option-source
dictType / x-dict-type
displayPattern
sensitive / x-sensitive
```

Validate:

```java
if ("DICT".equals(semanticType) && (dictType==null || dictType.isBlank()))
    throw invalid("DICT field requires dictType");
if (Set.of("USER_ID","DEPT_ID","POST_ID","ROLE_KEY","BUSINESS_REF").contains(semanticType)
        && (optionSource==null || optionSource.isBlank()))
    throw invalid("Reference field requires optionSource");
```

- [ ] **Step 5: Add the semantic metadata migration**

`V0_20_61` must:

- create or update `law_lead_contact_result`, `law_lead_review_result`, and `law_lead_retry_stage`;
- attach `DICT` metadata to `contactResult`, `reviewResult`, and `attemptStage`;
- attach `USER_ID` to owner/operator/reviewer fields;
- attach `DEPT_ID` to department fields;
- add `x-semantic-type` and `x-option-source` to `LEAD_ASSIGNED` v1 without changing required field names;
- remain idempotent under a clean database build.

- [ ] **Step 6: Run focused tests**

Run:

```powershell
mvn -pl law-todo,ruoyi-admin -am "-Dtest=TodoTemplateFieldUsageServiceTest,TodoConfigurationResourceCatalogServiceTest,TodoSemanticFieldMetadataMigrationContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: all selected tests pass.

- [ ] **Step 7: Commit**

```powershell
git add law-todo/src/main/java/com/law/todo/application/TodoTemplateFieldUsageService.java law-todo/src/main/java/com/law/todo/application/TodoPayloadSchemaDescriptor.java law-todo/src/main/java/com/law/todo/application/TodoConfigurationResourceCatalogService.java law-todo/src/main/java/com/law/todo/application/command/TodoConfigurationCommands.java law-todo/src/main/java/com/law/todo/application/TodoConfigurationResourceManagementService.java law-todo/src/test/java/com/law/todo/application/TodoTemplateFieldUsageServiceTest.java law-todo/src/test/java/com/law/todo/application/TodoConfigurationResourceCatalogServiceTest.java ruoyi-admin/src/main/resources/db/migration/V0_20_61__todo_semantic_field_metadata.sql ruoyi-admin/src/test/java/com/ruoyi/web/migration/TodoSemanticFieldMetadataMigrationContractTest.java
git commit -m "feat(todo): scope semantic fields to template usage"
```

---

### Task 2: Actor-Scoped Display Resolution and Governed Options

**Files:**
- Create: `law-todo/src/main/java/com/law/todo/spi/TodoFieldReferenceDirectory.java`
- Create: `law-todo/src/main/java/com/law/todo/application/TodoFieldDisplayResolutionService.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/todo/RuoYiTodoFieldReferenceDirectory.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/mapper/TodoBusinessDirectoryMapper.java`
- Modify: `ruoyi-system/src/main/resources/mapper/system/TodoBusinessDirectoryMapper.xml`
- Modify: `law-todo/src/main/java/com/law/todo/application/command/TodoConfigurationCommands.java`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/todo/TodoConfigurationController.java`
- Test: `law-todo/src/test/java/com/law/todo/application/TodoFieldDisplayResolutionServiceTest.java`
- Test: `ruoyi-system/src/test/java/com/ruoyi/system/service/todo/RuoYiTodoFieldReferenceDirectoryTest.java`
- Test: `ruoyi-admin/src/test/java/com/ruoyi/web/controller/todo/TodoConfigurationControllerJourneyTest.java`

**Interfaces:**

```java
public interface TodoFieldReferenceDirectory
{
    boolean supports(String semanticType,String optionSource);
    Map<Object,DisplayReference> resolve(
            String semanticType,String optionSource,String dictType,
            Collection<?> rawValues,Actor actor);
    ReferencePage options(
            String semanticType,String optionSource,String dictType,
            String keyword,int offset,int limit,Actor actor);
}

public record DisplayReference(
        Object rawValue,String displayValue,Map<String,Object> meta,
        boolean selectable,boolean restricted,String invalidReason) { }
```

- [ ] **Step 1: Write resolver tests**

Cover:

```java
assertThat(result.get(11L).displayValue()).isEqualTo("张三");
assertThat(result.get(11L).meta()).containsEntry("deptName","销售一部");
assertThat(result.get("VALID").displayValue()).isEqualTo("有效首联");
assertThat(result.get(99L).restricted()).isTrue();
assertThat(result.get(88L).invalidReason()).isEqualTo("原配置对象已失效");
```

Verify one batch query per semantic group, not one query per field.

- [ ] **Step 2: Confirm test failure**

Run:

```powershell
mvn -pl law-todo,ruoyi-system,ruoyi-admin -am "-Dtest=TodoFieldDisplayResolutionServiceTest,RuoYiTodoFieldReferenceDirectoryTest,TodoConfigurationControllerJourneyTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: new SPI and endpoint types are missing.

- [ ] **Step 3: Implement the SPI and batching service**

Group values by:

```java
record ResolutionKey(String semanticType,String optionSource,String dictType) { }
```

For each key, select exactly one supporting directory. No provider returns the raw ID as `displayValue`. Missing providers produce `invalidReason="名称解析服务不可用"`.

- [ ] **Step 4: Implement RuoYi reference queries**

Support:

```text
SYSTEM_USER
SYSTEM_DEPARTMENT
SYSTEM_POST
SYSTEM_ROLE
SYSTEM_DICTIONARY
LEAD / CUSTOMER / CONTRACT / CASE / MATTER
```

User option rows must include `userName`, `nickName`, `deptName`, `postName`, and account status. Department options return `ancestors` and full path. Business references reuse actor-scoped business-directory predicates.

- [ ] **Step 5: Add the option command and endpoint**

Add:

```java
public record FieldOptionQuery(
        @NotBlank String semanticType,
        @NotBlank String optionSource,
        String dictType,
        String keyword,
        @Positive Integer pageNum,
        @Positive @Max(100) Integer pageSize) { }
```

Expose:

```http
GET /todo/config/resources/field-options
```

Require `todo:simulation:simulate` or `todo:resource:list`. Return `value`, `label`, `meta`, `disabled`, and `reason`.

- [ ] **Step 6: Run focused tests**

Run the command from Step 2. Expected: all selected tests pass and controller security tests prove unauthorized requests return 403.

- [ ] **Step 7: Commit**

```powershell
git add law-todo/src/main/java/com/law/todo/spi/TodoFieldReferenceDirectory.java law-todo/src/main/java/com/law/todo/application/TodoFieldDisplayResolutionService.java ruoyi-system/src/main/java/com/ruoyi/system/service/todo/RuoYiTodoFieldReferenceDirectory.java ruoyi-system/src/main/java/com/ruoyi/system/mapper/TodoBusinessDirectoryMapper.java ruoyi-system/src/main/resources/mapper/system/TodoBusinessDirectoryMapper.xml law-todo/src/main/java/com/law/todo/application/command/TodoConfigurationCommands.java ruoyi-admin/src/main/java/com/ruoyi/web/controller/todo/TodoConfigurationController.java law-todo/src/test/java/com/law/todo/application/TodoFieldDisplayResolutionServiceTest.java ruoyi-system/src/test/java/com/ruoyi/system/service/todo/RuoYiTodoFieldReferenceDirectoryTest.java ruoyi-admin/src/test/java/com/ruoyi/web/controller/todo/TodoConfigurationControllerJourneyTest.java
git commit -m "feat(todo): resolve semantic field labels and options"
```

---

### Task 3: Grouped Journey Payload and Creation Validation

**Files:**
- Create: `law-todo/src/main/java/com/law/todo/application/view/TodoJourneyPayloadView.java`
- Create: `law-todo/src/main/java/com/law/todo/application/TodoJourneyPayloadPreparationService.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoBusinessPayloadHydrationService.java`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/todo/TodoConfigurationController.java`
- Test: `law-todo/src/test/java/com/law/todo/application/TodoJourneyPayloadPreparationServiceTest.java`
- Test: `law-todo/src/test/java/com/law/todo/application/TodoBusinessPayloadHydrationServiceTest.java`
- Test: `ruoyi-admin/src/test/java/com/ruoyi/web/controller/todo/TodoConfigurationControllerJourneyTest.java`

**Interfaces:**

```java
public record PayloadField(
        String path,String label,Object rawValue,String displayValue,
        String semanticType,String dictType,String source,
        boolean required,boolean missing,boolean sensitive,
        boolean editable,Map<String,Object> displayMeta,
        List<FieldUsage> usages,String issueCode,String issueMessage) { }

public record TodoJourneyPayloadView(
        BusinessObjectSummary businessObject,
        List<PayloadField> eventInput,
        List<PayloadField> creationDependencies,
        List<PayloadField> completionFields,
        List<PayloadField> routingFields,
        List<PayloadField> advancedFields,
        int creationCoveragePercent,
        List<JourneyIssue> blockingIssues) { }
```

- [ ] **Step 1: Write payload preparation tests**

For TD-001 and `DEMO-L-001`, assert:

```java
assertThat(view.eventInput())
        .extracting(PayloadField::path)
        .containsExactlyInAnyOrder("schemaVersion","assignmentId","ownerId","ownerDeptId","operatorId");
assertThat(allDefaultPaths(view))
        .doesNotContain("reviewResult","reviewOpinion","attemptStage","attemptCount");
assertThat(field(view,"ownerId").displayValue()).isEqualTo("张三");
assertThat(view.creationCoveragePercent()).isEqualTo(100);
assertThat(view.blockingIssues()).isEmpty();
```

Also prove an absent optional field is not a blocker and an absent required event field is.

- [ ] **Step 2: Confirm test failure**

Run:

```powershell
mvn -pl law-todo,ruoyi-admin -am "-Dtest=TodoJourneyPayloadPreparationServiceTest,TodoBusinessPayloadHydrationServiceTest,TodoConfigurationControllerJourneyTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

- [ ] **Step 3: Implement preparation service**

The service must:

```java
TodoConfigurationJourneyView journey=journeys.load(command.templateId(),actor);
requireVersionAndHash(journey,command.versionId(),command.expectedDefinitionHash());
PayloadHydration hydration=payloads.hydrate(
        command.eventType(),command.payloadVersion(),command.businessType(),
        command.businessId(),actor,command.manualOverrides());
Map<String,List<FieldUsage>> usages=fieldUsages.usages(journey.template().definitionJson());
return groupAndResolve(hydration,usages,actor);
```

If the journey view does not expose canonical JSON, add a `definitionJson` field to its template summary and populate it from the existing template-version row. Do not re-serialize UI steps as the authoritative source.

- [ ] **Step 4: Replace controller hydration response**

Change `/templates/{id}/journey/payload` to call `TodoJourneyPayloadPreparationService.prepare`. Keep top-level `payload`, `fields`, and `coveragePercent` compatibility aliases for existing clients until the frontend migration is complete.

- [ ] **Step 5: Run focused tests**

Run the command from Step 2. Expected: all selected tests pass.

- [ ] **Step 6: Commit**

```powershell
git add law-todo/src/main/java/com/law/todo/application/view/TodoJourneyPayloadView.java law-todo/src/main/java/com/law/todo/application/TodoJourneyPayloadPreparationService.java law-todo/src/main/java/com/law/todo/application/TodoBusinessPayloadHydrationService.java ruoyi-admin/src/main/java/com/ruoyi/web/controller/todo/TodoConfigurationController.java law-todo/src/test/java/com/law/todo/application/TodoJourneyPayloadPreparationServiceTest.java law-todo/src/test/java/com/law/todo/application/TodoBusinessPayloadHydrationServiceTest.java ruoyi-admin/src/test/java/com/ruoyi/web/controller/todo/TodoConfigurationControllerJourneyTest.java
git commit -m "feat(todo): prepare grouped journey simulation payload"
```

---

### Task 4: Phase-One Simulation Workbench UI

**Files:**
- Create: `ruoyi-ui/src/views/todo/config/journey/simulation-workbench-model.js`
- Create: `ruoyi-ui/src/views/todo/config/journey/components/SemanticValueRenderer.vue`
- Create: `ruoyi-ui/src/views/todo/config/journey/components/SemanticOptionSelector.vue`
- Create: `ruoyi-ui/src/views/todo/config/journey/components/CreationValidationPanel.vue`
- Create: `ruoyi-ui/src/views/todo/config/journey/components/EventInputPanel.vue`
- Create: `ruoyi-ui/src/views/todo/config/journey/components/TodoCreationPreview.vue`
- Create: `ruoyi-ui/src/views/todo/config/journey/components/AdvancedPayloadOverride.vue`
- Modify: `ruoyi-ui/src/views/todo/config/journey/steps/SimulationPublishStep.vue`
- Modify: `ruoyi-ui/src/api/todo-config.js`
- Modify: `ruoyi-ui/scripts/check-todo-journey-model.js`
- Modify: `ruoyi-ui/scripts/check-todo-phase-two-ux.js`

**Interfaces:**

```javascript
export function fieldState(field) {}
export function groupedCreationContext(response) {}
export function updateSemanticOverride(current, field, option) {}
export function invalidateSimulationState(state, nextDefinitionHash) {}
```

- [ ] **Step 1: Add failing frontend contract tests**

Add assertions:

```javascript
assert.equal(fieldState({ required: false, missing: true }).label, '可选')
assert.equal(fieldState({ required: true, missing: true }).label, '待补充')
assert.equal(fieldState({ source: 'SYSTEM_DEFAULT' }).label, '系统计算')
assert.equal(renderedPaths.includes('reviewResult'), false)
assert.equal(renderedPaths.includes('attemptStage'), false)
assert.equal(owner.displayValue, '张三')
assert.equal(owner.rawValueVisible, false)
```

Static UX checks must require `SemanticValueRenderer`, `SemanticOptionSelector`, and the advanced technical section to be collapsed by default.

- [ ] **Step 2: Confirm contract failure**

Run:

```powershell
Set-Location ruoyi-ui
npm run test:todo-phase-two
```

Expected: missing workbench model and components.

- [ ] **Step 3: Implement semantic renderer and selector**

`SemanticValueRenderer` shows `displayValue` and secondary metadata. `SemanticOptionSelector` calls:

```javascript
export function listTodoFieldOptions(params) {
  return request({ url: '/todo/config/resources/field-options', method: 'get', params })
}
```

It emits the raw `value`, never the label. `USER_ID`, `DEPT_ID`, `POST_ID`, `ROLE_KEY`, and `DICT` use governed choices; `PLAIN_VALUE` uses a typed input.

- [ ] **Step 4: Implement creation panels**

Render:

- selected business object;
- `eventInput`;
- `creationDependencies`;
- resolved owner and fallback;
- SLA timeline;
- Todo card preview;
- blocker list;
- collapsed `advancedFields`.

Do not iterate the legacy flat `hydration.fields` in the default view.

- [ ] **Step 5: Run frontend tests and build**

Run:

```powershell
Set-Location ruoyi-ui
npm run test:todo-phase-two
npm run test:todo-config
npm run test:encoding
npm run build:prod
```

Expected: all scripts pass; production build completes with no new warnings.

- [ ] **Step 6: Commit**

```powershell
git add ruoyi-ui/src/views/todo/config/journey/simulation-workbench-model.js ruoyi-ui/src/views/todo/config/journey/components/SemanticValueRenderer.vue ruoyi-ui/src/views/todo/config/journey/components/SemanticOptionSelector.vue ruoyi-ui/src/views/todo/config/journey/components/CreationValidationPanel.vue ruoyi-ui/src/views/todo/config/journey/components/EventInputPanel.vue ruoyi-ui/src/views/todo/config/journey/components/TodoCreationPreview.vue ruoyi-ui/src/views/todo/config/journey/components/AdvancedPayloadOverride.vue ruoyi-ui/src/views/todo/config/journey/steps/SimulationPublishStep.vue ruoyi-ui/src/api/todo-config.js ruoyi-ui/scripts/check-todo-journey-model.js ruoyi-ui/scripts/check-todo-phase-two-ux.js
git commit -m "feat(todo-ui): clarify creation simulation inputs"
```

---

### Task 5: Governed Scenario Resources and Evidence Schema

**Files:**
- Create: `ruoyi-admin/src/main/resources/db/migration/V0_20_62__todo_simulation_scenarios_and_evidence.sql`
- Modify: `law-todo/src/main/java/com/law/todo/application/command/TodoConfigurationCommands.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoConfigurationResourceManagementService.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoConfigurationResourceCatalogService.java`
- Modify: `law-todo/src/main/java/com/law/todo/mapper/TodoConfigurationMapper.java`
- Modify: `law-todo/src/main/resources/mapper/todo/TodoConfigurationMapper.xml`
- Create: `law-todo/src/main/java/com/law/todo/application/TodoSimulationScenarioCatalog.java`
- Create: `law-todo/src/main/java/com/law/todo/application/view/TodoSimulationScenarioViews.java`
- Test: `law-todo/src/test/java/com/law/todo/application/TodoSimulationScenarioCatalogTest.java`
- Test: `law-todo/src/test/java/com/law/todo/application/TodoConfigurationResourceManagementServiceTest.java`
- Test: `ruoyi-admin/src/test/java/com/ruoyi/web/migration/TodoSimulationScenarioMigrationContractTest.java`

**Interfaces:**

```java
public record SimulationScenario(
        long resourceItemId,String scenarioCode,String templateCode,
        String scenarioName,int scenarioVersion,
        Map<String,Object> completionPayload,List<String> editableFields,
        List<String> requiredMaterials,String completionNodeKey,
        int occurrence,String expectedNextTemplateCode,
        boolean requiredForPublish,String status,int sortOrder) { }

public List<SimulationScenario> scenarios(String templateCode);
```

- [ ] **Step 1: Add failing scenario catalog and migration tests**

Assert exactly three active required TD-001 scenarios:

```java
assertThat(catalog.scenarios("TD-001"))
        .extracting(SimulationScenario::scenarioCode)
        .containsExactly("TD001_VALID","TD001_SUSPECT_INVALID","TD001_UNREACHABLE");
assertThat(catalog.scenarios("TD-001"))
        .extracting(SimulationScenario::expectedNextTemplateCode)
        .containsExactly("TD-004","TD-002","TD-003");
```

Migration test must assert the evidence unique key:

```text
version_id + definition_hash + scenario_code + scenario_version + input_hash
```

- [ ] **Step 2: Confirm test failure**

Run:

```powershell
mvn -pl law-todo,ruoyi-admin -am "-Dtest=TodoSimulationScenarioCatalogTest,TodoConfigurationResourceManagementServiceTest,TodoSimulationScenarioMigrationContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

- [ ] **Step 3: Create the migration**

Create `todo_simulation_evidence` with:

```sql
evidence_id bigint not null auto_increment,
template_id bigint not null,
version_id bigint not null,
definition_hash varchar(64) not null,
scenario_code varchar(64) not null,
scenario_version int not null,
result_status varchar(16) not null,
input_hash varchar(64) not null,
trace_summary_json json not null,
executed_by bigint not null,
executed_time datetime not null,
expire_time datetime null,
primary key (evidence_id)
```

Add indexes for current-version gate lookup and the unique idempotency key. Seed all three scenarios as `SIMULATION_SCENARIO` resources. `completionPayload` uses `${SIMULATION_NOW}`, not a fixed date.

- [ ] **Step 4: Extend resource validation**

Allow `SIMULATION_SCENARIO` in `ConfigurationResourceCommand`. Validate all required scenario keys, existing template codes, known fields, known materials, and a nonblank `expectedNextTemplateCode`.

- [ ] **Step 5: Implement catalog reads**

Parse resource JSON into immutable `SimulationScenario`. Reject duplicate active `scenarioCode` rows and unsupported placeholder expressions.

- [ ] **Step 6: Run focused tests**

Run the command from Step 2. Expected: all selected tests pass.

- [ ] **Step 7: Commit**

```powershell
git add ruoyi-admin/src/main/resources/db/migration/V0_20_62__todo_simulation_scenarios_and_evidence.sql law-todo/src/main/java/com/law/todo/application/command/TodoConfigurationCommands.java law-todo/src/main/java/com/law/todo/application/TodoConfigurationResourceManagementService.java law-todo/src/main/java/com/law/todo/application/TodoConfigurationResourceCatalogService.java law-todo/src/main/java/com/law/todo/mapper/TodoConfigurationMapper.java law-todo/src/main/resources/mapper/todo/TodoConfigurationMapper.xml law-todo/src/main/java/com/law/todo/application/TodoSimulationScenarioCatalog.java law-todo/src/main/java/com/law/todo/application/view/TodoSimulationScenarioViews.java law-todo/src/test/java/com/law/todo/application/TodoSimulationScenarioCatalogTest.java law-todo/src/test/java/com/law/todo/application/TodoConfigurationResourceManagementServiceTest.java ruoyi-admin/src/test/java/com/ruoyi/web/migration/TodoSimulationScenarioMigrationContractTest.java
git commit -m "feat(todo): govern simulation scenarios and evidence"
```

---

### Task 6: Scenario Execution, Evidence, and Publication Gate

**Files:**
- Create: `law-todo/src/main/java/com/law/todo/application/TodoSimulationEvidenceService.java`
- Create: `law-todo/src/main/java/com/law/todo/application/TodoSimulationScenarioService.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoJourneySimulationService.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoDefinitionService.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/command/TodoConfigurationCommands.java`
- Modify: `law-todo/src/main/java/com/law/todo/mapper/TodoConfigurationMapper.java`
- Modify: `law-todo/src/main/resources/mapper/todo/TodoConfigurationMapper.xml`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/todo/TodoConfigurationController.java`
- Test: `law-todo/src/test/java/com/law/todo/application/TodoSimulationScenarioServiceTest.java`
- Test: `law-todo/src/test/java/com/law/todo/application/TodoSimulationEvidenceServiceTest.java`
- Test: `law-todo/src/test/java/com/law/todo/application/TodoDefinitionServiceTest.java`
- Test: `ruoyi-admin/src/test/java/com/ruoyi/web/controller/todo/TodoConfigurationControllerJourneyTest.java`

**Interfaces:**

```java
public record ScenarioSimulationCommand(
        @NotNull Long versionId,
        @NotBlank String definitionHash,
        @NotBlank String businessType,
        @NotNull Long businessId,
        Map<String,Object> manualOverrides,
        @NotNull LocalDateTime effectiveAt,
        @NotBlank String requestId) { }

public ScenarioSimulationResult simulate(
        long templateId,String scenarioCode,
        ScenarioSimulationCommand command,Actor actor);

public BatchScenarioResult simulateRequired(
        long templateId,ScenarioSimulationCommand command,Actor actor);
```

- [ ] **Step 1: Write scenario execution tests**

For each TD-001 scenario, assert:

```java
assertThat(valid.expectedNextTemplateCode()).isEqualTo("TD-004");
assertThat(valid.actualNextTemplateCode()).isEqualTo("TD-004");
assertThat(suspect.actualNextTemplateCode()).isEqualTo("TD-002");
assertThat(unreachable.actualNextTemplateCode()).isEqualTo("TD-003");
assertThat(result.passed()).isTrue();
```

Assert evidence stores no `name`, `city`, `demand`, phone, or raw completion payload. Assert a definition-hash change makes `publicationReady` false.

- [ ] **Step 2: Confirm test failure**

Run:

```powershell
mvn -pl law-todo,ruoyi-admin -am "-Dtest=TodoSimulationScenarioServiceTest,TodoSimulationEvidenceServiceTest,TodoDefinitionServiceTest,TodoConfigurationControllerJourneyTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

- [ ] **Step 3: Implement scenario command materialization**

Resolve:

```java
Map<String,Object> completion=resolveScenarioPayload(
        scenario.completionPayload(),command.effectiveAt(),command.manualOverrides());
VirtualTaskCompletionSample sample=new VirtualTaskCompletionSample(
        scenario.completionNodeKey(),scenario.occurrence(),completion,command.effectiveAt());
```

Call the existing journey simulation with this completion. Determine `actualNextTemplateCode` from the sanitized route trace and compare it to the governed expectation.

- [ ] **Step 4: Implement evidence**

Compute:

```text
inputHash = SHA-256(
  templateId + versionId + definitionHash + scenarioCode +
  scenarioVersion + businessType + businessId +
  canonicalizedManualOverrides + effectiveAt
)
```

Persist only status and redacted trace summary. Reuse evidence for an identical key. Mark evidence invalid when the scenario is disabled, version changes, definition hash changes, or `expire_time` passes.

- [ ] **Step 5: Add endpoints**

Expose:

```http
GET  /todo/config/templates/{id}/journey/scenarios
POST /todo/config/templates/{id}/journey/scenarios/{scenarioCode}/simulate
POST /todo/config/templates/{id}/journey/scenarios/batch-simulate
```

Require `todo:simulation:simulate`; batch results preserve scenario order and report every failure instead of stopping at the first failure.

- [ ] **Step 6: Add publication preflight gate**

`TodoDefinitionService.preflight(versionId)` must add blocker:

```text
TODO_REQUIRED_SIMULATION_SCENARIOS_INCOMPLETE
```

when any active `requiredForPublish` scenario lacks passing evidence for the exact current definition hash and scenario version.

- [ ] **Step 7: Run focused tests**

Run the command from Step 2. Expected: all selected tests pass.

- [ ] **Step 8: Commit**

```powershell
git add law-todo/src/main/java/com/law/todo/application/TodoSimulationEvidenceService.java law-todo/src/main/java/com/law/todo/application/TodoSimulationScenarioService.java law-todo/src/main/java/com/law/todo/application/TodoJourneySimulationService.java law-todo/src/main/java/com/law/todo/application/TodoDefinitionService.java law-todo/src/main/java/com/law/todo/application/command/TodoConfigurationCommands.java law-todo/src/main/java/com/law/todo/mapper/TodoConfigurationMapper.java law-todo/src/main/resources/mapper/todo/TodoConfigurationMapper.xml ruoyi-admin/src/main/java/com/ruoyi/web/controller/todo/TodoConfigurationController.java law-todo/src/test/java/com/law/todo/application/TodoSimulationScenarioServiceTest.java law-todo/src/test/java/com/law/todo/application/TodoSimulationEvidenceServiceTest.java law-todo/src/test/java/com/law/todo/application/TodoDefinitionServiceTest.java ruoyi-admin/src/test/java/com/ruoyi/web/controller/todo/TodoConfigurationControllerJourneyTest.java
git commit -m "feat(todo): gate releases with scenario evidence"
```

---

### Task 7: Scenario Workbench and Batch Gate UI

**Files:**
- Create: `ruoyi-ui/src/views/todo/config/journey/components/ScenarioSelector.vue`
- Create: `ruoyi-ui/src/views/todo/config/journey/components/CompletionFormRenderer.vue`
- Create: `ruoyi-ui/src/views/todo/config/journey/components/BatchScenarioGate.vue`
- Modify: `ruoyi-ui/src/views/todo/config/journey/components/SimulationTrace.vue`
- Modify: `ruoyi-ui/src/views/todo/config/journey/components/PublishPreflightPanel.vue`
- Modify: `ruoyi-ui/src/views/todo/config/journey/steps/SimulationPublishStep.vue`
- Modify: `ruoyi-ui/src/views/todo/config/journey/simulation-workbench-model.js`
- Modify: `ruoyi-ui/src/api/todo-config.js`
- Modify: `ruoyi-ui/scripts/check-todo-journey-model.js`
- Modify: `ruoyi-ui/scripts/check-todo-phase-two-ux.js`
- Modify: `ruoyi-ui/tests/e2e/todo-config-journey.spec.js`

**Interfaces:**

```javascript
export function listJourneyScenarios(id) {}
export function simulateJourneyScenario(id, scenarioCode, data) {}
export function batchSimulateJourneyScenarios(id, data) {}
export function scenarioFormFields(scenario, completionFields) {}
export function scenarioGate(scenarios, evidence, definitionHash) {}
```

- [ ] **Step 1: Add failing frontend and E2E assertions**

Require:

```text
有效首联
疑似无效
未接通
验证待办创建
运行当前场景
批量验证三个场景
预期下一待办
实际下一待办
```

Assert `contactResult` is a governed dictionary selector, `contactedAt` defaults to the simulation time, and `reviewResult` never appears in the TD-001 valid scenario.

- [ ] **Step 2: Confirm failure**

Run:

```powershell
Set-Location ruoyi-ui
npm run test:todo-phase-two
npm run test:e2e:contract
```

- [ ] **Step 3: Implement scenario cards and dynamic forms**

Cards show name, purpose, expected next template, last evidence status, and stale status. Form fields are derived from `editableFields` intersected with `completionFields`. Controlled semantic selectors are reused from Task 4.

- [ ] **Step 4: Implement single and batch execution**

Single execution updates only its card and trace. Batch execution runs the required list returned by the server and shows all outcomes. A new definition hash clears all local evidence and disables publication until rerun.

- [ ] **Step 5: Integrate preflight and repair navigation**

The gate panel lists missing or failed scenario codes. Clicking a failure opens its card and trace; clicking a configuration blocker returns to the exact EVENT, OWNER, DOD, SLA, or ROUTING step.

- [ ] **Step 6: Run frontend suite and build**

Run:

```powershell
Set-Location ruoyi-ui
npm run test:todo
npm run test:todo-config
npm run test:todo-phase-two
npm run test:e2e:contract
npm run test:encoding
npm run build:prod
```

Expected: all scripts pass and production build completes.

- [ ] **Step 7: Commit**

```powershell
git add ruoyi-ui/src/views/todo/config/journey/components/ScenarioSelector.vue ruoyi-ui/src/views/todo/config/journey/components/CompletionFormRenderer.vue ruoyi-ui/src/views/todo/config/journey/components/BatchScenarioGate.vue ruoyi-ui/src/views/todo/config/journey/components/SimulationTrace.vue ruoyi-ui/src/views/todo/config/journey/components/PublishPreflightPanel.vue ruoyi-ui/src/views/todo/config/journey/steps/SimulationPublishStep.vue ruoyi-ui/src/views/todo/config/journey/simulation-workbench-model.js ruoyi-ui/src/api/todo-config.js ruoyi-ui/scripts/check-todo-journey-model.js ruoyi-ui/scripts/check-todo-phase-two-ux.js ruoyi-ui/tests/e2e/todo-config-journey.spec.js
git commit -m "feat(todo-ui): run governed completion scenarios"
```

---

### Task 8: MySQL Read-Only Proof, Full Regression, and Chrome Acceptance

**Files:**
- Create: `ruoyi-admin/src/test/java/com/ruoyi/web/migration/TodoScenarioSimulationExternalMysqlIT.java`
- Modify: `ruoyi-ui/tests/e2e/support/todo-config-e2e-database.js`
- Modify: `ruoyi-ui/tests/e2e/todo-config-journey.spec.js`
- Modify: `ruoyi-ui/scripts/check-todo-config-real-e2e.js`
- Create: `doc/reviews/todo-scenario-semantic-fields-acceptance.md`

**Interfaces:**
- Consumes all Tasks 1–7.
- Produces authoritative MySQL, frontend build, and real-Chrome evidence for every completion criterion.

- [ ] **Step 1: Write the external MySQL integration test**

Before each simulation, snapshot row counts and stable hashes for:

```text
todo_instance
todo_action_log
business_event
biz_lead
biz_lead_followup
biz_lead_assignment_log
biz_lead_call_record
biz_lead_invalid_review
biz_lead_retry_plan
```

After all three scenarios, assert those values are unchanged. Assert only `todo_simulation_evidence` gained rows and that its JSON contains none of the submitted customer values.

- [ ] **Step 2: Run the MySQL integration test**

Use the repository’s disposable external-MySQL profile and command documented by `TodoScenarioSimulationExternalMysqlIT`. Expected: TD-004, TD-002, and TD-003 are observed as simulated route targets; runtime and business snapshots remain identical.

- [ ] **Step 3: Run full Maven regression**

Run:

```powershell
mvn clean test
```

Expected: all reactor modules pass with no skipped scenario-specific tests.

- [ ] **Step 4: Run full frontend regression**

Run:

```powershell
Set-Location ruoyi-ui
npm run test:todo
npm run test:todo-config
npm run test:todo-phase-two
npm run test:todo-schema
npm run test:foundation-ci
npm run test:foundation-docs
npm run test:foundation-identities
npm run test:e2e:server
npm run test:e2e:db-safety
npm run test:e2e:contract
npm run test:lead-e2e-helpers
npm run test:encoding
npm run build:prod
```

Expected: every command exits 0.

- [ ] **Step 5: Start latest backend and frontend**

Build the executable backend JAR, start it against the disposable migrated MySQL and Redis environment, then start the frontend development server. Verify:

```text
GET http://127.0.0.1:8080/captchaImage = 200
GET http://127.0.0.1/dev-api/captchaImage = 200
GET http://127.0.0.1/ = 200
```

- [ ] **Step 6: Run real Chrome acceptance**

Using Playwright CLI:

1. log in as `todo_config_admin`;
2. open TD-001 configuration journey;
3. load `DEMO-L-001`;
4. prove event input has no unrelated missing fields;
5. prove owner and department display names, not IDs;
6. prove dictionary selectors show labels;
7. validate creation;
8. run valid, suspect-invalid, and unreachable scenarios;
9. run batch verification;
10. prove preflight allows publication only after all evidence passes;
11. confirm console has zero errors and the page contains no garbled text.

- [ ] **Step 7: Write the acceptance report**

Record:

- commit hash;
- commands and exit codes;
- MySQL before/after proof;
- scenario expected/actual route table;
- screenshots;
- console error count;
- known warnings, limited to pre-existing build warnings with evidence.

- [ ] **Step 8: Commit**

```powershell
git add ruoyi-admin/src/test/java/com/ruoyi/web/migration/TodoScenarioSimulationExternalMysqlIT.java ruoyi-ui/tests/e2e/support/todo-config-e2e-database.js ruoyi-ui/tests/e2e/todo-config-journey.spec.js ruoyi-ui/scripts/check-todo-config-real-e2e.js doc/reviews/todo-scenario-semantic-fields-acceptance.md
git commit -m "test(todo): prove scenario simulation end to end"
```

- [ ] **Step 9: Completion audit**

Map every requirement in `docs/superpowers/specs/2026-07-28-todo-simulation-scenario-and-semantic-fields-design.md` to a passing test, current file, MySQL proof, or Chrome artifact. Any missing or indirect evidence keeps the objective open.

