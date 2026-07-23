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
      v-else-if="!fields.length"
      title="所选事件没有可用字段"
      description="当前步骤会阻塞发布，请维护事件字段后再配置业务条件。"
      type="error"
      :closable="false"
      show-icon
    >
      <el-button v-if="!readonly" slot="default" type="text" @click="repairFields">维护事件字段</el-button>
    </el-alert>

    <typed-condition-builder
      v-if="event.eventType"
      ref="builder"
      :value="condition"
      :fields="fields"
      :readonly="readonly || !fields.length"
      @input="change"
    />
  </section>
</template>

<script>
import TypedConditionBuilder from '../components/TypedConditionBuilder'
import { buildTriggerPatch } from '../journey-step-model'

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
      const eventType = this.event.eventType
      return (this.resources.fields || []).filter(field => {
        const sources = field.sourceEvents || []
        return !sources.length || sources.includes(eventType)
      })
    },
    selectedEvent() {
      return (this.resources.events || []).find(item =>
        item.eventType === this.event.eventType &&
        Number(item.payloadVersion) === Number(this.event.payloadVersion)
      ) || {}
    },
    eventName() { return this.selectedEvent.eventName || this.event.eventType || '所选事件' }
  },
  methods: {
    change(document) { this.$emit('change', buildTriggerPatch(document)) },
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
</style>
