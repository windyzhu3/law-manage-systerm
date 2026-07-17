<template>
  <div class="todo-material-checklist">
    <el-alert v-if="missingCount" :title="`仍缺少 ${missingCount} 份必需材料`" type="warning" :closable="false" show-icon />
    <div v-for="item in rows" :key="item.materialType" class="material-item">
      <div class="material-heading">
        <strong>{{ item.label }}</strong>
        <el-tag size="mini" :type="item.values.length >= item.minCount ? 'success' : 'danger'">
          {{ item.values.length }}/{{ item.minCount }}
        </el-tag>
      </div>
      <business-file-picker
        v-if="!readonly"
        :value="item.values"
        :business-type="businessType"
        :business-id="businessId"
        :material-type="item.materialType"
        :limit="Math.max(item.minCount, item.limit || item.minCount)"
        @input="setFiles(item.materialType, $event)"
      />
      <span v-else-if="!item.values.length" class="missing">未提供</span>
      <ul v-else><li v-for="file in item.values" :key="file.fileObjectId">{{ file.fileName || `文件 #${file.fileObjectId}` }}</li></ul>
    </div>
  </div>
</template>

<script>
import BusinessFilePicker from './BusinessFilePicker'

export default {
  name: 'TodoMaterialChecklist',
  components: { BusinessFilePicker },
  props: {
    value: { type: Array, default: () => [] },
    requirements: { type: Array, default: () => [] },
    businessType: { type: String, default: 'TODO' },
    businessId: { type: [Number, String], required: true },
    readonly: Boolean
  },
  computed: {
    rows() {
      const rules = this.requirements.length ? this.requirements : this.value.map(value => ({ materialType: value.materialType, label: value.materialType, minCount: 0 }))
      const seen = new Set()
      return rules.filter(rule => {
        const materialType = rule.materialType || rule.type
        if (!materialType || seen.has(materialType)) return false
        seen.add(materialType)
        return true
      }).map(rule => {
        const materialType = rule.materialType || rule.type
        return {
          ...rule,
          materialType,
          label: rule.label || rule.name || materialType,
          minCount: Number(rule.minCount || rule.count || 1),
          values: this.value.filter(file => file.materialType === materialType && Number(file.fileObjectId) > 0)
        }
      })
    },
    missingCount() { return this.rows.reduce((sum, row) => sum + Math.max(0, row.minCount - row.values.length), 0) }
  },
  methods: {
    setFiles(materialType, values) {
      const next = this.value.filter(file => file.materialType !== materialType)
      const files = values == null ? [] : (Array.isArray(values) ? values : [values])
      files.forEach(file => next.push({ ...file, fileObjectId: Number(file.fileObjectId), materialType }))
      this.$emit('input', next)
      this.$emit('change', next)
    }
  }
}
</script>

<style scoped>.todo-material-checklist{display:grid;gap:12px}.material-item{padding:12px;border:1px solid #ebeef5;border-radius:6px}.material-heading{display:flex;justify-content:space-between;margin-bottom:8px}.material-item ul{margin:6px 0 0;padding-left:18px}.missing{color:#f56c6c}</style>
