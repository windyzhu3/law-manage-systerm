<template>
  <aside class="lead-sidebar">
    <section class="side-card">
      <h3>来源分布</h3>
      <div class="donut" :style="{ background: donutBackground }"><div><strong>{{ total }}</strong><small>线索总数</small></div></div>
      <div class="source-list">
        <p v-for="(item,index) in sources" :key="item.itemName"><i :style="{background: colors[index % colors.length]}" /><span>{{ item.itemName }}</span><b>{{ percent(item.itemValue) }}</b><em>{{ item.itemValue }}</em></p>
        <el-empty v-if="!sources.length" description="暂无来源数据" :image-size="42" />
      </div>
    </section>
    <section class="side-card reminder-card">
      <h3>待处理提醒</h3>
      <div class="reminder red"><svg-icon icon-class="time" /><span>今日及逾期跟进<small>请优先处理到期线索</small></span><b>{{ metric('followToday') }}</b></div>
      <div class="reminder orange"><svg-icon icon-class="user" /><span>待领取公海线索<small>及时建立首次联系</small></span><b>{{ metric('pool') }}</b></div>
      <div class="reminder blue"><svg-icon icon-class="peoples" /><span>我的负责线索<small>持续跟进客户需求</small></span><b>{{ metric('mine') }}</b></div>
    </section>
  </aside>
</template>

<script>
export default {
  name: 'LeadSidebar',
  props: { data: { type: Object, default: () => ({}) } },
  data() { return { colors: ['#2563eb','#0fb6c7','#f59e0b','#7c3aed','#10b981','#f472b6'] } },
  computed: {
    sources() { return this.data.sources || [] },
    total() { return this.sources.reduce((sum,item) => sum + Number(item.itemValue || 0), 0) },
    donutBackground() {
      if (!this.total) return '#edf2f8'
      let offset = 0
      const parts = this.sources.map((item,index) => { const start = offset; offset += Number(item.itemValue) / this.total * 100; return `${this.colors[index % this.colors.length]} ${start}% ${offset}%` })
      return `conic-gradient(${parts.join(',')})`
    }
  },
  methods: {
    percent(value) { return this.total ? (Number(value) / this.total * 100).toFixed(1) + '%' : '0%' },
    metric(key) { const item = (this.data.cards || []).find(card => card.metricKey === key); return item ? item.metricValue : 0 }
  }
}
</script>

<style lang="scss" scoped>
.lead-sidebar{display:flex;flex-direction:column;gap:14px}.side-card{padding:17px;border:1px solid #e8edf6;border-radius:11px;background:#fff;box-shadow:0 7px 16px rgba(36,73,135,.045);h3{margin:0 0 15px;color:#253858;font-size:15px}}
.donut{display:flex;align-items:center;justify-content:center;width:132px;height:132px;margin:4px auto 18px;border-radius:50%;div{display:flex;flex-direction:column;align-items:center;justify-content:center;width:88px;height:88px;border-radius:50%;background:#fff}strong{color:#243858;font-size:20px}small{margin-top:3px;color:#94a3b8;font-size:11px}}
.source-list p{display:grid;grid-template-columns:9px 1fr 42px 28px;gap:6px;align-items:center;margin:10px 0;color:#64748b;font-size:12px;i{width:7px;height:7px;border-radius:50%}span{white-space:nowrap}b{color:#94a3b8;font-weight:400}em{color:#475569;font-style:normal;text-align:right}}
.reminder{display:grid;grid-template-columns:18px 1fr auto;gap:8px;align-items:center;margin-top:9px;padding:10px;border-radius:7px;font-size:12px;span{color:#475569}small{display:block;margin-top:4px;color:#94a3b8;font-size:10px}b{font-size:17px}.svg-icon{font-size:15px}}.red{color:#ef4444;background:#fff4f4}.orange{color:#f97316;background:#fff8ee}.blue{color:#2563eb;background:#f2f6ff}
.side-card h3{font-size:var(--lead-font-card,15px)}.donut strong{font-size:calc(var(--lead-font-base,13px) + 7px)}.donut small,.source-list p,.reminder{font-size:var(--lead-font-small,12px)}.reminder small{font-size:var(--lead-font-mini,10px)}.reminder b{font-size:calc(var(--lead-font-base,13px) + 4px)}
</style>
