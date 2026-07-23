<template>
  <section class="journey-step sla-step">
    <header class="journey-step__header">
      <span class="journey-step__eyebrow">第五步 · 办理时限</span>
      <h2>员工需要在多长时间内办完？</h2>
      <p>用业务语言设置时长与工作日历，服务端会按真实工作时间自动计算 80% / 100% / 150% 管理节点。</p>
    </header>

    <section class="sla-sentence">
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

    <section class="sla-action-card">
      <header><div><h3>固定提醒与升级动作</h3><p>这些动作与当前运行时一致，避免配置出无法执行的策略。</p></div></header>
      <div class="sla-action-grid">
        <div><strong>80% 提醒</strong><span>提醒当前负责人尽快办理</span></div>
        <div><strong>100% 超时</strong><span>标记超时并提醒当前负责人</span></div>
        <div><strong>150% 升级</strong><span>标记升级并同时通知负责人和主管</span></div>
      </div>
    </section>

    <sla-timeline-preview v-loading="calculating" :points="timeline" />

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
    currentPatch() { return buildSlaPatch(this.form, this.value) },
    timeline() { return buildSlaTimeline(this.currentPatch.config, this.calculation) },
    blocker() { return slaRepairBlocker(this.currentPatch.config, this.calendars, this.timeline) }
  },
  watch: {
    value: {
      immediate: true,
      deep: true,
      handler() { if (!this.syncing) this.hydrate() }
    },
    resources: {
      deep: true,
      handler() { this.refreshCalculation({ commitOnSuccess: false }) }
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
      this.refreshCalculation({ commitOnSuccess: false })
    },
    emitPatch() {
      this.syncing = true
      this.$emit('change', buildSlaPatch(this.form, this.value))
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

@media (max-width: 760px) {
  .sla-runtime-grid,
  .sla-action-grid { grid-template-columns: 1fr; }
}
</style>
