<template>
  <section class="creation-validation" data-testid="creation-validation">
    <div>
      <strong>待办创建条件</strong>
      <small>只校验创建 TD-001 必需的数据，完成表单字段不会误报为缺失。</small>
    </div>
    <el-progress :percentage="coverage" :status="coverage === 100 ? 'success' : 'exception'" />
    <el-alert v-for="issue in issues" :key="issue.code + issue.fieldPath" :title="issue.message" type="error" :closable="false" show-icon />
    <el-tag v-if="coverage === 100 && !issues.length" type="success">可以创建待办</el-tag>
  </section>
</template>

<script>
export default {
  name: 'CreationValidationPanel',
  props: { coverage: { type: Number, default: 0 }, issues: { type: Array, default: () => [] } }
}
</script>

<style scoped>
.creation-validation { display: grid; gap: 10px; padding: 16px; border: 1px solid #D9E1EA; border-radius: 8px; background: #F8FAFC; }
.creation-validation > div:first-child { display: flex; justify-content: space-between; gap: 16px; }
.creation-validation small { color: #66758A; }
</style>
