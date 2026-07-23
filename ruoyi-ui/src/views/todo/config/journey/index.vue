<template>
  <div v-loading="loading" class="app-container journey-page">
    <header class="journey-page__header">
      <div>
        <el-button class="journey-page__back" type="text" icon="el-icon-back" @click="goBack">返回配置工作台</el-button>
        <div class="journey-page__title">
          <h1>{{ templateName }}</h1>
          <el-tag size="small" :type="publishedReadOnly ? 'info' : 'warning'">
            {{ publishedReadOnly ? '已发布 · 只读' : '草稿配置中' }}
          </el-tag>
        </div>
        <p>按照业务旅程依次完成七个步骤，右侧可随时检查员工看到的待办效果。</p>
      </div>
      <div v-if="journey" class="journey-page__identity">
        <span>{{ journey.template.templateCode || '未设置编码' }}</span>
        <small>V{{ journey.template.versionNo || 1 }}</small>
      </div>
    </header>

    <el-alert
      v-if="loadError"
      class="journey-page__error"
      :title="loadError"
      type="error"
      :closable="false"
      show-icon
    >
      <el-button slot="description" type="text" @click="loadJourney">重新加载</el-button>
    </el-alert>

    <div v-if="journey" class="journey-shell" data-testid="template-journey-shell">
      <journey-step-nav :steps="journey.steps" :active-code="activeStep" @select="selectStep" />

      <div v-if="activeIssues.length" class="journey-shell__notice" role="status">
        <i class="el-icon-warning-outline" aria-hidden="true" />
        <span>
          当前步骤有 <strong>{{ activeIssues.length }}</strong> 个问题需要处理，修复后可继续后续配置。
        </span>
        <el-button type="text" @click="repair(activeIssues[0])">立即处理</el-button>
      </div>

      <main class="journey-body">
        <section class="journey-editor">
          <component
            :is="activeComponent"
            ref="activeEditor"
            :key="activeStep"
            :step="activeStepItem"
            :value="activeValue"
            :resources="journey.resources || {}"
            :business-type="journey.template.businessType"
            :event="journey.definition.event || {}"
            :preview="journey.employeePreview || {}"
            :permissions="clientPermissions"
            :resource-revision="resourceRevision"
            :readonly="publishedReadOnly"
            @change="onStepChange"
            @repair-resource="openResourceRepair"
          />
        </section>
        <aside class="journey-aside">
          <configuration-health-panel :issues="activeIssues" @repair="repair" />
          <employee-todo-preview :value="journey.employeePreview || {}" />
        </aside>
      </main>

      <footer class="journey-footer">
        <journey-save-status :state="journey.saveState" @retry="retrySave" />
        <div class="journey-footer__actions">
          <el-button :disabled="activeIndex === 0" @click="previousStep">上一步</el-button>
          <el-button
            v-if="!publishedReadOnly"
            v-hasPermi="['todo:template:edit','todo:template:create','todo:template:copy']"
            :loading="saving"
            :disabled="!journey.dirty"
            @click="saveNow"
          >
            保存
          </el-button>
          <el-button type="primary" :loading="saving" @click="runPrimaryAction">{{ primaryAction.label }}</el-button>
        </div>
      </footer>
    </div>

    <journey-conflict-dialog
      v-if="journey"
      :visible.sync="conflictVisible"
      :conflict="journey.conflict || {}"
      :merging="conflictMerging"
      :copying="conflictCopying"
      @refresh-merge="refreshAndMergeConflict"
      @save-copy="saveConflictCopy"
      @discard-local="discardLocalConflict"
    />
    <context-resource-drawer
      v-if="journey"
      :visible.sync="resourceRepair.open"
      :request="resourceRepair.request"
      :permissions="clientPermissions"
      :resources="journey.resources || {}"
      :business-type="journey.template.businessType"
      @repaired="completeResourceRepair"
    />
  </div>
</template>

<script>
import JourneyStepNav from './components/JourneyStepNav'
import ConfigurationHealthPanel from './components/ConfigurationHealthPanel'
import EmployeeTodoPreview from './components/EmployeeTodoPreview'
import JourneySaveStatus from './components/JourneySaveStatus'
import JourneyConflictDialog from './components/JourneyConflictDialog'
import ContextResourceDrawer from './components/ContextResourceDrawer'
import EventStep from './steps/EventStep'
import TriggerStep from './steps/TriggerStep'
import OwnerStep from './steps/OwnerStep'
import {
  getTodoTemplate,
  getTodoTemplateJourney,
  updateTemplateDraft,
  copyTodoTemplate,
  listTemplateEventCatalog
} from '@/api/todo-config'
import {
  listFieldResources,
  listMaterialResources,
  listDodRecipeResources,
  listValidatorResources
} from '@/api/todo-resources'
import {
  hydrateJourney,
  applyStepPatch,
  derivePrimaryAction,
  mergeSaveResult,
  mergeConflictWithServer,
  buildConflictCopyJourney,
  rebaseJourneyAfterSave,
  canLeave,
  hasUnresolvedFieldConflicts
} from './journey-model'
import {
  eventSchemaHealth,
  ownerBlocker,
  createRepairRequest,
  completeResourceRepair as completeRepair
} from './journey-step-model'
import {
  resolveJourneyCapabilities,
  snapshotReadPlan,
  routeContextChanged,
  createCopyTransition,
  copyTransitionMatches,
  consumeCopyTransition
} from './journey-runtime'
import { hydrateTemplateDraft } from '../template/template-draft-model'
import { toDraftPayload } from '../definition-codec'

const STEP_CODES = ['EVENT', 'TRIGGER', 'OWNER', 'DOD', 'SLA', 'ROUTING', 'SIMULATION_PUBLISH']
const STEP_TITLES = {
  EVENT: '业务事件',
  TRIGGER: '触发条件',
  OWNER: '负责人',
  DOD: '完成标准',
  SLA: '办理时限',
  ROUTING: '后续路由',
  SIMULATION_PUBLISH: '模拟发布'
}

const JourneyStepPlaceholder = {
  name: 'JourneyStepPlaceholder',
  functional: true,
  props: {
    step: { type: Object, default: () => ({}) },
    readonly: Boolean
  },
  render(h, context) {
    const step = context.props.step || {}
    const stateLabels = {
      NOT_STARTED: '尚未开始',
      IN_PROGRESS: '正在配置',
      COMPLETED: '已完成',
      WARNING: '需要检查',
      BLOCKED: '存在阻塞'
    }
    return h('div', { class: 'journey-editor-placeholder' }, [
      h('span', { class: 'journey-editor-placeholder__eyebrow' }, '当前配置步骤'),
      h('h2', step.title || '待办模板配置'),
      h('p', context.props.readonly
        ? '当前展示已发布版本，配置内容仅供查看。'
        : '步骤编辑器将在此区域加载；切换步骤不会丢失已经保存在本地的修改。'),
      h('div', { class: 'journey-editor-placeholder__state' }, [
        h('i', { class: step.state === 'COMPLETED' ? 'el-icon-circle-check' : 'el-icon-edit-outline' }),
        h('span', stateLabels[step.state] || '尚未开始')
      ])
    ])
  }
}

export default {
  name: 'TodoTemplateJourney',
  components: {
    JourneyStepNav,
    ConfigurationHealthPanel,
    EmployeeTodoPreview,
    JourneySaveStatus,
    JourneyConflictDialog,
    ContextResourceDrawer,
    EventStep,
    TriggerStep,
    OwnerStep,
    JourneyStepPlaceholder
  },
  stepEditors: {
    EVENT: EventStep,
    TRIGGER: TriggerStep,
    OWNER: OwnerStep
  },
  data() {
    return {
      journey: null,
      draftContext: null,
      activeStep: 'EVENT',
      loading: false,
      loadError: '',
      saving: false,
      autosaveTimer: null,
      editRevision: 0,
      conflictVisible: false,
      conflictMerging: false,
      conflictCopying: false,
      conflictServerContext: null,
      loadSequence: 0,
      pendingCopyTransition: null,
      resourceRevision: 0,
      resourceRepair: { open: false, request: {} }
    }
  },
  computed: {
    templateId() {
      return Number(this.$route.query.templateId)
    },
    templateName() {
      return this.journey && this.journey.template.templateName
        ? this.journey.template.templateName
        : '待办模板业务旅程'
    },
    clientPermissions() {
      return (this.$store && this.$store.getters && this.$store.getters.permissions) || []
    },
    capabilities() {
      return resolveJourneyCapabilities(this.clientPermissions)
    },
    unresolvedFieldConflicts() {
      return hasUnresolvedFieldConflicts(this.journey)
    },
    publishedReadOnly() {
      const permissions = (this.journey && this.journey.permissions) || {}
      return this.$route.query.view === 'published' ||
        this.unresolvedFieldConflicts ||
        !this.capabilities.canSaveDraft ||
        permissions.canEdit === false
    },
    activeIndex() {
      return Math.max(0, STEP_CODES.indexOf(this.activeStep))
    },
    activeStepItem() {
      const steps = (this.journey && this.journey.steps) || []
      const step = steps.find(item => item.code === this.activeStep) || {}
      return { ...step, title: STEP_TITLES[this.activeStep] || step.title }
    },
    activeValue() {
      return this.activeStepItem.value || {}
    },
    activeIssues() {
      const issues = (this.journey && this.journey.issues) || []
      const current = issues.filter(issue => !issue.stepCode || issue.stepCode === this.activeStep)
      const local = this.localStepIssue
      if (local && !current.some(issue => issue.code === local.code)) current.push(local)
      return current
    },
    localStepIssue() {
      if (!this.journey) return null
      const resources = this.journey.resources || {}
      if (this.activeStep === 'EVENT' && this.activeValue.eventType) {
        const event = (resources.events || []).find(item =>
          item.eventType === this.activeValue.eventType &&
          Number(item.payloadVersion) === Number(this.activeValue.payloadVersion)
        ) || this.activeValue
        const health = eventSchemaHealth(event, resources.fields || [])
        return health.ready ? null : {
          code: 'TODO_JOURNEY_EVENT_SCHEMA_REQUIRED',
          severity: 'BLOCKER',
          stepCode: 'EVENT',
          fieldPath: 'event',
          message: '所选事件字段尚未维护完整',
          repairAction: '维护事件字段'
        }
      }
      if (this.activeStep === 'TRIGGER' && this.journey.definition.event.eventType) {
        const fields = (resources.fields || []).filter(field =>
          !(field.sourceEvents || []).length ||
          (field.sourceEvents || []).includes(this.journey.definition.event.eventType)
        )
        return fields.length ? null : {
          code: 'TODO_JOURNEY_EVENT_SCHEMA_REQUIRED',
          severity: 'BLOCKER',
          stepCode: 'TRIGGER',
          fieldPath: 'event.condition',
          message: '当前事件没有可用于触发条件的业务字段',
          repairAction: '维护事件字段'
        }
      }
      if (this.activeStep === 'OWNER') {
        const blocker = ownerBlocker(
          (this.activeValue && this.activeValue.config) || {},
          resources.fields || []
        )
        return blocker && { ...blocker, stepCode: 'OWNER', fieldPath: 'owner.config', repairAction: '设置负责人或兜底' }
      }
      return null
    },
    activeComponent() {
      return this.$options.stepEditors[this.activeStep] || JourneyStepPlaceholder
    },
    primaryAction() {
      if (!this.journey) return { code: 'CONTINUE_CONFIGURATION', label: '继续配置', stepCode: this.activeStep }
      return derivePrimaryAction({ ...this.journey, activeStepCode: this.activeStep })
    }
  },
  created() {
    this.loadJourney()
  },
  watch: {
    '$route.query': {
      deep: true,
      handler(value, previous) {
        if (routeContextChanged({ query: previous || {} }, { query: value || {} })) {
          this.loadJourney()
          return
        }
        const requested = String((value && value.step) || '').toUpperCase()
        if (STEP_CODES.includes(requested)) this.activeStep = requested
      }
    }
  },
  beforeDestroy() {
    this.loadSequence += 1
    clearTimeout(this.autosaveTimer)
  },
  beforeRouteUpdate(to, from, next) {
    if (copyTransitionMatches(this.pendingCopyTransition, to.query.templateId)) {
      clearTimeout(this.autosaveTimer)
      next()
      return
    }
    if (!routeContextChanged(from, to)) {
      next()
      return
    }
    if (canLeave(this.journey)) {
      clearTimeout(this.autosaveTimer)
      next()
      return
    }
    this.$confirm('当前模板还有未保存的修改，确定要切换模板吗？', '切换配置模板', {
      confirmButtonText: '仍然切换',
      cancelButtonText: '继续配置',
      type: 'warning'
    }).then(() => {
      clearTimeout(this.autosaveTimer)
      next()
    }).catch(() => next(false))
  },
  beforeRouteLeave(to, from, next) {
    if (canLeave(this.journey)) {
      clearTimeout(this.autosaveTimer)
      next()
      return
    }
    this.$confirm('当前模板还有未保存的修改，确定要离开吗？', '离开配置页面', {
      confirmButtonText: '仍然离开',
      cancelButtonText: '继续配置',
      type: 'warning'
    }).then(() => {
      clearTimeout(this.autosaveTimer)
      next()
    }).catch(() => next(false))
  },
  methods: {
    actionId(action) {
      return `journey-${action}-${Date.now()}-${Math.random().toString(16).slice(2)}`
    },
    snapshotContext(detail, aggregate) {
      const draft = hydrateTemplateDraft(detail || {})
      return {
        versionId: Number(draft.versionId || (aggregate.template && aggregate.template.versionId)),
        sourceDefinitionJson: draft.sourceDefinitionJson,
        ruleReferences: (draft.ruleReferences || []).map(reference => ({
          type: reference.type,
          id: Number(reference.id),
          order: Number(reference.order)
        })),
        preservedDefinition: {
          schemaVersion: Number(draft.schemaVersion) || 1,
          autoActions: draft.autoActions || [],
          decisionRefs: draft.decisionRefs || [],
          acceptanceRefs: draft.acceptanceRefs || []
        },
        changeSummary: draft.changeSummary || '',
        impactScope: draft.impactScope || ''
      }
    },
    snapshotFingerprint(context) {
      return JSON.stringify({
        versionId: context.versionId,
        sourceDefinitionJson: context.sourceDefinitionJson,
        ruleReferences: context.ruleReferences,
        preservedDefinition: context.preservedDefinition,
        changeSummary: context.changeSummary,
        impactScope: context.impactScope
      })
    },
    async fetchSnapshot(templateId) {
      const plan = snapshotReadPlan(this.capabilities)
      if (!plan.length) {
        const denied = new Error('当前账号没有查看模板配置旅程的权限')
        denied.code = 'TODO_JOURNEY_ACCESS_DENIED'
        throw denied
      }
      if (plan.length === 1 && plan[0] === 'JOURNEY') {
        const response = await getTodoTemplateJourney(templateId)
        return { aggregate: response.data || {}, context: null }
      }
      for (let attempt = 0; attempt < 2; attempt += 1) {
        const beforeResponse = await getTodoTemplate(templateId)
        const journeyResponse = await getTodoTemplateJourney(templateId)
        const afterResponse = await getTodoTemplate(templateId)
        const aggregate = journeyResponse.data || {}
        const before = this.snapshotContext(beforeResponse.data || {}, aggregate)
        const after = this.snapshotContext(afterResponse.data || {}, aggregate)
        const journeyVersionId = Number(aggregate.template && aggregate.template.versionId)
        if (this.snapshotFingerprint(before) === this.snapshotFingerprint(after) &&
            journeyVersionId === Number(after.versionId)) {
          return { aggregate, context: after }
        }
      }
      const error = new Error('模板在加载期间发生变化，请重新加载后继续')
      error.code = 'TODO_JOURNEY_SNAPSHOT_CHANGED'
      throw error
    },
    async loadJourney() {
      if (!this.templateId) {
        this.loadError = '缺少模板编号，无法加载配置旅程。'
        return
      }
      if (!this.capabilities.canLoadJourney) {
        this.loadError = '当前账号没有查看模板配置旅程的权限。'
        return
      }
      const sequence = ++this.loadSequence
      clearTimeout(this.autosaveTimer)
      this.loading = true
      this.loadError = ''
      try {
        const pending = consumeCopyTransition(this.pendingCopyTransition, this.templateId)
        if (pending) {
          this.pendingCopyTransition = null
          this.journey = pending.journey
          this.draftContext = pending.context
          this.conflictVisible = false
          this.editRevision = 0
          const requested = String(this.$route.query.step || '').toUpperCase()
          this.activeStep = STEP_CODES.includes(requested)
            ? requested
            : derivePrimaryAction(this.journey).stepCode
          await this.refreshResourceSnapshot('ALL', true)
          return
        }
        const snapshot = await this.fetchSnapshot(this.templateId)
        if (sequence !== this.loadSequence) return
        this.journey = hydrateJourney(snapshot.aggregate)
        this.draftContext = snapshot.context
        const requested = String(this.$route.query.step || '').toUpperCase()
        this.activeStep = STEP_CODES.includes(requested)
          ? requested
          : derivePrimaryAction(this.journey).stepCode
        this.editRevision = 0
        await this.refreshResourceSnapshot('ALL', true)
      } catch (error) {
        if (sequence !== this.loadSequence) return
        this.loadError = (error && (error.msg || error.message)) || '待办模板配置旅程加载失败。'
      } finally {
        if (sequence === this.loadSequence) this.loading = false
      }
    },
    selectStep(code) {
      if (STEP_CODES.includes(code)) this.activeStep = code
    },
    previousStep() {
      this.activeStep = STEP_CODES[Math.max(0, this.activeIndex - 1)]
    },
    onStepChange(value) {
      if (this.publishedReadOnly || !this.journey) return
      this.journey = applyStepPatch(this.journey, this.activeStep, value)
      this.editRevision += 1
      this.scheduleAutosave()
    },
    scheduleAutosave() {
      clearTimeout(this.autosaveTimer)
      this.autosaveTimer = setTimeout(() => {
        this.saveNow({ automatic: true })
      }, 600)
    },
    buildSavePayload(journey, context) {
      const definition = {
        ...journey.definition,
        ...(context.preservedDefinition || {}),
        templateCode: journey.definition.templateCode
      }
      const payload = toDraftPayload(definition, context.sourceDefinitionJson)
      return {
        actionId: this.actionId('save'),
        versionId: Number(context.versionId),
        ...payload,
        expectedDefinitionJson: context.sourceDefinitionJson,
        ruleReferences: context.ruleReferences || [],
        changeSummary: context.changeSummary || '',
        impactScope: context.impactScope || ''
      }
    },
    async persistJourney(journey, context) {
      const payload = this.buildSavePayload(journey, context)
      await updateTemplateDraft(context.versionId, payload)
    },
    async saveNow(options) {
      const automatic = Boolean(options && options.automatic)
      if (!this.journey || !this.capabilities.canSaveDraft || this.publishedReadOnly ||
          !this.journey.dirty || this.saving) return false
      clearTimeout(this.autosaveTimer)
      const localSnapshot = this.journey
      const savedRevision = this.editRevision
      this.saving = true
      this.journey = { ...this.journey, saveState: 'SAVING', saveError: null }
      try {
        await this.persistJourney(localSnapshot, this.draftContext)
        const fresh = await this.fetchSnapshot(this.templateId)
        this.draftContext = fresh.context
        if (this.editRevision === savedRevision) {
          this.journey = mergeSaveResult(localSnapshot, fresh.aggregate)
        } else {
          this.journey = rebaseJourneyAfterSave(this.journey, localSnapshot, fresh.aggregate)
          this.scheduleAutosave()
        }
        if (!automatic) this.$modal.msgSuccess('模板配置已保存')
        return true
      } catch (error) {
        await this.handleSaveError(error)
        if (!automatic && !this.conflictVisible) {
          this.$modal.msgError((error && (error.msg || error.message)) || '保存失败，本地修改已保留')
        }
        return false
      } finally {
        this.saving = false
      }
    },
    async handleSaveError(error) {
      const status = Number(error && error.response && error.response.status)
      const message = String((error && (error.msg || error.message)) || '')
      const versionConflict = status === 409 || /Draft version changed|reload before saving|VERSION_CONFLICT/i.test(message)
      if (versionConflict) {
        let server = error.response && error.response.data && error.response.data.data
        try {
          const fresh = await this.fetchSnapshot(this.templateId)
          server = fresh.aggregate
          this.conflictServerContext = fresh.context
        } catch (refreshError) {
          this.conflictServerContext = null
        }
        this.journey = mergeSaveResult(this.journey, {
          status: 'CONFLICT',
          server: server || {},
          error: { code: 'TODO_JOURNEY_VERSION_CONFLICT', message: '草稿已被其他用户更新' }
        })
        this.conflictVisible = true
        return
      }
      this.journey = mergeSaveResult(this.journey, {
        status: 'FAILED',
        error: {
          code: 'TODO_JOURNEY_SAVE_FAILED',
          message: (error && (error.msg || error.message)) || '网络暂时不可用'
        }
      })
    },
    retrySave() {
      if (this.unresolvedFieldConflicts) {
        this.conflictVisible = true
        return false
      }
      return this.saveNow({ automatic: false })
    },
    async refreshAndMergeConflict() {
      if (!this.journey || !this.journey.conflict) return
      this.conflictMerging = true
      try {
        const fresh = await this.fetchSnapshot(this.templateId)
        const local = this.journey.conflict.local
        this.journey = mergeConflictWithServer({
          ...this.journey,
          conflict: { local, server: fresh.aggregate }
        })
        this.draftContext = fresh.context
        this.editRevision += 1
        const collisions = (this.journey.conflict && this.journey.conflict.collisions) || []
        if (collisions.length) {
          this.conflictVisible = true
          this.$modal.msgWarning(`仍有 ${collisions.length} 个同一字段冲突，请核对差异或另存副本`)
          return
        }
        this.conflictVisible = false
        if (this.journey.dirty) this.scheduleAutosave()
        else this.$modal.msgSuccess('已刷新为服务器最新配置')
      } catch (error) {
        this.$modal.msgError((error && (error.msg || error.message)) || '刷新服务器配置失败')
      } finally {
        this.conflictMerging = false
      }
    },
    async saveConflictCopy() {
      if (!this.journey || !this.journey.conflict || !this.capabilities.canCopyTemplate) return
      this.conflictCopying = true
      try {
        const prompt = await this.$prompt('请输入副本的唯一模板编码', '另存副本', {
          confirmButtonText: '创建副本',
          cancelButtonText: '取消',
          inputValue: `${this.journey.template.templateCode || 'TODO_TEMPLATE'}_COPY`,
          inputPattern: /^[A-Za-z][A-Za-z0-9_-]{2,63}$/,
          inputErrorMessage: '请输入 3–64 位字母、数字、下划线或连字符'
        })
        const response = await copyTodoTemplate(this.templateId, {
          actionId: this.actionId('copy'),
          newTemplateCode: prompt.value,
          newTemplateName: `${this.templateName}（副本）`
        })
        const copiedId = Number(response.data && response.data.templateId)
        const copied = await this.fetchSnapshot(copiedId)
        const copiedJourney = buildConflictCopyJourney(this.journey, copied.aggregate)
        if (copiedJourney.dirty) await this.persistJourney(copiedJourney, copied.context)
        const savedCopy = await this.fetchSnapshot(copiedId)
        const authoritativeCopy = mergeSaveResult(copiedJourney, savedCopy.aggregate)
        this.pendingCopyTransition = createCopyTransition(copiedId, authoritativeCopy, savedCopy.context)
        try {
          await this.$router.push({
            path: '/todo-engine/todo-template-journey',
            query: { templateId: String(copiedId), view: 'draft' }
          })
        } catch (navigationError) {
          this.pendingCopyTransition = null
          throw navigationError
        }
        this.$modal.msgSuccess('已保存为新的待办模板副本')
      } catch (error) {
        if (error !== 'cancel' && error !== 'close') {
          this.$modal.msgError((error && (error.msg || error.message)) || '另存副本失败')
        }
      } finally {
        this.conflictCopying = false
      }
    },
    async discardLocalConflict() {
      if (!this.unresolvedFieldConflicts) return
      try {
        await this.$confirm(
          '采用服务器版本后，你在冲突字段上的本地修改将被放弃。是否继续？',
          '采用服务器版本',
          {
            confirmButtonText: '确认采用',
            cancelButtonText: '返回核对',
            type: 'warning'
          }
        )
        const fresh = await this.fetchSnapshot(this.templateId)
        this.journey = hydrateJourney(fresh.aggregate)
        this.draftContext = fresh.context
        this.conflictVisible = false
        this.editRevision = 0
        this.$modal.msgSuccess('已采用服务器最新版本')
      } catch (error) {
        if (error !== 'cancel' && error !== 'close') {
          this.$modal.msgError((error && (error.msg || error.message)) || '刷新服务器配置失败')
        }
      }
    },
    repair(issue) {
      const stepCode = issue && issue.stepCode
      if (STEP_CODES.includes(stepCode)) this.activeStep = stepCode
      if (issue && issue.code === 'TODO_JOURNEY_EVENT_SCHEMA_REQUIRED') {
        const event = this.journey && this.journey.definition && this.journey.definition.event
        const resource = ((this.journey && this.journey.resources && this.journey.resources.events) || []).find(item =>
          item.eventType === (event && event.eventType) &&
          Number(item.payloadVersion) === Number(event && event.payloadVersion)
        ) || {}
        this.openResourceRepair({
          type: 'EVENT',
          resourceId: resource.eventCatalogId,
          eventType: event && event.eventType,
          payloadVersion: event && event.payloadVersion,
          businessType: this.journey.template.businessType,
          returnStep: stepCode || 'EVENT',
          focusField: issue.fieldPath === 'event.condition' ? null : 'payloadSchema'
        })
      }
    },
    openResourceRepair(request) {
      this.resourceRepair = { open: true, request: createRepairRequest(request) }
    },
    async completeResourceRepair(payload) {
      const request = (payload && payload.request) || this.resourceRepair.request
      const result = completeRepair(request, this.journey, payload && payload.resourceId)
      this.resourceRepair.open = false
      try {
        await this.refreshResourceSnapshot(result.reloadResource)
        this.activeStep = STEP_CODES.includes(result.returnStep) ? result.returnStep : this.activeStep
        this.$nextTick(() => {
          const editor = this.$refs.activeEditor
          if (editor && editor.focusField) editor.focusField(result.focusField)
        })
        this.$modal.msgSuccess('资源已刷新，模板草稿和当前步骤已保留')
      } catch (error) {
        this.$modal.msgError((error && (error.msg || error.message)) || '资源已保存，但刷新失败，请稍后重试')
      }
    },
    async refreshResourceSnapshot(type, silent) {
      if (!this.journey) return
      const businessType = this.journey.template.businessType
      const current = this.journey.resources || {}
      const tasks = {}
      if (type === 'ALL' || type === 'EVENT') tasks.events = listTemplateEventCatalog()
      if (type === 'ALL' || type === 'EVENT' || type === 'FIELD') tasks.fields = listFieldResources({ businessType })
      if (type === 'ALL' || type === 'MATERIAL') tasks.materials = listMaterialResources({ businessType })
      if (type === 'ALL' || type === 'DOD_RECIPE') tasks.recipes = listDodRecipeResources({ businessType })
      if (type === 'ALL') tasks.validators = listValidatorResources({ businessType })
      try {
        const names = Object.keys(tasks)
        const responses = await Promise.all(names.map(name => tasks[name]))
        const resources = { ...current }
        names.forEach((name, index) => {
          const values = responses[index].data || []
          if (name === 'events') {
            resources.events = values.map(event => {
              const eventType = event.eventType || event.event_type
              const payloadVersion = Number(event.payloadVersion || event.payload_version || 1)
              const hasFields = (resources.fields || current.fields || []).some(field =>
                (field.sourceEvents || []).includes(eventType)
              )
              return {
                eventType,
                eventName: event.eventName || event.event_name || eventType,
                payloadVersion,
                businessObjectType: event.businessObjectType || event.business_object_type,
                sourceModule: event.sourceModule || event.source_module || '业务系统',
                schemaStatus: hasFields ? 'READY' : 'INCOMPLETE',
                status: event.status || 'ACTIVE'
              }
            })
          } else resources[name] = values
        })
        if (resources.events) {
          resources.events = resources.events.map(event => ({
            ...event,
            schemaStatus: (resources.fields || []).some(field =>
              (field.sourceEvents || []).includes(event.eventType)
            ) ? 'READY' : event.schemaStatus
          }))
        }
        this.journey = { ...this.journey, resources }
        this.resourceRevision += 1
      } catch (error) {
        if (!silent) throw error
      }
    },
    async runPrimaryAction() {
      if (!this.journey) return
      if (this.journey.dirty && !(await this.saveNow({ automatic: false }))) return
      const blocker = this.activeIssues.find(issue => String(issue.severity).toUpperCase() === 'BLOCKER')
      if (blocker) {
        this.$modal.msgWarning(blocker.message || '请先处理当前步骤的阻塞问题')
        return
      }
      const action = derivePrimaryAction({ ...this.journey, activeStepCode: this.activeStep })
      if (action.code === 'CONTINUE_CONFIGURATION' && this.activeIndex < STEP_CODES.length - 1) {
        this.activeStep = STEP_CODES[this.activeIndex + 1]
        return
      }
      if (action.stepCode && action.stepCode !== this.activeStep) {
        this.activeStep = action.stepCode
        return
      }
      this.$modal.msgInfo('模拟与发布操作将在当前步骤的专用面板中完成')
    },
    goBack() {
      this.$router.push('/todo-engine/todo-template')
    }
  }
}
</script>

<style scoped lang="scss">
.journey-page {
  --todo-navy: #0B2A55;
  --todo-gold: #C89A3D;
  --todo-surface: #FFFFFF;
  --todo-bg: #F4F7FA;
  --todo-border: #D9E1EA;
  min-height: calc(100vh - 84px);
  padding-bottom: 96px;
  background: var(--todo-bg);
}

.journey-page__header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 20px;

  h1 {
    margin: 0;
    font-size: 24px;
    line-height: 36px;
    color: var(--todo-navy);
  }

  p {
    margin: 6px 0 0;
    font-size: 14px;
    line-height: 22px;
    color: #65758A;
  }
}

.journey-page__back {
  padding: 0;
  margin-bottom: 8px;
  color: #586779;
}

.journey-page__title {
  display: flex;
  gap: 12px;
  align-items: center;
}

.journey-page__identity {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  font-size: 12px;
  line-height: 20px;
  color: #65758A;

  span {
    color: #34465B;
  }
}

.journey-page__error {
  margin-bottom: 16px;
}

.journey-shell__notice {
  display: flex;
  gap: 10px;
  align-items: center;
  min-height: 44px;
  padding: 8px 16px;
  margin-top: 16px;
  font-size: 13px;
  line-height: 20px;
  color: #7A5210;
  background: #FFF7E8;
  border: 1px solid #E9CB8C;
  border-radius: 8px;

  span {
    flex: 1;
  }
}

.journey-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 352px;
  gap: 20px;
  align-items: start;
  margin-top: 20px;
}

.journey-editor {
  min-height: 520px;
  padding: 24px;
  background: var(--todo-surface);
  border: 1px solid var(--todo-border);
  border-radius: 8px;
}

.journey-aside {
  display: grid;
  gap: 16px;
}

.journey-footer {
  position: fixed;
  right: 24px;
  bottom: 0;
  left: 224px;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 72px;
  padding: 12px 24px;
  background: #FFFFFF;
  border-top: 1px solid #D9E1EA;
}

.journey-footer__actions {
  display: flex;
  gap: 8px;
}

::v-deep .journey-editor-placeholder {
  display: flex;
  min-height: 450px;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  max-width: 560px;
  margin: 0 auto;

  h2 {
    margin: 6px 0 10px;
    font-size: 22px;
    line-height: 34px;
    color: #0B2A55;
  }

  p {
    margin: 0;
    font-size: 14px;
    line-height: 24px;
    color: #65758A;
  }
}

::v-deep .journey-editor-placeholder__eyebrow {
  font-size: 12px;
  line-height: 18px;
  color: #C89A3D;
}

::v-deep .journey-editor-placeholder__state {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  padding: 8px 12px;
  margin-top: 20px;
  font-size: 13px;
  color: #34465B;
  background: #F4F7FA;
  border-radius: 8px;
}

@media (max-width: 960px) {
  .journey-body {
    grid-template-columns: 1fr;
  }

  .journey-aside {
    grid-template-columns: 1fr 1fr;
  }

  .journey-footer {
    left: 24px;
  }
}

@media (max-width: 640px) {
  .journey-page {
    padding: 14px 12px 116px;
  }

  .journey-page__header,
  .journey-footer {
    align-items: stretch;
    flex-direction: column;
  }

  .journey-page__identity {
    display: none;
  }

  .journey-page__title {
    align-items: flex-start;
    flex-direction: column;
    gap: 6px;
  }

  .journey-body,
  .journey-aside {
    grid-template-columns: 1fr;
  }

  .journey-editor {
    min-height: 380px;
    padding: 18px;
  }

  .journey-footer {
    right: 0;
    left: 0;
    gap: 8px;
    min-height: 104px;
    padding: 10px 12px;
  }

  .journey-footer__actions {
    justify-content: flex-end;
  }
}
</style>
