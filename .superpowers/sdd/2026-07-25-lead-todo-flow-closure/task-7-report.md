# Task 7 — Todo completion handlers and transactional branch flow

## Status

Complete. The governed TD-001 through TD-004 completion catalog is connected to the Task 6
business services/boundary, legacy `LEAD_FIRST_CONTACT` remains supported, completion selection no
longer depends on a localized title, and the Todo/action/business-fact/Outbox flow is covered by a
real Spring transaction rollback test.

Task 8 templates, event routes, and recurrence definitions are intentionally not included.

## Delivered handler catalog

| Template | Completion catalog | Behavior |
|---|---|---|
| `TD-001` | `TD-001_COMPLETE` | Maps the Todo identity and manual contact form to `LeadFirstContactCommand`, then calls `LeadFirstContactService.complete`. |
| `LEAD_FIRST_CONTACT` | `TD-001_COMPLETE` | Preserves the existing-instance compatibility path through the same typed service. |
| `TD-002` | `TD-002_COMPLETE` | Resolves the review and source TD-001 Todo from persisted provenance, then calls the human or controlled-automatic `LeadInvalidReviewService` entry point. |
| `TD-003` | `TD-003_COMPLETE` | Resolves the schedule occurrence from the persisted Todo occurrence key and Todo ID, maps only user-owned attempt data, then calls `LeadRetryService.completeWindow`. |
| `TD-004` | `TD-004_COMPLETE` | Implements the approved stage-3 handoff boundary. DoD and the Todo action audit are the boundary fact in this slice; recurrence and TD-005 write-back remain Task 8+. |

The handlers are typed adapters. None writes a Mapper directly.

## Trusted source and authority boundaries

- Business identity always comes from `TodoInstance.businessType/businessId`; payload cannot select
  another lead.
- TD-001 and TD-003 call evidence is bound to the source Todo ID. The payload cannot supply another
  lead or Todo ID.
- A payload-supplied provider summary hash is discarded. Provider-authoritative outbound evidence
  continues to enter only through the trusted callback boundary delivered by Task 6.
- TD-002 loads the persisted trigger event (or graph `previousTodoId`), derives the invalid-review
  fact and original TD-001 Todo, and verifies lead, source Todo, and persisted reviewer against the
  TD-002 owner. A hidden `reviewId` can only confirm the derived value, not override it.
- TD-003 resolves the occurrence through `TodoScheduleService.requireOccurrenceIdForTodo`. That API
  reloads the stored occurrence and verifies both occurrence key and linked Todo ID. Payload
  `occurrenceId`, `planId`, `nextWindowCode`, and `nextRetryTime` have no authority.
- Retry attempt/result normalization, configured maximum attempts, window selection, and next retry
  time remain owned by the Task 6 schedule/retry services.

## Controlled TD-002 automatic completion

`TodoCompletionHandler.CompletionContext` is now assembled inside the Todo completion transaction.
Its `controlledAutomatic` flag is true only for the fenced `COMPLETE_DEFAULT` path using the exact
`TodoAutoActionService.SERVICE_ACTOR` capability object.

This closes both spoofing paths:

- payload fields such as `automatic=true` cannot elevate a human completion;
- a newly constructed actor with the same ID and name as the service actor is rejected by the
  controlled entry point and is logged as a human source elsewhere.

`LeadInvalidReviewTodoHandler` ignores client review-result fields on the controlled path and calls
the fixed automatic-review service entry point. Direct/human completion always calls `review`,
even if its operator values resemble the service actor.

## Validator behavior

`LeadFirstContactValidator` now selects only by governed template code:

- `TD-001`;
- legacy `LEAD_FIRST_CONTACT`;
- `TD-003`.

It does not compare the Todo title. Before completion it verifies:

- the lead exists and is not deleted;
- disposition is still `ACTIVE`;
- Todo owner still matches the lead owner;
- TD-001/legacy first contact is still pending;
- TD-003 is still in the unreachable retry state;
- the TD-003 schedule occurrence is actually linked to this Todo.

The Task 6 business services retain the final data-scope, permission, state, and optimistic-lock
checks.

## Transaction proof

`LeadTodoTransactionRollbackTest` uses:

- Spring `@EnableTransactionManagement` and the real proxied `TodoCommandService`;
- `DataSourceTransactionManager`;
- one real H2 JDBC transaction shared by Todo and lead persistence adapters;
- production `LeadFirstContactHandler`, `LeadFirstContactService`, and
  `LeadCallRecordService`.

Two failure paths are proven:

1. Outbox insertion succeeds and publishing then throws.
2. Earlier business facts are inserted and the lead branch transition then throws.

In both cases all of these return to their original state:

- Todo status;
- Todo action log;
- call fact;
- follow-up fact;
- lead transition;
- Outbox event.

The tests invoke the proxied public `TodoCommandService.complete`; they do not wrap a direct handler
call in a test-only transaction.

## TDD evidence

### Initial handler RED

The focused handler gate first failed at test compilation with the expected missing TD-002/TD-003
handlers, typed TD-001 constructor, and schedule-source resolver contracts.

After implementation, the focused handler, validator, and schedule tests passed.

### Trusted source RED

Source-context tests were introduced before the source resolver and mapper reads. The expected RED
failed compilation for the missing `LeadTodoSourceContextService`,
`BusinessEventMapper.selectEventById`, and source-Todo invalid-review lookup.

After implementation, the TD-002 handler/source tests passed, including hidden-review-ID rejection.

### Existing dynamic-form RED

Tests for the existing `contactedAt` field and stable retry-attempt evidence first failed because
the typed call commands had no start time or derived occurrence key. The payload mapper now
preserves those existing field aliases and derives stable server-bound fallback keys.

### Trusted automatic-context RED

The TD-002 test referenced `TodoCompletionHandler.CompletionContext` before that contract existed
and failed compilation as expected. The production Todo completion pipeline now creates the
context, and tests prove controlled automatic selection plus lookalike-actor rejection.

## Final verification

Exact Task 7 gate:

```powershell
mvn --batch-mode --no-transfer-progress -pl ruoyi-system -am "-Dtest=LeadFirstContactHandlerTest,LeadInvalidReviewTodoHandlerTest,LeadRetryTodoHandlerTest,LeadTodoTransactionRollbackTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: `BUILD SUCCESS`; 11 tests passed, 0 failures/errors/skips.

Focused Todo/handler/validator/rollback gate:

```powershell
mvn -pl ruoyi-system -am "-DskipTests=false" "-Dtest=TodoCommandServiceTest,TodoScheduleServiceTest,LeadFirstContactHandlerTest,LeadFirstContactValidatorTest,LeadInvalidReviewTodoHandlerTest,LeadRetryTodoHandlerTest,LeadProgressHandoffTodoHandlerTest,LeadTodoSourceContextServiceTest,LeadTodoTransactionRollbackTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: `BUILD SUCCESS`; 61 tests passed, 0 failures/errors/skips.

Broad lead regression:

```powershell
mvn --batch-mode --no-transfer-progress -pl ruoyi-system -am "-Dtest=TodoScheduleMigrationContractTest,TodoScheduleServiceTest,TodoMapperXmlContractTest,Lead*Test,BusinessEventCommandTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: `BUILD SUCCESS`; 134 tests passed:

- `law-business`: 4;
- `law-todo`: 49;
- `ruoyi-system`: 81;
- 0 failures/errors/skips.

`git diff --check` passes apart from Git's existing line-ending notices.

## Changed production areas

- Todo completion SPI/pipeline trusted completion context.
- Exception force-complete human completion context.
- Todo schedule source-Todo occurrence resolver.
- Business-event and invalid-review provenance reads.
- TD-001/002/003/004 completion handlers and payload adapter.
- First-contact/retry business validator.
- H2 test-only dependency and focused tests.

No Task 8 template, event-route, recurrence, or migration was added.

## Handoff and remaining boundary

Task 7 makes TD-001 through TD-004 completion behavior executable when matching Todo instances
exist. It does not publish the governed TD-001 through TD-004 templates/routes or create the
five-day progress recurrence. Those are the next-task boundary, not an incomplete handler path.

User-owned `.superpowers/sdd/task-7-report.md`, `ruoyi-ui/vue.config.js`, `.playwright-cli/`,
`.runtime-logs/`, `output/`, and `test-results/` are deliberately excluded from this task's staging.

## Independent review fix round 1

The three findings in `task-7-review.md` are now addressed:

- **C1 — authoritative TD-003 completion:** completion handlers return a server-owned
  `CompletionResult`. `CONTINUE_CURRENT_WINDOW` records `COMPLETE_RETAINED`, leaves the source Todo
  in its current state, and does not advance the graph. `CONNECTED`, `NEXT_WINDOW`, and
  `EXHAUSTED` complete and route only from the Task 6 `RetryOutcome`; client `planId`, window,
  stage, time, and terminal branch choices are not routing authority.
- **I1 — unforgeable controlled automatic context:** `CompletionContext` has a private
  constructor, a public human factory, and a package-private controlled-automatic factory. Only
  the fenced Todo application package can manufacture the controlled capability. Human exception
  completion continues to use the human factory.
- **I2 — full routing rollback proof:** the Spring/H2 transaction test now uses a real
  `TodoRoutingService` with JDBC-backed route join, token, next-Todo, and relation ports. A
  deliberate failure after relation insertion proves rollback of the source Todo, action, lead
  facts, Outbox, join/token/relation, and generated Todo.

Additional executable evidence:

- `LeadRetryTodoCommandFlowTest` runs the actual proxied `TodoCommandService`,
  `LeadRetryTodoHandler`, Task 6 `LeadRetryService`, call-record service, and real routing engine
  through attempts 1, 2, and the configured limit. It proves the first two attempts retain TD-003
  and the limit routes using the locked schedule outcome and exact server payload.
- Handler tests cover all four Task 6 results and prove terminality/routing payloads are derived
  from the server result even when the client submits conflicting branch values.
- Command-service order assertions prove handler business work precedes Todo transition/action,
  which precedes graph routing.

### Review-fix RED evidence

1. The first focused compile failed with 21 expected errors for the missing application
   `CompletionContext`, `CompletionResult`, and `handle` contract.
2. The new route-failure rollback case then failed because the transaction test still supplied
   `routing=null`; no routing exception was observed.
3. After wiring the real routing service and JDBC ports, all three rollback cases passed.

### Review-fix verification

Focused gate:

```powershell
mvn -pl ruoyi-system -am "-DskipTests=false" "-Dtest=CompletionContextCapabilityTest,TodoCommandServiceTest,LeadInvalidReviewTodoHandlerTest,LeadRetryTodoHandlerTest,LeadRetryTodoCommandFlowTest,LeadTodoTransactionRollbackTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: `BUILD SUCCESS`; 34 tests passed, 0 failures/errors/skips.

Broad module regression:

```powershell
mvn -pl law-todo,ruoyi-system -am test
```

Result: `BUILD SUCCESS`; `law-todo` 733 tests (2 pre-existing skips) and `ruoyi-system` 235 tests,
with 0 failures/errors. `git diff --check` passes apart from existing line-ending notices.
