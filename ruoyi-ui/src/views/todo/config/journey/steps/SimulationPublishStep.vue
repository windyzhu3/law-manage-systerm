<template>
  <div class="simulation-publish-step" data-testid="simulation-publish-step">
    <header class="simulation-publish-step__header">
      <div>
        <span class="simulation-publish-step__eyebrow">最后一步</span>
        <h2>用真实业务对象试运行，再安全发布</h2>
        <p>试运行只读取数据，不创建待办；发布会生成不可变版本，历史版本不会被覆盖。</p>
      </div>
      <el-tag v-if="simulationCurrent" type="success">当前草稿试运行通过</el-tag>
      <el-tag v-else type="info">尚未验证当前草稿</el-tag>
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
      @search="searchObjects"
      @sample="searchObjects('示例')"
      @select="selectObject"
      @hydrate="hydratePayload"
      @override="changeOverride"
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

    <simulation-trace
      :trace="simulation && simulation.trace"
      @repair="$emit('navigate-repair', $event)"
      @rerun="runSimulation"
    />

    <publish-preflight-panel
      v-if="canOperate && (capabilities.canPublish || preflight)"
      :preflight="preflight"
      :gate="publishGate"
      :warning-reason.sync="warningReason"
      :diff="diff"
      :loading="preflightLoading"
      @reload="runPreflight"
      @repair="$emit('navigate-repair', $event)"
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
  </div>
</template>

<script>
import BusinessObjectPayloadEditor from '../components/BusinessObjectPayloadEditor'
import SimulationTrace from '../components/SimulationTrace'
import PublishPreflightPanel from '../components/PublishPreflightPanel'
import {
  listBusinessObjects,
  hydrateTodoJourneyPayload,
  simulateTodoJourney,
  preflightTemplateDraft,
  listTemplateVersions,
  diffTemplateVersions,
  publishReleaseRecord
} from '@/api/todo-config'
import {
  updateManualOverrides,
  publishPreflightGate,
  simulationPublishCapabilities
} from '../journey-step-model'

export default {
  name: 'SimulationPublishStep',
  components: { BusinessObjectPayloadEditor, SimulationTrace, PublishPreflightPanel },
  props: {
    template: { type: Object, required: true },
    value: { type: Object, default: () => ({}) },
    event: { type: Object, default: () => ({}) },
    businessType: { type: String, default: '' },
    currentVersionId: { type: [Number, String], required: true },
    permissions: { type: Array, default: () => [] },
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
      preflightLoading: false,
      preflight: null,
      diff: null,
      warningReason: '',
      publishing: false
    }
  },
  computed: {
    capabilities() { return simulationPublishCapabilities(this.permissions) },
    canOperate() { return !this.readonly },
    draftHash() { return String(this.authoritativeDraftHash || this.template.definitionHash || '') },
    simulationState() {
      if (!this.simulation) return {}
      const issues = this.simulation.issues || []
      const definitionHash = (this.simulation.engine && this.simulation.engine.definitionHash) ||
        this.simulation.definitionHash
      return {
        successful: !issues.some(issue => String(issue.severity).toUpperCase() === 'BLOCKER') &&
          this.simulation.publishEligible !== false,
        definitionHash
      }
    },
    simulationCurrent() {
      return this.simulationState.successful && this.simulationState.definitionHash === this.draftHash
    },
    publishGate() {
      return publishPreflightGate(this.simulationState, this.preflight || {}, this.warningReason)
    }
  },
  watch: {
    'template.definitionHash'() {
      this.authoritativeDraftHash = ''
      this.simulation = null
      this.preflight = null
      this.diff = null
      this.warningReason = ''
    },
    'template.versionId'() {
      this.resetExecutionState()
    },
    'template.publishStatus'() {
      this.resetExecutionState()
    }
  },
  mounted() {
    if (this.canOperate && this.capabilities.canSimulate) this.searchObjects('')
  },
  methods: {
    actionId(action) {
      return `journey-${action}-${Date.now()}-${Math.random().toString(16).slice(2)}`
    },
    resetExecutionState() {
      this.authoritativeDraftHash = ''
      this.hydration = null
      this.simulation = null
      this.preflight = null
      this.diff = null
      this.warningReason = ''
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
    selectObject(id) {
      this.selectedId = id
      this.selectedObject = this.objects.find(item => Number(item.businessId) === Number(id)) || null
      this.hydration = null
      this.simulation = null
      this.preflight = null
      this.manualOverrides = {}
    },
    async hydratePayload() {
      if (!this.selectedObject) return
      this.hydrating = true
      try {
        const response = await hydrateTodoJourneyPayload(this.template.templateId, this.payloadCommand())
        this.hydration = response.data || {}
      } catch (error) {
        this.$modal.msgError((error && (error.msg || error.message)) || '载荷加载失败')
      } finally {
        this.hydrating = false
      }
    },
    changeOverride(change) {
      this.manualOverrides = updateManualOverrides(this.manualOverrides, change.path, change.value)
      this.simulation = null
      this.preflight = null
    },
    async runSimulation() {
      if (!this.canOperate || !this.capabilities.canSimulate || !this.selectedObject || !this.requireSavedDraft()) return
      this.simulating = true
      try {
        const preflightReady = await this.runPreflight({ includeDiff: false, quiet: true })
        if (!preflightReady) return
        if (!this.preflight || (this.preflight.errors || []).length) {
          this.$modal.msgWarning('请先修复发布预检中的阻塞项')
          return
        }
        await this.hydratePayload()
        const response = await simulateTodoJourney(this.template.templateId, {
          ...this.payloadCommand(),
          requestId: this.actionId('simulate'),
          effectiveAt: this.effectiveAt || this.localDateTimeNow(),
          taskCompletions: []
        })
        this.simulation = response.data || {}
        this.preflight = null
        if (this.simulationCurrent) {
          this.$modal.msgSuccess('当前草稿试运行通过')
          if (this.capabilities.canPublish) await this.runPreflight()
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
          try {
            await this.loadDiff()
          } catch (_) {
            this.diff = null
            this.$modal.msgWarning('版本差异暂时无法加载，发布预检结果不受影响')
          }
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
    async loadDiff() {
      if (!this.capabilities.canDiff) return
      const response = await listTemplateVersions(this.template.templateId)
      const versions = response.data || response.rows || []
      const published = versions.find(item => ['PUBLISHED', 'RETIRED'].includes(String(item.status || item.publishStatus).toUpperCase()))
      if (!published) {
        this.diff = { changes: [], overallRisk: 'LOW' }
        return
      }
      const publishedId = published.versionId || published.id
      const result = await diffTemplateVersions(publishedId, this.currentVersionId)
      this.diff = result.data || {}
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
.simulation-publish-step__actions { display: flex; justify-content: flex-end; gap: 12px; }
.simulation-publish-step__publish { display: flex; justify-content: space-between; align-items: center; gap: 20px; border: 1px solid #C89A3D; border-radius: 8px; padding: 18px 20px; background: #FFFCF5; }
@media (max-width: 720px) {
  .simulation-publish-step__header, .simulation-publish-step__publish { flex-direction: column; }
  .simulation-publish-step__actions { flex-direction: column; }
}
</style>
