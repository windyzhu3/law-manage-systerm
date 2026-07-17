<template>
  <div class="definition-builder">
    <el-alert title="触发事件和条件由触发规则页维护；请在触发规则页编辑（Task14 resource）。" type="info" :closable="false" />
    <el-form-item label="触发事件">
      <el-input :value="model.eventType" disabled />
      <el-input :value="String(model.payloadVersion)" disabled />
    </el-form-item>
    <el-form-item label="事件条件">
      <el-input :value="conditionText" type="textarea" :rows="3" disabled />
    </el-form-item>
  </div>
</template>
<script>
export default {
  name: 'EventConditionBuilder',
  props: { value: { type: Object, default: () => ({}) } },
  computed: {
    model() { return { eventType: '', payloadVersion: 1, condition: {}, ...this.value } },
    conditionText() { return JSON.stringify(this.model.condition || {}) }
  }
}
</script>
<style scoped>.definition-builder .el-input{margin-right:8px;margin-bottom:8px;max-width:360px}</style>
