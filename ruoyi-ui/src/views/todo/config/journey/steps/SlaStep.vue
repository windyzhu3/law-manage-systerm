<template>
  <section class="journey-step sla-step">
    <header class="journey-step__header">
      <span class="journey-step__eyebrow">第五步 · 办理时限</span>
      <h2>员工需要在多长时间内办完？</h2>
      <p>用业务语言设置时长与工作日历，服务端会按真实工作时间自动计算 80% / 100% / 150% 管理节点。</p>
    </header>

    <section v-if="!scheduleMode" class="sla-sentence">
      <strong>办理时长</strong>
      <span>这张待办应在</span>
      <el-input-number ref="durationInput" v-model="form.durationValue" :disabled="readonly" :min="1" :max="999" controls-position="right" @change="commitDuration" />
      <el-select v-model="form.durationUnit" :disabled="readonly" @change="commitDuration">
        <el-option label="工作分钟内" value="MINUTE" />
        <el-option label="工作小时内" value="HOUR" />
        <el-option label="工作日内" value="DAY" />
      </el-select>
      <span>完成，按</span>
      <el-select v-model="form.calendarCode" :disabled="readonly" filterable placeholder="选择工作日历" @change="commitDuration">
        <el-option v-for="calendar in calendars" :key="calendarCode(calendar)" :label="calendarName(calendar)" :value="calendarCode(calendar)" />
      </el-select>
      <span>计算。</span>
    </section>

    <section v-else class="sla-retry-timeline" aria-label="重试窗口时间轴">
      <header>
        <div><h3>重试窗口时间轴</h3><p>系统已按 T0、T+1、T+2 预设开始时间、截止时间和最大尝试次数。</p></div>
        <el-tag size="small" type="info">系统预设</el-tag>
      </header>
      <div class="sla-retry-timeline__track">
        <article v-for="group in retryWindowGroups" :key="group.dayOffset">
          <span>{{ group.label }}</span>
          <strong>{{ group.summary }}</strong>
          <small>{{ group.detail }}</small>
        </article>
      </div>
    </section>

    <el-alert
      v-if="scheduleSummary && !scheduleMode"
      class="sla-schedule-summary"
      :title="scheduleSummary.title"
      :description="scheduleSummary.description"
      type="info"
      :closable="false"
      show-icon
    />

    <div class="sla-runtime-grid">
      <section>
        <i class="el-icon-video-play" />
        <div><h3>待办创建时起算</h3><p>当前运行时统一从待办创建时间开始计算。</p></div>
      </section>
      <section>
        <i class="el-icon-video-pause" />
        <div><h3>员工申请暂停或恢复</h3><p>暂停由实际待办操作控制，并完整记录审计轨迹。</p></div>
      </section>
    </div>

    <section v-if="!scheduleMode" class="sla-action-card">
      <header><div><h3>固定提醒与升级动作</h3><p>这些动作与当前运行时一致，避免配置出无法执行的策略。</p></div></header>
      <div class="sla-action-grid">
        <div><strong>80% 提醒</strong><span>提醒当前负责人尽快办理</span></div>
        <div><strong>100% 超时</strong><span>标记超时并提醒当前负责人</span></div>
        <div><strong>150% 升级</strong><span>标记升级并同时通知负责人和主管</span></div>
      </div>
    </section>

    <sla-timeline-preview v-if="!scheduleMode" v-loading="calculating" :points="timeline" />

    <el-alert
      v-if="blocker"
      class="sla-step__blocker"
      title="办理时限配置会阻塞发布"
      :description="blocker.message"
      type="error"
      :closable="false"
      show-icon
    >
      <el-button slot="default" type="text" @click="repairBlocker">
        {{ blocker.code === 'TODO_JOURNEY_SLA_CALENDAR_REQUIRED' ? '修复工作日历' : '检查办理时长' }}
      </el-button>
    </el-alert>
  </section>
</template>

<script>
import SlaTimelinePreview from '../components/SlaTimelinePreview'
import { buildSlaPatch, buildSlaTimeline, slaRepairBlocker } from '../journey-step-model'
import { previewTodoJourneySla } from '@/api/todo-config'

export default {
  name: 'SlaStep',
  components: { SlaTimelinePreview },
  props: {
    value: { type: Object, default: () => ({}) },
    resources: { type: Object, default: () => ({}) },
    businessType: { type: String, default: '' },
    readonly: Boolean
  },
  data() {
    return {
      form: {
        durationValue: 8,
        durationUnit: 'HOUR',
        calendarCode: '',
        governedMinutes: null
      },
      syncing: false,
      calculation: {},
      calculationError: '',
      calculating: false,
      calculationSequence: 0
    }
  },
  computed: {
    config() { return this.value.config || {} },
    calendars() { return this.resources.calendars || [] },
    scheduleMode() {
      return Boolean(this.config.schedule && Array.isArray(this.config.schedule.windows) &&
        this.config.schedule.windows.length)
    },
    retryWindowGroups() {
      const windows = (this.config.schedule && this.config.schedule.windows) || []
      const groups = new Map()
      windows.forEach(window => {
        const dayOffset = Number(window.dayOffset) || 0
        if (!groups.has(dayOffset)) groups.set(dayOffset, [])
        groups.get(dayOffset).push(window)
      })
      return Array.from(groups.entries()).sort((left, right) => left[0] - right[0]).map(([dayOffset, rows]) => {
        const attempts = rows.reduce((total, row) => total + (Number(row.maxAttempts) || 0), 0)
        return {
          dayOffset,
          label: dayOffset === 0 ? 'T0' : `T+${dayOffset}`,
          summary: `${rows.length} 个办理窗口 · 最多 ${attempts} 次尝试`,
          detail: rows.map(row => this.retryWindowLabel(row)).join('；')
        }
      })
    },
    currentPatch() { return buildSlaPatch(this.form, this.value) },
    timeline() { return buildSlaTimeline(this.currentPatch.config, this.calculation) },
    blocker() {
      if (this.scheduleMode) {
        const calendarReady = this.calendars.some(calendar =>
          this.calendarCode(calendar) === String(this.config.calendarCode || '')
        )
        return calendarReady ? null : {
          code: 'TODO_JOURNEY_SLA_CALENDAR_REQUIRED',
          severity: 'BLOCKER',
          message: '重试窗口使用的工作日历不存在或已停用'
        }
      }
      return slaRepairBlocker(this.currentPatch.config, this.calendars, this.timeline)
    },
    scheduleSummary() {
      const code = String(this.config.schedulePurpose || this.config.scheduleType || '').toUpperCase()
      if (this.scheduleMode || code.includes('RETRY') || Array.isArray(this.config.retryWindows)) {
        const windows = this.retryWindowGroups.map(item => item.label)
        return {
          title: '重试窗口时间轴',
          description: `${windows.join(' → ') || 'T0 → T+1 → T+2'}；系统按窗口开始、截止和最大尝试次数推进。`
        }
      }
      if (String(this.config.effectKind || '').toUpperCase() === 'SCHEDULE_SELF' ||
          Number(this.config.minutes) === 7200) {
        return { title: '每 5 天循环', description: '记录实质进展后，以进展时间为锚点开启下一轮待办。' }
      }
      return null
    }
  },
  watch: {
    value: {
      immediate: true,
      deep: true,
      handler() { if (!this.syncing) this.hydrate() }
    },
    resources: {
      deep: true,
      handler() { if (!this.scheduleMode) this.refreshCalculation({ commitOnSuccess: false }) }
    },
    blocker: {
      immediate: true,
      handler(value) {
        this.$emit('issue-change', value
          ? { ...value, stepCode: 'SLA', fieldPath: value.code === 'TODO_JOURNEY_SLA_CALENDAR_REQUIRED' ? 'sla.calendarCode' : 'sla.minutes' }
          : null)
      }
    }
  },
  beforeDestroy() { this.calculationSequence += 1 },
  methods: {
    hydrate() {
      const config = this.config
      const minutes = Number(config.minutes)
      let durationValue = Number(config.durationValue)
      let durationUnit = config.durationUnit
      if (!(durationValue > 0)) {
        durationUnit = minutes && minutes % 60 === 0 ? 'HOUR' : 'MINUTE'
        durationValue = durationUnit === 'HOUR' ? minutes / 60 : (minutes || 8)
      }
      this.form = {
        durationValue,
        durationUnit: durationUnit || 'HOUR',
        calendarCode: config.calendarCode || '',
        governedMinutes: Number(config.minutes) || null
      }
      if (this.scheduleMode) {
        this.calculation = {}
        this.calculating = false
        return
      }
      this.refreshCalculation({ commitOnSuccess: false })
    },
    emitPatch() {
      this.syncing = true
      this.$emit('patch', { sla: buildSlaPatch(this.form, this.value) })
      this.$nextTick(() => { this.syncing = false })
    },
    commitDuration() {
      this.form.governedMinutes = null
      if (this.isDayDuration()) {
        this.refreshCalculation({ commitOnSuccess: true })
        return
      }
      this.emitPatch()
      this.refreshCalculation({ commitOnSuccess: false })
    },
    isDayDuration() {
      return String(this.form.durationUnit || '').toUpperCase() === 'DAY'
    },
    async refreshCalculation({ commitOnSuccess = false } = {}) {
      if (this.scheduleMode) return
      const patch = buildSlaPatch(this.form, this.value)
      const durationValue = Number(patch.config.durationValue)
      const durationUnit = patch.config.durationUnit
      const calendarCode = patch.config.calendarCode
      const sequence = ++this.calculationSequence
      this.calculation = {}
      this.calculationError = ''
      if (!(durationValue > 0) || !durationUnit || !calendarCode) {
        this.calculating = false
        return
      }
      this.calculating = true
      try {
        const response = await previewTodoJourneySla({
          calendarCode,
          durationValue,
          durationUnit,
          createdAt: this.localNow()
        })
        if (sequence !== this.calculationSequence) return
        this.calculation = response.data || {}
        const governedMinutes = Number(this.calculation.minutes)
        if (governedMinutes > 0) this.form.governedMinutes = governedMinutes
        if (commitOnSuccess) this.emitPatch()
      } catch (error) {
        if (sequence !== this.calculationSequence) return
        this.calculationError = (error && (error.msg || error.message)) || '服务端无法按当前工作日历计算时限'
      } finally {
        if (sequence === this.calculationSequence) this.calculating = false
      }
    },
    calendarCode(calendar) { return calendar.calendarCode || calendar.calendar_code || '' },
    calendarName(calendar) { return calendar.calendarName || calendar.calendar_name || this.calendarCode(calendar) },
    retryWindowLabel(window) {
      if (window.startTime || window.endTime) {
        return `${window.startTime || '当日开始'}-${window.endTime || '当日结束'}（${Number(window.maxAttempts) || 0}次）`
      }
      return `开始后 ${Number(window.durationMinutes) || 0} 分钟（${Number(window.maxAttempts) || 0}次）`
    },
    localNow() {
      const now = new Date()
      return new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 19)
    },
    repairBlocker() {
      if (this.blocker.code === 'TODO_JOURNEY_SLA_CALENDAR_REQUIRED') {
        this.$emit('repair-resource', {
          type: 'CALENDAR',
          businessType: this.businessType,
          returnStep: 'SLA',
          focusField: 'calendarCode'
        })
        return
      }
      this.focusField('durationValue')
    },
    focusField(field) {
      if (field !== 'durationValue') return
      this.$nextTick(() => {
        const input = this.$refs.durationInput && this.$refs.durationInput.$el.querySelector('input')
        if (input) {
          input.focus()
          input.scrollIntoView({ behavior: 'smooth', block: 'center' })
        }
      })
    }
  }
}
</script>

<style scoped lang="scss">
.journey-step__header {
  margin-bottom: 22px;
  h2 { margin: 5px 0 8px; font-size: 22px; line-height: 32px; color: #0B2A55; }
  p { margin: 0; font-size: 14px; line-height: 23px; color: #65758A; }
}
.journey-step__eyebrow { font-size: 12px; color: #C89A3D; }

.sla-sentence {
  display: flex;
  flex-wrap: wrap;
  gap: 9px;
  align-items: center;
  padding: 18px;
  font-size: 15px;
  color: #34465B;
  background: #FFF9EC;
  border: 1px solid #E7CD98;
  border-radius: 8px;

  .el-input-number { width: 120px; }
  .el-select { width: 160px; }
  .el-select:nth-of-type(2) { width: 210px; }
}

.sla-runtime-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin: 14px 0;

  section {
    display: flex;
    gap: 10px;
    padding: 16px;
    background: #FFFFFF;
    border: 1px solid #D9E1EA;
    border-radius: 8px;
  }

  i { margin-top: 2px; font-size: 20px; color: #C89A3D; }
  h3 { margin: 0; font-size: 15px; color: #0B2A55; }
  p { margin: 4px 0 0; font-size: 12px; color: #65758A; }
}

.sla-action-card {
  padding: 16px;
  margin-bottom: 14px;
  background: #F7F9FC;
  border: 1px solid #D9E1EA;
  border-radius: 8px;

  h3 { margin: 0; color: #0B2A55; }
  p { margin: 4px 0 12px; font-size: 12px; color: #65758A; }
}

.sla-action-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;

  > div {
    padding: 12px;
    background: #FFFFFF;
    border-radius: 8px;
  }

  strong { display: block; margin-bottom: 8px; color: #0B2A55; }
  span { font-size: 12px; line-height: 19px; color: #53667C; }
}

.sla-step__blocker { margin-top: 14px; }

.sla-retry-timeline {
  padding: 18px;
  background: #F7F9FC;
  border: 1px solid #D9E1EA;
  border-radius: 8px;

  > header { display: flex; justify-content: space-between; gap: 16px; }
  h3 { margin: 0; color: #0B2A55; }
  p { margin: 4px 0 0; font-size: 12px; color: #65758A; }
}

.sla-retry-timeline__track {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;

  article { padding: 14px; background: #FFFFFF; border-left: 3px solid #C89A3D; border-radius: 8px; }
  span,
  strong,
  small { display: block; }
  span { font-size: 12px; color: #C27D14; }
  strong { margin: 5px 0; color: #0B2A55; }
  small { line-height: 18px; color: #66758A; }
}

@media (max-width: 760px) {
  .sla-runtime-grid,
  .sla-action-grid,
  .sla-retry-timeline__track { grid-template-columns: 1fr; }
}
</style>
