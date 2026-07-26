# Lead Todo Flow Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the lead-stage Todo flow production-runnable from tag confirmation and round-robin assignment through first contact, valid, suspect-invalid, unreachable retry, Dead-Pool, and the TD-004 handoff, with real backend, frontend, MySQL, and browser verification.

**Architecture:** Keep lead business tables as the source of truth and use Todo Engine for event-driven assignment, DoD, SLA, schedule windows, routing, and audit. Add reusable engine primitives first, then lead commands/handlers, publish numeric-version routing graphs, and finally expose focused operational pages and real E2E acceptance.

**Tech Stack:** Java 17, Spring Boot 3.5, MyBatis, Flyway, MySQL 8, Quartz, Fastjson2, Vue 2.6, Element UI, Node contract tests, Playwright/Chrome.

## Global Constraints

- Work only in the existing linked worktree on branch `runtime/startup-wiring-fix`.
- Preserve user-owned changes in `.superpowers/sdd/task-7-report.md`, `ruoyi-ui/vue.config.js`, `.playwright-cli/`, `.runtime-logs/`, `output/`, and `test-results/`.
- Do not modify an already executed Flyway migration; add forward-only migrations after `V0_20_45`.
- Keep business facts in `ruoyi-system`; Todo Engine owns orchestration, timing, assignment, dynamic forms, and audit.
- Every production behavior follows red-green-refactor and each failing test must be observed before implementation.
- Do not create orphan Todo instances: unresolved Owner/candidate is a hard runtime failure.
- Do not bind a concrete outbound-call vendor; implement manual evidence plus a vendor-neutral adapter contract.
- Mock frontend tests do not count as business acceptance; final acceptance uses real backend, MySQL, file center, and Chrome.
- TD-004 is the handoff boundary for this plan; its downstream five-day/TD-005 cycle is not implemented here.

---

## File Structure Map

### Todo Engine

- `law-todo/.../validation/TodoEventPayloadValidator.java`: validates runtime payloads against the governed event-schema subset.
- `law-todo/.../spi/MapperTodoOrganizationAdapter.java`: production business owner, supervisor, availability, delegation, and round-robin persistence adapter.
- `law-todo/.../schedule/TodoScheduleService.java`: creates, advances, cancels, and idempotently materializes schedule windows.
- `law-todo/.../job/TodoScheduleTask.java`: Quartz entry point for due windows.
- `law-todo/.../application/TodoFormOptionService.java`: resolves governed dictionary options for dynamic forms.

### Lead Business

- `law-business/.../lead/dto/*Command.java`: typed commands used by controllers and Todo handlers.
- `ruoyi-system/.../service/lead/LeadFirstContactService.java`: first-contact facts and branch events.
- `ruoyi-system/.../service/lead/LeadInvalidReviewService.java`: review, default confirmation, Dead-Pool, and quality correction.
- `ruoyi-system/.../service/lead/LeadRetryService.java`: retry plans, calls, window outcomes, success, exhaustion, and cancellation.
- `ruoyi-system/.../service/lead/LeadTagConfirmationService.java`: source-tag confirmation.
- `ruoyi-system/.../service/lead/LeadAssignmentPolicyService.java`: governed round-robin policies.
- `ruoyi-system/.../service/event/Lead*TodoHandler.java`: thin adapters from Todo payloads into lead commands.

### Frontend

- `ruoyi-ui/src/views/lead/components/LeadFirstContactDrawer.vue`: focused first-contact handling.
- `ruoyi-ui/src/views/lead/review/index.vue`: supervisor invalid-review queue.
- `ruoyi-ui/src/views/lead/retry/index.vue`: retry timeline and window operations.
- `ruoyi-ui/src/views/lead/dead-pool/index.vue`: isolated Dead-Pool.
- `ruoyi-ui/src/views/lead/policy/index.vue`: assignment and retry policy administration.

---

### Task 1: Runtime event-schema validation and `LEAD_ASSIGNED` contract

**Files:**
- Create: `law-todo/src/main/java/com/law/todo/definition/validation/TodoEventPayloadValidator.java`
- Modify: `law-todo/src/main/java/com/law/todo/integration/TodoEventService.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/lead/LeadAssignmentService.java`
- Test: `law-todo/src/test/java/com/law/todo/definition/TodoEventPayloadValidatorTest.java`
- Test: `law-todo/src/test/java/com/law/todo/integration/TodoEventServiceTest.java`
- Test: `ruoyi-system/src/test/java/com/ruoyi/system/service/lead/LeadAssignmentServiceTest.java`

**Interfaces:**
- Produces: `TodoEventPayloadValidator.validate(String schemaJson, Map<String,Object> payload)`.
- Produces: `LEAD_ASSIGNED` payload with `schemaVersion`, `assignmentId`, `ownerId`, `ownerDeptId`, and `operatorId`.
- Consumes: active event Schema from `TodoEventCatalogService`.

- [ ] **Step 1: Write failing validator tests**

```java
@Test void rejects_missing_required_owner() {
    TodoException error=assertThrows(TodoException.class,
        ()->validator.validate(schema(),Map.of("assignmentId",1L)));
    assertEquals("TODO_EVENT_PAYLOAD_INVALID",error.getBusinessCode());
}

@Test void accepts_declared_integer_and_date_time_fields() {
    assertDoesNotThrow(()->validator.validate(schema(),
        Map.of("assignmentId",1L,"ownerId",11L,"contactedAt","2026-07-25T09:30:00")));
}
```

- [ ] **Step 2: Run the validator test and confirm RED**

Run:

```powershell
mvn -pl law-todo -Dtest=TodoEventPayloadValidatorTest test
```

Expected: test compilation fails because `TodoEventPayloadValidator` does not exist.

- [ ] **Step 3: Implement the governed Schema subset**

Implement required fields, object/string/integer/number/boolean/array types, string `enum`, and `date-time` format. Throw only:

```java
new TodoException("TODO_EVENT_PAYLOAD_INVALID",
    "Event payload field is missing or invalid: "+field);
```

Do not include field values in error messages.

- [ ] **Step 4: Integrate validation before trigger matching and Owner resolution**

In `TodoEventService.handle`, call:

```java
payloadValidator.validate(eventSchema,event.payload());
```

before `selectTriggerRules` and before any insert.

- [ ] **Step 5: Write and observe the failing assignment contract**

Change `LeadAssignmentServiceTest` to assert:

```java
event.getPayload().get("assignmentId").equals(21L)
&& event.getPayload().get("ownerId").equals(9L)
&& event.getPayload().containsKey("ownerDeptId")
&& !event.getPayload().containsKey("toOwnerId")
```

Run:

```powershell
mvn -pl ruoyi-system -am -Dtest=LeadAssignmentServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because the current producer still emits `toOwnerId`.

- [ ] **Step 6: Fix the producer at the source**

After the assignment update, read the target user department through a focused mapper query and publish canonical fields. Keep the existing idempotency key `LEAD_ASSIGNED:{leadId}:{assignmentLogId}`.

- [ ] **Step 7: Run focused and regression tests**

```powershell
mvn -pl law-todo,ruoyi-system -am "-Dtest=TodoEventPayloadValidatorTest,TodoEventServiceTest,LeadAssignmentServiceTest,LeadPoolServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: all selected tests PASS.

- [ ] **Step 8: Commit**

```powershell
git add law-todo/src/main/java/com/law/todo/definition/validation/TodoEventPayloadValidator.java law-todo/src/main/java/com/law/todo/integration/TodoEventService.java law-todo/src/test/java/com/law/todo/definition/TodoEventPayloadValidatorTest.java law-todo/src/test/java/com/law/todo/integration/TodoEventServiceTest.java ruoyi-system/src/main/java/com/ruoyi/system/service/lead/LeadAssignmentService.java ruoyi-system/src/test/java/com/ruoyi/system/service/lead/LeadAssignmentServiceTest.java
git commit -m "fix(todo): enforce lead assignment event contract"
```

### Task 2: Production Owner, supervisor, availability, delegation, and round-robin

**Files:**
- Create: `ruoyi-admin/src/main/resources/db/migration/V0_20_46__todo_assignment_runtime.sql`
- Modify: `law-todo/src/main/java/com/law/todo/spi/MapperTodoOrganizationAdapter.java`
- Modify: `law-todo/src/main/java/com/law/todo/mapper/TodoMapper.java`
- Modify: `law-todo/src/main/resources/mapper/todo/TodoMapper.xml`
- Modify: `law-todo/src/main/java/com/law/todo/integration/TodoEventService.java`
- Test: `law-todo/src/test/java/com/law/todo/spi/MapperTodoOrganizationAdapterTest.java`
- Test: `law-todo/src/test/java/com/law/todo/integration/TodoEventServiceTest.java`
- Test: `law-todo/src/test/java/com/law/todo/integration/LeadTodoAssignmentMigrationContractTest.java`

**Interfaces:**
- Produces tables `todo_round_robin_cursor`, `sys_user_availability`, `sys_user_delegation`.
- Produces atomic mapper method `selectAndAdvanceRoundRobin(String strategyKey,List<Long> candidates)`.
- Produces non-empty implementations for `businessOwner`, `supervisor`, `roundRobin`, `isAvailable`, and `delegateFor`.

- [ ] **Step 1: Write failing migration and adapter tests**

```java
@Test void round_robin_advances_atomically_across_available_candidates() {
    when(mapper.selectAndAdvanceRoundRobin("lead:sales",List.of(11L,12L))).thenReturn(12L);
    assertEquals(12L,adapter.roundRobin("lead:sales",List.of(11L,12L)).orElseThrow());
}

@Test void unavailable_primary_uses_active_delegate() {
    when(mapper.countAvailableUser(11L,NOW)).thenReturn(0);
    when(mapper.selectActiveDelegate(11L,NOW)).thenReturn(12L);
    assertFalse(adapter.isAvailable(11L,NOW));
    assertEquals(12L,adapter.delegateFor(11L,NOW).orElseThrow());
}
```

- [ ] **Step 2: Run and confirm RED**

```powershell
mvn -pl law-todo -Dtest=MapperTodoOrganizationAdapterTest,LeadTodoAssignmentMigrationContractTest test
```

Expected: FAIL because the mapper and migration do not exist.

- [ ] **Step 3: Add forward-only schema**

Create:

```sql
todo_round_robin_cursor(
  strategy_key varchar(128) primary key,
  last_user_id bigint null,
  version int not null default 0,
  update_time datetime not null
)
```

and effective-dated availability/delegation tables with unique keys and indexes on user/status/time.

- [ ] **Step 4: Implement atomic organization lookups**

Use a transaction and `select ... for update` for the cursor. Candidate order must be stable and the chosen ID must belong to the supplied available set. Supervisor lookup follows department leaders upward. `isAvailable` must require an enabled account and no active `UNAVAILABLE` row.

- [ ] **Step 5: Refuse orphan Todo creation**

After canonical Owner resolution:

```java
if(resolved.ownerId()==null && resolved.candidateUserIds().isEmpty())
    throw new TodoException("TODO_OWNER_UNRESOLVED",
        "No eligible owner or candidate is available");
```

- [ ] **Step 6: Verify focused behavior**

```powershell
mvn -pl law-todo -Dtest=MapperTodoOrganizationAdapterTest,CompositeOwnerResolverTest,TodoEventServiceTest,LeadTodoAssignmentMigrationContractTest test
```

Expected: PASS, including leave skip, delegate, supervisor fallback, fair rotation, and no-candidate rejection.

- [ ] **Step 7: Commit**

```powershell
git add ruoyi-admin/src/main/resources/db/migration/V0_20_46__todo_assignment_runtime.sql law-todo/src/main/java/com/law/todo/spi/MapperTodoOrganizationAdapter.java law-todo/src/main/java/com/law/todo/mapper/TodoMapper.java law-todo/src/main/resources/mapper/todo/TodoMapper.xml law-todo/src/main/java/com/law/todo/integration/TodoEventService.java law-todo/src/test/java/com/law/todo/spi/MapperTodoOrganizationAdapterTest.java law-todo/src/test/java/com/law/todo/integration/TodoEventServiceTest.java law-todo/src/test/java/com/law/todo/integration/LeadTodoAssignmentMigrationContractTest.java
git commit -m "feat(todo): add production assignment resolution"
```

### Task 3: Reusable schedule-window engine

**Files:**
- Create: `ruoyi-admin/src/main/resources/db/migration/V0_20_47__todo_schedule_windows.sql`
- Create: `law-todo/src/main/java/com/law/todo/schedule/TodoScheduleService.java`
- Create: `law-todo/src/main/java/com/law/todo/schedule/TodoScheduleWindow.java`
- Create: `law-todo/src/main/java/com/law/todo/job/TodoScheduleTask.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoRoutingService.java`
- Modify: `law-todo/src/main/java/com/law/todo/mapper/TodoMapper.java`
- Modify: `law-todo/src/main/resources/mapper/todo/TodoMapper.xml`
- Test: `law-todo/src/test/java/com/law/todo/schedule/TodoScheduleServiceTest.java`
- Test: `law-todo/src/test/java/com/law/todo/job/TodoScheduleTaskTest.java`
- Test: `law-todo/src/test/java/com/law/todo/integration/TodoScheduleMigrationContractTest.java`

**Interfaces:**
- Produces `createPlan(CreateSchedulePlanCommand)`, `materializeDue(LocalDateTime,int)`, `completeOccurrence(...)`, and `cancelPlan(...)`.
- Produces unique occurrence key `planId:windowCode:occurrenceNo`.
- Consumes a published template version ID and calls
  `TodoRoutingService.createScheduledNext(previousTodo,templateVersionId,occurrenceKey,dueAt)`.
- `createScheduledNext` uses `SCHEDULE:{occurrenceKey}` as `next_idempotency_key`, so multiple
  windows can create separate TD-003 instances while replaying one window returns the same Todo.

- [ ] **Step 1: Write failing schedule-domain tests**

```java
@Test void materializes_each_due_window_once() {
    service.materializeDue(NOW,100);
    service.materializeDue(NOW,100);
    verify(mapper,times(1)).insertScheduleOccurrenceIfAbsent(argThat(
        row->"T1_AM".equals(row.get("windowCode"))));
}

@Test void connected_result_cancels_future_windows() {
    service.completeOccurrence(9L,"CONNECTED",NOW);
    verify(mapper).cancelFutureScheduleWindows(3L,"CONTACTED",NOW);
}
```

- [ ] **Step 2: Run and confirm RED**

```powershell
mvn -pl law-todo -Dtest=TodoScheduleServiceTest,TodoScheduleTaskTest,TodoScheduleMigrationContractTest test
```

Expected: compilation FAIL because schedule classes are absent.

- [ ] **Step 3: Add schedule tables**

Create `todo_schedule_plan`, `todo_schedule_window`, and `todo_schedule_occurrence` with:

```text
unique(plan_id,window_code,occurrence_no)
index(status,due_at)
version columns for conditional updates
```

- [ ] **Step 4: Implement T0/T+1/T+2 calculation**

Default windows in `Asia/Shanghai`:

```text
T0: start=first-contact completion time, end=start+2h, maxAttempts=3
T1_AM/T2_AM: 09:00-11:00
T1_NOON/T2_NOON: 12:00-14:00
T1_PM/T2_PM: 15:00-18:00
```

Store calculated absolute dates so later policy edits do not change existing plans.

- [ ] **Step 5: Add the schedule-specific routing boundary**

Implement:

```java
TodoInstance createScheduledNext(TodoInstance previous,Long templateVersionId,
    String occurrenceKey,LocalDateTime dueAt);
```

It reuses canonical template/Owner/relation creation, overrides the instance due time with the
persisted window end, creates the SLA record from that due time, and rejects a blank occurrence key.

- [ ] **Step 6: Implement idempotent materialization and cancellation**

The job claims due rows conditionally, creates the target Todo once, and links the resulting `todo_id` back to the occurrence. A failed Todo creation resets the occurrence to retryable with an error code.

- [ ] **Step 7: Run focused tests**

```powershell
mvn -pl law-todo -Dtest=TodoScheduleServiceTest,TodoScheduleTaskTest,TodoScheduleMigrationContractTest test
```

Expected: PASS with no duplicate occurrence.

- [ ] **Step 8: Commit**

```powershell
git add ruoyi-admin/src/main/resources/db/migration/V0_20_47__todo_schedule_windows.sql law-todo/src/main/java/com/law/todo/schedule law-todo/src/main/java/com/law/todo/job/TodoScheduleTask.java law-todo/src/main/java/com/law/todo/application/TodoRoutingService.java law-todo/src/main/java/com/law/todo/mapper/TodoMapper.java law-todo/src/main/resources/mapper/todo/TodoMapper.xml law-todo/src/test/java/com/law/todo/schedule law-todo/src/test/java/com/law/todo/job/TodoScheduleTaskTest.java law-todo/src/test/java/com/law/todo/integration/TodoScheduleMigrationContractTest.java
git commit -m "feat(todo): add idempotent schedule windows"
```

### Task 4: Auto-default fields and governed dictionary forms

**Files:**
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoAutoActionConfiguration.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoQueryService.java`
- Create: `law-todo/src/main/java/com/law/todo/application/TodoFormOptionService.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/view/TodoFormView.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoDodService.java`
- Modify: `law-todo/src/main/java/com/law/todo/definition/validation/TodoFormValidator.java`
- Modify: `law-todo/src/main/java/com/law/todo/mapper/TodoConfigurationMapper.java`
- Modify: `law-todo/src/main/resources/mapper/todo/TodoConfigurationMapper.xml`
- Modify: `ruoyi-ui/src/components/TodoDynamicForm/index.vue`
- Modify: `ruoyi-ui/src/components/TodoDynamicForm/schema-runtime.js`
- Test: `law-todo/src/test/java/com/law/todo/application/TodoAutoActionConfigurationTest.java`
- Test: `law-todo/src/test/java/com/law/todo/application/TodoQueryServiceTest.java`
- Test: `law-todo/src/test/java/com/law/todo/application/TodoDodServiceTest.java`
- Test: `law-todo/src/test/java/com/law/todo/definition/TodoFormValidatorTest.java`
- Test: `ruoyi-ui/scripts/check-todo-lead-dynamic-form.js`

**Interfaces:**
- Produces `TodoFormView.ui.config.fields[*].options`.
- Produces server-side dictionary validation for fields with `dictType`.
- Consumes auto-action `config.fields`.

- [ ] **Step 1: Write failing backend tests**

```java
@Test void complete_default_passes_configured_fields_to_full_dod() {
    AutoActionRule rule=rule(Map.of("fields",Map.of(
        "reviewResult","TRUE_INVALID",
        "reviewOpinion","系统默认确认")));
    capability.execute(todo,rule,SERVICE_ACTOR);
    verify(commands).autoComplete(eq(1L),argThat(command->
        "TRUE_INVALID".equals(command.payload().get("reviewResult"))),eq(SERVICE_ACTOR));
}

@Test void rejects_disabled_dictionary_value() {
    assertThrows(TodoException.class,()->dodService.validate(
        todo(),definitionWithDict("law_first_contact_result"),"COMPLETE",
        Map.of("contactResult","UNKNOWN"),List.of(),actor()));
}
```

- [ ] **Step 2: Run and confirm RED**

```powershell
mvn -pl law-todo -Dtest=TodoAutoActionConfigurationTest,TodoQueryServiceTest,TodoDodServiceTest,TodoFormValidatorTest test
```

Expected: dictionary tests FAIL and form options are absent.

- [ ] **Step 3: Implement form-option projection and validation**

Resolve enabled `sys_dict_data` rows by `dictType` and return `{label,value}` options.
Inject `TodoDictionaryValidationPort` into `TodoDodService`; after the form validator checks required
fields, validate every submitted field whose UI definition declares `dictType`. Reject disabled
values with `TODO_DOD_DICTIONARY_VALUE_INVALID`. Keep `TodoFormValidator` independent of persistence.

- [ ] **Step 4: Write failing frontend contract**

Assert:

```javascript
strict.equal(dictFieldWithoutOptionsRendersInput, false)
strict.equal(missingDictionaryShowsBlockingError, true)
strict.equal(conditionalValidFieldsAreVisibleOnlyForVALID, true)
```

Run:

```powershell
node ruoyi-ui/scripts/check-todo-lead-dynamic-form.js
```

Expected: FAIL because empty dictionaries still fall back to text input and all conditional fields render.

- [ ] **Step 5: Implement dictionary blocking and conditional visibility**

`normalizeFields` derives `visible` from field `showWhen`; `TodoDynamicForm` renders only visible fields. A `dict` with no options renders an error alert and makes `validate()` fail.

- [ ] **Step 6: Run focused tests**

```powershell
mvn -pl law-todo -Dtest=TodoAutoActionConfigurationTest,TodoQueryServiceTest,TodoDodServiceTest,TodoFormValidatorTest test
node ruoyi-ui/scripts/check-todo-lead-dynamic-form.js
```

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add law-todo/src/main/java/com/law/todo/application/TodoAutoActionConfiguration.java law-todo/src/main/java/com/law/todo/application/TodoQueryService.java law-todo/src/main/java/com/law/todo/application/TodoFormOptionService.java law-todo/src/main/java/com/law/todo/application/view/TodoFormView.java law-todo/src/main/java/com/law/todo/application/TodoDodService.java law-todo/src/main/java/com/law/todo/definition/validation/TodoFormValidator.java law-todo/src/main/java/com/law/todo/mapper/TodoConfigurationMapper.java law-todo/src/main/resources/mapper/todo/TodoConfigurationMapper.xml law-todo/src/test/java/com/law/todo/application/TodoAutoActionConfigurationTest.java law-todo/src/test/java/com/law/todo/application/TodoQueryServiceTest.java law-todo/src/test/java/com/law/todo/application/TodoDodServiceTest.java law-todo/src/test/java/com/law/todo/definition/TodoFormValidatorTest.java ruoyi-ui/src/components/TodoDynamicForm ruoyi-ui/scripts/check-todo-lead-dynamic-form.js
git commit -m "feat(todo): govern dynamic dictionary forms"
```

### Task 5: Lead data model, dictionaries, permissions, and event contracts

**Files:**
- Create: `ruoyi-admin/src/main/resources/db/migration/V0_20_48__lead_todo_flow.sql`
- Create: `law-todo/src/test/java/com/law/todo/integration/LeadTodoFlowMigrationContractTest.java`
- Modify: `law-business/src/main/java/com/law/business/event/BusinessEventType.java`
- Modify: `law-business/src/main/java/com/law/business/security/LeadPermissions.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/domain/BizLead.java`
- Modify: `ruoyi-system/src/main/resources/mapper/system/BizLeadMapper.xml`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/mapper/BizLeadMapper.java`

**Interfaces:**
- Produces all lead fields/tables/dictionaries/permissions from design sections 6 and 9.
- Produces event enums `LEAD_TAG_CONFIRMED` through `LEAD_MOVED_TO_DEAD_POOL`.
- Preserves existing lead CRUD and public-pool semantics.

- [ ] **Step 1: Write the failing migration contract**

Assert exact columns, unique keys, dictionary values, menu permissions, and event-catalog Schema properties:

```java
assertTrue(sql.contains("biz_lead_call_record"));
assertTrue(sql.contains("law_first_contact_result"));
assertTrue(sql.contains("'VALID'"));
assertTrue(sql.contains("lead:invalid-review:handle"));
assertTrue(sql.contains("LEAD_FIRST_CONTACT_VALID"));
```

- [ ] **Step 2: Run and confirm RED**

```powershell
mvn -pl law-todo -Dtest=LeadTodoFlowMigrationContractTest test
```

Expected: FAIL because `V0_20_48` is absent.

- [ ] **Step 3: Add the forward-only migration**

Add `row_version`, lead flow columns, call/review/retry/quality/Dead-Pool/tag/policy tables, exact dictionaries, permissions, event schemas, and indexes. Use UTF-8 Chinese labels but stable uppercase business values.

- [ ] **Step 4: Extend Java domain and mapper**

Map each new `biz_lead` field. Add focused conditional-update methods instead of a generic Map update:

```java
int completeFirstContact(@Param("leadId")Long leadId,
  @Param("expectedStatus")String expectedStatus,
  @Param("result")String result,
  @Param("rowVersion")Integer rowVersion,
  @Param("updateBy")String updateBy);
```

- [ ] **Step 5: Run migration and mapper contracts**

```powershell
mvn -pl law-todo,ruoyi-system -am "-Dtest=LeadTodoFlowMigrationContractTest,TodoMapperXmlContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add ruoyi-admin/src/main/resources/db/migration/V0_20_48__lead_todo_flow.sql law-todo/src/test/java/com/law/todo/integration/LeadTodoFlowMigrationContractTest.java law-business/src/main/java/com/law/business/event/BusinessEventType.java law-business/src/main/java/com/law/business/security/LeadPermissions.java ruoyi-system/src/main/java/com/ruoyi/system/domain/BizLead.java ruoyi-system/src/main/java/com/ruoyi/system/mapper/BizLeadMapper.java ruoyi-system/src/main/resources/mapper/system/BizLeadMapper.xml
git commit -m "feat(lead): add todo flow business model"
```

### Task 6: Typed lead commands and business services

**Files:**
- Create: `law-business/src/main/java/com/law/business/lead/dto/LeadTagConfirmCommand.java`
- Create: `law-business/src/main/java/com/law/business/lead/dto/LeadCallRecordCommand.java`
- Create: `law-business/src/main/java/com/law/business/lead/dto/LeadFirstContactCommand.java`
- Create: `law-business/src/main/java/com/law/business/lead/dto/LeadInvalidReviewCommand.java`
- Create: `law-business/src/main/java/com/law/business/lead/dto/LeadRetryCompleteCommand.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/lead/LeadTagConfirmationService.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/lead/LeadCallRecordService.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/lead/LeadFirstContactService.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/lead/LeadInvalidReviewService.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/lead/LeadRetryService.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/lead/LeadDeadPoolService.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/lead/LeadAssignmentPolicyService.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/lead/LeadPoolService.java`
- Test: matching `*ServiceTest.java` files under `ruoyi-system/src/test/java/com/ruoyi/system/service/lead/`

**Interfaces:**
- Produces typed command methods:

```java
FirstContactOutcome complete(LeadFirstContactCommand command);
InvalidReviewOutcome review(LeadInvalidReviewCommand command);
RetryOutcome completeWindow(LeadRetryCompleteCommand command);
void cancelRemaining(Long leadId,String reason);
```

- Consumes current actor, access policy, dictionary service, file center relation validation, schedule service, and business event publisher.

- [ ] **Step 1: Write failing first-contact service tests**

Cover one behavior per test:

```java
@Test void valid_contact_persists_four_fields_and_publishes_one_event() {}
@Test void suspect_invalid_requires_one_of_four_reason_codes() {}
@Test void unreachable_creates_default_retry_plan() {}
@Test void duplicate_call_external_id_is_idempotent() {}
```

- [ ] **Step 2: Run first-contact tests and confirm RED**

```powershell
mvn -pl ruoyi-system -am "-Dtest=LeadFirstContactServiceTest,LeadCallRecordServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: compilation FAIL because the services are absent.

- [ ] **Step 3: Implement first-contact and call facts**

Use conditional updates and stable event keys. Valid requires `name/city/demand/visited`; suspect invalid requires a controlled reason; unreachable creates one schedule plan. Never publish more than one branch event.

- [ ] **Step 4: Write failing invalid-review and Dead-Pool tests**

```java
@Test void confirmed_invalid_moves_to_dead_pool_not_public_pool() {}
@Test void misjudged_valid_writes_quality_record_and_reopens_first_contact() {}
@Test void system_default_review_is_audited_as_system() {}
@Test void public_pool_claim_cannot_read_dead_pool_lead() {}
```

- [ ] **Step 5: Implement review and Dead-Pool transitions**

Dead-Pool sets `disposition=DEAD_POOL`, clears active retry windows, and writes a log. Misjudgment preserves all original facts, writes quality evidence, and publishes a new first-contact event with a new business occurrence key.

- [ ] **Step 6: Write failing retry tests**

```java
@Test void next_window_keeps_plan_active_and_advances_stage() {}
@Test void connected_cancels_future_occurrences_and_publishes_valid() {}
@Test void exhausted_moves_to_public_pool_and_clears_owner() {}
@Test void repeated_window_completion_returns_existing_outcome() {}
```

- [ ] **Step 7: Implement retry and assignment policy services**

Use the schedule occurrence as the idempotency boundary. Exhaustion uses the existing `LeadPoolService` through a dedicated system-authorized method so public-pool logs and events remain consistent.

- [ ] **Step 8: Run all lead service tests**

```powershell
mvn -pl ruoyi-system -am "-Dtest=LeadTagConfirmationServiceTest,LeadCallRecordServiceTest,LeadFirstContactServiceTest,LeadInvalidReviewServiceTest,LeadRetryServiceTest,LeadDeadPoolServiceTest,LeadAssignmentPolicyServiceTest,LeadPoolServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS.

- [ ] **Step 9: Commit**

```powershell
git add law-business/src/main/java/com/law/business/lead/dto/LeadTagConfirmCommand.java law-business/src/main/java/com/law/business/lead/dto/LeadCallRecordCommand.java law-business/src/main/java/com/law/business/lead/dto/LeadFirstContactCommand.java law-business/src/main/java/com/law/business/lead/dto/LeadInvalidReviewCommand.java law-business/src/main/java/com/law/business/lead/dto/LeadRetryCompleteCommand.java ruoyi-system/src/main/java/com/ruoyi/system/service/lead/LeadTagConfirmationService.java ruoyi-system/src/main/java/com/ruoyi/system/service/lead/LeadCallRecordService.java ruoyi-system/src/main/java/com/ruoyi/system/service/lead/LeadFirstContactService.java ruoyi-system/src/main/java/com/ruoyi/system/service/lead/LeadInvalidReviewService.java ruoyi-system/src/main/java/com/ruoyi/system/service/lead/LeadRetryService.java ruoyi-system/src/main/java/com/ruoyi/system/service/lead/LeadDeadPoolService.java ruoyi-system/src/main/java/com/ruoyi/system/service/lead/LeadAssignmentPolicyService.java ruoyi-system/src/main/java/com/ruoyi/system/service/lead/LeadPoolService.java ruoyi-system/src/test/java/com/ruoyi/system/service/lead/LeadTagConfirmationServiceTest.java ruoyi-system/src/test/java/com/ruoyi/system/service/lead/LeadCallRecordServiceTest.java ruoyi-system/src/test/java/com/ruoyi/system/service/lead/LeadFirstContactServiceTest.java ruoyi-system/src/test/java/com/ruoyi/system/service/lead/LeadInvalidReviewServiceTest.java ruoyi-system/src/test/java/com/ruoyi/system/service/lead/LeadRetryServiceTest.java ruoyi-system/src/test/java/com/ruoyi/system/service/lead/LeadDeadPoolServiceTest.java ruoyi-system/src/test/java/com/ruoyi/system/service/lead/LeadAssignmentPolicyServiceTest.java ruoyi-system/src/test/java/com/ruoyi/system/service/lead/LeadPoolServiceTest.java
git commit -m "feat(lead): implement first contact branch services"
```

### Task 7: Todo completion handlers and transactional branch flow

**Files:**
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/event/LeadFirstContactHandler.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/event/LeadFirstContactValidator.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/event/LeadInvalidReviewTodoHandler.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/event/LeadRetryTodoHandler.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/event/LeadProgressHandoffTodoHandler.java`
- Test: `ruoyi-system/src/test/java/com/ruoyi/system/integration/LeadFirstContactHandlerTest.java`
- Test: `ruoyi-system/src/test/java/com/ruoyi/system/integration/LeadInvalidReviewTodoHandlerTest.java`
- Test: `ruoyi-system/src/test/java/com/ruoyi/system/integration/LeadRetryTodoHandlerTest.java`
- Test: `ruoyi-system/src/test/java/com/ruoyi/system/integration/LeadTodoTransactionRollbackTest.java`

**Interfaces:**
- Produces handler catalog codes `TD-001_COMPLETE`, `TD-002_COMPLETE`, `TD-003_COMPLETE`, `TD-004_COMPLETE`.
- Consumes services from Task 6.
- Preserves legacy `LEAD_FIRST_CONTACT` support until existing instances finish.

- [ ] **Step 1: Write failing handler alias tests**

```java
assertTrue(handler.supports(todo("TD-001","首联待办")));
assertTrue(handler.supports(todo("LEAD_FIRST_CONTACT","线索首联")));
assertEquals("TD-001_COMPLETE",handler.catalogCode());
```

Expected current failure: TD-001 is not supported and catalog code is not explicit.

- [ ] **Step 2: Implement thin typed adapters**

Handlers translate payload only:

```java
firstContacts.complete(LeadFirstContactCommand.from(todo,payload,operatorId,operatorName));
```

No handler writes a Mapper directly.

- [ ] **Step 3: Write and observe rollback tests**

Force event publishing or branch persistence to fail and assert Todo status, action log, business fact, and Outbox all roll back.

Run:

```powershell
mvn -pl ruoyi-system -am "-Dtest=LeadFirstContactHandlerTest,LeadInvalidReviewTodoHandlerTest,LeadRetryTodoHandlerTest,LeadTodoTransactionRollbackTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: RED before integration, then PASS after transactional wiring.

- [ ] **Step 4: Remove title-based validator coupling**

Validator selection uses template code and governed validator refs, never Chinese title equality. Validate active state, owner stability, schedule occurrence, and business disposition.

- [ ] **Step 5: Commit**

```powershell
git add ruoyi-system/src/main/java/com/ruoyi/system/service/event/LeadFirstContactHandler.java ruoyi-system/src/main/java/com/ruoyi/system/service/event/LeadFirstContactValidator.java ruoyi-system/src/main/java/com/ruoyi/system/service/event/LeadInvalidReviewTodoHandler.java ruoyi-system/src/main/java/com/ruoyi/system/service/event/LeadRetryTodoHandler.java ruoyi-system/src/main/java/com/ruoyi/system/service/event/LeadProgressHandoffTodoHandler.java ruoyi-system/src/test/java/com/ruoyi/system/integration/LeadFirstContactHandlerTest.java ruoyi-system/src/test/java/com/ruoyi/system/integration/LeadInvalidReviewTodoHandlerTest.java ruoyi-system/src/test/java/com/ruoyi/system/integration/LeadRetryTodoHandlerTest.java ruoyi-system/src/test/java/com/ruoyi/system/integration/LeadTodoTransactionRollbackTest.java
git commit -m "feat(lead): connect todo handlers to branch services"
```

### Task 8: Publish TD-001 through TD-004 with executable routing

**Files:**
- Create: `ruoyi-admin/src/main/resources/db/migration/V0_20_49__publish_lead_todo_templates.sql`
- Modify: `law-todo/src/main/resources/todo-definitions/v0.2/TD-001.json`
- Modify: `law-todo/src/main/resources/todo-definitions/v0.2/TD-002.json`
- Modify: `law-todo/src/main/resources/todo-definitions/v0.2/TD-003.json`
- Modify: `law-todo/src/main/resources/todo-definitions/v0.2/TD-004.json`
- Modify: `law-todo/src/main/resources/todo-definitions/v0.2/manifest.json`
- Test: `law-todo/src/test/java/com/law/todo/integration/LeadTodoPublishedTemplateContractTest.java`
- Test: `law-todo/src/test/java/com/law/todo/integration/V02PrdDefinitionManifestTest.java`
- Test: `law-todo/src/test/java/com/law/todo/routing/TodoRoutingEngineTest.java`

**Interfaces:**
- Produces published numeric versions and enabled triggers for four templates.
- Produces executable route graphs with published target version IDs.
- Retires the legacy trigger without invalidating historical instances.

- [ ] **Step 1: Write failing published-template contract**

Assert:

```java
assertPublished("TD-001","LEAD_ASSIGNED");
assertRouteTargets("TD-001",Set.of("TD-002","TD-003","TD-004"));
assertAutoDefaultFields("TD-002","TRUE_INVALID");
assertNoEscalateAt("TD-001","SLA_80","SLA_100");
```

- [ ] **Step 2: Run and confirm RED**

```powershell
mvn -pl law-todo -Dtest=LeadTodoPublishedTemplateContractTest,V02PrdDefinitionManifestTest,TodoRoutingEngineTest test
```

Expected: FAIL because the definitions remain BLOCKED and routing config is empty.

- [ ] **Step 3: Update definition resources**

Add `dictType`, `showWhen`, validator refs, schedule config, corrected SLA actions, completed handler capability, no unresolved Q-006/Q-007/Q-008 blockers, and executable route nodes/edges.

- [ ] **Step 4: Create deterministic publish migration**

Insert published versions in dependency order:

```text
TD-004 → TD-002 → TD-003 → TD-001
```

Then bind TD-001 graph targets to those version IDs and enable exactly one `LEAD_ASSIGNED` trigger. Mark prior `LEAD_FIRST_CONTACT` triggers disabled but retain versions.

- [ ] **Step 5: Verify template and routing tests**

```powershell
mvn -pl law-todo -Dtest=LeadTodoPublishedTemplateContractTest,V02PrdDefinitionManifestTest,TodoRoutingEngineTest,ExistingTemplateParityTest test
```

Expected: PASS; no route points to `CONTRACT_SIGN`.

- [ ] **Step 6: Commit**

```powershell
git add ruoyi-admin/src/main/resources/db/migration/V0_20_49__publish_lead_todo_templates.sql law-todo/src/main/resources/todo-definitions/v0.2/TD-001.json law-todo/src/main/resources/todo-definitions/v0.2/TD-002.json law-todo/src/main/resources/todo-definitions/v0.2/TD-003.json law-todo/src/main/resources/todo-definitions/v0.2/TD-004.json law-todo/src/main/resources/todo-definitions/v0.2/manifest.json law-todo/src/test/java/com/law/todo/integration/LeadTodoPublishedTemplateContractTest.java law-todo/src/test/java/com/law/todo/integration/V02PrdDefinitionManifestTest.java law-todo/src/test/java/com/law/todo/routing/TodoRoutingEngineTest.java
git commit -m "feat(todo): publish executable lead templates"
```

### Task 9: Lead APIs, permissions, and read models

**Files:**
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/lead/BizLeadController.java`
- Create: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/lead/LeadInvalidReviewController.java`
- Create: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/lead/LeadRetryController.java`
- Create: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/lead/LeadDeadPoolController.java`
- Create: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/lead/LeadAssignmentPolicyController.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/IBizLeadService.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/impl/BizLeadServiceImpl.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/lead/LeadQueryService.java`
- Modify: `ruoyi-system/src/main/resources/mapper/system/BizLeadMapper.xml`
- Test: `ruoyi-admin/src/test/java/com/ruoyi/web/controller/lead/LeadTodoControllerContractTest.java`
- Test: `ruoyi-system/src/test/java/com/ruoyi/system/service/lead/LeadTodoReadModelTest.java`

**Interfaces:**
- Produces endpoints:

```text
POST /lead/tag/confirm
GET  /lead/{id}/call-records
POST /lead/{id}/call-records
GET  /lead/invalid-review/list
POST /lead/invalid-review/{todoId}/complete
GET  /lead/retry/list
GET  /lead/{id}/retry-timeline
GET  /lead/dead-pool/list
POST /lead/dead-pool/{id}/restore
GET/PUT /lead/assignment-policy
```

- [ ] **Step 1: Write failing permission and DTO contract tests**

Assert exact URL, method, `@Valid`, permission string, and that actor/target status cannot come from request JSON.

- [ ] **Step 2: Run and confirm RED**

```powershell
mvn -pl ruoyi-admin,ruoyi-system -am "-Dtest=LeadTodoControllerContractTest,LeadTodoReadModelTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: compilation FAIL because controllers are absent.

- [ ] **Step 3: Implement controllers and read models**

Use typed DTOs and service access policies. Return paged review/retry/Dead-Pool records with Todo SLA fields and business summaries.

- [ ] **Step 4: Verify permissions and data scopes**

Add cases for salesperson own records, supervisor scope, unauthorized peer, Dead-Pool restore privilege, and assignment-policy admin.

- [ ] **Step 5: Run and commit**

```powershell
mvn -pl ruoyi-admin,ruoyi-system -am "-Dtest=LeadTodoControllerContractTest,LeadTodoReadModelTest,LeadAccessPolicyTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
git add ruoyi-admin/src/main/java/com/ruoyi/web/controller/lead/BizLeadController.java ruoyi-admin/src/main/java/com/ruoyi/web/controller/lead/LeadInvalidReviewController.java ruoyi-admin/src/main/java/com/ruoyi/web/controller/lead/LeadRetryController.java ruoyi-admin/src/main/java/com/ruoyi/web/controller/lead/LeadDeadPoolController.java ruoyi-admin/src/main/java/com/ruoyi/web/controller/lead/LeadAssignmentPolicyController.java ruoyi-admin/src/test/java/com/ruoyi/web/controller/lead/LeadTodoControllerContractTest.java ruoyi-system/src/main/java/com/ruoyi/system/service/IBizLeadService.java ruoyi-system/src/main/java/com/ruoyi/system/service/impl/BizLeadServiceImpl.java ruoyi-system/src/main/java/com/ruoyi/system/service/lead/LeadQueryService.java ruoyi-system/src/main/resources/mapper/system/BizLeadMapper.xml ruoyi-system/src/test/java/com/ruoyi/system/service/lead/LeadTodoReadModelTest.java
git commit -m "feat(lead): expose todo flow operations"
```

### Task 10: Lead operational frontend

**Files:**
- Modify: `ruoyi-ui/src/api/lead.js`
- Modify: `ruoyi-ui/src/views/lead/index.vue`
- Modify: `ruoyi-ui/src/views/lead/components/LeadDetailDrawer.vue`
- Create: `ruoyi-ui/src/views/lead/components/LeadFirstContactDrawer.vue`
- Create: `ruoyi-ui/src/views/lead/components/LeadCallTimeline.vue`
- Create: `ruoyi-ui/src/views/lead/components/LeadRetryTimeline.vue`
- Create: `ruoyi-ui/src/views/lead/review/index.vue`
- Create: `ruoyi-ui/src/views/lead/retry/index.vue`
- Create: `ruoyi-ui/src/views/lead/dead-pool/index.vue`
- Create: `ruoyi-ui/src/views/lead/policy/index.vue`
- Test: `ruoyi-ui/scripts/check-lead-todo-flow-ui.js`
- Test: `ruoyi-ui/e2e/lead-todo-flow.spec.js`

**Interfaces:**
- Consumes Task 9 APIs and Todo dynamic form/file APIs.
- Produces tag confirmation, first-contact drawer, review queue, retry timeline, Dead-Pool, and policy pages.

- [x] **Step 1: Write failing structural/frontend behavior contract**

Assert:

```javascript
assertRoute('/lead/invalid-review','lead:invalid-review:list')
assertRoute('/lead/dead-pool','lead:dead-pool:list')
assertComponent('LeadFirstContactDrawer')
assertNoRawOwnerIdInput()
assertConditionalFields(['name','city','demand','visited'],'contactResult','VALID')
```

- [x] **Step 2: Run and confirm RED**

```powershell
node ruoyi-ui/scripts/check-lead-todo-flow-ui.js
```

Expected: FAIL because pages/components/routes are absent.

- [x] **Step 3: Implement API and focused components**

Reuse `TodoDynamicForm`, file picker, `BusinessTodoSummary`, timeline, and existing page-shell styles. Do not duplicate Todo action state transitions in the lead page.

- [x] **Step 4: Implement operational pages**

Show SLA remaining time, Owner, current window, evidence, business status, and allowed actions. Dead-Pool has no claim button. Review page shows the 24-hour default behavior before confirmation.

- [x] **Step 5: Run frontend contracts and production build**

```powershell
node ruoyi-ui/scripts/check-lead-todo-flow-ui.js
npm --prefix ruoyi-ui run build:prod
```

Expected: PASS and no new encoding/build errors.

- [x] **Step 6: Commit**

```powershell
git add ruoyi-ui/src/api/lead.js ruoyi-ui/src/views/lead/index.vue ruoyi-ui/src/views/lead/components/LeadDetailDrawer.vue ruoyi-ui/src/views/lead/components/LeadFirstContactDrawer.vue ruoyi-ui/src/views/lead/components/LeadCallTimeline.vue ruoyi-ui/src/views/lead/components/LeadRetryTimeline.vue ruoyi-ui/src/views/lead/review/index.vue ruoyi-ui/src/views/lead/retry/index.vue ruoyi-ui/src/views/lead/dead-pool/index.vue ruoyi-ui/src/views/lead/policy/index.vue ruoyi-ui/scripts/check-lead-todo-flow-ui.js ruoyi-ui/e2e/lead-todo-flow.spec.js
git commit -m "feat(lead-ui): add todo flow workbenches"
```

### Task 11: Real MySQL end-to-end flow

**Files:**
- Create: `ruoyi-admin/src/test/java/com/ruoyi/web/migration/LeadTodoFlowEndToEndTest.java`
- Create: `ruoyi-admin/src/test/java/com/ruoyi/web/migration/LeadTodoScheduleEndToEndTest.java`
- Create: `doc/reviews/lead-todo-flow-test-matrix.md`

**Interfaces:**
- Produces transaction-level evidence against MySQL 8.
- Consumes all prior tasks.

- [ ] **Step 1: Write a failing real-database golden-path test**

The test must:

```text
insert test lead and sales users
publish LEAD_ASSIGNED in the same transaction as assignment
run real Outbox processor
assert TD-001 Owner and SLA
complete Todo through TodoCommandService
run Outbox again
assert exactly one branch Todo and business fact
rollback all fixtures
```

- [ ] **Step 2: Run and confirm RED**

```powershell
$env:TODO_MIGRATION_DB_URL='jdbc:mysql://127.0.0.1:3306/law_lead_todo_e2e?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&connectionCollation=utf8mb4_unicode_ci'
$env:TODO_MIGRATION_DB_USER='root'
$env:TODO_MIGRATION_DB_PASSWORD='root'
mvn -pl ruoyi-admin -am "-Dtest=LeadTodoFlowEndToEndTest,LeadTodoScheduleEndToEndTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: initial FAIL at the first unclosed integration boundary, not a skipped test.

- [ ] **Step 3: Add all golden scenarios**

Cover:

```text
VALID → TD-004
SUSPECT_INVALID → TRUE_INVALID → Dead-Pool
SUSPECT_INVALID → MISJUDGED_VALID → reopened TD-001
TD-002 DUE → COMPLETE_DEFAULT → Dead-Pool
UNREACHABLE → T1_AM → CONNECTED → TD-004 and cancel future
UNREACHABLE → all windows exhausted → public pool
leave skip/delegation
80/100/150 SLA
duplicate event/action/window idempotency
business failure rollback
```

- [ ] **Step 4: Run baseline-to-head Flyway and real tests**

```powershell
mvn -pl ruoyi-admin -am "-Dtest=FlywayMigrationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn -pl ruoyi-admin -am "-Dtest=LeadTodoFlowEndToEndTest,LeadTodoScheduleEndToEndTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS with zero skipped golden scenarios.

- [ ] **Step 5: Record evidence and commit**

The matrix records scenario, fixture, action, business assertions, Todo assertions, audit assertions, and exact test method.

```powershell
git add ruoyi-admin/src/test/java/com/ruoyi/web/migration/LeadTodoFlowEndToEndTest.java ruoyi-admin/src/test/java/com/ruoyi/web/migration/LeadTodoScheduleEndToEndTest.java doc/reviews/lead-todo-flow-test-matrix.md
git commit -m "test(lead): prove todo flow against mysql"
```

### Task 12: Real Chrome acceptance, full regression, and completion audit

**Files:**
- Modify: `ruoyi-ui/e2e/lead-todo-flow.spec.js`
- Create: `doc/reviews/lead-todo-flow-acceptance.md`
- Modify: `.github/workflows/ci.yml`

**Interfaces:**
- Produces browser evidence for every design scenario and a requirement-by-requirement completion audit.

- [ ] **Step 1: Start clean runtime services**

Use the repository runtime profile and separate test database. Verify health endpoints and Flyway version before browser actions.

- [ ] **Step 2: Run the real Chrome scenarios**

```powershell
npm --prefix ruoyi-ui run test:e2e -- e2e/lead-todo-flow.spec.js --project=chromium --reporter=line
```

Expected: all 12 design E2E scenarios PASS against real APIs; no route interception provides mock business responses.

- [ ] **Step 3: Run full backend regression**

```powershell
mvn clean verify
```

Expected: BUILD SUCCESS with no new failed or skipped required tests.

- [ ] **Step 4: Run full frontend regression**

```powershell
npm --prefix ruoyi-ui run test:todo
npm --prefix ruoyi-ui run build:prod
```

Expected: all contracts PASS and production build succeeds.

- [ ] **Step 5: Audit production database state**

Assert through SQL:

```text
TD-001..TD-004 current_version > 0
current versions are PUBLISHED
catalog foundation_state and production_state are READY
exactly one enabled LEAD_ASSIGNED trigger
no lead route references CONTRACT_SIGN
no active lead Todo has null owner and no candidate
Dead-Pool leads are absent from public-pool query
```

- [ ] **Step 6: Write the acceptance report**

For every requirement in the design, cite one or more of:

```text
test method
fresh command output
database query result
API response
Chrome screenshot/trace
```

Do not mark an item complete from source inspection alone.

- [ ] **Step 7: Add a mandatory real lead-flow CI job**

Add `lead-todo-real-e2e` to `.github/workflows/ci.yml`. It provisions MySQL 8 and Redis,
initializes the v0.15 baseline, runs every Flyway migration, creates isolated lead/sales/supervisor
fixtures, builds and starts the real backend and production frontend, installs the configured Chrome
channel, and runs only `e2e/lead-todo-flow.spec.js`. Upload backend log, Playwright report, trace, and
screenshots with `if: always()`. No business API route may be mocked or intercepted.

- [ ] **Step 8: Commit the final evidence**

```powershell
git add ruoyi-ui/e2e/lead-todo-flow.spec.js doc/reviews/lead-todo-flow-acceptance.md .github/workflows/ci.yml
git commit -m "test(lead): complete todo flow acceptance"
```

- [ ] **Step 9: Verify clean implementation scope**

```powershell
git status --short
git log --oneline --decorate -15
```

Expected: only the previously preserved user-owned files remain dirty; all implementation work is committed.

---

## Plan Self-Review Mapping

| Design requirement | Implemented by |
|---|---|
| Event payload validation and corrected assignment event | Task 1 |
| Owner, supervisor, leave, delegation, round-robin | Task 2 |
| T0/T+1/T+2 reusable schedule windows | Task 3 |
| Auto default fields and dictionary dynamic forms | Task 4 |
| Lead fields, tables, dictionaries, permissions | Task 5 |
| First-contact, review, retry, Dead-Pool business facts | Task 6 |
| TD-001～TD-004 business completion handlers | Task 7 |
| Published executable templates and routes | Task 8 |
| Typed APIs and data scopes | Task 9 |
| Operational frontend pages | Task 10 |
| Real MySQL transaction/idempotency proof | Task 11 |
| Real Chrome, regression, database audit | Task 12 |
