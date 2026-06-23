<template>
  <div>
    <lead-hero @create="$emit('create')" @pool="$emit('pool')" />
    <lead-metrics :data="data" />
    <div class="dashboard-grid">
      <section class="chart-card">
        <div class="card-title"><strong>来源分布</strong><span>各渠道线索数量</span></div>
        <div v-for="item in sources" :key="item.itemName" class="bar-row">
          <span>{{ item.itemName }}</span><div><i :style="{ width: width(item.itemValue, sourceMax) }" /></div><strong>{{ item.itemValue }}</strong>
        </div>
        <el-empty v-if="!sources.length" description="暂无来源数据" :image-size="54" />
      </section>
      <section class="chart-card">
        <div class="card-title"><strong>转化漏斗</strong><span>线索当前状态分布</span></div>
        <div v-for="item in statuses" :key="item.itemName" class="funnel-row">
          <span>{{ statusName(item.itemName) }}</span><strong>{{ item.itemValue }}</strong>
        </div>
      </section>
      <lead-sidebar :data="data" />
    </div>
  </div>
</template>

<script>
import LeadHero from './components/LeadHero'
import LeadMetrics from './components/LeadMetrics'
import LeadSidebar from './components/LeadSidebar'
export default {
  name: 'LeadDashboard',
  dicts: ['law_lead_status'],
  components: { LeadHero, LeadMetrics, LeadSidebar },
  props: { data: { type: Object, default: () => ({}) } },
  computed: { sources() { return this.data.sources || [] }, statuses() { return this.data.statuses || [] }, sourceMax() { return Math.max(...this.sources.map(item => item.itemValue), 1) } },
  methods: { width(value, max) { return Math.max(8, Number(value) / max * 100) + '%' }, statusName(status) { return this.selectDictLabel(this.dict.type.law_lead_status || [], status) || status } }
}
</script>

<style lang="scss" scoped>
.dashboard-grid{display:grid;grid-template-columns:1.35fr .8fr 280px;gap:14px;margin-top:15px}.chart-card{min-height:274px;padding:18px;border:1px solid #e8edf6;border-radius:11px;background:#fff;box-shadow:0 7px 16px rgba(36,73,135,.045)}.card-title{margin-bottom:16px;strong,span{display:block}strong{color:#253858;font-size:15px}span{margin-top:5px;color:#94a3b8;font-size:11px}}.bar-row{display:grid;grid-template-columns:88px 1fr 24px;gap:9px;align-items:center;margin-top:17px;color:#64748b;font-size:12px;div{height:8px;overflow:hidden;border-radius:4px;background:#edf2fa}i{display:block;height:100%;border-radius:4px;background:linear-gradient(90deg,#2563eb,#06b6d4)}}.funnel-row{display:flex;justify-content:space-between;margin:9px auto;padding:10px 14px;border-radius:6px;color:#52709d;background:#f1f6ff;font-size:13px;strong{color:#2563eb}}@media(max-width:1200px){.dashboard-grid{grid-template-columns:1fr 1fr}.dashboard-grid ::v-deep .lead-sidebar{grid-column:span 2;display:grid;grid-template-columns:1fr 1fr}}@media(max-width:760px){.dashboard-grid{display:block}.chart-card{margin-top:12px}.dashboard-grid ::v-deep .lead-sidebar{display:block}.dashboard-grid ::v-deep .side-card{margin-top:12px}}
.card-title strong{font-size:var(--lead-font-card,15px)}.card-title span{font-size:var(--lead-font-mini,11px)}.bar-row{font-size:var(--lead-font-small,12px)}.funnel-row{font-size:var(--lead-font-base,13px)}
</style>
