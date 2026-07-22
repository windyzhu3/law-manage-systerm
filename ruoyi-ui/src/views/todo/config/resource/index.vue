<template>
  <config-page-shell
    title="配置资源中心"
    subtitle="统一管理事件、Payload 字段、校验器和业务材料，让规则配置有内容可选、有来源可查。"
    :loading="activeTab === 'events' && loading"
  >
    <template #secondary-actions><el-button icon="el-icon-refresh" size="small" @click="refresh">刷新</el-button></template>
    <template #primary-action><el-button v-if="activeTab === 'events'" v-hasPermi="['todo:resource:add']" type="primary" size="small" icon="el-icon-plus" @click="createEvent">新增事件</el-button></template>

    <el-tabs v-model="activeTab" class="resource-tabs">
      <el-tab-pane label="事件目录" name="events">
        <div class="resource-filters">
          <el-input v-model.trim="query.keyword" clearable prefix-icon="el-icon-search" placeholder="搜索事件编码或名称" @keyup.enter.native="loadEvents" @clear="loadEvents" />
          <el-select v-model="query.businessObjectType" clearable placeholder="业务对象" @change="loadEvents"><el-option v-for="item in businessTypes" :key="item" :label="item" :value="item" /></el-select>
          <el-select v-model="query.status" clearable placeholder="全部状态" @change="loadEvents"><el-option label="草稿" value="DRAFT" /><el-option label="已启用" value="ACTIVE" /><el-option label="已停用" value="DISABLED" /></el-select>
          <el-button type="primary" plain @click="loadEvents">查询</el-button>
        </div>
        <el-table :data="events" border stripe @row-dblclick="viewEvent">
          <el-table-column label="事件" min-width="230"><template #default="{ row }"><strong>{{ row.eventName || row.eventType }}</strong><p class="event-code">{{ row.eventType }}</p></template></el-table-column>
          <el-table-column prop="payloadVersion" label="版本" width="80"><template #default="{ row }">v{{ row.payloadVersion }}</template></el-table-column>
          <el-table-column prop="businessObjectType" label="业务对象" width="110" />
          <el-table-column prop="sourceModule" label="来源模块" width="120" />
          <el-table-column label="Schema" width="100"><template #default="{ row }"><el-tag :type="row.schemaStatus === 'READY' ? 'success' : 'warning'" size="small">{{ row.schemaStatus === 'READY' ? '可配置' : '待完善' }}</el-tag></template></el-table-column>
          <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
          <el-table-column prop="referenceCount" label="引用" width="70" align="center" />
          <el-table-column prop="updateTime" label="更新时间" width="160"><template #default="{ row }">{{ parseTime(row.updateTime, '{y}-{m}-{d} {h}:{i}') }}</template></el-table-column>
          <el-table-column label="操作" width="160" fixed="right"><template #default="{ row }"><el-button v-hasPermi="['todo:resource:query']" type="text" @click="viewEvent(row)">查看</el-button><el-button v-if="row.status === 'DRAFT'" v-hasPermi="['todo:resource:edit']" type="text" @click="editEvent(row)">编辑</el-button><el-button v-if="row.status !== 'DRAFT'" v-hasPermi="['todo:resource:add']" type="text" @click="openVersion(row)">新版本</el-button></template></el-table-column>
        </el-table>
        <el-empty v-if="!loading && !events.length" description="没有找到事件资源。新增事件后，触发规则即可选择事件和 Payload 字段。" :image-size="72" />
        <pagination v-show="total > 0" :total="total" :page.sync="query.pageNum" :limit.sync="query.pageSize" @pagination="loadEvents" />
      </el-tab-pane>
      <el-tab-pane label="校验器目录" name="validators"><validator-catalog-panel ref="validators" /></el-tab-pane>
      <el-tab-pane label="业务字段与材料" name="business"><business-resource-panel ref="business" /></el-tab-pane>
    </el-tabs>

    <event-resource-drawer :visible.sync="drawer.open" :resource-id="drawer.id" :mode="drawer.mode" @saved="saved" @version-created="versionCreated" />
  </config-page-shell>
</template>

<script>
import ConfigPageShell from '../shared/ConfigPageShell'
import EventResourceDrawer from './EventResourceDrawer'
import ValidatorCatalogPanel from './ValidatorCatalogPanel'
import BusinessResourcePanel from './BusinessResourcePanel'
import { listEventResources, createEventResourceVersion } from '@/api/todo-resources'

export default {
  name: 'TodoConfigurationResourceCenter',
  components: { ConfigPageShell, EventResourceDrawer, ValidatorCatalogPanel, BusinessResourcePanel },
  data() { return { activeTab: 'events', loading: false, events: [], total: 0, businessTypes: ['LEAD', 'CUSTOMER', 'CONTRACT', 'CASE', 'MATTER'], query: { pageNum: 1, pageSize: 10, keyword: '', businessObjectType: '', status: '' }, drawer: { open: false, id: null, mode: 'view' } } },
  created() { this.loadEvents() },
  methods: {
    async loadEvents() { this.loading = true; try { const response = await listEventResources(this.query); this.events = response.rows || []; this.total = Number(response.total || 0) } catch (error) { this.events = []; this.total = 0; this.$modal.msgError('加载事件目录失败') } finally { this.loading = false } },
    refresh() { if (this.activeTab === 'events') this.loadEvents(); else if (this.$refs[this.activeTab] && this.$refs[this.activeTab].load) this.$refs[this.activeTab].load() },
    createEvent() { this.drawer = { open: true, id: null, mode: 'create' } },
    viewEvent(row) { this.drawer = { open: true, id: row.eventCatalogId, mode: 'view' } },
    editEvent(row) { this.drawer = { open: true, id: row.eventCatalogId, mode: 'edit' } },
    async openVersion(row) { try { const response = await createEventResourceVersion(row.eventCatalogId, { actionId: `event-version-${Date.now()}` }); this.$modal.msgSuccess('已创建新版本'); this.drawer = { open: true, id: response.data, mode: 'edit' }; this.loadEvents() } catch (error) { this.$modal.msgError((error && (error.msg || error.message)) || '创建新版本失败') } },
    saved() { this.loadEvents() },
    versionCreated(id) { this.drawer = { open: true, id, mode: 'edit' }; this.loadEvents() },
    statusLabel(status) { return ({ DRAFT: '草稿', ACTIVE: '已启用', DISABLED: '已停用' })[status] || status },
    statusType(status) { return ({ ACTIVE: 'success', DISABLED: 'info', DRAFT: 'warning' })[status] || '' }
  }
}
</script>

<style scoped lang="scss">
.resource-tabs { padding: 0 2px; }.resource-filters { display: grid; grid-template-columns: minmax(240px,1fr) 150px 130px auto; gap: 10px; margin-bottom: 16px; }.event-code { margin: 4px 0 0; color: #64748b; font-family: Consolas,monospace; font-size: 12px; }
@media (max-width: 900px) { .resource-filters { grid-template-columns: 1fr 1fr; } }
</style>
