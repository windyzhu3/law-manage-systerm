<template>
  <section class="resource-panel" v-loading="loading">
    <div class="resource-panel__toolbar">
      <div><strong>校验器目录</strong><p>完成条件只允许选择已注册且可用的服务端校验器，无需手工填写代码。</p></div>
      <el-select v-model="businessType" clearable placeholder="全部业务类型" size="small" @change="load"><el-option v-for="item in businessTypes" :key="item" :label="item" :value="item" /></el-select>
    </div>
    <el-table :data="rows" border stripe>
      <el-table-column prop="name" label="校验器" min-width="160"><template #default="{ row }"><strong>{{ row.name || row.code }}</strong><p class="cell-note">{{ row.code }}</p></template></el-table-column>
      <el-table-column prop="description" label="用途说明" min-width="220" show-overflow-tooltip />
      <el-table-column label="业务范围" min-width="150"><template #default="{ row }"><el-tag v-for="item in row.businessTypes || []" :key="item" size="mini" class="tag-gap">{{ item }}</el-tag></template></el-table-column>
      <el-table-column label="实现状态" width="120"><template #default="{ row }"><el-tag :type="row.effectiveStatus === 'AVAILABLE' ? 'success' : 'danger'" size="small">{{ statusLabel(row.effectiveStatus) }}</el-tag></template></el-table-column>
      <el-table-column label="可用于规则" width="100" align="center"><template #default="{ row }"><i :class="row.selectable ? 'el-icon-success available' : 'el-icon-error unavailable'" /></template></el-table-column>
      <el-table-column prop="referenceCount" label="引用数" width="90" align="center" />
    </el-table>
    <el-alert v-if="!loading && !rows.length" title="当前条件下没有可用校验器。请先确认后端能力是否注册，再检查元数据配置。" type="warning" :closable="false" show-icon />
  </section>
</template>

<script>
import { listValidatorResources } from '@/api/todo-resources'
export default {
  name: 'ValidatorCatalogPanel',
  data() { return { loading: false, rows: [], businessType: '', businessTypes: ['LEAD', 'CUSTOMER', 'CONTRACT', 'CASE', 'MATTER'] } },
  created() { this.load() },
  methods: {
    async load() { this.loading = true; try { const response = await listValidatorResources(this.businessType ? { businessType: this.businessType } : {}); this.rows = response.data || [] } catch (error) { this.rows = []; this.$modal.msgError('加载校验器目录失败') } finally { this.loading = false } },
    statusLabel(status) { return ({ AVAILABLE: '可用', UNAVAILABLE: '未实现', DISABLED: '已停用' })[status] || status }
  }
}
</script>

<style scoped lang="scss">
.resource-panel__toolbar { display: flex; justify-content: space-between; align-items: flex-start; gap: 20px; margin-bottom: 16px; }
.resource-panel__toolbar p, .cell-note { margin: 5px 0 0; color: #64748b; font-size: 12px; }
.tag-gap { margin: 2px 4px 2px 0; }
.available { color: #22c55e; font-size: 18px; }.unavailable { color: #ef4444; font-size: 18px; }
.resource-panel > .el-alert { margin-top: 14px; }
</style>
