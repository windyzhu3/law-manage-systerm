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

    <el-alert
      v-else-if="governedWithoutCondition"
      class="trigger-step__preset"
      title="系统预设，无需附加条件"
      :description="`该业务事件“${eventName}”发生即触发，无需附加条件。`"
      type="success"
      :closable="false"
      show-icon
    />

    <el-collapse v-if="event.eventType && fields.length" class="trigger-step__advanced">
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
    eventName() { return this.selectedEvent.eventName || this.event.eventType || '所选事件' },
    governedWithoutCondition() {
      const root = this.condition && this.condition.$expression && this.condition.$expression.root
      const conditions = root && Array.isArray(root.conditions) ? root.conditions : []
      return Boolean(this.event.eventType) && (!Object.keys(this.condition).length || !conditions.length)
    }
  },
  methods: {
    change(document) { this.$emit('patch', { trigger: buildTriggerPatch(document) }) },
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
