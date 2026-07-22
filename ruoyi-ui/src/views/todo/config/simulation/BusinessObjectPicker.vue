<template>
  <section class="business-object-picker">
    <el-select
      :value="value"
      filterable
      remote
      reserve-keyword
      clearable
      :remote-method="search"
      :loading="loading"
      class="full-width"
      placeholder="输入编号或名称搜索测试对象"
      @input="selected"
      @visible-change="opened"
    >
      <el-option v-for="item in options" :key="item.businessId" :label="optionLabel(item)" :value="item.businessId">
        <div class="option-row"><span><strong>{{ item.businessName }}</strong><small>{{ item.businessNo }}</small></span><el-tag v-if="item.sample" size="mini" type="warning">示例对象</el-tag></div>
      </el-option>
    </el-select>
    <div v-if="total > query.pageSize" class="picker-pagination"><el-pagination small layout="prev,pager,next" :current-page.sync="query.pageNum" :page-size="query.pageSize" :total="total" @current-change="load" /></div>
    <el-alert v-if="sampleFallback" title="当前没有可用的真实数据，已提供安全的示例对象用于模拟；示例不会进入真实业务流程。" type="warning" :closable="false" show-icon />
    <el-alert v-else-if="emptyReason && !options.length" :title="emptyMessage" type="info" :closable="false" show-icon />
    <article v-if="selectedObject" class="selected-card">
      <div><small>已选择测试对象</small><strong>{{ selectedObject.businessName }}</strong><span>{{ selectedObject.businessNo }} · {{ selectedObject.businessType }}</span></div>
      <el-tag v-if="selectedObject.sample" type="warning" size="small">示例对象</el-tag><el-tag v-else type="success" size="small">真实对象</el-tag>
    </article>
  </section>
</template>

<script>
import { listBusinessObjects } from '@/api/todo-config'
export default {
  name: 'BusinessObjectPicker',
  props: { value: { type: [Number, String], default: null }, businessType: { type: String, required: true } },
  data() { return { loading: false, options: [], total: 0, emptyReason: '', sampleFallback: false, query: { keyword: '', pageNum: 1, pageSize: 10 } } },
  computed: {
    selectedObject() { return this.options.find(item => Number(item.businessId) === Number(this.value)) || null },
    emptyMessage() { return ({ NO_PERMISSION: '当前账号没有可用于模拟的业务对象权限，请联系管理员检查数据范围。', NO_MATCH: '没有找到匹配的业务对象，请更换关键词。', NO_DATA: '当前业务模块尚无数据，可先创建业务数据后再模拟。' })[this.emptyReason] || '暂无可选测试对象。' }
  },
  watch: { businessType() { this.reset(); this.load() } },
  methods: {
    opened(open) { if (open && !this.options.length) this.load() },
    search(keyword) { this.query.keyword = keyword || ''; this.query.pageNum = 1; this.load() },
    async load() {
      if (!this.businessType) return
      this.loading = true
      try {
        const response = await listBusinessObjects({ businessType: this.businessType, ...this.query })
        this.options = response.rows || []; this.total = Number(response.total || 0); this.emptyReason = response.emptyReason || ''; this.sampleFallback = Boolean(response.sampleFallback)
        if (this.value && !this.selectedObject) this.$emit('input', null)
      } catch (error) { this.options = []; this.total = 0; this.emptyReason = 'LOAD_FAILED'; this.sampleFallback = false; this.$modal.msgError('加载测试对象失败') } finally { this.loading = false }
    },
    selected(id) { const item = this.options.find(row => Number(row.businessId) === Number(id)) || null; this.$emit('input', id); this.$emit('select', item) },
    reset() { this.options = []; this.total = 0; this.emptyReason = ''; this.sampleFallback = false; this.query = { keyword: '', pageNum: 1, pageSize: 10 }; this.$emit('input', null); this.$emit('select', null) },
    optionLabel(item) { return `${item.businessName} · ${item.businessNo}${item.sample ? '（示例）' : ''}` }
  }
}
</script>

<style scoped lang="scss">
.full-width{width:100%}.option-row{display:flex;justify-content:space-between;align-items:center;gap:10px}.option-row span,.option-row small{display:block}.option-row small{color:#94a3b8;line-height:1.2}.picker-pagination{text-align:right}.business-object-picker>.el-alert{margin-top:8px}.selected-card{display:flex;justify-content:space-between;align-items:center;margin-top:10px;padding:12px;border:1px solid #dbe5f1;border-radius:8px;background:#f8fafc}.selected-card div,.selected-card strong,.selected-card span,.selected-card small{display:block}.selected-card strong{margin:3px 0}.selected-card span,.selected-card small{color:#64748b;font-size:12px}
</style>
