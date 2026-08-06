<template>
  <section ref="effectEditor" class="business-routing" aria-label="完成后下一步" tabindex="-1">
    <header>
      <div>
        <h3>完成后下一步</h3>
        <p v-if="typedMode">
          选择员工提交的“{{ outcomeSet.resultFieldName }}”，系统会自动生成判断条件和后续待办。
        </p>
        <p v-else>按业务结果从上到下判断，命中后进入下一张待办或结束当前流程。</p>
      </div>
      <div v-if="!readonly" class="business-routing__actions">
        <el-button
          v-if="typedMode && outcomeSet.recommendationCode"
          size="small"
          type="primary"
          icon="el-icon-magic-stick"
          @click="applyRecommendation"
        >
          应用首联推荐路由
        </el-button>
        <el-button
          v-if="!typedMode"
          size="small"
          icon="el-icon-plus"
          @click="addOutcome"
        >
          添加业务结果
        </el-button>
      </div>
    </header>

    <el-alert
      v-if="typedMode"
      class="business-routing__governed"
      type="info"
      :closable="false"
      show-icon
    >
      <template slot="title">
        业务结果来自“{{ outcomeSet.resultFieldName }}”字典，共 {{ outcomeOptions.length }} 项；每项必须且只能配置一次。
      </template>
    </el-alert>

    <div v-if="!typedMode" class="business-routing__mode">
      <span>执行方式</span>
      <el-radio-group v-model="mode" :disabled="readonly" size="small" @change="modeChanged">
        <el-radio-button label="SEQUENTIAL">按结果选择一步</el-radio-button>
        <el-radio-button label="PARALLEL">并行办理</el-radio-button>
      </el-radio-group>
      <el-select v-if="mode === 'PARALLEL'" v-model="joinMode" :disabled="readonly" @change="commit">
        <el-option label="全部完成后汇合" value="ALL" />
        <el-option label="任一完成后汇合" value="ANY" />
      </el-select>
    </div>

    <div v-if="draftRows.length" class="routing-outcomes">
      <article v-for="(row, index) in draftRows" :key="row.id" :ref="`outcomeCard-${index}`" class="routing-outcome" tabindex="-1">
        <div class="routing-outcome__order">
          <span>{{ index + 1 }}</span>
          <el-button-group v-if="!readonly && !typedMode">
            <el-button type="text" icon="el-icon-top" :disabled="index === 0" @click="move(index, -1)" />
            <el-button type="text" icon="el-icon-bottom" :disabled="index === draftRows.length - 1" @click="move(index, 1)" />
          </el-button-group>
        </div>

        <div class="routing-outcome__body">
          <div class="routing-outcome__main">
            <el-select
              v-if="typedMode"
              class="routing-outcome__result"
              :ref="`businessOutcomes-${index}`"
              v-model="row.resultValue"
              :disabled="readonly"
              placeholder="选择业务结果"
              @change="outcomeChanged(row, index)"
            >
              <el-option
                v-for="option in availableOutcomeOptions(row)"
                :key="option.value"
                :label="option.label"
                :value="String(option.value)"
              />
            </el-select>
            <el-input
              v-else
              class="routing-outcome__result"
              :ref="`businessOutcomes-${index}`"
              v-model="row.label"
              :disabled="readonly"
              placeholder="填写业务结果，例如：审批通过"
              @change="commit"
            >
              <template slot="prepend">业务结果</template>
            </el-input>

            <div
              v-if="typedMode"
              class="routing-effect-card"
              :class="`is-${effectFor(row).tone}`"
            >
              <i :class="effectIcon(row)" />
              <div>
                <strong>{{ effectFor(row).label }}</strong>
                <span>{{ effectExplanation(row) }}</span>
              </div>
            </div>
            <el-select
              v-else
              :ref="`effectKind-${index}`"
              v-model="row.resultType"
              :disabled="readonly || mode === 'PARALLEL'"
              @change="resultTypeChanged(row)"
            >
              <el-option label="进入下一张待办" value="NEXT" />
              <el-option label="结束" value="END" />
            </el-select>

            <div
              v-if="effectFor(row).needsTarget"
              class="routing-outcome__target-wrap"
            >
              <span>下一步待办</span>
              <el-select
                class="routing-outcome__target"
                :ref="`targetVersionId-${index}`"
                v-model="row.targetVersionId"
                :disabled="readonly"
                filterable
                placeholder="选择下一张待办"
                @change="targetChanged(row)"
              >
                <el-option
                  v-for="target in displayRoutingTargets"
                  :key="versionId(target)"
                  :label="templateName(target)"
                  :value="versionId(target)"
                >
                  <span>{{ templateName(target) }}</span>
                  <small>{{ target.templateCode || target.template_code }} · {{ targetVersionLabel(target) }}</small>
                </el-option>
              </el-select>
            </div>
            <div v-else-if="!typedMode" class="routing-outcome__end">
              <i class="el-icon-circle-close" />流程在此结束，不再创建后续待办
            </div>
          </div>

          <p v-if="typedMode" class="routing-outcome__sentence">
            <i class="el-icon-right" />{{ resultSentence(row) }}
          </p>

          <div v-else-if="mode === 'SEQUENTIAL'" class="routing-outcome__condition">
            <el-checkbox v-model="row.default" :disabled="readonly" @change="defaultChanged(index)">
              其他结果都不匹配时走此分支
            </el-checkbox>
            <el-collapse v-if="!row.default">
              <el-collapse-item title="设置此业务结果的判断条件" name="condition">
                <typed-condition-builder
                  :value="row.condition || {}"
                  :fields="fields"
                  :readonly="readonly"
                  @input="conditionChanged(row, $event)"
                />
              </el-collapse-item>
            </el-collapse>
          </div>
        </div>

        <el-button
          v-if="!readonly && !typedMode"
          type="text"
          class="is-danger"
          @click="removeOutcome(index)"
        >
          删除
        </el-button>
      </article>
    </div>
    <el-empty
      v-else
      :description="typedMode ? '点击“应用首联推荐路由”自动生成完整分支' : '尚未设置完成后的业务结果'"
      :image-size="68"
    />

    <el-alert
      v-if="blocker"
      title="请完善后续路由"
      :description="blocker.message"
      type="error"
      :closable="false"
      show-icon
    />

    <el-alert
      v-if="mode === 'PARALLEL' && !typedMode"
      title="并行办理说明"
      :description="joinMode === 'ALL' ? '系统会同时创建以上待办，全部完成后再继续。' : '系统会同时创建以上待办，任一完成后即可汇合继续。'"
      type="info"
      :closable="false"
      show-icon
    />
  </section>
</template>

<script>
import TypedConditionBuilder from './TypedConditionBuilder'
import {
  emptyConditionDocument,
  materializeOutcomeRouting,
  routingDraftBlocker
} from '../journey-step-model'
import { effectKind, effectPresentation, routeTargetsFor } from '../business-effect-model'

const clone = value => JSON.parse(JSON.stringify(value == null ? [] : value))

export default {
  name: 'BusinessRoutingEditor',
  components: { TypedConditionBuilder },
  props: {
    rows: { type: Array, default: () => [] },
    options: { type: Object, default: () => ({}) },
    routingTargets: { type: Array, default: () => [] },
    fields: { type: Array, default: () => [] },
    outcomeSet: { type: Object, default: () => ({}) },
    currentVersionId: [Number, String],
    businessType: String,
    readonly: Boolean
  },
  data() {
    return {
      draftRows: [],
      mode: 'SEQUENTIAL',
      joinMode: 'ALL',
      syncing: false
    }
  },
  computed: {
    outcomeOptions() {
      return Array.isArray(this.outcomeSet.options) ? this.outcomeSet.options : []
    },
    typedMode() {
      return Boolean(this.outcomeSet.resultField && this.outcomeOptions.length)
    },
    filteredRoutingTargets() {
      return routeTargetsFor(this.businessType, this.routingTargets)
    },
    displayRoutingTargets() {
      const targets = this.filteredRoutingTargets.slice()
      const known = new Set(targets.map(target => this.versionId(target)))
      this.draftRows.forEach(row => {
        const versionId = Number(row.targetVersionId)
        if (!this.effectFor(row).needsTarget || versionId <= 0 || known.has(versionId)) return
        targets.push({
          templateCode: row.targetTemplateCode || '',
          templateName: this.historicalTargetName(row.targetTemplateCode, versionId),
          businessType: this.businessType,
          versionId,
          versionNo: null,
          historicalReference: true
        })
        known.add(versionId)
      })
      return targets
    },
    blocker() {
      return routingDraftBlocker(this.draftRows, {
        mode: this.mode,
        joinMode: this.joinMode,
        outcomeSet: this.outcomeSet,
        routingTargets: this.filteredRoutingTargets,
        businessType: this.businessType,
        currentVersionId: this.currentVersionId
      })
    }
  },
  watch: {
    rows: { immediate: true, deep: true, handler() { if (!this.syncing) this.hydrate() } },
    options: { immediate: true, deep: true, handler() { if (!this.syncing) this.hydrateSettings() } },
    outcomeSet: { deep: true, handler() { if (!this.syncing) this.hydrate() } },
    blocker: {
      immediate: true,
      handler(value) {
        this.$emit('issue-change', value
          ? { ...value, stepCode: 'ROUTING', fieldPath: 'routing.businessOutcomes' }
          : null)
      }
    }
  },
  methods: {
    hydrate() {
      this.draftRows = clone(this.rows).map((row, index) => {
        const resultValue = row.resultValue || this.conditionResult(row.condition)
        const option = this.outcomeOptions.find(item => String(item.value) === String(resultValue))
        return {
          id: row.id || `result_${index + 1}`,
          label: row.label || '',
          resultField: row.resultField || (option && this.outcomeSet.resultField) || null,
          resultValue: resultValue == null ? null : String(resultValue),
          resultLabel: row.resultLabel || (option && option.label) || null,
          effectKind: effectKind(row.effectKind ? row : (option || row)),
          resultType: effectKind(row.effectKind ? row : (option || row)) === 'NEXT_TEMPLATE' ? 'NEXT' : 'END',
          targetVersionId: Number(row.targetVersionId) || null,
          targetTemplateCode: row.targetTemplateCode || (option && option.targetTemplateCode) || null,
          businessAction: row.businessAction,
          default: row.default === true,
          condition: clone(row.condition || {})
        }
      })
    },
    hydrateSettings() {
      this.mode = this.typedMode ? 'SEQUENTIAL' : (this.options.mode || 'SEQUENTIAL')
      this.joinMode = this.options.joinMode || 'ALL'
    },
    conditionResult(condition) {
      const root = condition && condition.$expression && condition.$expression.root
      const predicate = root && Array.isArray(root.conditions) && root.conditions[0]
      return predicate && predicate.operator === 'EQ' ? predicate.value : null
    },
    applyRecommendation() {
      const patch = materializeOutcomeRouting(
        this.outcomeSet,
        this.filteredRoutingTargets,
        this.currentVersionId,
        { config: {} }
      )
      this.mode = 'SEQUENTIAL'
      this.joinMode = 'ALL'
      this.draftRows = clone(patch.config.businessOutcomes || [])
      this.commit()
    },
    availableOutcomeOptions(row) {
      const selected = new Set(this.draftRows
        .filter(item => item !== row && item.resultValue)
        .map(item => String(item.resultValue)))
      return this.outcomeOptions.filter(option =>
        String(option.value) === String(row.resultValue) || !selected.has(String(option.value))
      )
    },
    addOutcome() {
      const id = `result_${Date.now()}`
      const target = this.filteredRoutingTargets[0]
      this.draftRows.push({
        id,
        label: '',
        effectKind: 'NEXT_TEMPLATE',
        resultType: 'NEXT',
        targetVersionId: target ? this.versionId(target) : null,
        default: this.mode === 'SEQUENTIAL' && this.draftRows.length === 0,
        condition: emptyConditionDocument()
      })
      this.commit()
    },
    outcomeChanged(row, index) {
      const recommended = materializeOutcomeRouting(
        { ...this.outcomeSet, options: this.outcomeOptions.filter(option => String(option.value) === String(row.resultValue)) },
        this.filteredRoutingTargets,
        this.currentVersionId,
        { config: {} }
      ).config.businessOutcomes[0]
      if (recommended) {
        Object.assign(row, recommended, {
          id: row.id || recommended.id || `result_${index + 1}`,
          default: index === this.draftRows.length - 1
        })
      }
      this.commit()
    },
    targetChanged(row) {
      const target = this.displayRoutingTargets.find(item => this.versionId(item) === Number(row.targetVersionId))
      row.targetTemplateCode = target ? (target.templateCode || target.template_code) : null
      this.commit()
    },
    resultSentence(row) {
      const result = row.resultLabel || row.resultValue || '未选择结果'
      const presentation = this.effectFor(row)
      const target = presentation.needsTarget
        ? `创建“${this.templateNameByVersion(row.targetVersionId)}”待办`
        : presentation.label
      return `当“${this.outcomeSet.resultFieldName || row.resultField || '业务结果'}”为“${result}”时，${target}`
    },
    effectFor(row) { return effectPresentation(row) },
    effectIcon(row) {
      return {
        NEXT_TEMPLATE: 'el-icon-right',
        END: 'el-icon-circle-close',
        RETAIN_CURRENT: 'el-icon-refresh-left',
        SCHEDULE_NEXT: 'el-icon-time',
        SCHEDULE_SELF: 'el-icon-refresh',
        EXPECTED_VALIDATION_FAILURE: 'el-icon-warning-outline'
      }[effectKind(row)] || 'el-icon-setting'
    },
    effectExplanation(row) {
      if (String(row.businessAction || '').toUpperCase() === 'START_RETRY') {
        return '当前首联待办结束，系统按重试策略建立后续联系计划'
      }
      if (this.effectFor(row).needsTarget) return '选择同一业务域的已发布待办'
      return '由系统按已治理的业务结果自动执行，无需选择虚假的下游模板'
    },
    removeOutcome(index) {
      this.draftRows.splice(index, 1)
      if (this.mode === 'SEQUENTIAL' && this.draftRows.length && !this.draftRows.some(row => row.default)) {
        this.draftRows[this.draftRows.length - 1].default = true
      }
      this.commit()
    },
    move(index, offset) {
      const target = index + offset
      if (target < 0 || target >= this.draftRows.length) return
      const row = this.draftRows.splice(index, 1)[0]
      this.draftRows.splice(target, 0, row)
      this.commit()
    },
    modeChanged() {
      if (this.mode === 'PARALLEL') {
        this.draftRows.forEach(row => {
          row.resultType = 'NEXT'
          row.effectKind = 'NEXT_TEMPLATE'
          row.default = false
        })
      } else if (this.draftRows.length) {
        this.draftRows[this.draftRows.length - 1].default = true
      }
      this.commit()
    },
    defaultChanged(index) {
      if (this.draftRows[index].default) {
        this.draftRows.forEach((row, rowIndex) => { if (rowIndex !== index) row.default = false })
      }
      this.commit()
    },
    resultTypeChanged(row) {
      if (row.resultType === 'END') row.targetVersionId = null
      else if (!row.targetVersionId && this.filteredRoutingTargets.length) row.targetVersionId = this.versionId(this.filteredRoutingTargets[0])
      row.effectKind = row.resultType === 'NEXT' ? 'NEXT_TEMPLATE' : 'END'
      this.commit()
    },
    conditionChanged(row, condition) {
      row.condition = clone(condition)
      this.commit()
    },
    commit() {
      this.syncing = true
      this.$emit('change', clone(this.draftRows), {
        mode: this.mode,
        joinMode: this.joinMode,
        outcomeSet: clone(this.outcomeSet),
        routingTargets: clone(this.filteredRoutingTargets),
        businessType: this.businessType
      })
      this.$nextTick(() => { this.syncing = false })
    },
    versionId(target) { return Number(target.versionId || target.version_id) },
    templateName(target) { return target.templateName || target.template_name || '未命名待办' },
    targetVersionLabel(target) {
      const versionNo = target.versionNo || target.version_no
      return versionNo ? `已发布 v${versionNo}` : '历史已发布版本'
    },
    historicalTargetName(templateCode, versionId) {
      const names = {
        'TD-001': '首联待办',
        'TD-002': '疑似无效主管复核',
        'TD-003': '无法联系重试',
        'TD-004': '5天实质进展'
      }
      return names[templateCode] || `历史已发布待办（版本 ${versionId}）`
    },
    templateNameByVersion(versionId) {
      const target = this.filteredRoutingTargets.find(item => this.versionId(item) === Number(versionId))
      return target ? this.templateName(target) : '尚未选择的后续待办'
    },
    routingFocusTarget(fieldPath, resourceKey) {
      const path = String(fieldPath || '')
      const match = path.match(/businessOutcomes(?:\[|\.)(\d+)/i)
      const index = match ? Number(match[1]) : 0
      const lower = path.toLowerCase()
      const key = String(resourceKey || '').toUpperCase()
      let control = 'businessOutcomes'
      if (key === 'ROUTING_TARGET' || lower.includes('target')) control = 'targetVersionId'
      else if (lower.includes('effect') || lower.includes('resulttype')) control = 'effectKind'
      else if (['businessOutcomes', 'effectKind', 'targetVersionId'].includes(path)) control = path
      return { control, index }
    },
    focusField(fieldPath, resourceKey) {
      const target = this.routingFocusTarget(fieldPath, resourceKey)
      this.$nextTick(() => {
        const named = this.$refs[`${target.control}-${target.index}`]
        const reference = Array.isArray(named) ? named[0] : named
        const fallbackNamed = this.$refs[`outcomeCard-${target.index}`]
        const fallback = Array.isArray(fallbackNamed) ? fallbackNamed[0] : fallbackNamed
        const resolved = reference || fallback || this.$refs.effectEditor
        const element = resolved && (resolved.$el || resolved)
        if (element && element.scrollIntoView) element.scrollIntoView({ behavior: 'smooth', block: 'center' })
        if (resolved && resolved.focus) resolved.focus()
        else if (element && element.focus) element.focus()
      })
    }
  }
}
</script>

<style scoped lang="scss">
.business-routing {
  > header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;

    h3 { margin: 0; font-size: 16px; color: #0B2A55; }
    p { margin: 4px 0 0; font-size: 12px; line-height: 20px; color: #65758A; }
  }

  > .el-alert { margin-top: 14px; }
}

.business-routing__actions { flex: 0 0 auto; }
.business-routing__governed { margin-bottom: 14px; }

.routing-effect-card {
  display: flex;
  gap: 9px;
  align-items: center;
  min-width: 0;
  padding: 10px 12px;
  color: #0B2A55;
  background: #F5F8FC;
  border: 1px solid #D9E1EA;
  border-radius: 8px;

  i { font-size: 18px; }
  strong,
  span { display: block; }
  span { margin-top: 2px; font-size: 11px; line-height: 17px; color: #66758A; }

  &.is-primary { color: #1D4E89; background: #EFF6FF; border-color: #B8D4F0; }
  &.is-success { color: #256B4A; background: #F0F8F4; border-color: #B7D7C8; }
  &.is-warning { color: #8A5A0A; background: #FFF9EC; border-color: #E7CD98; }
  &.is-danger { color: #9F2F2F; background: #FFF1F1; border-color: #E8B8B8; }
}

.business-routing__mode {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 12px;
  margin: 14px 0;
  font-size: 13px;
  color: #53667C;
  background: #F7F9FC;
  border-radius: 8px;

  .el-select { width: 180px; }
}

.routing-outcomes { display: grid; gap: 10px; }

.routing-outcome {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr) auto;
  gap: 12px;
  padding: 14px;
  background: #FFFFFF;
  border: 1px solid #D9E1EA;
  border-radius: 8px;

  .is-danger { align-self: start; color: #C43D3D; }
}

.routing-outcome__order {
  text-align: center;

  > span {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    margin: 0 auto 4px;
    font-weight: 700;
    color: #FFFFFF;
    background: #0B2A55;
    border-radius: 50%;
  }

  .el-button-group { display: flex; flex-direction: column; }
}

.routing-outcome__main {
  display: grid;
  grid-template-columns: minmax(180px, .8fr) minmax(260px, 1.2fr);
  gap: 12px;
  align-items: stretch;
  min-width: 0;
}

.routing-outcome__body,
.routing-outcome__main > * { min-width: 0; }

.routing-outcome__result,
.routing-outcome__target { width: 100%; }

.routing-outcome__target-wrap {
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr);
  grid-column: 1 / -1;
  gap: 10px;
  align-items: center;
  padding: 10px 12px;
  background: #F7F9FC;
  border: 1px solid #E0E6ED;
  border-radius: 8px;

  > span { font-size: 13px; font-weight: 600; color: #53667C; }
}

.routing-outcome__next-label {
  padding: 10px 12px;
  font-size: 13px;
  text-align: center;
  color: #53667C;
  background: #F7F9FC;
  border-radius: 6px;
}

.routing-outcome__sentence {
  margin: 10px 0 0;
  padding: 9px 12px;
  font-size: 13px;
  line-height: 20px;
  color: #0B2A55;
  background: #F3F7FC;
  border-left: 3px solid #C89A3D;
  border-radius: 4px;

  i { margin-right: 6px; color: #C89A3D; }
}

.routing-outcome__end {
  padding: 10px;
  font-size: 12px;
  color: #65758A;
  background: #F7F9FC;
  border-radius: 8px;

  i { margin-right: 5px; color: #C89A3D; }
}

.routing-outcome__condition {
  padding-top: 10px;

  ::v-deep .el-collapse-item__header { color: #53667C; }
}

::v-deep .el-select-dropdown__item small {
  float: right;
  margin-left: 18px;
  color: #8A98A8;
}

@media (max-width: 1280px) {
  .routing-outcome__main { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .routing-outcome__target-wrap { grid-column: 1 / -1; }
}

@media (max-width: 760px) {
  .business-routing > header {
    align-items: stretch;
    flex-direction: column;
  }
  .business-routing__actions .el-button { width: 100%; }
  .business-routing__mode { align-items: stretch; flex-direction: column; }
  .routing-outcome { grid-template-columns: 38px minmax(0, 1fr); }
  .routing-outcome > .is-danger { grid-column: 2; }
  .routing-outcome__main { grid-template-columns: 1fr; }
  .routing-outcome__target-wrap { grid-template-columns: 1fr; grid-column: auto; }
}
</style>
