<template>
  <config-page-shell
    v-hasPermi="['todo:trigger:list']"
    title="触发规则"
    subtitle="管理业务事件到已发布待办模板版本的触发关系"
    :loading="loading"
    :empty="!loading && !rows.length"
    empty-text="暂无触发规则"
  >
    <template #primary-action>
      <el-button v-hasPermi="['todo:trigger:create']" type="primary" size="small" icon="el-icon-plus" @click="openCreate">新增规则</el-button>
    </template>
    <template #secondary-actions>
      <el-button
        v-hasPermi="['todo:trigger:edit']"
        size="small"
        icon="el-icon-sort"
        :loading="sorting || sortPreparing"
        :disabled="!sortDirty || sorting || sortPreparing || sortLockedByKeyword"
        @click="saveSort"
      >保存排序</el-button>
    </template>
    <template #metrics>
      <config-metric-card label="规则总数" :value="total" icon="el-icon-connection" />
      <config-metric-card label="当前页已启用" :value="enabledCount" tone="success" icon="el-icon-circle-check" />
      <config-metric-card label="当前页有条件" :value="conditionalCount" tone="warning" icon="el-icon-set-up" />
      <config-metric-card label="待保存排序" :value="changedSortCount" :tone="sortDirty ? 'danger' : 'primary'" icon="el-icon-sort" />
    </template>
    <template #filters>
      <el-input v-model="query.keyword" clearable size="small" placeholder="规则名称 / 规则编码" class="filter-item" style="width: 240px" @keyup.enter.native="search" @clear="search" />
      <el-button size="small" icon="el-icon-search" @click="search">搜索</el-button>
      <el-alert title="规则按后端 sortOrder 排序；列表接口当前仅支持分页，不展示无效筛选条件。" type="info" :closable="false" show-icon />
    </template>

    <el-table v-loading="loading" :data="rows" stripe :row-key="rowId" @row-click="openDetail">
      <el-table-column label="规则名称" min-width="160"><template slot-scope="{ row }"><strong>{{ field(row, 'ruleName', 'rule_name') || '-' }}</strong></template></el-table-column>
      <el-table-column label="规则编码" min-width="160"><template slot-scope="{ row }"><code>{{ field(row, 'ruleCode', 'rule_code') || '-' }}</code></template></el-table-column>
      <el-table-column label="来源事件" min-width="190">
        <template slot-scope="{ row }"><strong>{{ sourceEventLabel(row) }}</strong><small class="cell-subtitle">{{ businessSourceLabel(row) }}</small></template>
      </el-table-column>
      <el-table-column label="来源模板" width="140"><template>—（事件触发）</template></el-table-column>
      <el-table-column label="条件摘要" min-width="220" show-overflow-tooltip><template slot-scope="{ row }">{{ conditionSummary(row) }}</template></el-table-column>
      <el-table-column label="目标动作" width="110"><template>{{ targetActionLabel() }}</template></el-table-column>
      <el-table-column label="目标模板 / 版本" min-width="190">
        <template slot-scope="{ row }"><strong>{{ targetTemplateLabel(row) }}</strong><small class="cell-subtitle">版本 ID：{{ field(row, 'templateVersionId', 'template_version_id') }}</small></template>
      </el-table-column>
      <el-table-column label="触发方式" width="110"><template>{{ triggerModeLabel() }}</template></el-table-column>
      <el-table-column label="状态" width="90">
        <template slot-scope="{ row }"><dict-tag :options="dict.type.law_todo_rule_status" :value="ruleStatus(row)" /></template>
      </el-table-column>
      <el-table-column prop="sortOrder" label="排序" width="72"><template slot-scope="{ row }">{{ field(row, 'sortOrder', 'sort_order') }}</template></el-table-column>
      <el-table-column label="更新时间" width="165"><template slot-scope="{ row }">{{ format(field(row, 'updateTime', 'update_time')) }}</template></el-table-column>
      <el-table-column label="操作" fixed="right" width="300">
        <template slot-scope="{ row }">
          <el-button v-hasPermi="['todo:trigger:edit']" type="text" @click.stop="openEdit(row)">编辑</el-button>
          <el-button v-hasPermi="['todo:trigger:create']" type="text" @click.stop="openCopy(row)">复制</el-button>
          <el-button
            v-hasPermi="['todo:trigger:toggle']"
            type="text"
            :disabled="sortDirty"
            :loading="Boolean(rowToggleLoading[field(row, 'triggerRuleId', 'trigger_rule_id')])"
            @click.stop="toggleRow(row)"
          >{{ field(row, 'enabled', 'enabled') === 'Y' ? '停用' : '启用' }}</el-button>
          <el-button v-hasPermi="['todo:trigger:edit']" type="text" :disabled="isFirst(row)" @click.stop="moveUp(row)">上移</el-button>
          <el-button v-hasPermi="['todo:trigger:edit']" type="text" :disabled="isLast(row)" @click.stop="moveDown(row)">下移</el-button>
          <el-button v-hasPermi="['todo:simulation:simulate']" type="text" @click.stop="handoffSimulation(row)">模拟</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" :page.sync="query.pageNum" :limit.sync="query.pageSize" @pagination="handlePagination" />

    <template #persistent><trigger-rule-drawer :visible.sync="drawerOpen" :rule="selected" :mode="drawerMode" @edit="openEdit" @saved="afterSaved" /></template>
  </config-page-shell>
</template>

<script>
import ConfigPageShell from '../shared/ConfigPageShell'
import ConfigMetricCard from '../shared/ConfigMetricCard'
import TriggerRuleDrawer from './TriggerRuleDrawer'
import { listTriggerRules, toggleTriggerRule, sortTriggerRules } from '@/api/todo-config'
const { moveTriggerRows, pageTriggerRows, changedTriggerSortItems, buildTriggerToggleCommand, canModifyTriggerSort } = require('./trigger-sort-model')

export default {
  name: 'TodoTriggerRuleConfig',
  components: { ConfigPageShell, ConfigMetricCard, TriggerRuleDrawer },
  dicts: ['law_todo_trigger_mode', 'law_todo_condition_operator', 'law_todo_rule_status'],
  data() {
    return {
      loading: false,
      sorting: false,
      sortPreparing: false,
      rows: [],
      total: 0,
      query: { pageNum: 1, pageSize: 20, keyword: '' },
      drawerOpen: false,
      drawerMode: 'view',
      selected: null,
      rowToggleLoading: {},
      globalRows: null,
      initialGlobalSort: {}
    }
  },
  computed: {
    enabledCount() { return this.rows.filter(row => this.field(row, 'enabled', 'enabled') === 'Y').length },
    conditionalCount() { return this.rows.filter(row => Boolean(this.field(row, 'conditionJson', 'condition_json'))).length },
    changedSortRows() { return this.globalRows ? changedTriggerSortItems(this.globalRows, this.initialGlobalSort) : [] },
    changedSortCount() { return this.changedSortRows.length },
    sortDirty() { return this.changedSortCount > 0 },
    sortLockedByKeyword() { return !canModifyTriggerSort(this.query.keyword) }
  },
  created() { this.load() },
  methods: {
    field(row, camel, snake) { return row && (row[camel] !== undefined ? row[camel] : row[snake]) },
    rowId(row) { return Number(this.field(row, 'triggerRuleId', 'trigger_rule_id')) },
    actionId(action) { return `trigger-${action}-${Date.now()}-${Math.random().toString(16).slice(2)}` },
    async load() {
      this.loading = true
      try {
        const response = await listTriggerRules({ pageNum: this.query.pageNum, pageSize: this.query.pageSize, keyword: this.query.keyword || undefined })
        this.rows = (response.rows || []).map(row => ({ ...row }))
        this.total = Number(response.total || 0)
        this.globalRows = null
        this.initialGlobalSort = {}
      } catch (error) {
        this.rows = []
        this.total = 0
        this.globalRows = null
        this.initialGlobalSort = {}
        this.showError(error, '加载触发规则失败')
      } finally { this.loading = false }
    },
    openCreate() { this.selected = null; this.drawerMode = 'create'; this.drawerOpen = true },
    openDetail(row) { this.selected = { ...row }; this.drawerMode = 'view'; this.drawerOpen = true },
    openEdit(row) { this.selected = { ...row }; this.drawerMode = 'edit'; this.drawerOpen = true },
    openCopy(row) { this.selected = { ...row }; this.drawerMode = 'copy'; this.drawerOpen = true },
    search() { this.query.pageNum = 1; this.globalRows = null; this.initialGlobalSort = {}; this.load() },
    afterSaved() { this.drawerOpen = false; this.load() },
    sourceEventLabel(row) { return `${this.field(row, 'eventType', 'event_type') || '-'} · v${this.field(row, 'payloadVersion', 'payload_version') || 1}` },
    ruleIdentityLabel(row) { return `${this.field(row, 'ruleName', 'rule_name') || '-'} (${this.field(row, 'ruleCode', 'rule_code') || '-'})` },
    businessSourceLabel(row) { return `业务对象：${this.field(row, 'businessType', 'business_type') || '-'}` },
    targetActionLabel() { return '创建待办' },
    targetTemplateLabel(row) { const name = this.field(row, 'templateName', 'template_name'); const code = this.field(row, 'templateCode', 'template_code'); return code ? `${name || code} (${code})` : (name || '-') },
    triggerModeLabel() { const item = (this.dict.type.law_todo_trigger_mode || []).find(option => option.value === 'EVENT'); return item ? item.label : '事件触发' },
    ruleStatus(row) { return this.field(row, 'enabled', 'enabled') === 'Y' ? '0' : '1' },
    operatorLabel(value) { const item = (this.dict.type.law_todo_condition_operator || []).find(option => option.value === value); return item ? item.label : value },
    conditionSummary(row) {
      const raw = this.field(row, 'conditionJson', 'condition_json')
      if (!raw) return '无条件，事件到达即触发'
      try {
        const document = typeof raw === 'string' ? JSON.parse(raw) : raw
        if (document.$expression && document.$expression.root) return this.expressionSummary(document.$expression.root)
        return Object.keys(document).map(key => `${key} = ${this.shortValue(document[key])}`).join(' 且 ') || '无条件'
      } catch (error) { return '条件 JSON 格式异常，请进入详情修复' }
    },
    expressionSummary(node) {
      if (!node) return '无条件'
      if (node.field) return `${node.field} ${this.operatorLabel(node.operator)} ${this.shortValue(node.value)}`
      if (node.type === 'NOT') return `非（${this.expressionSummary(node.condition)}）`
      const conjunction = node.type === 'OR' ? ' 或 ' : ' 且 '
      return (node.conditions || []).map(item => this.expressionSummary(item)).join(conjunction)
    },
    shortValue(value) { const text = typeof value === 'string' ? value : JSON.stringify(value); return text && text.length > 36 ? `${text.slice(0, 33)}…` : (text == null ? 'null' : text) },
    async toggleRow(row) {
      const id = this.rowId(row)
      if (!id || this.rowToggleLoading[id]) return
      if (this.sortDirty) return this.$modal.msgWarning('请先保存触发规则排序，再变更规则状态')
      const targetEnabled = this.field(row, 'enabled', 'enabled') === 'Y' ? 'N' : 'Y'
      const actionLabel = targetEnabled === 'Y' ? '启用' : '停用'
      this.$set(this.rowToggleLoading, id, true)
      try {
        await this.$confirm(`确认${actionLabel}该触发规则吗？`, `${actionLabel}触发规则`, { type: 'warning' })
        await toggleTriggerRule(id, buildTriggerToggleCommand(targetEnabled, Number(this.field(row, 'version', 'version') || 0), this.actionId('row-toggle')))
        this.$modal.msgSuccess(`触发规则已${actionLabel}`)
        await this.load()
      } catch (error) {
        if (error !== 'cancel' && error !== 'close') this.showError(error, `${actionLabel}触发规则失败，请刷新后重试`)
      } finally { this.$set(this.rowToggleLoading, id, false) }
    },
    globalIndex(row) { return this.globalRows ? this.globalRows.findIndex(item => this.rowId(item) === this.rowId(row)) : ((this.query.pageNum - 1) * this.query.pageSize) + this.rows.indexOf(row) },
    isFirst(row) { return this.globalIndex(row) <= 0 },
    isLast(row) { return this.globalIndex(row) >= this.total - 1 },
    moveUp(row) { this.move(row, -1) },
    moveDown(row) { this.move(row, 1) },
    async move(row, offset) {
      if (this.sortLockedByKeyword) return this.$modal.msgWarning('排序筛选结果不能调整全局排序，请清空关键词后重试')
      if (this.sortPreparing || this.sorting) return
      try {
        await this.loadGlobalSortSnapshot()
        const moved = moveTriggerRows(this.globalRows, this.rowId(row), offset)
        if (moved.movedIndex < 0) return
        this.globalRows = moved.rows
        this.query.pageNum = Math.floor(moved.movedIndex / this.query.pageSize) + 1
        this.renderGlobalPage()
      } catch (error) { this.showError(error, '加载完整排序快照失败') }
    },
    async loadGlobalSortSnapshot() {
      if (this.globalRows) return
      this.sortPreparing = true
      try {
        const all = []
        const seen = new Set()
        let pageNum = 1
        let expectedTotal = Number(this.total || 0)
        while (!expectedTotal || all.length < expectedTotal) {
          const response = await listTriggerRules({ pageNum, pageSize: 500, keyword: this.query.keyword || undefined })
          const batch = response.rows || []
          expectedTotal = Number(response.total || 0)
          batch.forEach(row => { const id = this.rowId(row); if (id && !seen.has(id)) { seen.add(id); all.push({ ...row }) } })
          if (!batch.length || batch.length < 500) break
          pageNum += 1
        }
        if (all.length !== expectedTotal) throw new Error(`完整排序快照应有 ${expectedTotal} 条，实际读取 ${all.length} 条`)
        this.globalRows = all
        this.total = expectedTotal
        this.initialGlobalSort = Object.fromEntries(all.map(row => [this.rowId(row), Number(this.field(row, 'sortOrder', 'sort_order') || 0)]))
        this.renderGlobalPage()
      } finally { this.sortPreparing = false }
    },
    renderGlobalPage() { this.rows = pageTriggerRows(this.globalRows || [], this.query.pageNum, this.query.pageSize) },
    handlePagination() {
      if (this.globalRows) this.renderGlobalPage()
      else this.load()
    },
    async saveSort() {
      if (this.sortLockedByKeyword) return this.$modal.msgWarning('排序筛选结果不能调整全局排序，请清空关键词后重试')
      if (!this.sortDirty || this.sorting) return
      this.sorting = true
      try {
        const items = changedTriggerSortItems(this.globalRows, this.initialGlobalSort)
        await sortTriggerRules({ actionId: this.actionId('sort'), items: items })
        this.$modal.msgSuccess('触发规则排序已保存')
        await this.load()
      } catch (error) {
        this.showError(error, '保存排序失败，请刷新后重试')
      } finally { this.sorting = false }
    },
    async handoffSimulation(row) {
      this.$router.push({ name: 'TodoConfigSimulation', query: { triggerRuleId: this.rowId(row), eventType: this.field(row, 'eventType', 'event_type'), payloadVersion: this.field(row, 'payloadVersion', 'payload_version') } })
    },
    format(value) { return value ? this.parseTime(value, '{y}-{m}-{d} {h}:{i}:{s}') : '-' },
    showError(error, fallback) { this.$modal.msgError((error && (error.msg || error.message)) || fallback) }
  }
}
</script>

<style scoped lang="scss">
.cell-subtitle { display: block; margin-top: 4px; color: #94a3b8; font-size: 12px; font-weight: 400; }
::v-deep .config-page-shell__filters .el-alert { width: 100%; }
</style>
