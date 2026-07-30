# Todo Engine 模拟发布统一准入 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让必要场景、完整试运行、配置健康度、发布预检、版本差异和发布按钮使用同一套服务端准入事实，消除成功、报错和阻塞状态同时出现的矛盾。

**Architecture:** 复用 append-only 的 `todo_simulation_evidence`，以 `FULL_SIMULATION` 作为完整试运行证据编码；新增 `TodoSimulationReadinessService` 统一计算双重门禁。完整试运行、发布预检、模板旅程和配置工作台都消费该投影，前端只负责展示、定位和刷新，不再自行推断发布资格。

**Tech Stack:** Java 17、Spring Boot、MyBatis、MySQL 8、JUnit 5、Mockito、Vue 2、Element UI、Node 契约测试、Playwright。

## Global Constraints

- 发布必须同时满足：当前定义哈希下所有必要场景通过，并且当前定义哈希下完整试运行通过。
- 试运行不得创建运行时待办，不得修改线索、客户、合同、案件或财务业务数据。
- 模拟证据和脱敏审计记录必须 append-only，不修改不可变发布版本。
- 定义哈希、绑定规则或必要场景版本变化后，旧证据自动失效。
- 所有用户主文案使用中文；技术错误码只放在诊断详情中。
- 版本差异属于辅助信息，失败不得阻塞发布预检。
- 不升级 Vue、RuoYi 或其他基础技术栈。
- 保留工作区中与本计划无关的用户修改和运行产物，不使用 `git add -A`。

---

## File Structure

### Backend

- Create: `law-todo/src/main/java/com/law/todo/application/TodoSimulationReadinessService.java`
  - 双重门禁的唯一计算入口及中文问题投影。
- Create: `law-todo/src/main/java/com/law/todo/application/view/TodoSimulationReadinessView.java`
  - 稳定的服务端准入响应类型，避免视图层依赖具体服务实现。
- Create: `law-todo/src/test/java/com/law/todo/application/TodoSimulationReadinessServiceTest.java`
  - 覆盖四种门禁组合和哈希失效。
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoSimulationEvidenceService.java`
  - 追加 `FULL_SIMULATION` 证据并提供精确证据查询。
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoJourneySimulationService.java`
  - 完整试运行后记录证据并返回统一准入状态。
- Modify: `law-todo/src/main/java/com/law/todo/application/view/TodoJourneySimulationResult.java`
  - 增加 `readiness` 字段。
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoDefinitionService.java`
  - 发布预检应用统一双重门禁。
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoConfigurationJourneyEvaluator.java`
  - 删除对 `ui.simulationStatus` 的依赖，接收统一准入投影。
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoConfigurationJourneyService.java`
  - 模板详情和工作台使用统一准入投影。
- Modify: `law-todo/src/main/java/com/law/todo/mapper/TodoConfigurationMapper.java`
  - 增加单模板和工作台批量准入查询。
- Modify: `law-todo/src/main/resources/mapper/todo/TodoConfigurationMapper.xml`
  - 查询当前哈希下场景证据和完整试运行证据。
- Modify tests:
  - `law-todo/src/test/java/com/law/todo/application/TodoSimulationEvidenceServiceTest.java`
  - `law-todo/src/test/java/com/law/todo/application/TodoJourneySimulationServiceTest.java`
  - `law-todo/src/test/java/com/law/todo/application/TodoDefinitionServiceTest.java`
  - `law-todo/src/test/java/com/law/todo/application/TodoConfigurationJourneyEvaluatorTest.java`
  - `law-todo/src/test/java/com/law/todo/application/TodoConfigurationJourneyServiceTest.java`

### Frontend

- Modify: `ruoyi-ui/src/views/todo/config/journey/simulation-workbench-model.js`
  - 版本字段规范化、差异请求计划和统一成功提示判定。
- Modify: `ruoyi-ui/src/views/todo/config/journey/steps/SimulationPublishStep.vue`
  - 单一操作收口、内联差异警告、服务端准入同步。
- Modify: `ruoyi-ui/src/views/todo/config/journey/index.vue`
  - 接收准入变化并同步步骤导航和健康度。
- Modify: `ruoyi-ui/src/views/todo/config/journey/components/ConfigurationHealthPanel.vue`
  - 中文模拟问题和准确修复动作。
- Modify: `ruoyi-ui/scripts/check-todo-phase-two-ux.js`
  - 可执行的前端模型与源码契约测试。

### Integration and E2E

- Create: `ruoyi-admin/src/test/java/com/ruoyi/web/migration/TodoSimulationReadinessExternalMysqlIT.java`
  - 验证证据、门禁和业务表不变。
- Modify: `ruoyi-ui/tests/e2e/todo-config-center.spec.js`
  - 真实页面完整闭环。

---

### Task 1: 建立统一 SimulationReadiness 计算服务

**Files:**
- Create: `law-todo/src/main/java/com/law/todo/application/TodoSimulationReadinessService.java`
- Create: `law-todo/src/main/java/com/law/todo/application/view/TodoSimulationReadinessView.java`
- Create: `law-todo/src/test/java/com/law/todo/application/TodoSimulationReadinessServiceTest.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoSimulationEvidenceService.java`

**Interfaces:**
- Produces:

```java
public static final String FULL_SIMULATION = "FULL_SIMULATION";

public record TodoSimulationReadinessView(
        long templateId,
        long versionId,
        String definitionHash,
        int requiredScenarioCount,
        int passedScenarioCount,
        List<SimulationGateBlocker> blockingScenarios,
        boolean fullSimulationPassed,
        boolean publicationReady,
        List<JourneyIssue> issues) { }

public TodoSimulationReadinessView readiness(
        long templateId,
        long versionId,
        String definitionHash,
        String templateCode,
        String businessType);
```

- Consumes:
  - `TodoSimulationScenarioCatalog.scenarios(templateCode, businessType)`
  - `TodoSimulationEvidenceService.gate(...)`
  - `TodoConfigurationMapper.selectPassingSimulationEvidence(query)`

- [ ] **Step 1: Write the failing readiness tests**

Add exact cases:

```java
@Test void blocksWhenScenariosPassButFullSimulationIsMissing()
{
    when(scenarios.scenarios("TD-001","LEAD")).thenReturn(requiredScenarios());
    when(evidence.gate(17L,88L,"hash-88",requiredScenarios()))
            .thenReturn(new PublicationGate(true,List.of()));
    when(evidence.hasPassingEvidence(17L,88L,"hash-88","FULL_SIMULATION",1))
            .thenReturn(false);

    TodoSimulationReadinessView value=service.readiness(17L,88L,"hash-88","TD-001","LEAD");

    assertThat(value.publicationReady()).isFalse();
    assertThat(value.issues()).extracting(JourneyIssue::code)
            .containsExactly("TODO_FULL_SIMULATION_REQUIRED");
}

@Test void blocksWhenFullSimulationPassesButOneScenarioIsMissing()
{
    when(evidence.gate(17L,88L,"hash-88",requiredScenarios()))
            .thenReturn(new PublicationGate(false,List.of("TD001_UNREACHABLE")));
    when(evidence.hasPassingEvidence(17L,88L,"hash-88","FULL_SIMULATION",1))
            .thenReturn(true);

    TodoSimulationReadinessView value=service.readiness(17L,88L,"hash-88","TD-001","LEAD");

    assertThat(value.publicationReady()).isFalse();
    assertThat(value.blockingScenarios()).extracting(SimulationGateBlocker::scenarioCode)
            .containsExactly("TD001_UNREACHABLE");
}

@Test void isReadyOnlyWhenBothGatesPass()
{
    when(evidence.gate(17L,88L,"hash-88",requiredScenarios()))
            .thenReturn(new PublicationGate(true,List.of()));
    when(evidence.hasPassingEvidence(17L,88L,"hash-88","FULL_SIMULATION",1))
            .thenReturn(true);

    assertThat(service.readiness(17L,88L,"hash-88","TD-001","LEAD").publicationReady())
            .isTrue();
}

@Test void doesNotReuseEvidenceFromAnotherDefinitionHash()
{
    when(evidence.gate(17L,88L,"hash-new",requiredScenarios()))
            .thenReturn(new PublicationGate(false,List.of("TD001_VALID")));
    when(evidence.hasPassingEvidence(17L,88L,"hash-new","FULL_SIMULATION",1))
            .thenReturn(false);

    TodoSimulationReadinessView value=
            service.readiness(17L,88L,"hash-new","TD-001","LEAD");

    assertThat(value.publicationReady()).isFalse();
    verify(evidence,never()).hasPassingEvidence(
            17L,88L,"hash-old","FULL_SIMULATION",1);
}
```

- [ ] **Step 2: Run the tests and verify RED**

Run:

```powershell
mvn -pl law-todo -am `
  -Dtest=TodoSimulationReadinessServiceTest `
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation fails because `TodoSimulationReadinessService` and `hasPassingEvidence` do not exist.

- [ ] **Step 3: Add exact evidence lookup**

Add to `TodoSimulationEvidenceService`:

```java
@Transactional(readOnly=true)
public boolean hasPassingEvidence(long templateId,long versionId,String definitionHash,
        String scenarioCode,int scenarioVersion)
{
    Map<String,Object> query=new HashMap<>();
    query.put("templateId",templateId);
    query.put("versionId",versionId);
    query.put("definitionHash",definitionHash);
    query.put("scenarioCode",scenarioCode);
    query.put("scenarioVersion",scenarioVersion);
    return mapper.selectPassingSimulationEvidence(query)!=null;
}
```

Do not add a fallback that ignores `definitionHash` or `scenarioVersion`.

- [ ] **Step 4: Implement the readiness service**

Implement four deterministic states:

```java
boolean scenariosReady=scenarioGate.publicationReady();
boolean fullReady=evidence.hasPassingEvidence(
        templateId,versionId,definitionHash,FULL_SIMULATION,1);
boolean ready=scenariosReady&&fullReady;
```

Create Chinese `JourneyIssue` values:

```java
new JourneyIssue(
    "TODO_REQUIRED_SIMULATION_SCENARIOS_INCOMPLETE",
    "BLOCKER",
    "SIMULATION_PUBLISH",
    "simulation.scenarios",
    "还有必测场景未通过："+scenarioNames,
    "验证未通过场景");

new JourneyIssue(
    "TODO_FULL_SIMULATION_REQUIRED",
    "BLOCKER",
    "SIMULATION_PUBLISH",
    "simulation.full",
    "完整试运行尚未通过",
    "运行完整试运行");
```

- [ ] **Step 5: Run focused and module tests**

Run:

```powershell
mvn -pl law-todo -am `
  -Dtest=TodoSimulationReadinessServiceTest,TodoSimulationEvidenceServiceTest `
  -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl law-todo -am test
```

Expected: all tests pass; no existing scenario gate behavior changes.

- [ ] **Step 6: Commit**

```powershell
git add -- `
  law-todo/src/main/java/com/law/todo/application/TodoSimulationReadinessService.java `
  law-todo/src/main/java/com/law/todo/application/view/TodoSimulationReadinessView.java `
  law-todo/src/main/java/com/law/todo/application/TodoSimulationEvidenceService.java `
  law-todo/src/test/java/com/law/todo/application/TodoSimulationReadinessServiceTest.java `
  law-todo/src/test/java/com/law/todo/application/TodoSimulationEvidenceServiceTest.java
git commit -m "feat(todo): add unified simulation readiness"
```

---

### Task 2: 记录完整试运行证据并返回准入状态

**Files:**
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoSimulationEvidenceService.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoJourneySimulationService.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/view/TodoJourneySimulationResult.java`
- Modify: `law-todo/src/test/java/com/law/todo/application/TodoJourneySimulationServiceTest.java`
- Modify: `law-todo/src/test/java/com/law/todo/application/TodoSimulationEvidenceServiceTest.java`

**Interfaces:**
- Consumes: `TodoSimulationReadinessService.readiness(...)`
- Produces:

```java
@Transactional(propagation=Propagation.REQUIRES_NEW)
public SimulationEvidenceSummary recordFull(
        long templateId,
        JourneySimulationCommand command,
        boolean passed,
        List<String> traceCodes,
        Actor actor);
```

- Extends:

```java
public record TodoJourneySimulationResult(
        HydratedPayload payload,
        TodoSimulationView engine,
        List<TraceSection> trace,
        EmployeeTodoPreview employeePreview,
        List<JourneyIssue> issues,
        boolean publishEligible,
        TodoSimulationReadinessView readiness) { }
```

- [ ] **Step 1: Write failing full-evidence tests**

Add a passing and a failing test:

```java
@Test void recordsFullSimulationEvidenceForTheExactDefinition()
{
    TodoJourneySimulationResult result=service.simulate(command(),actor());

    verify(evidence).recordFull(
            eq(17L),eq(command()),eq(true),anyList(),eq(actor()));
    assertThat(result.readiness().publicationReady()).isTrue();
}

@Test void recordsFailedFullSimulationWithoutMakingTheDraftReady()
{
    when(simulations.simulate(any(),any(),any())).thenReturn(failedSimulation());

    TodoJourneySimulationResult result=service.simulate(command(),actor());

    verify(evidence).recordFull(
            eq(17L),eq(command()),eq(false),anyList(),eq(actor()));
    assertThat(result.readiness().publicationReady()).isFalse();
}
```

- [ ] **Step 2: Verify RED**

Run:

```powershell
mvn -pl law-todo -am `
  -Dtest=TodoJourneySimulationServiceTest,TodoSimulationEvidenceServiceTest `
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation fails because `recordFull` and `readiness` do not exist in the result.

- [ ] **Step 3: Implement append-only full evidence**

`recordFull` must insert:

```java
row.put("scenarioCode",TodoSimulationReadinessService.FULL_SIMULATION);
row.put("scenarioVersion",1);
row.put("resultStatus",passed?"PASSED":"FAILED");
row.put("definitionHash",command.expectedDefinitionHash());
```

Build `inputHash` from:

```text
templateId + versionId + definitionHash + businessType + businessId
+ manualOverrides + effectiveAt + taskCompletions
```

Do not reuse scenario input hashes and do not update existing rows.

- [ ] **Step 4: Wire simulation completion**

In `TodoJourneySimulationService.simulate`:

```java
boolean successful=successful(engine,command.expectedDefinitionHash());
evidence.recordFull(command.templateId(),command,successful,
        trace(engine,preview,policy).stream().map(TraceSection::code).toList(),actor);
TodoSimulationReadinessView readiness=readinessService.readiness(
        command.templateId(),command.versionId(),command.expectedDefinitionHash(),
        journey.template().templateCode(),command.businessType());
```

Return the readiness projection. `publishEligible` must equal:

```java
successful && readiness.publicationReady()
```

- [ ] **Step 5: Run tests**

Run:

```powershell
mvn -pl law-todo -am `
  -Dtest=TodoJourneySimulationServiceTest,TodoSimulationEvidenceServiceTest `
  -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl law-todo -am test
```

Expected: all pass; failed simulations append evidence but never become publishable.

- [ ] **Step 6: Commit**

```powershell
git add -- `
  law-todo/src/main/java/com/law/todo/application/TodoSimulationEvidenceService.java `
  law-todo/src/main/java/com/law/todo/application/TodoJourneySimulationService.java `
  law-todo/src/main/java/com/law/todo/application/view/TodoJourneySimulationResult.java `
  law-todo/src/test/java/com/law/todo/application/TodoJourneySimulationServiceTest.java `
  law-todo/src/test/java/com/law/todo/application/TodoSimulationEvidenceServiceTest.java
git commit -m "feat(todo): persist full simulation evidence"
```

---

### Task 3: 让发布预检、旅程健康度和工作台同源

**Files:**
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoSimulationReadinessService.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoDefinitionService.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoConfigurationJourneyEvaluator.java`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoConfigurationJourneyService.java`
- Modify: `law-todo/src/main/java/com/law/todo/mapper/TodoConfigurationMapper.java`
- Modify: `law-todo/src/main/resources/mapper/todo/TodoConfigurationMapper.xml`
- Modify tests:
  - `law-todo/src/test/java/com/law/todo/application/TodoDefinitionServiceTest.java`
  - `law-todo/src/test/java/com/law/todo/application/TodoConfigurationJourneyEvaluatorTest.java`
  - `law-todo/src/test/java/com/law/todo/application/TodoConfigurationJourneyServiceTest.java`

**Interfaces:**
- Consumes: `TodoSimulationReadinessView readiness(...)`
- Produces:

```java
// TodoSimulationReadinessService
public DefinitionValidationReport applyPreflightGate(
        long versionId,
        DefinitionValidationReport report);

public Evaluation evaluate(
        TemplateConfigurationDetail detail,
        TodoDefinitionDocument definition,
        TodoSimulationReadinessView readiness);
```

- [ ] **Step 1: Write failing consistency tests**

Add assertions:

```java
@Test void journeyAndPreflightAreReadyOnlyWhenTheSameReadinessIsReady()
{
    TodoSimulationReadinessView ready=readyProjection();
    when(readiness.readiness(17L,88L,"hash-88","TD-001","LEAD"))
            .thenReturn(ready);

    TodoConfigurationJourneyView view=service.load(17L,actor());

    assertThat(view.issues()).extracting(JourneyIssue::code)
            .doesNotContain("TODO_JOURNEY_SIMULATION_REQUIRED",
                    "TODO_FULL_SIMULATION_REQUIRED",
                    "TODO_REQUIRED_SIMULATION_SCENARIOS_INCOMPLETE");
    assertThat(view.steps()).filteredOn(step->step.code().equals("SIMULATION_PUBLISH"))
            .extracting(JourneyStep::state).containsExactly("COMPLETED");
}
```

In `TodoDefinitionServiceTest`, verify preflight blocks separately for missing scenarios and missing full simulation, and has no simulation errors when `publicationReady=true`.

- [ ] **Step 2: Verify RED**

Run:

```powershell
mvn -pl law-todo -am `
  -Dtest=TodoDefinitionServiceTest,TodoConfigurationJourneyEvaluatorTest,TodoConfigurationJourneyServiceTest `
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: tests fail because the evaluator still reads `ui.simulationStatus`.

- [ ] **Step 3: Remove definition-embedded simulation status**

Delete logic that reads:

```text
ui.simulationStatus
ui.lastSimulationStatus
ui.simulationSuccessful
ui.simulationDefinitionHash
```

Build the seventh `JourneyStep` only from `TodoSimulationReadinessView`:

```java
private JourneyStep evaluateSimulation(
        TodoSimulationReadinessView readiness,
        List<JourneyIssue> issues,
        Map<String,Object> ui)
{
    List<JourneyIssue> local=readiness.issues();
    append(issues,local);
    return step("SIMULATION_PUBLISH","Simulation and publish",local,
            readiness.requiredScenarioCount()>0 || readiness.fullSimulationPassed(),
            readiness.publicationReady(),fieldValue("config",ui));
}
```

`evaluatePure` must accept an explicit readiness projection. It must not silently construct a successful projection.

- [ ] **Step 4: Apply the same readiness to preflight**

Replace the scenario-only gate in `TodoDefinitionService` with:

```java
TodoSimulationReadinessView state=readiness.readiness(
        templateId,versionId,report.definitionHash(),templateCode,businessType);
errors.addAll(toValidationIssues(state.issues()));
```

Deduplicate by `code + path`.

- [ ] **Step 5: Add batch-safe workbench readiness**

Add mapper query:

```java
List<Map<String,Object>> selectSimulationReadinessBatch(
        @Param("versionIds") List<Long> versionIds);
```

The SQL must return, per version:

```text
version_id
definition_hash
full_simulation_passed
required_scenario_count
passed_scenario_count
blocking_scenario_codes
```

The full gate must require a `PASSED` `FULL_SIMULATION` row matching the current definition hash. Scenario counts must use active `SIMULATION_SCENARIO` resources whose `templateCode`, `scenarioVersion` and `requiredForPublish` match the current template.

Load one batch for the bounded workbench page and index it by `version_id`; do not issue one query per template.

- [ ] **Step 6: Run focused and full backend tests**

Run:

```powershell
mvn -pl law-todo -am `
  -Dtest=TodoDefinitionServiceTest,TodoConfigurationJourneyEvaluatorTest,TodoConfigurationJourneyServiceTest `
  -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl law-todo -am test
```

Expected: journey, preflight and workbench agree for all four gate combinations.

- [ ] **Step 7: Commit**

```powershell
git add -- `
  law-todo/src/main/java/com/law/todo/application/TodoSimulationReadinessService.java `
  law-todo/src/main/java/com/law/todo/application/TodoDefinitionService.java `
  law-todo/src/main/java/com/law/todo/application/TodoConfigurationJourneyEvaluator.java `
  law-todo/src/main/java/com/law/todo/application/TodoConfigurationJourneyService.java `
  law-todo/src/main/java/com/law/todo/mapper/TodoConfigurationMapper.java `
  law-todo/src/main/resources/mapper/todo/TodoConfigurationMapper.xml `
  law-todo/src/test/java/com/law/todo/application/TodoDefinitionServiceTest.java `
  law-todo/src/test/java/com/law/todo/application/TodoConfigurationJourneyEvaluatorTest.java `
  law-todo/src/test/java/com/law/todo/application/TodoConfigurationJourneyServiceTest.java
git commit -m "fix(todo): unify simulation publish gates"
```

---

### Task 4: 修复版本差异字段与非法请求

**Files:**
- Modify: `ruoyi-ui/src/views/todo/config/journey/simulation-workbench-model.js`
- Modify: `ruoyi-ui/src/views/todo/config/journey/steps/SimulationPublishStep.vue`
- Modify: `ruoyi-ui/scripts/check-todo-phase-two-ux.js`

**Interfaces:**
- Produces:

```javascript
export function versionIdentity(row) {
  const value = row && (row.versionId ?? row.version_id ?? row.id)
  const number = Number(value)
  return Number.isInteger(number) && number > 0 ? number : null
}

export function versionStatus(row) {
  return String(
    (row && (row.status ?? row.publishStatus ?? row.publish_status)) || ''
  ).toUpperCase()
}

export function versionDiffPlan(versions, currentVersionId) {
  // { available, leftVersionId, rightVersionId, reason }
}
```

- [ ] **Step 1: Write the failing frontend model test**

Add executable assertions:

```javascript
assert.strictEqual(model.versionIdentity({ version_id: 82 }), 82)
assert.strictEqual(model.versionIdentity({ versionId: 88 }), 88)
assert.strictEqual(model.versionIdentity({ id: '79' }), 79)
assert.strictEqual(model.versionIdentity({ version_id: undefined }), null)
assert.deepStrictEqual(
  model.versionDiffPlan(
    [{ version_id: 82, status: 'PUBLISHED' }],
    88
  ),
  {
    available: true,
    leftVersionId: 82,
    rightVersionId: 88,
    reason: ''
  }
)
```

- [ ] **Step 2: Verify RED**

Run:

```powershell
cd ruoyi-ui
npm run test:todo-phase-two
```

Expected: fails because the normalization functions do not exist.

- [ ] **Step 3: Implement the normalization and guard**

`versionDiffPlan` must:

- accept snake_case and camelCase;
- select only `PUBLISHED/RETIRED`;
- return `available=false, reason='NO_PUBLISHED_VERSION'` if no historical version exists;
- return `available=false, reason='INVALID_VERSION_ID'` if either ID is invalid;
- never return `undefined`, `null` or `NaN` IDs in an available plan.

- [ ] **Step 4: Replace `loadDiff`**

Use:

```javascript
const plan = versionDiffPlan(versions, this.currentVersionId)
if (!plan.available) {
  this.diff = { changes: [], overallRisk: 'LOW' }
  this.diffError = plan.reason === 'NO_PUBLISHED_VERSION'
    ? '当前为首个待发布版本'
    : ''
  return
}
const result = await diffTemplateVersions(
  plan.leftVersionId,
  plan.rightVersionId
)
```

On API failure set:

```javascript
this.diffError = '版本差异暂时无法加载，发布预检结果不受影响'
```

Do not call `$modal.msgError` or `$modal.msgWarning` for diff failures.

- [ ] **Step 5: Run frontend tests and build**

Run:

```powershell
npm run test:todo-phase-two
npm run test:todo-config
npm run test:encoding
npm run build:prod
```

Expected: all commands exit 0; no request URL can contain `undefined`.

- [ ] **Step 6: Commit**

```powershell
git add -- `
  ruoyi-ui/src/views/todo/config/journey/simulation-workbench-model.js `
  ruoyi-ui/src/views/todo/config/journey/steps/SimulationPublishStep.vue `
  ruoyi-ui/scripts/check-todo-phase-two-ux.js
git commit -m "fix(todo): guard template version diff"
```

---

### Task 5: 收口前端成功提示、健康度和修复定位

**Files:**
- Modify: `ruoyi-ui/src/views/todo/config/journey/simulation-workbench-model.js`
- Modify: `ruoyi-ui/src/views/todo/config/journey/steps/SimulationPublishStep.vue`
- Modify: `ruoyi-ui/src/views/todo/config/journey/index.vue`
- Modify: `ruoyi-ui/src/views/todo/config/journey/components/ConfigurationHealthPanel.vue`
- Modify: `ruoyi-ui/scripts/check-todo-phase-two-ux.js`

**Interfaces:**
- Produces:

```javascript
export function simulationCompletionMessage(readiness) {
  return readiness && readiness.publicationReady
    ? '模拟发布验证已全部通过'
    : ''
}

export function readinessRepairTarget(issue, scenarios) {
  // { stepCode, scenarioCode, focusTarget }
}
```

- Component event:

```javascript
this.$emit('readiness-change', readiness)
```

- [ ] **Step 1: Write failing behavior tests**

Add:

```javascript
assert.strictEqual(
  model.simulationCompletionMessage({ publicationReady: true }),
  '模拟发布验证已全部通过'
)
assert.strictEqual(
  model.simulationCompletionMessage({ publicationReady: false }),
  ''
)
assert.deepStrictEqual(
  model.readinessRepairTarget(
    { code: 'TODO_FULL_SIMULATION_REQUIRED' },
    []
  ),
  {
    stepCode: 'SIMULATION_PUBLISH',
    scenarioCode: '',
    focusTarget: 'full-simulation'
  }
)
```

Add source-contract checks for:

```text
@readiness-change="applySimulationReadiness"
$emit('readiness-change'
模拟发布验证已全部通过
完整试运行尚未通过
```

- [ ] **Step 2: Verify RED**

Run:

```powershell
npm run test:todo-phase-two
```

Expected: fails because readiness synchronization is absent.

- [ ] **Step 3: Make full simulation one atomic UI operation**

The complete-run action must:

1. run server preflight quietly;
2. hydrate payload;
3. run full simulation;
4. store `simulation`;
5. store and emit `result.readiness`;
6. rerun preflight quietly;
7. load diff without global toast;
8. show exactly one final success or failure message.

Remove intermediate success messages from `runScenarioBatch`, `runSimulation` and nested `runPreflight`. Scenario cards and panels continue showing their own inline status.

- [ ] **Step 4: Synchronize parent journey state without destroying the trace**

Implement in `journey/index.vue`:

```javascript
applySimulationReadiness(readiness) {
  if (!this.journey || !readiness) return
  const otherIssues = (this.journey.issues || [])
    .filter(issue => issue.stepCode !== 'SIMULATION_PUBLISH')
  const issues = otherIssues.concat(readiness.issues || [])
  const steps = (this.journey.steps || []).map(step =>
    step.code === 'SIMULATION_PUBLISH'
      ? {
          ...step,
          state: readiness.publicationReady ? 'COMPLETED' : 'BLOCKED',
          issueCount: (readiness.issues || []).length
        }
      : step
  )
  this.journey = { ...this.journey, issues, steps }
}
```

The trace, selected object and scenario cards must remain visible after synchronization.

- [ ] **Step 5: Implement accurate repair focus**

- `TODO_REQUIRED_SIMULATION_SCENARIOS_INCOMPLETE` → first blocked scenario card.
- `TODO_FULL_SIMULATION_REQUIRED` and `TODO_FULL_SIMULATION_STALE` → full simulation action block.
- other issues → existing step navigation.

Use a temporary `is-repair-target` class for 1.8 seconds and display one information message confirming the location.

- [ ] **Step 6: Run frontend verification**

Run:

```powershell
npm run test:todo-phase-two
npm run test:todo-config
npm run test:encoding
npm run build:prod
```

Expected: all commands exit 0; source checks confirm one final success path.

- [ ] **Step 7: Commit**

```powershell
git add -- `
  ruoyi-ui/src/views/todo/config/journey/simulation-workbench-model.js `
  ruoyi-ui/src/views/todo/config/journey/steps/SimulationPublishStep.vue `
  ruoyi-ui/src/views/todo/config/journey/index.vue `
  ruoyi-ui/src/views/todo/config/journey/components/ConfigurationHealthPanel.vue `
  ruoyi-ui/scripts/check-todo-phase-two-ux.js
git commit -m "fix(todo): synchronize simulation readiness UI"
```

---

### Task 6: MySQL 和真实浏览器闭环验收

**Files:**
- Create: `ruoyi-admin/src/test/java/com/ruoyi/web/migration/TodoSimulationReadinessExternalMysqlIT.java`
- Modify: `ruoyi-ui/tests/e2e/todo-config-center.spec.js`

**Interfaces:**
- Consumes:
  - `POST /todo/config/templates/{id}/journey/scenarios/batch-simulate`
  - `POST /todo/config/templates/{id}/journey/simulate`
  - `POST /todo/config/template-versions/{id}/preflight`
  - `GET /todo/config/templates/{id}/journey`
- Produces: executable acceptance evidence for the double gate.

- [ ] **Step 1: Write the failing MySQL integration test**

Test sequence:

```java
assertThat(readiness().publicationReady()).isFalse();
runThreeRequiredScenarios();
assertThat(readiness().fullSimulationPassed()).isFalse();
assertThat(preflightErrors()).extracting("code")
        .contains("TODO_FULL_SIMULATION_REQUIRED");
runFullSimulation();
assertThat(readiness().publicationReady()).isTrue();
assertThat(preflightErrors()).isEmpty();
```

Capture counts before and after:

```text
biz_lead
todo_instance
todo_action_log
business_event
todo_simulation_evidence
```

Assert the first four counts and checksums are unchanged. Assert the evidence table appends the three required scenario codes and `FULL_SIMULATION` for the same version and definition hash.

- [ ] **Step 2: Verify MySQL test RED**

Run with the existing external database environment:

```powershell
$env:TODO_E2E_DB_URL='jdbc:mysql://127.0.0.1:13319/lead_todo_task12_e2e?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai'
$env:TODO_E2E_DB_USERNAME='root'
$env:TODO_E2E_DB_PASSWORD='Task12Root_20260726'
mvn -pl ruoyi-admin -am `
  -Dtest=TodoSimulationReadinessExternalMysqlIT `
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: fails before the full evidence and unified readiness implementation is complete.

- [ ] **Step 3: Complete the MySQL fixture and assertions**

Reuse `MigrationTestDatabase` and the existing `TD-001` template fixture. Do not create a second schema when `lead_todo_task12_e2e` is explicitly provided.

- [ ] **Step 4: Extend the Playwright flow**

Add a test that:

```javascript
await page.getByRole('button', { name: /一键加载只读样例/ }).click()
await page.getByRole('button', { name: /批量验证三个场景/ }).click()
await page.getByRole('button', { name: /运行完整试运行/ }).click()

await expect(page.getByText('模拟发布验证已全部通过')).toBeVisible()
await expect(page.getByText('0 项阻塞')).toBeVisible()
await expect(page.getByText('当前配置可以发布')).toBeVisible()
await expect(page.getByRole('button', { name: /发布当前版本/ })).toBeEnabled()
```

Capture requests and assert:

```javascript
expect(todoRequests.some(url => /undefined|null|NaN/.test(url))).toBeFalsy()
```

Reload and assert the seventh step and health panel remain ready. Do not click the publish button.

- [ ] **Step 5: Run full verification**

Run:

```powershell
mvn -pl law-todo -am test
mvn -pl ruoyi-admin -am `
  -Dtest=TodoSimulationReadinessExternalMysqlIT `
  -Dsurefire.failIfNoSpecifiedTests=false test

cd ruoyi-ui
npm run test:todo-phase-two
npm run test:todo-config
npm run test:encoding
npm run build:prod
```

Start the current backend and built frontend, then run:

```powershell
$env:TODO_E2E_BASE_URL='http://127.0.0.1:4173'
$env:TODO_E2E_API_BASE_URL='http://127.0.0.1:8080'
npx playwright test tests/e2e/todo-config-center.spec.js --project=chromium
```

Expected:

- all commands exit 0;
- no browser console error;
- no `undefined/null/NaN` version request;
- health, preflight, navigation and button agree after reload;
- no publish action occurs.

- [ ] **Step 6: Commit**

```powershell
git add -- `
  ruoyi-admin/src/test/java/com/ruoyi/web/migration/TodoSimulationReadinessExternalMysqlIT.java `
  ruoyi-ui/tests/e2e/todo-config-center.spec.js
git commit -m "test(todo): prove simulation readiness closure"
```

- [ ] **Step 7: Final scope and diff check**

Run:

```powershell
git diff --check HEAD~6..HEAD
git status --short
git log -6 --oneline
```

Confirm `.superpowers/sdd/task-7-report.md`, `ruoyi-ui/vue.config.js`, `.runtime-logs/`, `.playwright-cli/`, `output/playwright/` and `test-results/` were not staged by this plan.

---

## Plan Self-Review Checklist

- [x] Every requirement in `2026-07-30-todo-simulation-readiness-design.md` maps to a task above.
- [x] Both gate combinations, the all-ready state and hash invalidation have automated backend coverage.
- [x] The plan never writes simulation status into the template definition.
- [x] Workbench readiness uses one batch query, not N+1 queries.
- [x] Version diff cannot receive invalid IDs and remains non-blocking.
- [x] Frontend displays one final result and keeps trace/context visible.
- [x] MySQL and browser acceptance prove business tables remain unchanged.
- [x] No task publishes a template or modifies immutable published versions.
