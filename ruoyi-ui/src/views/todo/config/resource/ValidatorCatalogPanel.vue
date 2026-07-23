<template>
  <section class="resource-panel" v-loading="loading">
    <div class="resource-panel__toolbar">
      <div><strong>校验器目录</strong><p>完成条件只允许选择已注册且可用的服务端校验器，无需手工填写代码。</p></div>
      <el-select v-model="businessType" clearable placeholder="全部业务类型" size="small" @change="load"><el-option v-for="item in businessTypes" :key="item" :label="item" :value="item" /></el-select>
    </div>
    <el-table :data="rows" stripe>
      <el-table-column prop="name" label="校验器" min-width="160"><template #default="{ row }"><strong>{{ row.name || row.code }}</strong><p class="cell-note">{{ row.code }}</p></template></el-table-column>
      <el-table-column prop="description" label="用途说明" min-width="220" show-overflow-tooltip />
      <el-table-column label="业务范围" min-width="150"><template #default="{ row }"><el-tag v-for="item in row.businessTypes || []" :key="item" size="mini" class="tag-gap">{{ item }}</el-tag></template></el-table-column>
      <el-table-column label="服务状态" width="120"><template #default="{ row }"><el-tag :type="row.selectable ? 'success' : 'danger'" size="small">{{ statusLabel(row.effectiveStatus) }}</el-tag></template></el-table-column>
      <el-table-column label="可用于规则" width="100" align="center"><template #default="{ row }"><i :class="row.selectable ? 'el-icon-success available' : 'el-icon-error unavailable'" /></template></el-table-column>
      <el-table-column prop="referenceCount" label="引用数" width="90" align="center" />
      <el-table-column type="expand" width="46">
        <template #default="{ row }">
          <div class="validator-advanced">
            <strong>高级状态（只读）</strong>
            <dl>
              <div><dt>校验器编码</dt><dd>{{ row.code }}</dd></div>
              <div><dt>元数据状态</dt><dd>{{ statusLabel(row.configuredStatus) }}</dd></div>
              <div><dt>实现注册状态</dt><dd>{{ statusLabel(row.effectiveStatus) }}</dd></div>
              <div><dt>规则引用数</dt><dd>{{ row.referenceCount || 0 }}</dd></div>
            </dl>
            <p>实现类由服务端注册并受发布治理保护，此处仅展示状态，不提供上传或执行入口。</p>
          </div>
        </template>
      </el-table-column>
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
    statusLabel(status) { return ({ ACTIVE: '可用', AVAILABLE: '可用', UNAVAILABLE: '未实现', UNMANAGED: '未纳入治理', DISABLED: '已停用' })[status] || status }
  }
}
</script>

<style scoped lang="scss">
.resource-panel__toolbar { display: flex; justify-content: space-between; align-items: flex-start; gap: 20px; margin-bottom: 16px; }
.resource-panel__toolbar p, .cell-note { margin: 5px 0 0; color: #64748b; font-size: 12px; }
.tag-gap { margin: 2px 4px 2px 0; }
.available { color: #22c55e; font-size: 18px; }.unavailable { color: #ef4444; font-size: 18px; }
.resource-panel > .el-alert { margin-top: 14px; }
.validator-advanced { padding: 12px 28px; background: #f8fafc; }.validator-advanced strong { color: #0B2A55; }.validator-advanced dl { display: grid; grid-template-columns: repeat(4,minmax(120px,1fr)); gap: 12px; margin: 12px 0; }.validator-advanced dt { font-size: 12px; color: #7B8898; }.validator-advanced dd { margin: 4px 0 0; font-size: 13px; color: #34465B; }.validator-advanced p { margin: 0; font-size: 12px; color: #65758A; }
@media (max-width: 640px) { .validator-advanced dl { grid-template-columns: 1fr 1fr; } }
</style>
