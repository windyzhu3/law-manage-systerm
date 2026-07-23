<template>
  <section class="template-problem-summary" aria-label="配置健康状态">
    <button
      type="button"
      class="problem-summary-item problem-summary-item--blocker"
      :class="{ 'is-active': activeFilter === 'BLOCKER' }"
      @click="$emit('filter', 'BLOCKER')"
    >
      <span class="problem-summary-item__icon"><i class="el-icon-warning-outline" /></span>
      <span class="problem-summary-item__copy">
        <strong>{{ count('blockerTemplates') }} 项阻塞</strong>
        <small>需要先处理，完成后才可发布</small>
      </span>
      <i class="el-icon-arrow-right" />
    </button>
    <button
      type="button"
      class="problem-summary-item problem-summary-item--warning"
      :class="{ 'is-active': activeFilter === 'WARNING' }"
      @click="$emit('filter', 'WARNING')"
    >
      <span class="problem-summary-item__icon"><i class="el-icon-bell" /></span>
      <span class="problem-summary-item__copy">
        <strong>{{ count('warningTemplates') }} 项警告</strong>
        <small>建议发布前确认并补充说明</small>
      </span>
      <i class="el-icon-arrow-right" />
    </button>
    <button
      type="button"
      class="problem-summary-item problem-summary-item--ready"
      :class="{ 'is-active': activeFilter === 'READY' }"
      @click="$emit('filter', 'READY')"
    >
      <span class="problem-summary-item__icon"><i class="el-icon-circle-check" /></span>
      <span class="problem-summary-item__copy">
        <strong>{{ count('readyTemplates') }} 项可继续</strong>
        <small>配置健康，可继续模拟或发布</small>
      </span>
      <i class="el-icon-arrow-right" />
    </button>
  </section>
</template>

<script>
export default {
  name: 'TemplateProblemSummary',
  props: {
    summary: { type: Object, default: () => ({}) },
    activeFilter: { type: String, default: '' }
  },
  methods: {
    count(key) {
      const value = Number(this.summary && this.summary[key])
      return Number.isFinite(value) ? value : 0
    }
  }
}
</script>

<style scoped lang="scss">
@import '../styles/config-center.scss';
</style>
