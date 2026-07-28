<template>
  <el-collapse class="advanced-payload" data-testid="advanced-payload-override">
    <el-collapse-item title="高级设置：查看完成与路由字段" name="advanced">
      <div v-for="field in fields" :key="field.path" class="advanced-payload__row">
        <span>{{ field.label }}</span>
        <semantic-value-renderer :field="field" empty-label="本场景不需要填写" />
      </div>
    </el-collapse-item>
  </el-collapse>
</template>

<script>
import SemanticValueRenderer from './SemanticValueRenderer'
export default {
  name: 'AdvancedPayloadOverride',
  components: { SemanticValueRenderer },
  props: { fields: { type: Array, default: () => [] } }
}
</script>

<style scoped>
.advanced-payload__row { display: flex; justify-content: space-between; gap: 16px; padding: 9px 0; border-bottom: 1px solid #E8EDF3; }
</style>
