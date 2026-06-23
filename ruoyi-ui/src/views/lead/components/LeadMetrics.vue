<template>
  <div class="lead-metrics">
    <article v-for="item in items" :key="item.key" class="metric-item">
      <div :class="'metric-icon ' + item.color"><svg-icon :icon-class="item.icon" /></div>
      <div><span>{{ item.label }}</span><strong>{{ item.value }}</strong><small>{{ item.hint }}</small></div>
    </article>
  </div>
</template>

<script>
export default {
  name: 'LeadMetrics',
  props: { data: { type: Object, default: () => ({}) } },
  computed: {
    values() { const result = {}; (this.data.cards || []).forEach(item => { result[item.metricKey] = item.metricValue }); return result },
    converted() { const item = (this.data.statuses || []).find(status => String(status.itemName) === '3'); return item ? item.itemValue : 0 },
    items() {
      return [
        { key: 'total', label: '线索总数', value: this.values.total || 0, hint: '全部潜在客户', icon: 'peoples', color: 'blue' },
        { key: 'mine', label: '我的线索', value: this.values.mine || 0, hint: '当前负责线索', icon: 'user', color: 'cyan' },
        { key: 'pool', label: '公海线索', value: this.values.pool || 0, hint: '等待领取分配', icon: 'list', color: 'violet' },
        { key: 'follow', label: '待跟进线索', value: this.values.followToday || 0, hint: '今日及逾期任务', icon: 'time', color: 'orange' },
        { key: 'converted', label: '已转化客户', value: this.converted, hint: '已完成线索转化', icon: 'validCode', color: 'green' }
      ]
    }
  }
}
</script>

<style lang="scss" scoped>
.lead-metrics{display:grid;grid-template-columns:repeat(5,1fr);gap:12px;margin-top:15px}
.metric-item{display:flex;align-items:center;gap:12px;min-height:94px;padding:15px 16px;border:1px solid #e8edf6;border-radius:11px;background:#fff;box-shadow:0 7px 16px rgba(36,73,135,.045);span,strong,small{display:block}span{color:#64748b;font-size:12px}strong{margin:4px 0;color:#172b4d;font-size:24px}small{color:#a1aec0;font-size:11px}}
.metric-icon{display:flex;align-items:center;justify-content:center;width:48px;height:48px;border-radius:50%;font-size:22px}.blue{color:#2563eb;background:#eef4ff}.cyan{color:#06aebd;background:#e8fafb}.violet{color:#7c3aed;background:#f3efff}.orange{color:#f97316;background:#fff5e9}.green{color:#0eada4;background:#e8fbfa}
@media(max-width:1200px){.lead-metrics{grid-template-columns:repeat(3,1fr)}}@media(max-width:700px){.lead-metrics{grid-template-columns:1fr 1fr}.metric-item{padding:12px}.metric-icon{width:40px;height:40px}}
.metric-item span{font-size:var(--lead-font-small,12px)}.metric-item strong{font-size:var(--lead-font-metric,24px)}.metric-item small{font-size:var(--lead-font-mini,11px)}
</style>
