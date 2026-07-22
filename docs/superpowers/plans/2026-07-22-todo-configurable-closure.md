# Todo Engine Configurable Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the business-admin configuration loop from event metadata through guided DoD and object-backed simulation, meeting every phase-one acceptance criterion without requiring raw JSON or internal codes for common flows.

**Architecture:** Extend the existing event catalogue as the versioned source of payload metadata, enrich trusted Java validator capabilities with governed database metadata, and layer typed resource APIs over both. Reuse the actor-scoped business directory, add simulation-only sample objects, and drive the Vue configuration editors from these resources while preserving immutable published definitions.

**Tech Stack:** Java 17, Spring Boot 3.5, MyBatis, Flyway, MySQL 8, Jackson, Vue 2.6, Element UI, Node contract tests, Playwright/Chrome.

## Global Constraints

- Preserve existing published template versions, Todo instances, Foundation gates, and business data permissions.
- Do not commit `.superpowers/sdd/task-7-report.md`, `ruoyi-ui/vue.config.js`, `.runtime-logs/`, `.playwright-cli/`, or captured `output/` artifacts unless explicitly required as review evidence.
- All new production behavior follows red-green-refactor; each test must fail for the expected missing behavior before implementation.
- Active event schemas are immutable; changes create a new draft payload version.
- Java SPI remains the executable validator trust boundary; the database cannot register a class or script.
- Sample business objects are simulation-only and cannot be persisted into Todo runtime facts.
- Common trigger, DoD, and simulation flows must not require raw JSON or internal codes.

---

### Task 1: Phase-one acceptance contract and migration

**Files:**
- Create: `ruoyi-admin/src/main/resources/db/migration/V0_20_36__todo_configuration_resources.sql`
- Create: `law-todo/src/test/java/com/law/todo/integration/TodoConfigurableClosureMigrationContractTest.java`
- Modify: `law-todo/src/test/java/com/law/todo/integration/V02PrdDefinitionManifestTest.java`

**Interfaces:**
- Produces: enriched `todo_event_catalog`, `todo_validator_metadata`, resource permissions/menu, 36 READY event schemas.

- [ ] Add a failing migration contract asserting new columns, validator metadata constraints, resource permissions, and all known event update statements.
- [ ] Run `mvn -pl law-todo -Dtest=TodoConfigurableClosureMigrationContractTest test` and confirm failure because `V0_20_36` is absent.
- [ ] Add the forward-only migration with event metadata and typed schemas for every active v1 event.
- [ ] Add a manifest test that rejects an active event with no schema properties.
- [ ] Run both tests and confirm they pass.

### Task 2: Versioned event resource service and API

**Files:**
- Create: `law-todo/src/main/java/com/law/todo/application/TodoEventResourceService.java`
- Create: `law-todo/src/main/java/com/law/todo/application/command/TodoResourceCommands.java`
- Create: `law-todo/src/main/java/com/law/todo/application/view/TodoResourceViews.java`
- Modify: `law-todo/src/main/java/com/law/todo/mapper/TodoConfigurationMapper.java`
- Modify: `law-todo/src/main/resources/mapper/todo/TodoConfigurationMapper.xml`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/todo/TodoConfigurationController.java`
- Test: `law-todo/src/test/java/com/law/todo/application/TodoEventResourceServiceTest.java`

**Interfaces:**
- Produces: paged event resources, detail, create v1, create next draft version, update draft, activate/disable, dependency view.

- [ ] Write failing tests for schema validation, immutable active versions, optimistic locking, sample validation, and activation guards.
- [ ] Implement commands, views, mapper methods, service, stable errors, and controller endpoints under `/todo/config/resources/events`.
- [ ] Run focused service/controller contract tests and the existing definition compiler suite.

### Task 3: Validator, field, material, and recipe catalogues

**Files:**
- Create: `law-todo/src/main/java/com/law/todo/application/TodoConfigurationResourceCatalogService.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoDefinitionCatalogService.java`
- Modify: `law-todo/src/main/java/com/law/todo/spi/TodoBusinessValidator.java`
- Modify: `law-todo/src/main/java/com/law/todo/mapper/TodoConfigurationMapper.java`
- Modify: `law-todo/src/main/resources/mapper/todo/TodoConfigurationMapper.xml`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/todo/TodoConfigurationController.java`
- Test: `law-todo/src/test/java/com/law/todo/application/TodoConfigurationResourceCatalogServiceTest.java`

**Interfaces:**
- Produces: `/resources/validators`, `/resources/fields`, `/resources/materials`, `/resources/dod-recipes`.

- [ ] Write failing tests for registered/unavailable validators, business-type filtering, typed fields, and recipe expansion.
- [ ] Implement governed metadata merging without permitting database-defined executable code.
- [ ] Enforce that new DoD references only selectable validators and known resource codes.
- [ ] Run focused tests plus all DoD management and compiler tests.

### Task 4: Real and sample business-object directory

**Files:**
- Modify: `law-todo/src/main/java/com/law/todo/spi/TodoBusinessDirectoryAccess.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoConfigurationQueryService.java`
- Modify: `ruoyi-system/src/main/java/com/ruoyi/system/service/todo/RuoYiTodoBusinessDirectoryAccess.java`
- Modify: `ruoyi-system/src/main/resources/mapper/system/TodoBusinessDirectoryMapper.xml`
- Create: `law-todo/src/main/java/com/law/todo/application/TodoSimulationSampleCatalog.java`
- Test: `law-todo/src/test/java/com/law/todo/application/TodoConfigurationBusinessObjectDirectoryTest.java`
- Test: `ruoyi-system/src/test/java/com/ruoyi/system/service/todo/RuoYiTodoBusinessDirectoryAccessTest.java`

**Interfaces:**
- Produces: paged entries with `source`, `sample`, and diagnostic empty state; sample IDs accepted only by simulation.

- [ ] Write failing tests for five types, no-data samples, no-permission diagnosis, and rejection of sample IDs outside simulation.
- [ ] Implement the enriched directory response and sample catalogue.
- [ ] Add deterministic test fixtures without mutating production business tables.
- [ ] Run both modules' focused tests.

### Task 5: Configuration resource frontend

**Files:**
- Create: `ruoyi-ui/src/api/todo-resources.js`
- Create: `ruoyi-ui/src/views/todo/config/resource/index.vue`
- Create: `ruoyi-ui/src/views/todo/config/resource/EventResourceDrawer.vue`
- Create: `ruoyi-ui/src/views/todo/config/resource/PayloadSchemaDesigner.vue`
- Create: `ruoyi-ui/src/views/todo/config/resource/ValidatorCatalogPanel.vue`
- Create: `ruoyi-ui/src/views/todo/config/resource/BusinessResourcePanel.vue`
- Modify: `ruoyi-ui/src/views/todo/config/trigger/TriggerConditionBuilder.vue`
- Test: `ruoyi-ui/scripts/check-todo-configurable-closure.js`

**Interfaces:**
- Consumes: Tasks 2–3 resource APIs.
- Produces: event maintenance, payload field design, validator governance, actionable empty states.

- [ ] Add a failing Node contract test for routes, permissions, APIs, typed controls, and no-raw-code common flow.
- [ ] Implement resource pages and Schema designer with existing configuration-center components/styles.
- [ ] Replace trigger builder's generic empty dropdown with typed fields and actionable schema errors.
- [ ] Run the contract, encoding, and production build checks.

### Task 6: Guided DoD simple mode

**Files:**
- Create: `ruoyi-ui/src/views/todo/config/dod/DodSimpleEditor.vue`
- Create: `ruoyi-ui/src/views/todo/config/dod/dod-recipe-codec.js`
- Modify: `ruoyi-ui/src/views/todo/config/dod/DodRuleDrawer.vue`
- Modify: `ruoyi-ui/src/views/todo/config/template/steps/TemplateDodStep.vue`
- Test: `ruoyi-ui/scripts/check-todo-dod-simple-editor.js`

**Interfaces:**
- Consumes: typed fields, materials, validators, recipes.
- Produces: existing `todo_dod_rule` JSON without business-user JSON/code entry.

- [ ] Add failing codec and structure tests for recipe loading, simple-to-legacy JSON, round-trip edit, and advanced compatibility.
- [ ] Implement the four guided sections and collapse advanced mode.
- [ ] Add inline human-readable summary and rule test action.
- [ ] Run frontend contracts and DoD backend regression tests.

### Task 7: Simulation workspace closure

**Files:**
- Modify: `ruoyi-ui/src/views/todo/config/simulation/SimulationDrawer.vue`
- Modify: `ruoyi-ui/src/views/todo/config/simulation/SimulationResult.vue`
- Create: `ruoyi-ui/src/views/todo/config/simulation/SchemaPayloadForm.vue`
- Create: `ruoyi-ui/src/views/todo/config/simulation/BusinessObjectPicker.vue`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoConfigurationSimulationService.java`
- Test: `law-todo/src/test/java/com/law/todo/application/TodoConfigurationSimulationServiceTest.java`
- Test: `ruoyi-ui/scripts/check-todo-simulation-closure.js`

**Interfaces:**
- Consumes: event schemas and enriched business-object directory.
- Produces: object-backed, schema-driven, read-only simulation with ordered results.

- [ ] Write failing backend tests for sample isolation and ordered diagnostics.
- [ ] Write failing frontend contracts for remote object search, sample fallback, selected object card, typed payload form, and ordered result steps.
- [ ] Implement the workspace using existing read-only simulation APIs.
- [ ] Run focused backend, frontend, and no-side-effect regression tests.

### Task 8: Publish guards, E2E, and acceptance audit

**Files:**
- Modify: `law-todo/src/main/java/com/law/todo/definition/compiler/TodoDefinitionCompiler.java`
- Modify: `ruoyi-ui/tests/e2e/todo-config-center.spec.js`
- Create: `doc/reviews/todo-configurable-closure-acceptance.md`
- Modify: `.github/workflows/ci.yml`

**Interfaces:**
- Produces: publish rejection for incomplete resources and a real-browser acceptance journey.

- [ ] Add failing compiler tests for empty schema, missing field, unavailable validator, and invalid example payload.
- [ ] Implement publish guards and stable validation issues.
- [ ] Add Chrome E2E for event resource maintenance, trigger field selection, simple DoD, sample/real object simulation, and publish.
- [ ] Run `mvn verify`, real MySQL Flyway, all Todo/frontend contract scripts, `npm run build:prod`, and the real-backend E2E.
- [ ] Audit each design acceptance criterion against fresh command, database, API, and browser evidence; record only proven results.

