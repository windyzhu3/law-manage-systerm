<template>
  <div class="simulation-publish-step" data-testid="simulation-publish-step">
    <header class="simulation-publish-step__header">
      <div>
        <span class="simulation-publish-step__eyebrow">最后一步</span>
        <h2>用真实业务对象试运行，再安全发布</h2>
        <p>试运行只读取数据，不创建待办；发布会生成不可变版本，历史版本不会被覆盖。</p>
      </div>
      <el-tag v-if="simulationCurrent" type="success">模拟发布验证已全部通过</el-tag>
      <el-tag v-else type="info">尚未完成模拟发布验证</el-tag>
    </header>

    <el-alert v-if="!capabilities.canSimulate" title="当前角色仅可查看，不能运行模拟。" type="info" :closable="false" show-icon />

    <business-object-payload-editor
      :objects="objects"
      :selected-id="selectedId"
      :selected="selectedObject"
      :hydration="hydration"
      :manual-overrides="manualOverrides"
      :loading="objectLoading"
      :hydrating="hydrating"
      :readonly="readonly"
      :template-name="template.templateName"
      @search="searchObjects"
      @sample-load="loadReadOnlySample"
      @select="selectObject"
      @hydrate="hydratePayload"
      @override="changeOverride"
    />

    <scenario-selector
      ref="scenarioSelector"
      :scenarios="scenarios"
      :selected-code="selectedScenarioCode"
      :results="scenarioResults"
      :routing-targets="resources.routingTargets || []"
      @select="selectScenario"
    />

    <completion-form-renderer
      :scenario="selectedScenario"
      :completion-fields="completionFields"
      :overrides="scenarioOverrides"
      :effective-at="effectiveAt || localDateTimeNow()"
      :loading="simulatingScenario"
      :business-selected="Boolean(selectedObject)"
      @override="changeScenarioOverride"
      @run="runSelectedScenario"
    />

    <batch-scenario-gate
      :gate="scenarioGateState"
      :scenarios="scenarios"
      :loading="batchSimulating"
      :disabled="!selectedObject || dirty || saving"
      @run-batch="runScenarioBatch"
    />

    <div
      ref="fullSimulationBlock"
      class="simulation-publish-step__full-simulation"
    >
      <el-alert
        v-if="readiness && !readiness.fullSimulationPassed"
        title="完整试运行尚未通过"
        description="请运行完整试运行；只有当前草稿的必测场景和完整试运行都通过后，才具备发布资格。"
        type="warning"
        :closable="false"
        show-icon
      />
      <div class="simulation-publish-step__actions">
        <el-date-picker
          v-if="!readonly"
          v-model="effectiveAt"
          type="datetime"
          value-format="yyyy-MM-dd'T'HH:mm:ss"
          placeholder="试运行时间（默认现在）"
        />
        <el-button
          v-if="canOperate && capabilities.canSimulate"
          type="primary"
          :disabled="!selectedObject || dirty || saving"
          :loading="simulating"
          data-testid="run-journey-simulation"
          @click="runSimulation"
        >
          运行完整试运行
        </el-button>
      </div>
    </div>

    <simulation-trace
      :trace="simulation && simulation.trace"
      @repair="$emit('navigate-repair', $event)"
      @rerun="runSimulation"
    />

    <el-alert
      v-if="diffError"
      class="simulation-publish-step__diff-notice"
      :title="diffError"
      type="info"
      :closable="false"
      show-icon
    />

    <publish-preflight-panel
      v-if="canOperate && (capabilities.canPublish || preflight)"
      :preflight="preflight"
      :gate="publishGate"
      :warning-reason.sync="warningReason"
      :diff="diff"
      :fields="resources.fields || []"
      :loading="preflightLoading"
      @reload="runPreflight"
      @repair="repairPreflight"
    />

    <div v-if="canOperate && capabilities.canPublish" class="simulation-publish-step__publish">
      <div>
        <strong>不可变发布</strong>
        <p>{{ publishGate.message || '发布后该版本永久保留，可复制为新草稿继续调整。' }}</p>
      </div>
      <el-button
        type="primary"
        :loading="publishing"
        :disabled="!publishGate.allowed || dirty || saving"
        data-testid="publish-current-draft"
        @click="publish"
      >
        发布当前版本
      </el-button>
    </div>

    <section v-if="leadReleaseApplicable" class="lead-release-panel" data-testid="lead-release-panel">
      <div class="lead-release-panel__title">
        <div>
          <span>线索待办整体启用</span>
          <h3>一次启用首联入口和三个下游待办</h3>
        </div>
        <el-tag :type="releaseAlreadyActive ? 'success' : 'info'">
          {{ releaseAlreadyActive ? '当前组合已启用' : '等待整体启用' }}
        </el-tag>
      </div>
      <el-alert v-if="releaseError" :title="releaseError" type="warning" :closable="false" show-icon />
      <dl class="lead-release-panel__versions">
        <div>
          <dt>当前入口</dt>
          <dd>线索已分配 → 首联待办（TD-001，版本ID {{ activeRelease.activeTd001VersionId || '-' }}）</dd>
        </div>
        <div>
          <dt>当前下游版本</dt>
          <dd>{{ activeDownstreamText }}</dd>
        </div>
        <div>
          <dt>本次下游版本</dt>
          <dd>疑似无效主管复核（TD-002，版本ID {{ releaseVersions['TD-002'] || '-' }}） /
            无法联系重试（TD-003，版本ID {{ releaseVersions['TD-003'] || '-' }}） /
            5天实质进展（TD-004，版本ID {{ releaseVersions['TD-004'] || '-' }}）</dd>
        </div>
      </dl>
      <div class="lead-release-panel__evidence">
        <span v-for="code in ['TD-001','TD-002','TD-003','TD-004']" :key="code">
          <i :class="releaseEvidence(code) ? 'el-icon-circle-check' : 'el-icon-circle-close'" />
          {{ releaseTemplateLabel(code) }}：{{ releaseEvidence(code) ? '当前版本验证通过' : '验证未完成' }}
        </span>
      </div>
      <div class="lead-release-panel__actions">
        <small>启用按钮只依据服务端返回的四版本证据和当前入口绑定状态，不在页面中推测验证结果。</small>
        <el-button
          v-if="canActivateLeadRelease"
          type="primary"
          :loading="releaseActivating || releaseLoading"
          :disabled="!releaseReadiness || !releaseReadiness.activationReady || releaseAlreadyActive"
          data-testid="activate-lead-release"
          @click="activateCoordinatedLeadRelease"
        >整体启用线索待办</el-button>
      </div>
    </section>
  </div>
</template>

<script>
import BusinessObjectPayloadEditor from '../components/BusinessObjectPayloadEditor'
import SimulationTrace from '../components/SimulationTrace'
import PublishPreflightPanel from '../components/PublishPreflightPanel'
import ScenarioSelector from '../components/ScenarioSelector'
import CompletionFormRenderer from '../components/CompletionFormRenderer'
import BatchScenarioGate from '../components/BatchScenarioGate'
import {
  listBusinessObjects,
  hydrateTodoJourneyPayload,
  simulateTodoJourney,
  preflightTemplateDraft,
  listTemplateVersions,
  diffTemplateVersions,
  publishReleaseRecord,
  listJourneyScenarios,
  simulateJourneyScenario,
  batchSimulateJourneyScenarios,
  getLeadReleaseReadiness,
  activateLeadRelease
} from '@/api/todo-config'
import {
  updateManualOverrides,
  publishPreflightGate,
  simulationPublishCapabilities
} from '../journey-step-model'
import {
  failedScenarioResult,
  persistedScenarioResults,
  scenarioGate,
  readinessRepairTarget,
  simulationCompletionMessage,
  versionDiffPlan
} from '../simulation-workbench-model'

export default {
  name: 'SimulationPublishStep',
  components: {
    BusinessObjectPayloadEditor,
    SimulationTrace,
    PublishPreflightPanel,
    ScenarioSelector,
    CompletionFormRenderer,
    BatchScenarioGate
  },
  props: {
    template: { type: Object, required: true },
    value: { type: Object, default: () => ({}) },
    event: { type: Object, default: () => ({}) },
    definition: { type: Object, default: () => ({}) },
    businessType: { type: String, default: '' },
    currentVersionId: { type: [Number, String], required: true },
    permissions: { type: Array, default: () => [] },
    resources: { type: Object, default: () => ({}) },
    initialReadiness: { type: Object, default: null },
    readonly: Boolean,
    dirty: Boolean,
    saving: Boolean
  },
  data() {
    return {
      objects: [],
      objectLoading: false,
      selectedId: null,
      selectedObject: null,
      hydration: null,
      hydrating: false,
      authoritativeDraftHash: '',
      manualOverrides: {},
      effectiveAt: '',
      simulating: false,
      simulation: null,
      readiness: this.initialReadiness,
      preflightLoading: false,
      preflight: null,
      diff: null,
      diffError: '',
      warningReason: '',
      publishing: false,
      scenarios: [],
      selectedScenarioCode: '',
      scenarioOverrides: {},
      scenarioResults: {},
      simulatingScenario: false,
      batchSimulating: false,
      serverScenarioGate: null,
      releaseReadiness: null,
      releaseLoading: false,
      releaseActivating: false,
      releaseError: ''
    }
  },
  computed: {
    capabilities() { return simulationPublishCapabilities(this.permissions) },
    canOperate() { return !this.readonly },
    draftHash() { return String(this.authoritativeDraftHash || this.template.definitionHash || '') },
    simulationState() {
      if (!this.readiness) return {}
      return {
        successful: Boolean(this.readiness.publicationReady),
        definitionHash: this.readiness.definitionHash || ''
      }
    },
    simulationCurrent() {
      return this.simulationState.successful && this.simulationState.definitionHash === this.draftHash
    },
    publishGate() {
      return publishPreflightGate(this.simulationState, this.preflight || {}, this.warningReason)
    },
    selectedScenario() {
      return this.scenarios.find(item => item.scenarioCode === this.selectedScenarioCode) || null
    },
    completionFields() {
      return (this.hydration && this.hydration.completionFields) || []
    },
    scenarioGateState() {
      if (this.serverScenarioGate && this.serverScenarioGate.publicationReady) return this.serverScenarioGate
      return scenarioGate(this.scenarios, this.scenarioResults, this.draftHash)
    },
    leadReleaseApplicable() {
      return String(this.template.templateCode || '') === 'TD-001' &&
        String(this.template.publishStatus || '') === 'PUBLISHED'
    },
    canActivateLeadRelease() {
      return this.permissions.includes('todo:definition:publish')
    },
    releaseVersions() {
      const versions = { 'TD-001': Number(this.currentVersionId) }
      const routing = (((this.definition || {}).routing || {}).config || {})
      for (const outcome of routing.businessOutcomes || []) {
        if (['TD-002', 'TD-003', 'TD-004'].includes(outcome.targetTemplateCode)) {
          versions[outcome.targetTemplateCode] = Number(outcome.targetVersionId)
        }
      }
      return versions
    },
    releaseQuery() {
      const values = this.releaseVersions
      if (!this.draftHash || !values['TD-001'] || !values['TD-002'] || !values['TD-003'] || !values['TD-004']) return null
      return {
        td001VersionId: values['TD-001'], td001DefinitionHash: this.draftHash,
        td002VersionId: values['TD-002'], td003VersionId: values['TD-003'], td004VersionId: values['TD-004']
      }
    },
    activeRelease() {
      return (this.releaseReadiness && this.releaseReadiness.activeRelease) || {}
    },
    activeDownstreamText() {
      const versions = this.activeRelease.downstreamVersions || {}
      return `疑似无效主管复核（TD-002，版本ID ${versions['TD-002'] || '-'}） / ` +
        `无法联系重试（TD-003，版本ID ${versions['TD-003'] || '-'}） / ` +
        `5天实质进展（TD-004，版本ID ${versions['TD-004'] || '-'}）`
    },
    releaseAlreadyActive() {
      if (!this.activeRelease.activeTd001VersionId) return false
      const active = this.activeRelease.downstreamVersions || {}
      return Number(this.activeRelease.activeTd001VersionId) === Number(this.releaseVersions['TD-001']) &&
        ['TD-002', 'TD-003', 'TD-004'].every(code => Number(active[code]) === Number(this.releaseVersions[code]))
    }
  },
  watch: {
    'template.definitionHash'() {
      this.authoritativeDraftHash = ''
      this.simulation = null
      this.readiness = this.initialReadiness
      this.preflight = null
      this.diff = null
      this.diffError = ''
      this.warningReason = ''
    },
    'template.versionId'() {
      this.resetExecutionState()
    },
    'template.publishStatus'() {
      this.resetExecutionState()
      this.loadLeadReleaseReadiness()
    },
    initialReadiness: {
      deep: true,
      handler(value) {
        this.readiness = value || null
      }
    }
  },
  mounted() {
    if (this.canOperate && this.capabilities.canSimulate) {
      this.searchObjects('')
      this.loadScenarios()
      if (this.capabilities.canPublish) this.runPreflight({ quiet: true })
    }
    this.loadLeadReleaseReadiness()
  },
  methods: {
    releaseTemplateLabel(code) {
      return { 'TD-001': '首联待办', 'TD-002': '疑似无效主管复核', 'TD-003': '无法联系重试', 'TD-004': '5天实质进展' }[code] || code
    },
    releaseEvidence(code) {
      return Boolean(this.releaseReadiness && this.releaseReadiness.evidenceReady && this.releaseReadiness.evidenceReady[code])
    },
    async loadLeadReleaseReadiness() {
      if (!this.leadReleaseApplicable || !this.canActivateLeadRelease || !this.releaseQuery) {
        this.releaseReadiness = null
        return
      }
      this.releaseLoading = true
      this.releaseError = ''
      try {
        const response = await getLeadReleaseReadiness(this.releaseQuery)
        this.releaseReadiness = response.data || null
      } catch (error) {
        this.releaseReadiness = null
        this.releaseError = (error && (error.msg || error.message)) || '线索待办整体启用检查失败'
      } finally {
        this.releaseLoading = false
      }
    },
    async activateCoordinatedLeadRelease() {
      if (!this.releaseReadiness || !this.releaseReadiness.activationReady || !this.releaseQuery) return
      try {
        await this.$confirm('将同时启用首联入口和三个下游版本，且只保留一个线索已分配入口。确认继续？', '确认整体启用', {
          confirmButtonText: '确认启用', cancelButtonText: '继续检查', type: 'warning'
        })
      } catch (_) { return }
      this.releaseActivating = true
      try {
        const response = await activateLeadRelease({
          actionId: this.actionId('lead-release'), ...this.releaseQuery,
          triggerExpectedVersion: Number(this.releaseReadiness.triggerExpectedVersion || 0)
        })
        this.$modal.msgSuccess('线索待办整体启用成功')
        await this.loadLeadReleaseReadiness()
        this.$emit('release-activated', response.data || {})
        this.$emit('published', this.template.templateId)
      } catch (error) {
        this.$modal.msgError((error && (error.msg || error.message)) || '线索待办整体启用失败')
      } finally {
        this.releaseActivating = false
      }
    },
    actionId(action) {
      return `journey-${action}-${Date.now()}-${Math.random().toString(16).slice(2)}`
    },
    resetExecutionState() {
      this.authoritativeDraftHash = ''
      this.hydration = null
      this.simulation = null
      this.readiness = this.initialReadiness
      this.preflight = null
      this.diff = null
      this.diffError = ''
      this.warningReason = ''
      this.scenarioResults = {}
      this.scenarioOverrides = {}
      this.serverScenarioGate = null
    },
    requireSavedDraft() {
      if (!this.dirty && !this.saving) return true
      this.$modal.msgWarning(this.saving ? '草稿正在保存，请稍后再试' : '请先保存当前草稿，再运行模拟')
      return false
    },
    localDateTimeNow() {
      const date = new Date()
      const pad = value => String(value).padStart(2, '0')
      return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
        `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
    },
    payloadCommand() {
      return {
        templateId: Number(this.template.templateId),
        versionId: Number(this.currentVersionId),
        eventType: this.event.eventType || '',
        payloadVersion: Number(this.event.payloadVersion) || 1,
        businessType: this.businessType,
        businessId: Number(this.selectedId),
        manualOverrides: { ...this.manualOverrides },
        expectedDefinitionHash: this.draftHash
      }
    },
    async searchObjects(keyword) {
      if (!this.canOperate || !this.capabilities.canSimulate) return
      this.objectLoading = true
      try {
        const response = await listBusinessObjects({
          businessType: this.businessType,
          keyword: keyword || undefined,
          pageNum: 1,
          pageSize: 20
        })
        this.objects = response.rows || (response.data && response.data.rows) || []
      } finally {
        this.objectLoading = false
      }
    },
    async loadReadOnlySample() {
      await this.searchObjects('示例')
      const sample = this.objects.find(item => item.sample)
      if (!sample) {
        this.$modal.msgWarning('当前业务类型没有可用的只读样例')
        return
      }
      this.selectObject(sample.businessId)
      await this.hydratePayload()
    },
    selectObject(id) {
      this.selectedId = id
      this.selectedObject = this.objects.find(item => Number(item.businessId) === Number(id)) || null
      this.hydration = null
      this.simulation = null
      this.preflight = null
      this.manualOverrides = {}
      this.scenarioOverrides = {}
      this.scenarioResults = {}
      this.serverScenarioGate = null
    },
    async hydratePayload(options) {
      if (!this.selectedObject) return false
      this.hydrating = true
      try {
        const response = await hydrateTodoJourneyPayload(this.template.templateId, this.payloadCommand())
        this.hydration = response.data || {}
        return true
      } catch (error) {
        if (!options || !options.quiet) {
          this.$modal.msgError((error && (error.msg || error.message)) || '载荷加载失败')
        }
        return false
      } finally {
        this.hydrating = false
      }
    },
    changeOverride(change) {
      this.manualOverrides = updateManualOverrides(this.manualOverrides, change.path, change.value)
      this.simulation = null
      this.preflight = null
    },
    async loadScenarios() {
      try {
        const response = await listJourneyScenarios(this.template.templateId)
        this.scenarios = response.data || []
        const restored = persistedScenarioResults(this.scenarios, this.readiness, this.draftHash)
        if (Object.keys(restored).length) {
          this.scenarioResults = { ...restored, ...this.scenarioResults }
          this.serverScenarioGate = scenarioGate(this.scenarios, this.scenarioResults, this.draftHash)
        }
        if (!this.scenarios.some(item => item.scenarioCode === this.selectedScenarioCode)) {
          this.selectedScenarioCode = this.scenarios.length ? this.scenarios[0].scenarioCode : ''
        }
      } catch (error) {
        this.scenarios = []
        this.$modal.msgError((error && (error.msg || error.message)) || '场景目录加载失败')
      }
    },
    selectScenario(code) {
      this.selectedScenarioCode = code
      this.scenarioOverrides = {}
    },
    changeScenarioOverride(change) {
      this.scenarioOverrides = updateManualOverrides(this.scenarioOverrides, change.path, change.value)
      this.serverScenarioGate = null
      this.preflight = null
    },
    scenarioCommand(action) {
      return {
        versionId: Number(this.currentVersionId),
        definitionHash: this.draftHash,
        businessType: this.businessType,
        businessId: Number(this.selectedId),
        manualOverrides: { ...this.manualOverrides, ...this.scenarioOverrides },
        effectiveAt: this.effectiveAt || this.localDateTimeNow(),
        requestId: this.actionId(action)
      }
    },
    async prepareScenarioRun() {
      if (!this.selectedObject || !this.requireSavedDraft()) return false
      if (!this.preflight || !this.draftHash) {
        if (!(await this.runPreflight({ includeDiff: false, quiet: true }))) return false
      }
      const configurationErrors = ((this.preflight && this.preflight.errors) || [])
        .filter(issue => !this.isSimulationReadinessIssue(issue))
      if (configurationErrors.length) {
        this.$modal.msgWarning('请先修复场景证据以外的配置阻塞项')
        return false
      }
      if (!this.hydration && !(await this.hydratePayload())) return false
      return true
    },
    async runSelectedScenario() {
      if (!this.selectedScenario || !(await this.prepareScenarioRun())) return
      this.simulatingScenario = true
      try {
        const response = await simulateJourneyScenario(this.template.templateId,
          this.selectedScenario.scenarioCode, this.scenarioCommand(`scenario-${this.selectedScenario.scenarioCode}`))
        const result = response.data || {}
        this.$set(this.scenarioResults, this.selectedScenario.scenarioCode, result)
        this.simulation = result.simulation || null
        this.serverScenarioGate = null
        this.preflight = null
        if (result.passed) this.$modal.msgSuccess('当前场景验证通过')
        else this.$modal.msgWarning(result.message || '当前场景未通过')
      } catch (error) {
        const result = failedScenarioResult(this.selectedScenario, error)
        this.$set(this.scenarioResults, this.selectedScenario.scenarioCode, result)
        this.serverScenarioGate = null
        this.preflight = null
      } finally {
        this.simulatingScenario = false
      }
    },
    async runScenarioBatch() {
      if (!(await this.prepareScenarioRun())) return
      this.batchSimulating = true
      try {
        const response = await batchSimulateJourneyScenarios(
          this.template.templateId, this.scenarioCommand('scenario-batch'))
        const result = response.data || {}
        for (const item of result.results || []) this.$set(this.scenarioResults, item.scenarioCode, item)
        this.serverScenarioGate = {
          publicationReady: Boolean(result.publicationReady),
          blockingScenarioCodes: result.blockingScenarioCodes || []
        }
        const last = (result.results || []).slice(-1)[0]
        this.simulation = (last && last.simulation) || this.simulation
        await this.runPreflight({ includeDiff: false, quiet: true })
      } catch (error) {
        this.$modal.msgError((error && (error.msg || error.message)) || '批量场景验证失败')
      } finally {
        this.batchSimulating = false
      }
    },
    async runSimulation() {
      if (!this.canOperate || !this.capabilities.canSimulate || !this.selectedObject || !this.requireSavedDraft()) return
      this.simulating = true
      try {
        const preflightReady = await this.runPreflight({ includeDiff: false, quiet: true })
        if (!preflightReady) {
          this.$modal.msgError('发布预检失败，未执行完整试运行')
          return
        }
        const configurationErrors = ((this.preflight && this.preflight.errors) || [])
          .filter(issue => !this.isSimulationReadinessIssue(issue))
        if (configurationErrors.length) {
          this.$modal.msgWarning(configurationErrors[0].message || '请先修复发布预检中的配置阻塞项')
          return
        }
        if (!(await this.hydratePayload({ quiet: true }))) {
          this.$modal.msgError('载荷加载失败，未执行完整试运行')
          return
        }
        const response = await simulateTodoJourney(this.template.templateId, {
          ...this.payloadCommand(),
          requestId: this.actionId('simulate'),
          effectiveAt: this.effectiveAt || this.localDateTimeNow(),
          taskCompletions: []
        })
        this.simulation = response.data || {}
        this.readiness = this.simulation.readiness || null
        if (this.readiness) this.$emit('readiness-change', this.readiness)
        const refreshed = await this.runPreflight({ includeDiff: true, quiet: true })
        if (!refreshed) {
          this.$modal.msgError('完整试运行已执行，但发布预检刷新失败，请重试')
          return
        }
        const completionMessage = simulationCompletionMessage(this.readiness)
        if (completionMessage) this.$modal.msgSuccess(completionMessage)
        else {
          const issue = this.readiness && (this.readiness.issues || [])[0]
          this.$modal.msgWarning((issue && issue.message) || '模拟发布验证尚未全部通过，请按页面提示修复')
        }
      } catch (error) {
        this.$modal.msgError((error && (error.msg || error.message)) || '试运行失败')
      } finally {
        this.simulating = false
      }
    },
    normalizePreflight(data) {
      const source = data || {}
      const report = source.report || source
      return {
        ...report,
        definitionHash: source.definitionHash || report.definitionHash || this.draftHash,
        errors: report.errors || [],
        warnings: report.warnings || []
      }
    },
    async runPreflight(options) {
      if ((!this.capabilities.canSimulate && !this.capabilities.canPublish) || !this.requireSavedDraft()) return false
      this.preflightLoading = true
      try {
        const response = await preflightTemplateDraft(this.currentVersionId)
        this.preflight = this.normalizePreflight(response.data)
        this.authoritativeDraftHash = this.preflight.definitionHash || ''
        if ((!options || options.includeDiff !== false) && this.capabilities.canDiff) {
          await this.loadDiff()
        }
        return true
      } catch (error) {
        if (!options || !options.quiet) {
          this.$modal.msgError((error && (error.msg || error.message)) || '发布预检失败')
        }
        return false
      } finally {
        this.preflightLoading = false
      }
    },
    isSimulationReadinessIssue(issue) {
      return [
        'TODO_REQUIRED_SIMULATION_SCENARIOS_INCOMPLETE',
        'TODO_FULL_SIMULATION_REQUIRED',
        'TODO_FULL_SIMULATION_STALE',
        'TODO_JOURNEY_SIMULATION_REQUIRED'
      ].includes(String((issue && issue.code) || ''))
    },
    focusReadinessIssue(issue) {
      this.repairPreflight(issue)
    },
    repairPreflight(issue) {
      const readinessBlockers = (this.readiness && this.readiness.blockingScenarios) || []
      const target = readinessRepairTarget({
        ...(issue || {}),
        blockingScenarios: readinessBlockers.length
          ? readinessBlockers
          : (this.scenarioGateState.blockingScenarios || []),
        blockingScenarioCodes: this.scenarioGateState.blockingScenarioCodes || []
      }, this.scenarios)
      if (target.stepCode !== 'SIMULATION_PUBLISH') {
        this.$emit('navigate-repair', {
          ...(issue || {}),
          stepKey: target.stepCode,
          stepCode: target.stepCode,
          resourceKey: (issue && issue.resourceKey) || target.stepCode,
          fieldPath: (issue && issue.fieldPath) || target.focusTarget
        })
        return
      }
      if (target.scenarioCode) this.selectScenario(target.scenarioCode)
      this.$nextTick(() => {
        const element = target.focusTarget === 'full-simulation'
          ? this.$refs.fullSimulationBlock
          : (this.$refs.scenarioSelector && this.$refs.scenarioSelector.$el)
        if (element && element.scrollIntoView) {
          element.scrollIntoView({ behavior: 'smooth', block: 'center' })
          element.classList.add('is-repair-target')
          window.setTimeout(() => element.classList.remove('is-repair-target'), 1800)
        }
      })
      this.$modal.msgInfo(target.focusTarget === 'full-simulation'
        ? '已定位到完整试运行，请运行后重新检查发布资格'
        : '已定位到未通过场景，请运行当前场景或批量验证三个场景')
    },
    async loadDiff() {
      if (!this.capabilities.canDiff) return
      this.diffError = ''
      try {
        const response = await listTemplateVersions(this.template.templateId)
        const versions = response.data || response.rows || []
        const plan = versionDiffPlan(versions, this.currentVersionId)
        if (!plan.available) {
          this.diff = { changes: [], overallRisk: 'LOW' }
          this.diffError = plan.reason === 'NO_PUBLISHED_VERSION'
            ? '当前为首个待发布版本'
            : '版本信息不完整，暂时无法加载差异'
          return
        }
        const result = await diffTemplateVersions(plan.leftVersionId, plan.rightVersionId)
        this.diff = result.data || {}
      } catch (_) {
        this.diff = { changes: [], overallRisk: 'LOW' }
        this.diffError = '版本差异暂时无法加载，发布预检结果不受影响'
      }
    },
    async publish() {
      if (!this.canOperate || !this.requireSavedDraft()) return
      if (!(await this.runPreflight())) return
      if (!this.publishGate.allowed) return
      try {
        await this.$confirm('发布后将生成不可变版本。确认发布当前草稿？', '确认发布', {
          confirmButtonText: '确认发布',
          cancelButtonText: '继续检查',
          type: 'warning'
        })
      } catch (_) {
        return
      }
      this.publishing = true
      try {
        await publishReleaseRecord(this.currentVersionId, {
          actionId: this.actionId('publish'),
          versionId: Number(this.currentVersionId),
          expectedDefinitionHash: this.preflight.definitionHash,
          warningReason: this.warningReason.trim() || null
        })
        this.$modal.msgSuccess('当前版本已发布')
        this.$emit('published', this.template.templateId)
      } catch (error) {
        this.$modal.msgError((error && (error.msg || error.message)) || '发布失败')
      } finally {
        this.publishing = false
      }
    }
  }
}
</script>

<style scoped>
.simulation-publish-step { display: grid; gap: 18px; }
.simulation-publish-step__header { display: flex; justify-content: space-between; align-items: flex-start; gap: 20px; }
.simulation-publish-step__eyebrow { color: #C89A3D; font-weight: 700; }
.simulation-publish-step__header h2 { margin: 6px 0; color: #0B2A55; }
.simulation-publish-step__header p, .simulation-publish-step__publish p { margin: 0; color: #66758A; }
.simulation-publish-step__full-simulation { display: grid; gap: 12px; padding: 14px; border: 1px solid transparent; border-radius: 8px; transition: border-color .2s ease, box-shadow .2s ease; }
.simulation-publish-step__actions { display: flex; justify-content: flex-end; gap: 12px; }
.simulation-publish-step__publish { display: flex; justify-content: space-between; align-items: center; gap: 20px; border: 1px solid #C89A3D; border-radius: 8px; padding: 18px 20px; background: #FFFCF5; }
.lead-release-panel { display: grid; gap: 14px; padding: 20px; border: 1px solid #B8C7D9; border-radius: 8px; background: #F8FAFD; }
.lead-release-panel__title, .lead-release-panel__actions { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; }
.lead-release-panel__title span { color: #C89A3D; font-size: 12px; font-weight: 700; }
.lead-release-panel__title h3 { margin: 4px 0 0; color: #0B2A55; }
.lead-release-panel__versions { display: grid; gap: 10px; margin: 0; }
.lead-release-panel__versions div { display: grid; grid-template-columns: 110px 1fr; gap: 12px; }
.lead-release-panel__versions dt { color: #66758A; }
.lead-release-panel__versions dd { margin: 0; color: #243B5A; }
.lead-release-panel__evidence { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
.lead-release-panel__evidence .el-icon-circle-check { color: #2E7D57; }
.lead-release-panel__evidence .el-icon-circle-close { color: #B63C3C; }
.lead-release-panel__actions small { max-width: 620px; color: #66758A; line-height: 20px; }
.simulation-publish-step__full-simulation.is-repair-target,
.simulation-publish-step ::v-deep .scenario-selector.is-repair-target { border-color: #C89A3D; box-shadow: 0 0 0 3px rgba(200, 154, 61, 0.18); }
@media (max-width: 720px) {
  .simulation-publish-step__header, .simulation-publish-step__publish { flex-direction: column; }
  .simulation-publish-step__actions { flex-direction: column; }
  .lead-release-panel__title, .lead-release-panel__actions { flex-direction: column; }
  .lead-release-panel__versions div { grid-template-columns: 1fr; gap: 4px; }
  .lead-release-panel__evidence { grid-template-columns: 1fr; }
}
</style>
