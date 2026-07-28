<template>
  <section class="todo-preview" data-testid="todo-creation-preview">
    <div><span>预计创建</span><strong>{{ templateName || '待办' }}</strong></div>
    <div><span>负责人</span><semantic-value-renderer :field="ownerField" empty-label="等待负责人规则解析" /></div>
    <el-tag :type="ready ? 'success' : 'warning'">{{ ready ? '创建条件已满足' : '仍有创建阻塞项' }}</el-tag>
  </section>
</template>

<script>
import SemanticValueRenderer from './SemanticValueRenderer'
export default {
  name: 'TodoCreationPreview',
  components: { SemanticValueRenderer },
  props: { templateName: String, fields: { type: Array, default: () => [] }, ready: Boolean },
  computed: {
    ownerField() { return this.fields.find(field => field.path === 'ownerId') || { displayValue: '', displayMeta: {} } }
  }
}
</script>

<style scoped>
.todo-preview { display: flex; align-items: center; justify-content: space-between; gap: 18px; border: 1px solid #C89A3D; border-radius: 8px; padding: 16px; background: #FFFCF5; }
.todo-preview div { display: grid; gap: 4px; }
.todo-preview span { color: #66758A; font-size: 12px; }
.todo-preview strong { color: #0B2A55; }
</style>
