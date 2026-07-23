<template>
  <config-page-shell
    title="配置资源中心"
    subtitle="统一治理事件、业务字段、材料、校验器、工作日历和模拟数据源，让模板配置有内容可选、有来源可查。"
    :loading="loading"
  >
    <template #secondary-actions>
      <el-button icon="el-icon-refresh" size="small" @click="refresh">刷新当前资源</el-button>
    </template>
    <template #primary-action>
      <el-button
        v-if="activeTab === 'events'"
        v-hasPermi="['todo:resource:add']"
        type="primary"
        size="small"
        icon="el-icon-plus"
        @click="createEvent"
      >新增事件</el-button>
      <el-button
        v-else-if="activeTab === 'calendars' && canManageCalendars"
        type="primary"
        size="small"
        icon="el-icon-plus"
        @click="$refs.calendarDialog.show({})"
      >新增工作日历</el-button>
    </template>

    <el-tabs v-model="activeTab" class="resource-tabs" @tab-click="tabChanged">
      <el-tab-pane label="事件目录" name="events">
        <div class="resource-filters">
          <el-input v-model.trim="query.keyword" clearable prefix-icon="el-icon-search" placeholder="搜索事件编码或名称" @keyup.enter.native="loadEvents" @clear="loadEvents" />
          <el-select v-model="query.businessObjectType" clearable placeholder="业务对象" @change="loadEvents">
            <el-option v-for="item in businessTypes" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="query.status" clearable placeholder="全部状态" @change="loadEvents">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已启用" value="ACTIVE" />
            <el-option label="已停用" value="DISABLED" />
          </el-select>
          <el-button type="primary" plain @click="loadEvents">查询</el-button>
        </div>
        <el-table :data="events" stripe @row-dblclick="viewEvent">
          <el-table-column label="事件" min-width="230">
            <template #default="{ row }"><strong>{{ row.eventName || row.eventType }}</strong><small class="cell-note">{{ row.eventType }}</small></template>
          </el-table-column>
          <el-table-column prop="payloadVersion" label="版本" width="80"><template #default="{ row }">v{{ row.payloadVersion }}</template></el-table-column>
          <el-table-column label="业务对象" width="110"><template #default="{ row }">{{ businessLabel(row.businessObjectType) }}</template></el-table-column>
          <el-table-column prop="sourceModule" label="来源模块" width="120" />
          <el-table-column label="字段健康" width="110"><template #default="{ row }"><el-tag :type="row.schemaStatus === 'READY' ? 'success' : 'warning'" size="small">{{ row.schemaStatus === 'READY' ? '可配置' : '待完善' }}</el-tag></template></el-table-column>
          <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
          <el-table-column prop="referenceCount" label="引用" width="70" align="center" />
          <el-table-column prop="updateTime" label="更新时间" width="160"><template #default="{ row }">{{ parseTime(row.updateTime, '{y}-{m}-{d} {h}:{i}') }}</template></el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button v-hasPermi="['todo:resource:query']" type="text" @click="viewEvent(row)">查看</el-button>
              <el-button v-if="row.status === 'DRAFT'" v-hasPermi="['todo:resource:edit']" type="text" @click="editEvent(row)">编辑</el-button>
              <el-button v-if="row.status !== 'DRAFT'" v-hasPermi="['todo:resource:add']" type="text" @click="openVersion(row)">新版本</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!loading && !events.length" description="没有找到事件资源。新增事件后，模板即可选择事件和业务字段。" :image-size="72" />
        <pagination v-show="total > 0" :total="total" :page.sync="query.pageNum" :limit.sync="query.pageSize" @pagination="loadEvents" />
      </el-tab-pane>

      <el-tab-pane label="业务资源" name="business">
        <business-resource-panel ref="business" />
      </el-tab-pane>

      <el-tab-pane label="校验器目录" name="validators">
        <validator-catalog-panel ref="validators" />
      </el-tab-pane>

      <el-tab-pane label="工作日历" name="calendars">
        <section class="calendar-panel">
          <header>
            <div><h2>工作日历</h2><p>定义待办 SLA 的工作日、工作时段和例外日期。模板只可选择已启用日历。</p></div>
          </header>
          <el-table :data="calendars" stripe>
            <el-table-column label="日历" min-width="180">
              <template #default="{ row }"><strong>{{ field(row, 'calendarName', 'calendar_name') }}</strong><small class="cell-note">{{ field(row, 'calendarCode', 'calendar_code') }}</small></template>
            </el-table-column>
            <el-table-column label="时区" min-width="150"><template #default="{ row }">{{ field(row, 'timezone', 'timezone') || 'Asia/Shanghai' }}</template></el-table-column>
            <el-table-column v-if="canManageCalendars" label="工作日" min-width="140"><template #default="{ row }">{{ workDaysLabel(field(row, 'workDays', 'work_days')) }}</template></el-table-column>
            <el-table-column v-if="canManageCalendars" label="工作时段" width="150"><template #default="{ row }">{{ field(row, 'workStart', 'work_start') }}–{{ field(row, 'workEnd', 'work_end') }}</template></el-table-column>
            <el-table-column v-if="canManageCalendars" label="操作" width="90"><template #default="{ row }"><el-button type="text" @click="$refs.calendarDialog.show(row)">编辑</el-button></template></el-table-column>
          </el-table>
          <el-alert v-if="!canManageCalendars" title="当前为只读目录；日历维护需要“todo:calendar:manage”权限。" type="info" :closable="false" show-icon />
        </section>
      </el-tab-pane>

      <el-tab-pane label="数据源状态" name="dataSources">
        <business-data-source-panel ref="dataSources" />
      </el-tab-pane>
    </el-tabs>

    <event-resource-drawer
      :visible.sync="drawer.open"
      :resource-id="drawer.id"
      :mode="drawer.mode"
      @saved="saved"
      @version-created="versionCreated"
    />
    <work-calendar-dialog ref="calendarDialog" :on-save="saveCalendar" />
  </config-page-shell>
</template>

<script>
import ConfigPageShell from '../shared/ConfigPageShell'
import EventResourceDrawer from './EventResourceDrawer'
import ValidatorCatalogPanel from './ValidatorCatalogPanel'
import BusinessResourcePanel from './BusinessResourcePanel'
import BusinessDataSourcePanel from './BusinessDataSourcePanel'
import WorkCalendarDialog from '../components/WorkCalendarDialog'
import { listEventResources, createEventResourceVersion } from '@/api/todo-resources'
import { listTemplateCalendarCatalog } from '@/api/todo-config'
import { listWorkCalendars, saveWorkCalendar } from '@/api/todo-definition'

export default {
  name: 'TodoConfigurationResourceCenter',
  components: {
    ConfigPageShell,
    EventResourceDrawer,
    ValidatorCatalogPanel,
    BusinessResourcePanel,
    BusinessDataSourcePanel,
    WorkCalendarDialog
  },
  data() {
    return {
      activeTab: 'events',
      loading: false,
      events: [],
      total: 0,
      calendars: [],
      businessTypes: [
        { value: 'LEAD', label: '线索' },
        { value: 'CUSTOMER', label: '客户' },
        { value: 'CONTRACT', label: '合同' },
        { value: 'CASE', label: '案件' },
        { value: 'MATTER', label: '事项' }
      ],
      query: { pageNum: 1, pageSize: 10, keyword: '', businessObjectType: '', status: '' },
      drawer: { open: false, id: null, mode: 'view' }
    }
  },
  computed: {
    clientPermissions() {
      return (this.$store && this.$store.getters && this.$store.getters.permissions) || []
    },
    canManageCalendars() {
      return this.clientPermissions.includes('*:*:*') || this.clientPermissions.includes('todo:calendar:manage')
    }
  },
  created() { this.loadEvents() },
  methods: {
    field(row, camel, snake) { return row && (row[camel] !== undefined ? row[camel] : row[snake]) },
    async loadEvents() {
      this.loading = true
      try {
        const response = await listEventResources(this.query)
        this.events = response.rows || []
        this.total = Number(response.total || 0)
      } catch (error) {
        this.events = []
        this.total = 0
        this.$modal.msgError((error && (error.msg || error.message)) || '加载事件目录失败')
      } finally {
        this.loading = false
      }
    },
    async loadCalendars() {
      this.loading = true
      try {
        const response = await (this.canManageCalendars ? listWorkCalendars() : listTemplateCalendarCatalog())
        this.calendars = response.data || []
      } catch (error) {
        this.calendars = []
        this.$modal.msgError((error && (error.msg || error.message)) || '加载工作日历失败')
      } finally {
        this.loading = false
      }
    },
    tabChanged() {
      if (this.activeTab === 'calendars') this.loadCalendars()
    },
    refresh() {
      if (this.activeTab === 'events') return this.loadEvents()
      if (this.activeTab === 'calendars') return this.loadCalendars()
      const component = this.$refs[this.activeTab]
      if (component && component.load) return component.load()
    },
    createEvent() { this.drawer = { open: true, id: null, mode: 'create' } },
    viewEvent(row) { this.drawer = { open: true, id: row.eventCatalogId, mode: 'view' } },
    editEvent(row) { this.drawer = { open: true, id: row.eventCatalogId, mode: 'edit' } },
    async openVersion(row) {
      try {
        const response = await createEventResourceVersion(row.eventCatalogId, { actionId: `event-version-${Date.now()}` })
        this.$modal.msgSuccess('已创建新版本')
        this.drawer = { open: true, id: response.data, mode: 'edit' }
        this.loadEvents()
      } catch (error) {
        this.$modal.msgError((error && (error.msg || error.message)) || '创建新版本失败')
      }
    },
    async saveCalendar(data) {
      await saveWorkCalendar(data)
      this.$modal.msgSuccess('工作日历已保存')
      await this.loadCalendars()
    },
    saved() { this.loadEvents() },
    versionCreated(id) { this.drawer = { open: true, id, mode: 'edit' }; this.loadEvents() },
    statusLabel(status) { return ({ DRAFT: '草稿', ACTIVE: '已启用', DISABLED: '已停用' })[status] || status },
    statusType(status) { return ({ ACTIVE: 'success', DISABLED: 'info', DRAFT: 'warning' })[status] || '' },
    businessLabel(type) { return (this.businessTypes.find(item => item.value === type) || {}).label || type },
    workDaysLabel(value) {
      const labels = { 1: '一', 2: '二', 3: '三', 4: '四', 5: '五', 6: '六', 7: '日' }
      return String(value || '').split(',').filter(Boolean).map(day => `周${labels[day] || day}`).join('、')
    }
  }
}
</script>

<style scoped lang="scss">
.resource-tabs {
  padding: 0 2px;
}

.resource-filters {
  display: grid;
  grid-template-columns: minmax(240px, 1fr) 150px 130px auto;
  gap: 10px;
  margin-bottom: 16px;
}

.cell-note {
  display: block;
  margin-top: 4px;
  font-family: Consolas, monospace;
  font-size: 11px;
  color: #718096;
}

.calendar-panel > header {
  margin-bottom: 16px;

  h2 {
    margin: 0;
    font-size: 18px;
    color: #0B2A55;
  }

  p {
    margin: 5px 0 0;
    font-size: 13px;
    color: #65758A;
  }
}

.calendar-panel > .el-alert {
  margin-top: 14px;
}

@media (max-width: 900px) {
  .resource-filters {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 640px) {
  .resource-filters {
    grid-template-columns: 1fr;
  }
}
</style>
