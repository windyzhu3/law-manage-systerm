<template>
  <section class="journey-step trigger-step">
    <header class="journey-step__header">
      <span class="journey-step__eyebrow">第二步 · 触发条件</span>
      <h2>哪些业务情况需要创建待办？</h2>
      <p>不设置条件时，每次收到“{{ eventName }}”都会创建待办；添加条件后，仅匹配的业务对象会进入后续流程。</p>
    </header>

    <el-alert
      v-if="!event.eventType"
      title="请先选择业务事件"
      description="触发条件依赖事件字段，完成第一步后即可继续。"
      type="warning"
      :closable="false"
      show-icon
    />
    <el-alert
      v-else-if="staleCondition"
      title="当前草稿保留了已停用的旧条件"
      description="该字段已不再允许作为触发条件。请清除旧条件后使用当前事件的业务预设。"
      type="error"
      :closable="false"
      show-icon
    >
      <el-button v-if="!readonly" slot="default" type="text" @click="clearStaleCondition">清除旧条件</el-button>
    </el-alert>

    <el-alert
      v-else-if="selectedEvent.configurationReady === false"
      title="所选事件的字段治理尚未完成"
      description="请先维护事件字段的中文名称、业务说明和配置用途。"
      type="error"
      :closable="false"
      show-icon
    >
      <el-button v-if="!readonly" slot="default" type="text" @click="repairFields">维护事件字段</el-button>
    </el-alert>

    <el-alert
      v-else-if="governedWithoutCondition"
      class="trigger-step__preset"
      title="系统预设，无需附加条件"
      :description="`该业务事件“${eventName}”发生即触发，无需附加条件。`"
      type="success"
      :closable="false"
      show-icon
    />

    <el-collapse v-if="event.eventType && (fields.length || hasCondition)" class="trigger-step__advanced">
      <el-collapse-item name="conditions">
        <template slot="title"><i class="el-icon-setting" />高级设置：附加业务条件</template>
        <p>仅当同一业务事件还需要二次筛选时使用；系统预设事件通常不需要添加。</p>
        <typed-condition-builder
          ref="builder"
          :value="condition"
          :fields="fields"
          :readonly="readonly"
          @input="change"
          @issue-change="$emit('issue-change', $event)"
        />
      </el-collapse-item>
    </el-collapse>
  </section>
</template>

<script>
import TypedConditionBuilder from '../components/TypedConditionBuilder'
import { buildTriggerPatch, scopeEventFields } from '../journey-step-model'

export default {
  name: 'TriggerStep',
  components: { TypedConditionBuilder },
  props: {
    value: { type: Object, default: () => ({}) },
    resources: { type: Object, default: () => ({}) },
    event: { type: Object, default: () => ({}) },
    readonly: Boolean
  },
  computed: {
    condition() { return this.value.condition || {} },
    fields() {
      return scopeEventFields(this.resources.fields || [], this.event, 'CONDITION')
    },
    selectedEvent() {
      return (this.resources.events || []).find(item =>
        item.eventType === this.event.eventType &&
        Number(item.payloadVersion) === Number(this.event.payloadVersion)
      ) || {}
    },
    eventName() { return this.selectedEvent.eventName || this.event.eventType || '所选事件' },
    hasCondition() {
      const root = this.condition && this.condition.$expression && this.condition.$expression.root
      if (root && Array.isArray(root.conditions)) return root.conditions.length > 0
      return Boolean(this.condition && Object.keys(this.condition).length)
    },
    conditionFieldCodes() {
      const result = new Set()
      const visit = value => {
        if (!value || typeof value !== 'object') return
        if (!Array.isArray(value) && typeof value.field === 'string') result.add(value.field)
        Object.keys(value).forEach(key => visit(value[key]))
      }
      visit(this.condition)
      return Array.from(result)
    },
    staleCondition() {
      if (!this.event.eventType || !this.hasCondition) return false
      const allowed = new Set(this.fields.map(field => String(field.code)))
      return !this.fields.length || this.conditionFieldCodes.some(field => !allowed.has(String(field)))
    },
    governedWithoutCondition() {
      return Boolean(this.event.eventType) && !this.hasCondition
    }
  },
  methods: {
    change(document) { this.$emit('patch', { trigger: buildTriggerPatch(document) }) },
    clearStaleCondition() { this.change({}) },
    repairFields() {
      this.$emit('repair-resource', {
        type: 'EVENT',
        resourceId: this.selectedEvent.eventCatalogId,
        eventType: this.event.eventType,
        payloadVersion: this.event.payloadVersion,
        businessType: this.selectedEvent.businessObjectType,
        returnStep: 'TRIGGER',
        focusField: (this.fields[0] && this.fields[0].code) || 'payloadSchema'
      })
    },
    focusField(path) {
      if (this.$refs.builder) this.$refs.builder.focusField(path)
    }
  }
}
</script>

<style scoped lang="scss">
.journey-step__header {
  margin-bottom: 22px;

  h2 {
    margin: 5px 0 8px;
    font-size: 22px;
    line-height: 32px;
    color: #0B2A55;
  }

  p {
    max-width: 760px;
    margin: 0;
    font-size: 14px;
    line-height: 23px;
    color: #65758A;
  }
}

.journey-step__eyebrow {
  font-size: 12px;
  color: #C89A3D;
}

.trigger-step > .el-alert {
  margin-bottom: 16px;
}

.trigger-step__advanced {
  margin-top: 14px;

  ::v-deep .el-collapse-item__header {
    padding: 0 14px;
    color: #0B2A55;
    background: #F7F9FC;
  }

  ::v-deep .el-collapse-item__content { padding: 14px; }
  .el-icon-setting { margin-right: 7px; color: #C89A3D; }
  p { margin: 0 0 12px; color: #65758A; }
}
</style>
