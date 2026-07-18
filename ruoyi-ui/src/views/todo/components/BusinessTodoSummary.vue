<template>
  <section class="business-todo" data-testid="business-todo-summary">
    <header>
      <div><h4>待办摘要</h4><span v-if="businessNo">{{ businessNo }}</span></div>
      <el-button v-hasPermi="['todo:query']" type="text" :disabled="!validBusinessId" @click="drawer = true">查看全部</el-button>
    </header>
    <div v-loading="loading" class="values">
      <span><b>{{ summary.activeCount || 0 }}</b>活动</span>
      <span :class="{ danger: summary.overdueCount }"><b>{{ summary.overdueCount || 0 }}</b>超时</span>
      <span><b>{{ summary.ownerIds ? summary.ownerIds.length : 0 }}</b>负责人</span>
      <span><b>{{ summary.nearestDueAt || '-' }}</b>最近截止</span>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <business-todo-drawer :visible.sync="drawer" :business-type="businessType" :business-id="businessId" @changed="load" />
  </section>
</template>

<script>
import { getBusinessTodoSummary } from '@/api/todo'
import BusinessTodoDrawer from './BusinessTodoDrawer'

export default {
  name: 'BusinessTodoSummary',
  components: { BusinessTodoDrawer },
  props: { businessType: { type: String, required: true }, businessId: { type: [Number, String], required: true }, businessNo: String },
  data() { return { loading: false, error: '', summary: {}, drawer: false, requestGeneration: 0 } },
  computed: { validBusinessId() { return Number(this.businessId) > 0 } },
  watch: {
    businessId: { immediate: true, handler() { this.reloadForBusiness() } },
    businessType() { this.reloadForBusiness() }
  },
  methods: {
    reloadForBusiness() {
      this.drawer = false
      this.requestGeneration++
      this.summary = {}
      this.error = ''
      if (this.validBusinessId) this.load()
    },
    load() {
      if (!this.validBusinessId) return
      const generation = ++this.requestGeneration
      const businessKey = `${this.businessType}/${this.businessId}`
      this.loading = true
      this.error = ''
      getBusinessTodoSummary(this.businessType, this.businessId).then(response => { if (generation === this.requestGeneration && businessKey === `${this.businessType}/${this.businessId}`) this.summary = response.data || {} })
        .catch(error => { if (generation === this.requestGeneration) this.error = error.msg || '待办摘要加载失败' })
        .finally(() => { if (generation === this.requestGeneration) this.loading = false })
    }
  }
}
</script>

<style scoped>
.business-todo{padding:16px;margin:14px 0;border:1px solid #e2e8f0;border-radius:8px;background:#fff}.business-todo header{display:flex;align-items:center;justify-content:space-between}.business-todo h4{margin:0}.business-todo header span{font-size:12px;color:#64748b}.values{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin-top:12px}.values span{padding:10px;background:#f8fafc;border-radius:6px;color:#64748b}.values b{display:block;color:#0f172a}.values .danger b{color:#dc2626}
</style>
