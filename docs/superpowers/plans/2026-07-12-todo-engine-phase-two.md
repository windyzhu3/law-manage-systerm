# Todo Engine Phase Two Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the complete V0.17 phase-two Todo Engine: hybrid template administration, contract/case/matter/archive workflows, business summaries, operations controls, frontend management surfaces, integration tests, E2E tests, and green CI.

**Architecture:** Extend the existing modular monolith. `law-todo` owns definitions, instances, lifecycle, operations, summaries and persistence; `ruoyi-system` exposes business application services through typed Todo validators/completion handlers; `ruoyi-admin` owns secured HTTP APIs and Flyway; `ruoyi-ui` owns configuration, operations and embedded business components. Business facts create Outbox events, while Todo completion calls public business services in the same transaction.

**Tech Stack:** Java 17, Spring Boot, Spring Security, MyBatis, MySQL 8, Flyway, Quartz, JUnit 5, Mockito, MockMvc, Vue 2.6, Element UI, Node 16 CI, Playwright.

## Global Constraints

- Branch is `V0.17`, based exactly on V0.16 commit `62a6394765c1a1106d39cba0bf0d75a6e9148243`.
- Use system-seeded standard templates plus copy/edit-draft/publish immutable versions.
- Never modify an applied Flyway migration; phase-two migrations start at `V0_17_1`.
- Business controllers and mappers never write Todo tables directly.
- Completion handlers call typed public business services and share the Todo completion transaction.
- Every write API uses a typed DTO, stable `businessCode`, data-scope policy and `actionId` idempotency.
- No MQ, microservices, BPM, visual drag designer, complex expression DSL, multi-tenancy or mobile application.
- Production code is written only after the corresponding failing test has been observed.

---

### Task 1: Definition Drafts, Copying, Publishing and Schema

**Files:**
- Create: `ruoyi-admin/src/main/resources/db/migration/V0_17_1__todo_definition_drafts.sql`
- Create: `law-todo/src/main/java/com/law/todo/application/TodoDefinitionService.java`
- Create: `law-todo/src/main/java/com/law/todo/application/command/TodoDefinitionCommands.java`
- Modify: `law-todo/src/main/java/com/law/todo/mapper/TodoMapper.java`
- Modify: `law-todo/src/main/resources/mapper/todo/TodoMapper.xml`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/todo/TodoTemplateController.java`
- Test: `law-todo/src/test/java/com/law/todo/application/TodoDefinitionServiceTest.java`
- Test: `ruoyi-admin/src/test/java/com/ruoyi/web/controller/todo/TodoDefinitionControllerValidationTest.java`

**Interfaces:**
- Produces: `copyTemplate(Long, CopyTemplateCommand, Actor)`, `copyVersion(Long, int, CopyVersionCommand, Actor)`, `updateDraft(UpdateDraftCommand, Actor)`, `publish(PublishDraftCommand, Actor)`, `versions(Long)`.
- Consumes: existing `TodoMapper`, `TodoTemplateService`, `TodoException`.

- [ ] Write failing tests proving published versions cannot change, copies retain source IDs, invalid Owner/DoD/SLA/next/UI JSON is rejected, unknown calendars and unpublished next versions are rejected, and two publishers cannot publish the same draft.
- [ ] Run `mvn -q -pl law-todo -am -Dtest=TodoDefinitionServiceTest test` and confirm failures are caused by missing service/schema methods.
- [ ] Add `status varchar(16) not null default 'PUBLISHED'` and `source_version_id bigint` to `todo_template_version`; add indexes on template/status/version and source; migrate existing rows to `PUBLISHED`.
- [ ] Implement records `CopyTemplateCommand(actionId,newTemplateCode,newTemplateName)`, `CopyVersionCommand(actionId,newVersionNo)`, `UpdateDraftCommand(actionId,versionId,ownerRule,dodRule,slaRule,nextRule,uiSchema)`, and `PublishDraftCommand(actionId,versionId)` with Bean Validation.
- [ ] Implement transactional copy/update/publish using `where status='DRAFT'` and unique action logging; validate JSON structures and referenced calendar/template versions before publishing.
- [ ] Expose `POST /todo/template/{id}/copy`, `GET /todo/template/{id}/versions`, `POST /todo/template/{id}/version/{version}/copy`, `PUT /todo/template/version/{versionId}`, and `POST /todo/template/version/{versionId}/publish` with the new granular permissions.
- [ ] Run the focused service and MockMvc tests, then `mvn -q -pl ruoyi-admin -am test`; expect zero failures.
- [ ] Commit with `feat(todo): add draftable versioned definitions`.

### Task 2: Business Summary, Chain and Query Scopes

**Files:**
- Create: `law-todo/src/main/java/com/law/todo/application/TodoBusinessViewService.java`
- Create: `law-todo/src/main/java/com/law/todo/application/view/TodoBusinessSummary.java`
- Create: `law-todo/src/main/java/com/law/todo/application/view/TodoChainView.java`
- Create: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/todo/TodoBusinessViewController.java`
- Modify: `law-todo/src/main/java/com/law/todo/mapper/TodoMapper.java`
- Modify: `law-todo/src/main/resources/mapper/todo/TodoMapper.xml`
- Test: `law-todo/src/test/java/com/law/todo/application/TodoBusinessViewServiceTest.java`
- Test: `ruoyi-admin/src/test/java/com/ruoyi/web/controller/todo/TodoBusinessViewControllerTest.java`

**Interfaces:**
- Produces: `summary(String type,Long id,Actor)`, `businessTodos(String type,Long id,TodoListQuery,Actor)`, `chain(Long rootTodoId,Actor)`.
- Consumes: `TodoAccessPolicy.canView` and `todo_relation`.

- [ ] Write failing tests for active/overdue counts, nearest due date, current owners, most recent action, chronological root-chain ordering, invisible items, and CC read-only visibility.
- [ ] Run focused tests and confirm `TodoBusinessViewService` is missing.
- [ ] Add mapper projections that join `todo_relation`, instances and action logs while enforcing the same owner/candidate/dept/supervisor/CC predicate as the Todo center.
- [ ] Implement immutable summary and chain DTOs; reject unknown business types and inaccessible roots with `TODO_ACCESS_DENIED`.
- [ ] Expose `GET /todo/business/{type}/{id}/summary`, `/list`, and `GET /todo/chain/{rootTodoId}` using `todo:chain:query`.
- [ ] Run focused tests and full backend tests.
- [ ] Commit with `feat(todo): expose business summaries and chain views`.

### Task 3: Exception Operations and SLA Waivers

**Files:**
- Create: `ruoyi-admin/src/main/resources/db/migration/V0_17_2__todo_operations.sql`
- Create: `law-todo/src/main/java/com/law/todo/application/TodoExceptionOperationService.java`
- Create: `law-todo/src/main/java/com/law/todo/application/command/TodoOperationCommands.java`
- Create: `law-todo/src/main/java/com/law/todo/domain/TodoExceptionPolicy.java`
- Create: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/todo/TodoOperationsController.java`
- Modify: `law-todo/src/main/java/com/law/todo/mapper/TodoMapper.java`
- Modify: `law-todo/src/main/resources/mapper/todo/TodoMapper.xml`
- Test: `law-todo/src/test/java/com/law/todo/application/TodoExceptionOperationServiceTest.java`
- Test: `ruoyi-admin/src/test/java/com/ruoyi/web/controller/todo/TodoOperationsControllerValidationTest.java`

**Interfaces:**
- Produces: `forceComplete`, `forceCancel`, `regenerate`, `batchTransfer`, `waiveSla`, `operationsDashboard`.
- Consumes: Todo state machine, immutable audit, Outbox replay, data scope.

- [ ] Write failing tests for required reason/actionId, terminal guards, non-bypassable business validators, duplicate action IDs, concurrent updates, partial batch rollback, new-owner department, SLA original/new due dates and unauthorized operators.
- [ ] Run tests and observe missing operation service errors.
- [ ] Create `todo_exception_log` and `todo_sla_waiver` with unique `action_id`, immutable actor/reason/payload snapshots, and due-date indexes; add `source_id` to directed notification uniqueness.
- [ ] Implement exception policy and transactional services. Force-complete invokes DoD business validators but may bypass only UI-required fields/attachments; force-cancel and batch-transfer use status conditions; SLA waiver extends the record and resets only thresholds not yet reached.
- [ ] Expose operations dashboard and action APIs under exact exception permissions.
- [ ] Run focused, backend and MySQL invariant tests.
- [ ] Commit with `feat(todo): add controlled exception operations`.

### Task 4: Standard Phase-Two Templates and Trigger Rules

**Files:**
- Create: `ruoyi-admin/src/main/resources/db/migration/V0_17_3__todo_standard_templates.sql`
- Create: `law-todo/src/test/java/com/law/todo/integration/PhaseTwoTemplateContractTest.java`
- Modify: `ruoyi-admin/src/test/java/com/ruoyi/web/migration/FlywayMigrationTest.java`

**Interfaces:**
- Produces stable codes: `CONTRACT_REVIEW`, `CONTRACT_SIGN`, `PAYMENT_CONFIRM`, `INVOICE_HANDLE`, `CASE_CREATE_CHECK`, `CASE_ASSIGN`, `CASE_ACCEPT`, `CASE_REASSIGN`, `CASE_TRANSFER_REVIEW`, `MATTER_NODE_HANDLE`, `MATTER_EXPENSE_REVIEW`, `MATTER_DOCUMENT_SUPPLY`, `CASE_CLOSE_CONFIRM`, `CASE_ARCHIVE_CONFIRM`.

- [ ] Write a failing migration contract test asserting every code, published version, event mapping, Owner rule, DoD rule, SLA rule and menu permission exists exactly once.
- [ ] Run the contract test and confirm missing templates.
- [ ] Seed idempotent templates and published version 1 definitions, using role IDs resolved from stable role keys rather than environment-specific numeric IDs; seed triggers for all phase-two business events.
- [ ] Add permissions and menus for definitions, operations and chain queries without granting dangerous actions to ordinary roles.
- [ ] Extend MySQL migration assertions to final version `0.17.3` and verify unique trigger constraints.
- [ ] Run migration tests in CI-compatible mode and full unit tests.
- [ ] Commit with `feat(todo): seed phase two standard workflows`.

### Task 5: Typed Business Actor Context

**Files:**
- Create: `law-business/src/main/java/com/law/business/security/BusinessActor.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/security/SecurityBusinessActorProvider.java`
- Modify: contract/case/matter public application services listed in Tasks 6-9.
- Test: `ruoyi-system/src/test/java/com/ruoyi/system/security/SecurityBusinessActorProviderTest.java`

**Interfaces:**
- Produces: `BusinessActor(userId,userName,displayName,deptId)` and `BusinessActorProvider.current()`.
- Consumes: RuoYi `SecurityUtils` only in provider/controller boundaries.

- [ ] Write failing tests proving completion handlers can invoke business services without reading thread-local `SecurityUtils`, while HTTP calls still resolve the authenticated actor.
- [ ] Run tests and confirm missing actor provider/overloads.
- [ ] Implement actor record/provider and typed service overloads; existing controller-facing methods delegate using `provider.current()`.
- [ ] Replace direct SecurityUtils reads only in phase-two command paths, preserving existing query behavior.
- [ ] Run contract, case and matter tests.
- [ ] Commit with `refactor(todo): pass typed actors into business commands`.

### Task 6: Contract Review and Signing Handlers

**Files:**
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/event/ContractReviewTodoHandler.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/event/ContractSignTodoHandler.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/event/ContractTodoValidator.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/contract/ContractLifecycleService.java`
- Test: `ruoyi-system/src/test/java/com/ruoyi/system/integration/ContractTodoFlowTest.java`

**Interfaces:**
- Review payload: `{action,opinion}`.
- Sign payload: `{signStatus,signMethod,signDate,signFileUrl}`.
- Produces `CONTRACT_APPROVED` and `CONTRACT_SIGNED` Outbox facts via existing lifecycle service.

- [ ] Write failing tests for pass/back/reject DoD, stale audit state, duplicate completion, missing signature file, successful signing and rollback when business action fails.
- [ ] Run tests and confirm missing handlers.
- [ ] Add typed actor overloads `approve(contractId,action,opinion,actor)` and `sign(contractId,command,actor)` while keeping status conditions and event publication.
- [ ] Implement template-code-aware validator/handlers; never dispatch by mutable title.
- [ ] Ensure review back/reject terminates the standard chain and successful pass creates only the sign Todo.
- [ ] Run flow tests and full backend tests.
- [ ] Commit with `feat(todo): connect contract review and signing`.

### Task 7: Payment, Invoice and Case-Creation Handlers

**Files:**
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/event/ContractPaymentTodoHandler.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/event/ContractInvoiceTodoHandler.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/event/CaseCreationTodoHandler.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/contract/ContractPaymentService.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/contract/ContractInvoiceService.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/casecenter/CaseCreationService.java`
- Test: `ruoyi-system/src/test/java/com/ruoyi/system/integration/ContractToCaseTodoFlowTest.java`

**Interfaces:**
- Payment payload: `{planId,receivedAmount,voucherUrl,remark}` with relation to fee plan.
- Invoice payload: `{planId,action,invoiceNo,invoiceFileUrl,remark}`.
- Case-create payload: `{contractId,caseType,materialsChecked}`.

- [ ] Write failing tests for one Todo per fee plan, duplicate payment/invoice actions, conditional invoice skip, incomplete contract prerequisites, and exactly one created case.
- [ ] Run failing tests.
- [ ] Add typed actor command overloads and stable idempotency keys that include fee-plan IDs.
- [ ] Implement handlers/validators and add secondary Todo relations for contract and fee plan.
- [ ] Remove direct case creation from contract signing; case creation occurs only after configured payment/invoice prerequisites through `CASE_CREATE_CHECK`.
- [ ] Run contract-to-case integration and regression tests.
- [ ] Commit with `feat(todo): drive payment invoice and case creation`.

### Task 8: Case Assignment, Acceptance, Reassignment and Transfer

**Files:**
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/event/CaseAssignmentTodoHandler.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/event/CaseAcceptanceTodoHandler.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/event/CaseTransferReviewTodoHandler.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/event/CaseTodoValidator.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/casecenter/CaseAssignmentService.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/casecenter/CaseConfirmationService.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/casecenter/CaseTransferService.java`
- Test: `ruoyi-system/src/test/java/com/ruoyi/system/integration/CaseManagementTodoFlowTest.java`

**Interfaces:**
- Assignment payload: typed `CaseAssignCommand` plus actor.
- Acceptance payload: `{accepted,reason}`.
- Transfer review payload: `{transferId,action,opinion}`.

- [ ] Write failing tests for assign→accept, reject→reassign, transfer-request→approve→new-accept, reject transfer, stale owner, disabled lawyer, duplicate event and cancellation of old-owner active Todos.
- [ ] Run failures.
- [ ] Add typed actor overloads to existing case application services without weakening state/data checks.
- [ ] Implement validators/handlers keyed by template code and cancellation service `cancelActiveByBusinessAndOwner(type,id,owner,reason,actor)`.
- [ ] Ensure every path produces one correct next Todo and immutable audit.
- [ ] Run flow and backend tests.
- [ ] Commit with `feat(todo): drive case assignment acceptance and transfer`.

### Task 9: Matter Node, Expense and Document Workflows

**Files:**
- Modify: `law-business/src/main/java/com/law/business/event/BusinessEventType.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/event/MatterNodeTodoHandler.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/event/MatterExpenseTodoHandler.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/event/MatterDocumentTodoHandler.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/matter/MatterNodeService.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/matter/MatterExpenseService.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/matter/MatterDocumentService.java`
- Test: `ruoyi-system/src/test/java/com/ruoyi/system/integration/MatterWorkTodoFlowTest.java`

**Interfaces:**
- Adds events `MATTER_NODE_READY`, `MATTER_NODE_COMPLETED`, `MATTER_EXPENSE_SUBMITTED`, `MATTER_DOCUMENT_REQUIRED`.
- Node completion payload includes node ID, actual date, dynamic fields and material statuses.

- [ ] Write failing tests for event publication, node material DoD, dynamic fields, next-node generation, final-node close candidate, expense review, required voucher and required document upload.
- [ ] Run failures.
- [ ] Add typed business commands and publish events only after successful status/material updates.
- [ ] Implement handlers/validators and secondary relations to node, expense and document records.
- [ ] Configure next-node routing from immutable node/template data, not frontend input.
- [ ] Run matter flow and regression tests.
- [ ] Commit with `feat(todo): drive matter nodes expenses and documents`.

### Task 10: Closing and Archive Workflows

**Files:**
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/event/CaseCloseTodoHandler.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/event/CaseArchiveTodoHandler.java`
- Create: `ruoyi-system/src/main/java/com/ruoyi/system/service/event/ArchiveTodoValidator.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/matter/MatterArchiveService.java`
- Test: `ruoyi-system/src/test/java/com/ruoyi/system/integration/ArchiveTodoFlowTest.java`

**Interfaces:**
- Close payload: `{action,opinion,feeClearStatus}`.
- Archive payload: `{action,opinion,archiveNo,materials}`.

- [ ] Write failing tests for unfinished nodes, unpaid expenses, missing materials, close rejection, successful close→archive, archive rejection, duplicate archive and cancellation of remaining active Todos.
- [ ] Run failures.
- [ ] Add typed actor overloads for apply/confirm-close/confirm-archive and preserve existing status conditions.
- [ ] Implement validators/handlers and terminal cleanup of nonterminal case Todos.
- [ ] Run archive flow and full backend tests.
- [ ] Commit with `feat(todo): drive close and archive workflows`.

### Task 11: Configuration Center Frontend

**Files:**
- Create: `ruoyi-ui/src/api/todo-definition.js`
- Create: `ruoyi-ui/src/views/todo/config/index.vue`
- Create: `ruoyi-ui/src/views/todo/config/components/TemplateList.vue`
- Create: `ruoyi-ui/src/views/todo/config/components/VersionDrawer.vue`
- Create: `ruoyi-ui/src/views/todo/config/components/DefinitionForm.vue`
- Create: `ruoyi-ui/src/views/todo/config/components/TriggerRuleTable.vue`
- Create: `ruoyi-ui/src/views/todo/config/components/WorkCalendarTable.vue`
- Modify: `ruoyi-ui/scripts/check-todo-ui.js`

**Interfaces:**
- Consumes Task 1 definition APIs and existing calendar APIs.
- Produces route component `todo/config/index`.

- [ ] Extend the static UI contract first; require all API exports, structured Owner/DoD/SLA/next forms, copy actions, draft-only editing, publish confirmation, permissions, loading and error retention; run and observe failure.
- [ ] Implement focused components using Element UI, existing business UI primitives and dict tags; advanced JSON is generated preview, not free-form input.
- [ ] Add validation for required rules, positive SLA minutes and published next versions.
- [ ] Run `npm run test:todo` and `npm run build:prod`.
- [ ] Commit with `feat(todo): add workflow configuration center`.

### Task 12: Operations Center Frontend

**Files:**
- Create: `ruoyi-ui/src/api/todo-operations.js`
- Create: `ruoyi-ui/src/views/todo/operations/index.vue`
- Create: `ruoyi-ui/src/views/todo/operations/components/OperationsMetrics.vue`
- Create: `ruoyi-ui/src/views/todo/operations/components/EventFailureTable.vue`
- Create: `ruoyi-ui/src/views/todo/operations/components/EscalationTable.vue`
- Create: `ruoyi-ui/src/views/todo/operations/components/ExceptionActionDialog.vue`
- Modify: `ruoyi-ui/scripts/check-todo-ui.js`

**Interfaces:**
- Consumes Task 3 operations APIs and existing notifications/events.
- Produces route component `todo/operations/index`.

- [ ] Extend UI contract first for metrics, DEAD replay, force actions, batch transfer, SLA waiver, permission directives, confirmation and mandatory reason; run to verify failure.
- [ ] Implement API and components; dangerous operations display business object, current state and consequence before confirmation.
- [ ] Keep form data after errors and refresh only after success.
- [ ] Run UI contract and production build.
- [ ] Commit with `feat(todo): add todo operations center`.

### Task 13: Business Embedding and Todo Center Expansion

**Files:**
- Create: `ruoyi-ui/src/views/todo/components/BusinessTodoSummary.vue`
- Create: `ruoyi-ui/src/views/todo/components/BusinessTodoDrawer.vue`
- Create: `ruoyi-ui/src/views/todo/components/TodoChainTimeline.vue`
- Modify: `ruoyi-ui/src/views/contract/components/ContractDetailDrawer.vue`
- Modify: `ruoyi-ui/src/views/case/components/CaseDetailDrawer.vue`
- Modify: `ruoyi-ui/src/views/matter/components/MatterDetailDrawer.vue`
- Modify: `ruoyi-ui/src/views/todo/index.vue`
- Modify: `ruoyi-ui/src/api/todo.js`
- Modify: `ruoyi-ui/scripts/check-todo-ui.js`

**Interfaces:**
- Props: `businessType:String`, `businessId:Number|String`, optional `businessNo:String`.
- Consumes Task 2 summary/list/chain APIs.

- [ ] Extend static contract first for all three embedded drawers, shared summary/drawer/timeline, visible/dept/CC views and source navigation; observe failure.
- [ ] Implement shared components with independent loading/error states and no mutation of parent business data.
- [ ] Embed components in contract, case and matter details; expand Todo center modes and chain action.
- [ ] Run Todo UI contract and production build.
- [ ] Commit with `feat(todo): embed todo summaries and chains`.

### Task 14: API Integration and MySQL End-to-End Tests

**Files:**
- Create: `ruoyi-admin/src/test/java/com/ruoyi/web/todo/TodoPhaseTwoApiTest.java`
- Create: `ruoyi-admin/src/test/java/com/ruoyi/web/migration/PhaseTwoDatabaseInvariantTest.java`
- Create: `ruoyi-system/src/test/java/com/ruoyi/system/integration/TodoPhaseTwoTransactionTest.java`
- Modify: `.github/workflows/ci.yml`

**Interfaces:**
- Tests all APIs and chains produced by Tasks 1-13.

- [ ] Write failing MockMvc tests for every new route, DTO validation, granular permission and business error code.
- [ ] Write MySQL tests for Flyway final version, seeded definitions, immutable versions, unique idempotency keys, conditional publication, exception action uniqueness and SLA waiver transactions.
- [ ] Write transaction tests proving handler failure rolls back Todo completion and successful completion commits business fact plus Outbox atomically.
- [ ] Run tests against MySQL 8 and fix only production defects exposed by these tests.
- [ ] Extend CI migration job to assert the phase-two final Flyway version and add a dedicated phase-two integration job.
- [ ] Commit with `test(todo): cover phase two api and database integration`.

### Task 15: Playwright E2E, Final Audit and Acceptance

**Files:**
- Create: `ruoyi-ui/e2e/todo-contract-to-case.spec.js`
- Create: `ruoyi-ui/e2e/todo-case-management.spec.js`
- Create: `ruoyi-ui/e2e/todo-matter-archive.spec.js`
- Create: `doc/v0.17-todo-phase-two-acceptance.md`
- Modify: `.github/workflows/ci.yml`

**Interfaces:**
- Produces final acceptance evidence for the phase-two design criteria.

- [ ] Add deterministic test fixtures and write failing Playwright flows for contract submission→case creation, case assignment→accept/transfer, and node handling→close/archive.
- [ ] Run each E2E test against the CI MySQL application and fix exposed product defects with regression tests first.
- [ ] Run `mvn --batch-mode --no-transfer-progress clean verify`, `npm run test:todo`, `npm run build:prod`, MySQL/Flyway tests and all Playwright specs; require zero failures.
- [ ] Audit every design acceptance criterion against current files, APIs, database state, rendered pages and test evidence; do not mark indirect evidence as complete.
- [ ] Write the acceptance matrix with exact commands, test counts, commit SHA and CI URL.
- [ ] Push `V0.17`, monitor backend/frontend/migration/integration/E2E jobs and fix any failure.
- [ ] Commit final evidence with `docs(todo): record phase two acceptance evidence` and confirm the final commit CI is green.

## Plan Self-Review

- All design requirements map to Tasks 1-15.
- Template administration, contract, case management, matter/archive, operations, embedding, database integration and E2E are independently testable deliverables.
- Every production task begins with a failing test and ends with focused plus regression verification.
- All cross-task types and endpoint names match the phase-two design.
- No task introduces excluded DSL, BPM, MQ, microservice, multi-tenant or mobile scope.
