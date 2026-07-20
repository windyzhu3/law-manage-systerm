<template>
  <article class="config-metric-card" :class="`config-metric-card--${tone}`" :aria-label="accessibleLabel">
    <div class="config-metric-card__icon" aria-hidden="true"><i :class="icon" /></div>
    <div class="config-metric-card__content">
      <p class="config-metric-card__label">{{ label }}</p>
      <el-skeleton v-if="loading" :rows="1" animated class="config-metric-card__skeleton" />
      <strong v-else class="config-metric-card__value">{{ value }}</strong>
      <span v-if="helper || trend" class="config-metric-card__helper">{{ trend || helper }}</span>
    </div>
  </article>
</template>

<script>
export default {
  name: 'ConfigMetricCard',
  props: {
    label: { type: String, required: true },
    value: { type: [String, Number], default: '-' },
    helper: { type: String, default: '' },
    trend: { type: String, default: '' },
    icon: { type: String, default: 'el-icon-setting' },
    tone: { type: String, default: 'primary' },
    loading: Boolean
  },
  computed: {
    accessibleLabel() {
      if (this.loading) return `${this.label}，正在加载`
      return `${this.label}：${this.value}${this.trend || this.helper ? `，${this.trend || this.helper}` : ''}`
    }
  }
}
</script>

<style scoped lang="scss">
@import '../styles/config-center.scss';
</style>
