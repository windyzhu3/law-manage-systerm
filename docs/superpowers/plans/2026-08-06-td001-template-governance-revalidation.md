# TD-001 Template Governance and Revalidation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the retired legacy first-contact template unambiguous, let authorized users revalidate immutable TD-001 versions after scenario upgrades, and remove the routing editor overlap.

**Architecture:** Extend the existing configuration journey read model instead of creating another template API. Keep template definitions immutable while treating simulations as repeatable, append-only validation operations; reuse the existing template toggle endpoint and simulation services. Keep all layout changes inside the existing Vue 2 configuration components and validate the final behavior against real MySQL and a real browser.

**Tech Stack:** Java 17, Spring Boot, MyBatis, MySQL 8, JUnit 5, Mockito, Vue 2.6, Element UI, Node contract scripts, Playwright.

## Global Constraints

- Base every change on the `v0.2-Foundation` code tree represented by the current isolated worktree.
- Do not modify any already executed Flyway migration.
- Do not change TD-001 business definition, routing outcomes, or the active `LEAD_FIRST_CONTACT_ENTRY` binding.
- Do not delete or rewrite historical templates, versions, simulation evidence, Todo instances, or audit actions.
- Do not copy scenario-version-1 evidence into scenario version 2 and do not lower the governed scenario version.
- Published definition fields remain immutable; only read-only simulation inputs and append-only evidence may change.
- Do not upgrade Vue, Element UI, RuoYi, Java, or MySQL dependencies.
- Stage and commit only files owned by each task; preserve `.superpowers/sdd/task-7-report.md`, `ruoyi-ui/vue.config.js`, runtime logs, Playwright output, and test-results.

---

## File Map

### Backend read model and evidence

- `law-todo/src/main/resources/mapper/todo/TodoConfigurationMapper.xml`: expose replacement-template metadata and add a query for the latest evidence across scenario versions.
- `law-todo/src/main/java/com/law/todo/mapper/TodoConfigurationMapper.java`: declare the new evidence query.
- `law-todo/src/main/java/com/law/todo/application/view/TodoConfigurationJourneyView.java`: expose runtime state, replacement metadata, lock version, and authoritative simulation readiness.
- `law-todo/src/main/java/com/law/todo/application/TodoConfigurationJourneyService.java`: derive workbench runtime state and return readiness on journey detail.
- `law-todo/src/main/java/com/law/todo/application/TodoSimulationEvidenceService.java`: classify old-version evidence as `SCENARIO_UPDATED`.

### Frontend behavior and layout

- `ruoyi-ui/src/views/todo/config/template/template-workbench-model.js`: pure runtime-state, replacement-target, and toggle-presentation helpers.
- `ruoyi-ui/src/views/todo/config/template/index.vue`: runtime-state filter, status display, replacement navigation, and template toggle controls.
- `ruoyi-ui/src/views/todo/config/journey/simulation-workbench-model.js`: pure published-version simulation access and blocker presentation helpers.
- `ruoyi-ui/src/views/todo/config/journey/steps/SimulationPublishStep.vue`: split edit and simulation permissions and orchestrate one-click revalidation.
- `ruoyi-ui/src/views/todo/config/journey/components/BatchScenarioGate.vue`: show version-aware blocker reasons and the one-click action.
- `ruoyi-ui/src/views/todo/config/journey/components/CompletionFormRenderer.vue`: respect simulation permission independently of definition read-only state.
- `ruoyi-ui/src/views/todo/config/journey/components/BusinessRoutingEditor.vue`: responsive three/two/one-column routing layout.

### Tests

- `law-todo/src/test/java/com/law/todo/application/TodoConfigurationJourneyServiceTest.java`: workbench state and journey readiness projection.
- `law-todo/src/test/java/com/law/todo/application/TodoSimulationEvidenceServiceTest.java`: scenario-version invalidation classification.
- `law-todo/src/test/java/com/law/todo/mapper/TodoMapperXmlContractTest.java`: mapper query contract.
- `ruoyi-ui/scripts/check-todo-phase-two-ux.js`: pure-model and component contract assertions.
- `ruoyi-ui/tests/e2e/todo-config-journey.spec.js`: real-backend TD-001 status, revalidation, persistence, and responsive-layout acceptance.

---

### Task 1: Project Template Runtime and Replacement State

**Files:**
- Modify: `law-todo/src/main/resources/mapper/todo/TodoConfigurationMapper.xml:220-252`
- Modify: `law-todo/src/main/java/com/law/todo/application/view/TodoConfigurationJourneyView.java:68-83`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoConfigurationJourneyService.java:177-220`
- Test: `law-todo/src/test/java/com/law/todo/application/TodoConfigurationJourneyServiceTest.java`

**Interfaces:**
- Consumes: existing `todo_template.status`, `todo_template.version`, and `todo_template.replacement_template_code`.
- Produces: `TemplateWorkbenchItem.runtimeState()`, `templateStatus()`, `lockVersion()`, `replacementTemplateId()`, `replacementTemplateCode()`, and `replacementTemplateName()`.

- [ ] **Step 1: Write failing workbench projection tests**

Add a replacement row fixture and these assertions:

```java
@Test void projectsRuntimeStateAndReplacementWithoutConfusingConfigurationProgress()
{
    Map<String,Object> active=new java.util.LinkedHashMap<>(
            workbenchRow(17L,"TD-001","PUBLISHED","hash-td001",
                    definition("hash-td001",false,false,true,true),"{}"));
    active.put("template_status","0");

    Map<String,Object> replaced=new java.util.LinkedHashMap<>(
            workbenchRow(1L,"LEAD_FIRST_CONTACT","PUBLISHED","hash-legacy",
                    definition("hash-legacy",false,false,true,true),"{}"));
    replaced.put("template_status","1");
    replaced.put("replacement_template_id",17L);
    replaced.put("replacement_template_code","TD-001");
    replaced.put("replacement_template_name","首联待办");
    when(mapper.selectTemplateJourneySummaries(anyMap())).thenReturn(List.of(active,replaced));

    TemplateWorkbenchPage page=service.workbench(Map.of("offset",0,"limit",20),actor);

    assertThat(page.rows()).extracting(
            TodoConfigurationJourneyView.TemplateWorkbenchItem::runtimeState)
            .containsExactly("ACTIVE","REPLACED");
    assertThat(page.rows().get(1).primaryAction()).isEqualTo("OPEN_REPLACEMENT");
    assertThat(page.rows().get(1).replacementTemplateId()).isEqualTo(17L);
    assertThat(page.rows().get(1).completedSteps()).isEqualTo(7);
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
mvn -pl law-todo -am "-Dtest=TodoConfigurationJourneyServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: compilation fails because the new record accessors do not exist.

- [ ] **Step 3: Extend the mapper projection and view record**

Join the replacement template in `templateJourneySummaryFrom`:

```xml
left join todo_template replacement
  on replacement.template_code=t.replacement_template_code
```

Add to `selectTemplateJourneySummaries`:

```sql
t.replacement_template_code,
replacement.template_id replacement_template_id,
replacement.template_name replacement_template_name
```

Append these fields to `TemplateWorkbenchItem` so existing leading JSON fields remain stable:

```java
String nextStepCode,String nextStepTitle,
String templateStatus,int lockVersion,String runtimeState,
Long replacementTemplateId,String replacementTemplateCode,String replacementTemplateName
```

Keep the existing convenience constructors and delegate them with active defaults:

```java
this(...,nextStepCode,nextStepTitle,"0",0,"ACTIVE",null,null,null);
```

- [ ] **Step 4: Derive authoritative runtime state and primary action**

In `workbenchItem`, add:

```java
String templateStatus=text(row,"template_status","templateStatus");
String replacementCode=text(row,"replacement_template_code","replacementTemplateCode");
String runtimeState="0".equals(templateStatus)?"ACTIVE":
        replacementCode!=null?"REPLACED":"INACTIVE";
String primaryAction="REPLACED".equals(runtimeState)?"OPEN_REPLACEMENT":
        published?"VIEW_PUBLISHED":"CONTINUE_CONFIGURATION";
```

Pass the template lock version and nullable replacement fields into the record. Do not infer replacement from `templateName`.

- [ ] **Step 5: Run focused and module tests and verify GREEN**

Run:

```powershell
mvn -pl law-todo -am "-Dtest=TodoConfigurationJourneyServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn -pl law-todo -am test
```

Expected: both commands pass; the workbench still performs one bounded summary query and no per-row detail query.

- [ ] **Step 6: Commit the backend workbench state**

```powershell
git add law-todo/src/main/resources/mapper/todo/TodoConfigurationMapper.xml law-todo/src/main/java/com/law/todo/application/view/TodoConfigurationJourneyView.java law-todo/src/main/java/com/law/todo/application/TodoConfigurationJourneyService.java law-todo/src/test/java/com/law/todo/application/TodoConfigurationJourneyServiceTest.java
git commit -m "feat: expose template runtime replacement state"
```

---

### Task 2: Version-Aware Simulation Readiness

**Files:**
- Modify: `law-todo/src/main/java/com/law/todo/mapper/TodoConfigurationMapper.java:60-64`
- Modify: `law-todo/src/main/resources/mapper/todo/TodoConfigurationMapper.xml:425-437`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoSimulationEvidenceService.java:106-204`
- Modify: `law-todo/src/main/java/com/law/todo/application/view/TodoConfigurationJourneyView.java:16-28`
- Modify: `law-todo/src/main/java/com/law/todo/application/TodoConfigurationJourneyService.java:86-96`
- Test: `law-todo/src/test/java/com/law/todo/application/TodoSimulationEvidenceServiceTest.java`
- Test: `law-todo/src/test/java/com/law/todo/application/TodoConfigurationJourneyServiceTest.java`
- Test: `law-todo/src/test/java/com/law/todo/mapper/TodoMapperXmlContractTest.java`

**Interfaces:**
- Consumes: current `SimulationScenario.scenarioVersion()` and append-only `todo_simulation_evidence` rows.
- Produces: `SimulationGateBlocker` with current and previous scenario versions; journey JSON property `simulationReadiness`.

- [ ] **Step 1: Write failing scenario-upgrade and journey-readiness tests**

Add this evidence test using a version-2 scenario fixture:

```java
@Test void oldScenarioVersionEvidenceRequiresRealRevalidation()
{
    SimulationScenario current=new SimulationScenario(11L,"TD001_VALID","TD-001","有效首联",2,
            Map.of("contactResult","VALID","contactedAt","${SIMULATION_NOW}"),
            List.of("contactResult","contactedAt"),List.of(),"TD-001",1,"TD-004",true,"ACTIVE",10);
    when(mapper.selectPassingSimulationEvidence(anyMap())).thenReturn(null);
    when(mapper.selectLatestSimulationEvidence(anyMap())).thenReturn(null);
    when(mapper.selectLatestSimulationEvidenceForScenario(anyMap())).thenReturn(Map.of(
            "scenario_version",1,"definition_hash","definition-hash","result_status","PASSED"));

    var gate=new TodoSimulationEvidenceService(mapper).gate(
            17L,88L,"definition-hash",List.of(current));

    assertThat(gate.blockers()).containsExactly(
            new TodoSimulationEvidenceService.SimulationGateBlocker(
                    "TD001_VALID","SCENARIO_UPDATED",2,1));
}
```

In `TodoConfigurationJourneyServiceTest.loadsSevenOrderedStepsAndKeepsOptimisticLockVersion`, add:

```java
assertThat(view.simulationReadiness()).isEqualTo(ready(42L,101L,"hash-42"));
```

Add an XML contract assertion that the cross-version query filters `template_id`, `version_id`, and `scenario_code`, but does not filter `scenario_version`.

- [ ] **Step 2: Run focused tests and verify RED**

```powershell
mvn -pl law-todo -am "-Dtest=TodoSimulationEvidenceServiceTest,TodoConfigurationJourneyServiceTest,TodoMapperXmlContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: compilation fails for the missing mapper method, readiness accessor, and four-argument blocker constructor.

- [ ] **Step 3: Add the cross-version evidence query**

Declare:

```java
Map<String,Object> selectLatestSimulationEvidenceForScenario(Map<String,Object> query);
```

Implement:

```xml
<select id="selectLatestSimulationEvidenceForScenario" resultType="java.util.Map">
  select * from todo_simulation_evidence
  where template_id=#{templateId} and version_id=#{versionId}
    and scenario_code=#{scenarioCode}
  order by executed_time desc,evidence_id desc limit 1
</select>
```

- [ ] **Step 4: Return version-aware blocker metadata**

Change the record to:

```java
public record SimulationGateBlocker(String scenarioCode,String reason,
        Integer scenarioVersion,Integer evidenceScenarioVersion)
{
    public SimulationGateBlocker(String scenarioCode,String reason)
    {this(scenarioCode,reason,null,null);}
}
```

When current-version evidence is absent, query across scenario versions and return:

```java
Integer evidenceVersion=integer(value(previous,"scenario_version","scenarioVersion"));
String reason=evidenceVersion!=null&&evidenceVersion!=scenario.scenarioVersion()
        ?"SCENARIO_UPDATED":evidenceReason(previous,definitionHash);
blockers.add(new SimulationGateBlocker(scenario.scenarioCode(),reason,
        scenario.scenarioVersion(),evidenceVersion));
```

Add the local numeric helper used above:

```java
private Integer integer(Object value)
{
    if(value==null)return null;
    if(value instanceof Number number)return number.intValue();
    try{return Integer.valueOf(String.valueOf(value));}
    catch(NumberFormatException invalid){return null;}
}
```

Add the Chinese label:

```java
case "SCENARIO_UPDATED" -> "测试场景规则已升级，请重新验证";
```

- [ ] **Step 5: Put authoritative readiness on the journey response**

Append `TodoSimulationReadinessView simulationReadiness` to `TodoConfigurationJourneyView`, retain a six-argument compatibility constructor, and change `load` to:

```java
return new TodoConfigurationJourneyView(summary(detail,version),evaluation.steps(),
        resources(detail,definition),preview.project(detail,definition),
        evaluation.issues(),permissions(actor),state);
```

- [ ] **Step 6: Run focused tests and verify GREEN**

```powershell
mvn -pl law-todo -am "-Dtest=TodoSimulationEvidenceServiceTest,TodoSimulationReadinessServiceTest,TodoConfigurationJourneyServiceTest,TodoMapperXmlContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn -pl law-todo -am test
```

Expected: old evidence is reported as `SCENARIO_UPDATED`, current evidence logic remains unchanged, and the journey exposes the same authoritative readiness used by its evaluator.

- [ ] **Step 7: Commit version-aware readiness**

```powershell
git add law-todo/src/main/java/com/law/todo/mapper/TodoConfigurationMapper.java law-todo/src/main/resources/mapper/todo/TodoConfigurationMapper.xml law-todo/src/main/java/com/law/todo/application/TodoSimulationEvidenceService.java law-todo/src/main/java/com/law/todo/application/view/TodoConfigurationJourneyView.java law-todo/src/main/java/com/law/todo/application/TodoConfigurationJourneyService.java law-todo/src/test/java/com/law/todo/application/TodoSimulationEvidenceServiceTest.java law-todo/src/test/java/com/law/todo/application/TodoConfigurationJourneyServiceTest.java law-todo/src/test/java/com/law/todo/mapper/TodoMapperXmlContractTest.java
git commit -m "fix: explain stale scenario-version evidence"
```

---

### Task 3: Workbench Runtime-State UX and Replacement Navigation

**Files:**
- Modify: `ruoyi-ui/src/views/todo/config/template/template-workbench-model.js`
- Modify: `ruoyi-ui/src/views/todo/config/template/index.vue`
- Modify: `ruoyi-ui/scripts/check-todo-phase-two-ux.js`

**Interfaces:**
- Consumes: Task 1 workbench fields and existing `toggleTodoTemplate(id, command)` API.
- Produces: pure helpers `templateRuntimePresentation(row)`, `templateNavigationTarget(row)`, and `templateTogglePresentation(row)`.

- [ ] **Step 1: Write failing model and source-contract checks**

Extend the phase-two UX script with:

```javascript
check('separates configuration progress from template runtime state', () => {
  const model = require(path.join(root, summaryModelPath))
  assert.deepStrictEqual(model.templateRuntimePresentation({ runtimeState: 'ACTIVE' }), {
    state: 'ACTIVE', label: '运行中', type: 'success', replacementText: ''
  })
  assert.deepStrictEqual(model.templateRuntimePresentation({
    runtimeState: 'REPLACED', replacementTemplateCode: 'TD-001', replacementTemplateName: '首联待办'
  }), {
    state: 'REPLACED', label: '已停用', type: 'info',
    replacementText: '已由首联待办（TD-001）替代'
  })
  assert.deepStrictEqual(model.templateNavigationTarget({
    templateId: 1, primaryAction: 'OPEN_REPLACEMENT', replacementTemplateId: 17
  }), { templateId: 17, view: 'published' })
})
```

Also assert that the workbench source includes `运行状态`, `打开现行模板`, `查看历史配置`, `toggleTodoTemplate`, and the `status` filter.

- [ ] **Step 2: Run the frontend contract and verify RED**

```powershell
Set-Location ruoyi-ui
npm run test:todo-phase-two
```

Expected: failure because the new helpers and UI tokens are missing.

- [ ] **Step 3: Implement pure runtime-state helpers**

Add to `template-workbench-model.js`:

```javascript
function templateRuntimePresentation(row) {
  const state = String((row && row.runtimeState) || 'INACTIVE')
  if (state === 'ACTIVE') return { state, label: '运行中', type: 'success', replacementText: '' }
  if (state === 'REPLACED') {
    const name = row.replacementTemplateName || row.replacementTemplateCode || '现行模板'
    const code = row.replacementTemplateCode ? `（${row.replacementTemplateCode}）` : ''
    return { state, label: '已停用', type: 'info', replacementText: `已由${name}${code}替代` }
  }
  return { state: 'INACTIVE', label: '已停用', type: 'info', replacementText: '' }
}

function templateNavigationTarget(row) {
  const replacement = Number(row && row.replacementTemplateId)
  if (row && row.primaryAction === 'OPEN_REPLACEMENT' && replacement > 0) {
    return { templateId: replacement, view: 'published' }
  }
  return {
    templateId: Number(row && row.templateId),
    view: row && row.primaryAction === 'VIEW_PUBLISHED' ? 'published' : 'draft'
  }
}

function templateTogglePresentation(row) {
  const presentation = templateRuntimePresentation(row)
  if (presentation.state === 'REPLACED') return null
  const active = presentation.state === 'ACTIVE'
  return {
    targetStatus: active ? '1' : '0',
    label: active ? '停用模板' : '启用模板',
    confirmText: active
      ? '停用后不会再为新业务生成该模板待办，确认继续？'
      : '启用后模板可被有效触发规则使用，确认继续？'
  }
}
```

Export all three helpers together with `resolveProblemSummary`.

- [ ] **Step 4: Add status filter, state column, navigation, and toggle controls**

Update `emptyQuery` with `status: ''`. Add an Element select whose values are `0` for “运行中” and `1` for “已停用”.

Render a separate status column:

```vue
<el-table-column label="运行状态" min-width="210">
  <template slot-scope="{ row }">
    <el-tag :type="runtimePresentation(row).type" size="small">
      {{ runtimePresentation(row).label }}
    </el-tag>
    <small v-if="runtimePresentation(row).replacementText" class="template-replacement">
      {{ runtimePresentation(row).replacementText }}
    </small>
  </template>
</el-table-column>
```

Use `templateNavigationTarget` for row clicks. Add an explicit “查看历史配置” action for replaced rows and use the existing toggle endpoint with `expectedVersion`, unique `actionId`, confirmation, per-row loading, reload on success, and `v-hasPermi="['todo:template:toggle']"` on the status-changing control.

- [ ] **Step 5: Run contract, encoding, and production build checks**

```powershell
Set-Location ruoyi-ui
npm run test:todo-phase-two
npm run test:encoding
npm run build:prod
```

Expected: all commands pass; Chinese source text remains UTF-8.

- [ ] **Step 6: Commit workbench UX**

```powershell
git add ruoyi-ui/src/views/todo/config/template/template-workbench-model.js ruoyi-ui/src/views/todo/config/template/index.vue ruoyi-ui/scripts/check-todo-phase-two-ux.js
git commit -m "feat: clarify template runtime status"
```

---

### Task 4: Revalidate Published TD-001 Versions

**Files:**
- Modify: `ruoyi-ui/src/views/todo/config/journey/simulation-workbench-model.js`
- Modify: `ruoyi-ui/src/views/todo/config/journey/steps/SimulationPublishStep.vue`
- Modify: `ruoyi-ui/src/views/todo/config/journey/components/BatchScenarioGate.vue`
- Modify: `ruoyi-ui/src/views/todo/config/journey/components/CompletionFormRenderer.vue`
- Modify: `ruoyi-ui/scripts/check-todo-phase-two-ux.js`

**Interfaces:**
- Consumes: Task 2 `simulationReadiness.blockingScenarios`, existing scenario/batch/full simulation APIs, and existing read-only sample loader.
- Produces: `simulationInteractionState(readonly, capabilities)`, `scenarioBlockerPresentation(blocker, scenarios)`, and component method `runAllRequiredValidation()`.

- [ ] **Step 1: Write failing access and blocker-presentation tests**

Add pure-model checks:

```javascript
check('keeps published definitions immutable while allowing authorized simulation', () => {
  const model = loadScenarioWorkbenchModel()
  assert.deepStrictEqual(model.simulationInteractionState(true, {
    canSimulate: true, canPublish: true
  }), { canEditDefinition: false, canRunSimulation: true, canPublishDraft: false })
})

check('explains upgraded scenario evidence with both versions', () => {
  const model = loadScenarioWorkbenchModel()
  assert.deepStrictEqual(model.scenarioBlockerPresentation({
    scenarioCode: 'TD001_VALID', reason: 'SCENARIO_UPDATED',
    scenarioVersion: 2, evidenceScenarioVersion: 1
  }, [{ scenarioCode: 'TD001_VALID', scenarioName: '有效首联', scenarioVersion: 2 }]), {
    scenarioCode: 'TD001_VALID', scenarioName: '有效首联',
    reason: 'SCENARIO_UPDATED', reasonLabel: '测试场景已升级，请重新验证',
    versionText: '当前 v2 · 上次证据 v1'
  })
})
```

Extend `loadScenarioWorkbenchModel()` so its returned object exposes
`simulationInteractionState` and `scenarioBlockerPresentation`. Add source assertions for
`一键重新验证`, `当前为已发布不可变版本`, and `runAllRequiredValidation`.

- [ ] **Step 2: Run the frontend contract and verify RED**

```powershell
Set-Location ruoyi-ui
npm run test:todo-phase-two
```

Expected: failure for missing pure helpers and component tokens.

- [ ] **Step 3: Implement permission and blocker helpers**

Add:

```javascript
export function simulationInteractionState(readonly, capabilities) {
  const source = capabilities || {}
  return {
    canEditDefinition: !readonly,
    canRunSimulation: Boolean(source.canSimulate),
    canPublishDraft: !readonly && Boolean(source.canPublish)
  }
}
```

Implement `scenarioBlockerPresentation` with labels for `SCENARIO_UPDATED`, `DEFINITION_CHANGED`, `LAST_RUN_FAILED`, `EVIDENCE_EXPIRED`, `EVIDENCE_UNAVAILABLE`, and `MISSING`. Build version text only from non-null numeric versions.

```javascript
export function scenarioBlockerPresentation(blocker, scenarios) {
  const source = blocker || {}
  const scenario = (scenarios || []).find(item => item.scenarioCode === source.scenarioCode) || {}
  const reason = String(source.reason || 'MISSING')
  const labels = {
    SCENARIO_UPDATED: '测试场景已升级，请重新验证',
    DEFINITION_CHANGED: '模板配置已变化，请重新验证',
    LAST_RUN_FAILED: '最近一次验证未通过',
    EVIDENCE_EXPIRED: '验证结果已过期，请重新验证',
    EVIDENCE_UNAVAILABLE: '验证结果不可用，请重新验证',
    MISSING: '尚未验证'
  }
  const current = Number(source.scenarioVersion || scenario.scenarioVersion)
  const previous = Number(source.evidenceScenarioVersion)
  const versions = []
  if (Number.isInteger(current) && current > 0) versions.push(`当前 v${current}`)
  if (Number.isInteger(previous) && previous > 0) versions.push(`上次证据 v${previous}`)
  return {
    scenarioCode: source.scenarioCode || '',
    scenarioName: scenario.scenarioName || source.scenarioCode || '未命名场景',
    reason,
    reasonLabel: labels[reason] || labels.MISSING,
    versionText: versions.join(' · ')
  }
}
```

- [ ] **Step 4: Split definition editing from simulation execution**

In `SimulationPublishStep`:

- compute `interaction` from `readonly` and capabilities;
- pass `:readonly="!interaction.canRunSimulation"` to `BusinessObjectPayloadEditor`;
- pass the same read-only state to `CompletionFormRenderer`;
- load objects and scenarios whenever `interaction.canRunSimulation` is true;
- remove `canOperate` from `searchObjects` and `runSimulation` guards;
- continue hiding draft publish controls unless `interaction.canPublishDraft` is true;
- show an informational alert when `readonly && interaction.canRunSimulation`.

Add a `readonly` prop to `CompletionFormRenderer`; pass it to the date picker and semantic selector and include it in the run-button disabled expression.

- [ ] **Step 5: Implement one-click revalidation with one final message**

Add `revalidating` state and:

```javascript
async runAllRequiredValidation() {
  if (!this.interaction.canRunSimulation || this.revalidating) return
  this.revalidating = true
  try {
    if (!this.selectedObject) await this.loadReadOnlySample()
    if (!this.selectedObject) throw new Error('没有可用的测试对象或只读样例')
    if (!(await this.runScenarioBatch({ quiet: true }))) {
      throw new Error('必测场景验证未全部通过，请查看场景结果')
    }
    if (!(await this.runSimulation({ quiet: true }))) {
      throw new Error('完整试运行未通过，请查看模拟轨迹')
    }
    this.$modal.msgSuccess('模拟发布验证已全部通过')
  } catch (error) {
    this.$modal.msgError(error.message || '重新验证失败')
  } finally {
    this.revalidating = false
  }
}
```

Make `runScenarioBatch(options)` and `runSimulation(options)` return `true` only on successful API completion and suppress their own final toast when `options.quiet` is true. They must still merge authoritative readiness and refresh preflight.

- [ ] **Step 6: Render version-aware remediation**

Pass authoritative blockers to `BatchScenarioGate`. Render each blocker with its Chinese name, reason, and version text. Keep “批量验证三个场景” for diagnosis and add the primary “一键重新验证” action emitting `run-all`. Give the two actions separate disabled inputs: batch validation still requires a selected object, while one-click revalidation is disabled only by unsaved/saving state because it is responsible for loading the sample itself.

- [ ] **Step 7: Run frontend checks and build**

```powershell
Set-Location ruoyi-ui
npm run test:todo-phase-two
npm run test:todo-config
npm run test:encoding
npm run build:prod
```

Expected: all pass; a published version with simulation permission is operable without exposing definition editing or draft publishing.

- [ ] **Step 8: Commit published-version revalidation**

```powershell
git add ruoyi-ui/src/views/todo/config/journey/simulation-workbench-model.js ruoyi-ui/src/views/todo/config/journey/steps/SimulationPublishStep.vue ruoyi-ui/src/views/todo/config/journey/components/BatchScenarioGate.vue ruoyi-ui/src/views/todo/config/journey/components/CompletionFormRenderer.vue ruoyi-ui/scripts/check-todo-phase-two-ux.js
git commit -m "fix: allow published template revalidation"
```

---

### Task 5: Responsive Business Routing Layout

**Files:**
- Modify: `ruoyi-ui/src/views/todo/config/journey/components/BusinessRoutingEditor.vue:66-138,458-606`
- Modify: `ruoyi-ui/scripts/check-todo-phase-two-ux.js`

**Interfaces:**
- Consumes: existing business outcome, effect, and target controls.
- Produces: CSS classes `routing-outcome__result`, `routing-effect-card`, and `routing-outcome__target` with three/two/one-column layouts.

- [ ] **Step 1: Add a failing layout contract**

Add assertions that the component contains explicit result and target classes, `.routing-effect-card { ... min-width: 0; }`, full-width selects, and no `grid-template-columns: minmax(200px, 1fr) 150px minmax(200px, 1fr)`.

- [ ] **Step 2: Run the frontend contract and verify RED**

```powershell
Set-Location ruoyi-ui
npm run test:todo-phase-two
```

Expected: failure because the old fixed middle column remains.

- [ ] **Step 3: Add semantic classes and flexible sizing**

Add `class="routing-outcome__result"` to the business-result select/input and `class="routing-outcome__target"` to the target select. Use:

```scss
.routing-outcome__body { min-width: 0; }
.routing-outcome__main {
  display: grid;
  grid-template-columns: minmax(160px, .9fr) minmax(180px, 1fr) minmax(220px, 1.2fr);
  gap: 12px;
  align-items: stretch;
  min-width: 0;
}
.routing-outcome__main > * { min-width: 0; }
.routing-effect-card { min-width: 0; }
.routing-outcome__result,
.routing-outcome__target { width: 100%; }
```

At the existing medium desktop breakpoint, use two columns and put the target across the full second row. Preserve the existing 760px single-column behavior.

```scss
@media (max-width: 1280px) {
  .routing-outcome__main { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .routing-outcome__target { grid-column: 1 / -1; }
}

@media (max-width: 760px) {
  .routing-outcome__main { grid-template-columns: 1fr; }
  .routing-outcome__target { grid-column: auto; }
}
```

- [ ] **Step 4: Run contract and production build checks**

```powershell
Set-Location ruoyi-ui
npm run test:todo-phase-two
npm run build:prod
```

Expected: both pass and no Vue template compiler warning is introduced.

- [ ] **Step 5: Commit the layout fix**

```powershell
git add ruoyi-ui/src/views/todo/config/journey/components/BusinessRoutingEditor.vue ruoyi-ui/scripts/check-todo-phase-two-ux.js
git commit -m "fix: make todo routing outcomes responsive"
```

---

### Task 6: Real MySQL and Browser Acceptance

**Files:**
- Modify: `ruoyi-ui/tests/e2e/todo-config-journey.spec.js`
- Create: `output/playwright/td001-template-governance/` test artifacts only; do not stage this directory.

**Interfaces:**
- Consumes: Tasks 1-5 and the existing disposable real-backend harness.
- Produces: durable acceptance coverage proving state clarity, real version-2 evidence, business-data safety, and responsive layout.

- [ ] **Step 1: Add the failing real-backend acceptance scenario**

Add one serial test that:

```javascript
test('TD001_RETIRED_TEMPLATE_AND_PUBLISHED_REVALIDATION are truthful and operable', async ({ page }) => {
  await loginAs(page, 'todo_config_admin', password)
  await page.goto('/todo-engine/todo-template')
  const legacy = page.locator('tr').filter({ hasText: 'LEAD_FIRST_CONTACT' })
  const current = page.locator('tr').filter({ hasText: 'TD-001' })
  await expect(legacy).toContainText('已停用')
  await expect(legacy).toContainText('已由首联待办（TD-001）替代')
  await expect(current).toContainText('运行中')
  await legacy.getByRole('button', { name: '打开现行模板' }).click()
  await expect(page.getByTestId('template-journey-shell')).toContainText('TD-001')

  await page.getByText('模拟发布', { exact: false }).last().click()
  const step = page.getByTestId('simulation-publish-step')
  await expect(step).toContainText('当前为已发布不可变版本')
  await expect(step.getByRole('button', { name: '一键重新验证' })).toBeEnabled()
})
```

Resolve the current published TD-001 identity and perform the persistence-safe run with:

```javascript
const td001 = loadTd001GovernanceFixture()
const before = snapshotSimulationPersistence(td001.templateId, 'LEAD', SAMPLE_LEAD_ID)
await step.getByRole('button', { name: '一键重新验证' }).click()
await expect(step.locator('.simulation-publish-step__header')).toContainText('模拟发布验证已全部通过')
await expect(step.locator('.batch-gate')).toContainText('三个必测场景均已通过')
const after = snapshotSimulationPersistence(td001.templateId, 'LEAD', SAMPLE_LEAD_ID)
assertSimulationPersistenceUnchanged(before, after)
await page.reload()
await expect(page.getByTestId('simulation-publish-step')).toContainText('模拟发布验证已全部通过')
```

Add this fixture helper beside the existing MySQL helpers:

```javascript
function loadTd001GovernanceFixture() {
  const rows = parseMysqlRows(executeSql(`
    select t.template_id,v.version_id,v.definition_hash
    from todo_template t join todo_template_version v on v.template_id=t.template_id
    where t.template_code='TD-001' and v.status='PUBLISHED'
    order by v.version_no desc,v.version_id desc limit 1;
  `, process.env.TODO_E2E_DB_NAME), ['templateId', 'versionId', 'definitionHash'])
  if (rows.length !== 1) throw new Error('Current published TD-001 was not found')
  return { ...rows[0], templateId: Number(rows[0].templateId), versionId: Number(rows[0].versionId) }
}
```

- [ ] **Step 2: Add SQL evidence and active-entry assertions**

Before opening the page, resolve `template_id`, current journey `version_id`, and `definition_hash` by `template_code='TD-001'`. After revalidation assert with those resolved values:

```sql
select count(*) from todo_simulation_evidence
where template_id=:templateId and version_id=:versionId
  and definition_hash=:definitionHash
  and scenario_version=2 and result_status='PASSED'
  and scenario_code in ('TD001_VALID','TD001_SUSPECT_INVALID','TD001_UNREACHABLE')
```

equals `3`, and confirm exactly one enabled `LEAD_FIRST_CONTACT_ENTRY` binding targets `TD-001`. Implement the assertions with:

```javascript
const evidence = Number(executeSql(`
  select count(*) from todo_simulation_evidence
  where template_id=${td001.templateId} and version_id=${td001.versionId}
    and definition_hash=${sqlLiteral(td001.definitionHash)}
    and scenario_version=2 and result_status='PASSED'
    and scenario_code in ('TD001_VALID','TD001_SUSPECT_INVALID','TD001_UNREACHABLE');
`, process.env.TODO_E2E_DB_NAME).trim())
expect(evidence).toBe(3)

const activeEntry = Number(executeSql(`
  select count(*) from todo_trigger_rule r
  join todo_template t on t.template_id=r.template_id
  where r.entry_slot_code='LEAD_FIRST_CONTACT_ENTRY' and r.enabled='Y'
    and t.template_code='TD-001' and t.status='0';
`, process.env.TODO_E2E_DB_NAME).trim())
expect(activeEntry).toBe(1)
```

- [ ] **Step 3: Add responsive visual assertions**

At `1672x941` and `1180x820`, open the routing step and assert that the bounding boxes do not intersect:

```javascript
async function expectRoutingControlsDoNotOverlap(page) {
  const effect = await page.locator('.routing-effect-card').first().boundingBox()
  const target = await page.locator('.routing-outcome__target').first().boundingBox()
  expect(effect).not.toBeNull()
  expect(target).not.toBeNull()
  const separated = effect.x + effect.width <= target.x || target.x + target.width <= effect.x ||
    effect.y + effect.height <= target.y || target.y + target.height <= effect.y
  expect(separated).toBeTruthy()
}
```

Save screenshots to `output/playwright/td001-template-governance/` after both viewport assertions.

- [ ] **Step 4: Run focused Playwright acceptance**

Use the existing disposable E2E environment variables and run:

```powershell
Set-Location ruoyi-ui
$env:TODO_E2E_REAL_BACKEND='true'
$env:TODO_E2E_BROWSER='chrome'
npm run test:e2e -- tests/e2e/todo-config-journey.spec.js --grep "TD001_RETIRED_TEMPLATE_AND_PUBLISHED_REVALIDATION"
```

Expected: pass with real API responses, three version-2 scenario evidence rows, one full-simulation evidence row, unchanged business/runtime snapshots, and no overlap at either viewport.

- [ ] **Step 5: Run the full proportional regression suite**

From the repository root:

```powershell
mvn -pl law-todo,ruoyi-admin -am test
Set-Location ruoyi-ui
npm run test:todo
npm run test:todo-config
npm run test:todo-phase-two
npm run test:encoding
npm run build:prod
```

Expected: every command exits `0`. Inspect backend logs for new exceptions and browser console output for errors.

- [ ] **Step 6: Commit real acceptance coverage**

```powershell
git add ruoyi-ui/tests/e2e/todo-config-journey.spec.js
git commit -m "test: cover TD-001 governance revalidation"
```

- [ ] **Step 7: Verify the final diff and commit set**

```powershell
git diff --check HEAD~5..HEAD
git status --short
git log --oneline -6
```

Expected: no whitespace errors; only the pre-existing runtime files and generated artifacts remain untracked or modified; implementation commits are limited to the files listed in this plan.
