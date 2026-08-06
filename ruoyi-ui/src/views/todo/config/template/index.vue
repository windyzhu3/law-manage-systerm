<template>
  <section class="template-workbench" data-testid="template-workbench">
    <header class="workbench-header">
      <div class="workbench-header__copy">
        <span class="workbench-header__eyebrow">TODO ENGINE</span>
        <h1>待办配置工作台</h1>
        <p>按业务旅程完成配置、模拟与发布</p>
      </div>
      <el-button
        v-hasPermi="['todo:template:create']"
        class="workbench-header__action"
        type="primary"
        icon="el-icon-plus"
        @click="createDraft"
      >
        新建配置
      </el-button>
    </header>

    <template-problem-summary
      :summary="problemSummary"
      :active-filter="query.issueType"
      @filter="applyIssueFilter"
    />

    <section class="template-workbench__table-section">
      <div class="workbench-toolbar">
        <div class="workbench-toolbar__title">
          <h2>配置任务</h2>
          <span>共 {{ total }} 项</span>
        </div>
        <div class="workbench-filters">
          <el-input
            v-model.trim="query.keyword"
            class="workbench-filters__search"
            clearable
            prefix-icon="el-icon-search"
            placeholder="搜索业务场景或模板编码"
            @keyup.enter.native="search"
            @clear="search"
          />
          <el-select v-model="query.businessType" clearable placeholder="业务类型" @change="search">
            <el-option
              v-for="item in dict.type.law_todo_business_type"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
          <el-select v-model="query.businessStage" clearable placeholder="业务阶段" @change="search">
            <el-option
              v-for="item in dict.type.law_todo_business_stage"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
          <el-select v-model="query.publishStatus" clearable placeholder="配置状态" @change="search">
            <el-option label="配置中" value="DRAFT" />
            <el-option label="已发布" value="PUBLISHED" />
          </el-select>
          <el-select v-model="query.status" clearable placeholder="运行状态" @change="search">
            <el-option label="运行中" value="0" />
            <el-option label="已停用" value="1" />
          </el-select>
          <el-select v-model="query.issueType" clearable placeholder="健康状态" @change="search">
            <el-option label="存在阻塞" value="BLOCKER" />
            <el-option label="存在警告" value="WARNING" />
            <el-option label="可继续" value="READY" />
          </el-select>
          <el-button @click="resetQuery">重置</el-button>
        </div>
      </div>

      <el-table
        v-loading="loading"
        :data="rows"
        class="template-workbench__table"
        empty-text="暂无待办配置任务"
        row-key="templateId"
        @row-click="continueConfiguration"
      >
        <el-table-column label="业务场景" min-width="280">
          <template slot-scope="{ row }">
            <div class="template-scenario">
              <strong>{{ row.templateName || '未命名配置' }}</strong>
              <span>
                {{ businessTypeLabel(row.businessType) }}
                <i aria-hidden="true" />
                {{ businessStageLabel(row.businessStage) }}
              </span>
              <small v-if="row.templateCode" class="template-code">{{ row.templateCode }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="配置进度" min-width="220">
          <template slot-scope="{ row }">
            <template-progress-cell :row="row" />
          </template>
        </el-table-column>
        <el-table-column label="运行状态" min-width="220">
          <template slot-scope="{ row }">
            <div class="template-runtime-state">
              <el-tag :type="runtimePresentation(row).type" size="small">
                {{ runtimePresentation(row).label }}
              </el-tag>
              <small
                v-if="runtimePresentation(row).replacementText"
                class="template-replacement"
              >
                {{ runtimePresentation(row).replacementText }}
              </small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="健康状态" min-width="180">
          <template slot-scope="{ row }">
            <div class="template-health">
              <el-tag v-if="number(row.blockerCount)" size="small" type="danger">
                {{ number(row.blockerCount) }} 项阻塞
              </el-tag>
              <el-tag v-else-if="number(row.warningCount)" size="small" type="warning">
                {{ number(row.warningCount) }} 项警告
              </el-tag>
              <el-tag v-else size="small" type="success">可继续</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="最近修改" min-width="190">
          <template slot-scope="{ row }">
            <div class="template-updated">
              <strong>{{ row.lastEditor || '系统' }}</strong>
              <span>{{ formatTime(row.updateTime) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="290" align="right">
          <template slot-scope="{ row }">
            <el-button
              v-hasPermi="['todo:template:list']"
              class="template-primary-action"
              type="text"
              icon="el-icon-right"
              @click.stop="continueConfiguration(row)"
            >
              {{ primaryActionLabel(row) }}
            </el-button>
            <el-button
              v-if="runtimePresentation(row).state === 'REPLACED'"
              v-hasPermi="['todo:template:list']"
              type="text"
              @click.stop="openHistory(row)"
            >
              查看历史配置
            </el-button>
            <el-button
              v-else-if="togglePresentation(row)"
              v-hasPermi="['todo:template:toggle']"
              type="text"
              :loading="Boolean(rowToggleLoading[row.templateId])"
              @click.stop="toggleRow(row)"
            >
              {{ togglePresentation(row).label }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="total > 0"
        :total="total"
        :page.sync="query.pageNum"
        :limit.sync="query.pageSize"
        @pagination="load"
      />
    </section>

    <template-drawer
      :visible.sync="createDrawerOpen"
      :detail="null"
      mode="create"
      initial-step="basic"
      @saved="afterDraftCreated"
    />
  </section>
</template>

<script>
import TemplateDrawer from './TemplateDrawer'
import TemplateProblemSummary from './TemplateProblemSummary'
import TemplateProgressCell from './TemplateProgressCell'
import { listTodoTemplateWorkbench, toggleTodoTemplate } from '@/api/todo-config'
import {
  resolveProblemSummary,
  templateRuntimePresentation,
  templateNavigationTarget,
  templateTogglePresentation
} from './template-workbench-model'

const emptyQuery = () => ({
  pageNum: 1,
  pageSize: 20,
  keyword: '',
  businessType: '',
  businessStage: '',
  publishStatus: '',
  status: '',
  issueType: ''
})

export default {
  name: 'TodoTemplateConfig',
  components: {
    TemplateDrawer,
    TemplateProblemSummary,
    TemplateProgressCell
  },
  dicts: ['law_todo_business_stage', 'law_todo_business_type'],
  data() {
    return {
      loading: false,
      rows: [],
      total: 0,
      serverSummary: {},
      query: emptyQuery(),
      createDrawerOpen: false,
      rowToggleLoading: {}
    }
  },
  computed: {
    problemSummary() {
      return resolveProblemSummary(this.serverSummary, this.rows)
    }
  },
  created() {
    this.load()
  },
  methods: {
    number(value) {
      const parsed = Number(value)
      return Number.isFinite(parsed) ? parsed : 0
    },
    runtimePresentation(row) {
      return templateRuntimePresentation(row)
    },
    togglePresentation(row) {
      return templateTogglePresentation(row)
    },
    primaryActionLabel(row) {
      if (row && row.primaryAction === 'OPEN_REPLACEMENT') return '打开现行模板'
      return row && row.primaryAction === 'VIEW_PUBLISHED' ? '查看已发布版本' : '继续配置'
    },
    async load() {
      this.loading = true
      try {
        const response = await listTodoTemplateWorkbench(this.query)
        this.rows = Array.isArray(response.rows) ? response.rows : []
        this.total = this.number(response.total)
        this.serverSummary = {
          blockerTemplates: response.blockerTemplates,
          warningTemplates: response.warningTemplates,
          readyTemplates: response.readyTemplates
        }
      } catch (error) {
        this.rows = []
        this.total = 0
        this.serverSummary = {}
        this.$modal.msgError((error && (error.msg || error.message)) || '加载配置任务失败')
      } finally {
        this.loading = false
      }
    },
    search() {
      this.query.pageNum = 1
      this.load()
    },
    resetQuery() {
      this.query = emptyQuery()
      this.load()
    },
    applyIssueFilter(issueType) {
      this.query.issueType = this.query.issueType === issueType ? '' : issueType
      this.search()
    },
    createDraft() {
      this.createDrawerOpen = true
    },
    afterDraftCreated(templateId) {
      this.createDrawerOpen = false
      if (templateId) {
        this.openJourney(templateId, 'draft')
      } else {
        this.load()
      }
    },
    continueConfiguration(row) {
      const target = templateNavigationTarget(row)
      this.openJourney(target.templateId, target.view)
    },
    openHistory(row) {
      this.openJourney(row && row.templateId, 'published')
    },
    openJourney(templateId, view) {
      if (!templateId) return
      this.$router.push({
        path: '/todo-engine/todo-template-journey',
        query: {
          templateId: String(templateId),
          view: view === 'published' ? 'published' : 'draft'
        }
      })
    },
    actionId(action) {
      return `template-${action}-${Date.now()}-${Math.random().toString(16).slice(2)}`
    },
    async toggleRow(row) {
      const presentation = templateTogglePresentation(row)
      const templateId = Number(row && row.templateId)
      if (!presentation || !templateId || this.rowToggleLoading[templateId]) return
      this.$set(this.rowToggleLoading, templateId, true)
      try {
        await this.$confirm(presentation.confirmText, presentation.label, {
          type: 'warning',
          confirmButtonText: `确认${presentation.label}`
        })
        await toggleTodoTemplate(templateId, {
          status: presentation.targetStatus,
          actionId: this.actionId('toggle'),
          expectedVersion: Number(row.lockVersion || 0)
        })
        this.$modal.msgSuccess(`模板已${presentation.targetStatus === '0' ? '启用' : '停用'}`)
        await this.load()
      } catch (error) {
        if (error !== 'cancel' && error !== 'close') {
          this.$modal.msgError((error && (error.msg || error.message)) || '模板运行状态更新失败')
        }
      } finally {
        this.$set(this.rowToggleLoading, templateId, false)
      }
    },
    businessTypeLabel(value) {
      return this.dictLabel(this.dict.type.law_todo_business_type, value, '未设置业务类型')
    },
    businessStageLabel(value) {
      return this.dictLabel(this.dict.type.law_todo_business_stage, value, '未设置业务阶段')
    },
    dictLabel(options, value, fallback) {
      const item = (options || []).find(option => String(option.value) === String(value || ''))
      return item ? item.label : (value || fallback)
    },
    formatTime(value) {
      return value ? this.parseTime(value, '{y}-{m}-{d} {h}:{i}') : '暂无记录'
    }
  }
}
</script>

<style scoped lang="scss">
@import '../styles/config-center.scss';
</style>
