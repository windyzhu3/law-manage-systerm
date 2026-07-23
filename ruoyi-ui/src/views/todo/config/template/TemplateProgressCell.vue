<template>
  <div class="template-progress-cell" aria-label="配置进度">
    <div class="template-progress-cell__label">
      <span>配置进度</span>
      <strong>{{ completedSteps }}/{{ totalSteps }}</strong>
    </div>
    <el-progress
      :percentage="percentage"
      :show-text="false"
      :stroke-width="6"
      :color="progressColor"
    />
    <small>{{ progressDescription }}</small>
  </div>
</template>

<script>
export default {
  name: 'TemplateProgressCell',
  props: {
    row: { type: Object, required: true }
  },
  computed: {
    completedSteps() {
      return Math.max(0, this.number(this.row.completedSteps))
    },
    totalSteps() {
      return Math.max(1, this.number(this.row.totalSteps) || 7)
    },
    percentage() {
      return Math.min(100, Math.round((this.completedSteps / this.totalSteps) * 100))
    },
    progressColor() {
      return this.percentage === 100 ? '#2E8B68' : '#C89A3D'
    },
    progressDescription() {
      if (this.percentage === 100) return '全部步骤已完成'
      if (this.completedSteps === 0) return '等待开始配置'
      return `下一步：继续完成第 ${this.completedSteps + 1} 步`
    }
  },
  methods: {
    number(value) {
      const parsed = Number(value)
      return Number.isFinite(parsed) ? parsed : 0
    }
  }
}
</script>

<style scoped lang="scss">
@import '../styles/config-center.scss';
</style>
