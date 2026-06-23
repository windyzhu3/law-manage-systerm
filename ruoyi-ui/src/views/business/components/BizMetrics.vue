<template>
  <div class="biz-metrics" :style="metricGridStyle">
    <article v-for="item in normalizedItems" :key="item.key" class="metric-card">
      <div :class="'metric-icon ' + item.color">
        <svg-icon :icon-class="item.icon" />
      </div>
      <div>
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
        <small>{{ item.hint }}</small>
      </div>
    </article>
  </div>
</template>

<script>
export default {
  name: 'BizMetrics',
  props: {
    metrics: { type: Array, default: () => [] },
    config: { type: Array, default: () => [] }
  },
  computed: {
    valueMap() {
      const result = {}
      this.metrics.forEach(item => {
        result[item.metricKey] = item.metricValue
      })
      return result
    },
    normalizedItems() {
      return this.config.map(item => ({
        ...item,
        value: this.valueMap[item.key] || 0
      }))
    },
    metricGridStyle() {
      return {
        '--biz-metric-count': Math.max(this.normalizedItems.length, 1)
      }
    }
  }
}
</script>

<style scoped lang="scss">
.biz-metrics {
  display: grid;
  grid-template-columns: repeat(var(--biz-metric-count), minmax(0, 1fr));
  gap: 12px;
  margin: 14px 0;
}

.metric-card {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 92px;
  padding: 15px 16px;
  border: 1px solid #e8edf6;
  border-radius: 11px;
  background: #fff;
  box-shadow: 0 7px 16px rgba(36, 73, 135, .045);

  span,
  strong,
  small {
    display: block;
  }

  span {
    color: #64748b;
    font-size: var(--biz-font-small, 12px);
  }

  strong {
    margin: 4px 0;
    color: #172b4d;
    font-size: var(--biz-font-metric, 24px);
  }

  small {
    color: #a1aec0;
    font-size: var(--biz-font-mini, 11px);
  }
}

.metric-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  font-size: var(--biz-font-icon, 22px);
}

.blue { color: #2563eb; background: #eef4ff; }
.cyan { color: #06aebd; background: #e8fafb; }
.violet { color: #7c3aed; background: #f3efff; }
.orange { color: #f97316; background: #fff5e9; }
.green { color: #0eada4; background: #e8fbfa; }

@media (max-width: 1200px) {
  .biz-metrics {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 700px) {
  .biz-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .metric-card {
    padding: 12px;
  }

  .metric-icon {
    width: 40px;
    height: 40px;
  }
}
</style>
