<template>
  <section v-loading="loading" class="journey-step event-step">
    <header class="journey-step__header">
      <span class="journey-step__eyebrow">第一步 · 业务事件</span>
      <h2>什么业务变化会创建这张待办？</h2>
      <p>选择一个已治理的业务事件版本。事件字段完整后，后续条件和负责人配置才有可靠的数据来源。</p>
    </header>

    <div class="event-step__filters">
      <el-input
        ref="eventSearch"
        v-model.trim="filters.keyword"
        :class="{ 'is-focus-restored': focusRestoredTarget === 'eventSearch' }"
        clearable
        prefix-icon="el-icon-search"
        placeholder="搜索事件名称、说明或来源"
      />
      <el-select v-model="filters.source" clearable placeholder="全部来源">
        <el-option v-for="source in sourceOptions" :key="source" :label="source" :value="source" />
      </el-select>
      <el-select v-model="filters.status" clearable placeholder="全部状态">
        <el-option label="可配置" value="ACTIVE" />
        <el-option label="待完善" value="DRAFT" />
        <el-option label="已停用" value="DISABLED" />
      </el-select>
    </div>

    <div class="event-step__layout">
      <div class="event-step__catalog">
        <div v-for="group in groupedEvents" :key="group.name" class="event-group">
          <div class="event-group__title">
            <span>{{ businessLabel(group.name) }}</span>
            <small>{{ group.items.length }} 个事件版本</small>
          </div>
          <button
            v-for="event in group.items"
            :key="eventKey(event)"
            type="button"
            class="event-option"
            :class="{ 'is-selected': eventKey(event) === selectedKey, 'is-unavailable': !selectable(event) }"
            @click="selectEvent(event)"
          >
            <span class="event-option__marker"><i :class="eventKey(event) === selectedKey ? 'el-icon-check' : 'el-icon-bell'" /></span>
            <span class="event-option__content">
              <strong>{{ event.eventName || '未命名业务事件' }}</strong>
              <small>{{ timingLabel(event) }} · {{ event.sourceModule || '业务系统' }}</small>
            </span>
            <span class="event-option__meta">
              <el-tag size="mini" :type="selectable(event) ? 'success' : 'warning'">
                {{ selectable(event) ? `v${event.payloadVersion} 可配置` : '待完善' }}
              </el-tag>
            </span>
          </button>
        </div>
        <el-empty v-if="!groupedEvents.length" description="没有符合条件的事件" :image-size="64" />
      </div>

      <article class="event-detail">
        <template v-if="selected">
          <div class="event-detail__heading">
            <div>
              <span>已选择事件</span>
              <h3>{{ selected.eventName || '未命名业务事件' }}</h3>
            </div>
            <el-tag :type="schemaHealth.ready ? 'success' : 'danger'" size="small">
              {{ schemaHealth.ready ? '字段完整' : '字段不完整' }}
            </el-tag>
          </div>
          <dl class="event-detail__facts">
            <div><dt>发生时机</dt><dd>{{ timingLabel(selected) }}</dd></div>
            <div><dt>来源业务</dt><dd>{{ businessLabel(selected.businessObjectType) }} · {{ selected.sourceModule || '业务系统' }}</dd></div>
            <div><dt>事件版本</dt><dd>v{{ selected.payloadVersion }}</dd></div>
            <div><dt>版本状态</dt><dd>{{ statusLabel(selected.status) }}</dd></div>
          </dl>
          <div class="event-detail__description">
            <strong>业务说明</strong>
            <p>{{ selected.description || '该事件用于在对应业务节点完成后创建后续待办。' }}</p>
          </div>
          <div class="event-detail__description">
            <strong>示例业务对象</strong>
            <p>{{ sampleSummary }}</p>
          </div>
          <div
            ref="schemaRepair"
            class="event-detail__fields"
            :class="{ 'is-focus-restored': focusRestoredTarget === 'schemaRepair' }"
            tabindex="-1"
          >
            <strong>可用于后续配置的字段</strong>
            <div v-if="selectedFields.length" class="event-detail__field-list">
              <span v-for="field in selectedFields.slice(0, 8)" :key="field.code">
                {{ field.name || '未命名业务字段' }}<small>{{ fieldTypeLabel(field) }}</small>
              </span>
            </div>
            <p v-else>尚未形成可用字段目录。</p>
          </div>
          <div v-if="!schemaHealth.ready" class="event-detail__repair">
            <el-alert
              title="该事件暂不能用于模板"
              description="请先补齐并启用事件字段；当前步骤会阻塞发布。"
              type="error"
              :closable="false"
              show-icon
            />
            <el-button
              v-if="!readonly"
              type="text"
              @click="repairSchema"
            >维护事件字段</el-button>
          </div>
          <details class="event-detail__technical">
            <summary>技术详情</summary>
            <p>事件编码：{{ selected.eventType }} · Payload v{{ selected.payloadVersion }}</p>
          </details>
        </template>
        <el-empty v-else description="请从左侧选择业务事件" :image-size="72" />
      </article>
    </div>
  </section>
</template>

<script>
import { listEventResources, getEventResource } from '@/api/todo-resources'
import { buildEventPatch, eventSchemaHealth, repairFocusTarget } from '../journey-step-model'

export default {
  name: 'EventStep',
  props: {
    value: { type: Object, default: () => ({}) },
    resources: { type: Object, default: () => ({}) },
    businessType: { type: String, default: '' },
    permissions: { type: Array, default: () => [] },
    readonly: Boolean,
    resourceRevision: { type: Number, default: 0 }
  },
  data() {
    return {
      loading: false,
      events: [],
      detail: null,
      focusRestoredTarget: '',
      focusTimer: null,
      filters: { keyword: '', source: '', status: '' }
    }
  },
  computed: {
    selectedKey() { return this.value.eventType ? `${this.value.eventType}:${Number(this.value.payloadVersion || 1)}` : '' },
    selected() {
      return this.detail || this.events.find(item => this.eventKey(item) === this.selectedKey) || null
    },
    selectedFields() {
      const eventType = this.selected && this.selected.eventType
      return (this.resources.fields || []).filter(field => (field.sourceEvents || []).includes(eventType))
    },
    schemaHealth() { return eventSchemaHealth(this.selected, this.resources.fields || []) },
    filteredEvents() {
      const keyword = this.filters.keyword.toLowerCase()
      return this.events.filter(event => {
        const matchesKeyword = !keyword || [
          event.eventName, event.eventType, event.description, event.sourceModule
        ].some(value => String(value || '').toLowerCase().includes(keyword))
        return matchesKeyword &&
          (!this.filters.source || event.sourceModule === this.filters.source) &&
          (!this.filters.status || event.status === this.filters.status)
      })
    },
    groupedEvents() {
      const groups = new Map()
      this.filteredEvents.forEach(event => {
        const key = event.businessObjectType || 'OTHER'
        if (!groups.has(key)) groups.set(key, [])
        groups.get(key).push(event)
      })
      return Array.from(groups.entries()).map(([name, items]) => ({ name, items }))
    },
    sourceOptions() {
      return [...new Set(this.events.map(item => item.sourceModule).filter(Boolean))].sort()
    },
    canReadRichCatalog() {
      return this.permissions.includes('*:*:*') || this.permissions.includes('todo:resource:list')
    },
    sampleSummary() {
      const value = this.selected && this.selected.samplePayloadJson
      if (!value) return '保存业务对象后，系统将按事件字段自动带入示例数据。'
      try {
        const parsed = typeof value === 'string' ? JSON.parse(value) : value
        const keys = Object.keys(parsed || {})
        return keys.length ? `示例已包含 ${keys.length} 个受治理业务字段。` : '示例对象暂未填写业务字段。'
      } catch (_) {
        return '示例对象暂不可用，请在事件资源中修复。'
      }
    }
  },
  watch: {
    selectedKey: { immediate: true, handler() { this.loadSelectedDetail() } },
    resourceRevision() { this.loadEvents() },
    'resources.events': {
      deep: true,
      handler(value) {
        if (!this.canReadRichCatalog) this.events = Array.isArray(value) ? value.slice() : []
      }
    }
  },
  created() { this.loadEvents() },
  beforeDestroy() { if (this.focusTimer) clearTimeout(this.focusTimer) },
  methods: {
    async loadEvents() {
      if (!this.canReadRichCatalog) {
        this.events = (this.resources.events || []).slice()
        await this.loadSelectedDetail()
        return
      }
      this.loading = true
      try {
        const response = await listEventResources({
          pageNum: 1,
          pageSize: 200,
          businessObjectType: this.businessType || undefined
        })
        this.events = response.rows || []
        await this.loadSelectedDetail()
      } catch (error) {
        this.events = []
        this.$modal.msgError((error && (error.msg || error.message)) || '加载业务事件失败')
      } finally {
        this.loading = false
      }
    },
    async loadSelectedDetail() {
      const summary = this.events.find(item => this.eventKey(item) === this.selectedKey)
      if (!summary || !summary.eventCatalogId) {
        this.detail = null
        return
      }
      try {
        const response = await getEventResource(summary.eventCatalogId)
        this.detail = { ...summary, ...(response.data || {}) }
      } catch (_) {
        this.detail = summary
      }
    },
    eventKey(event) { return `${event.eventType}:${Number(event.payloadVersion || 1)}` },
    selectable(event) {
      return event.status === 'ACTIVE' && event.schemaStatus === 'READY'
    },
    async selectEvent(event) {
      if (this.readonly) return
      if (!this.selectable(event)) {
        this.detail = event
        await this.loadSelectedResource(event)
        return
      }
      this.detail = event
      this.$emit('patch', { event: buildEventPatch(event) })
      await this.loadSelectedResource(event)
    },
    async loadSelectedResource(event) {
      if (!event.eventCatalogId) return
      try {
        const response = await getEventResource(event.eventCatalogId)
        this.detail = { ...event, ...(response.data || {}) }
      } catch (_) { /* summary remains usable */ }
    },
    repairSchema() {
      const event = this.selected
      this.$emit('repair-resource', {
        type: 'EVENT',
        resourceId: event && event.eventCatalogId,
        eventType: event && event.eventType,
        payloadVersion: event && event.payloadVersion,
        businessType: event && event.businessObjectType,
        returnStep: 'EVENT',
        focusField: 'payloadSchema'
      })
    },
    timingLabel(event) {
      return event.lifecycleTiming || event.timing || event.occurredWhen || '业务节点完成时'
    },
    businessLabel(value) {
      return ({ LEAD: '线索', CUSTOMER: '客户', CONTRACT: '合同', CASE: '案件', MATTER: '事项', OTHER: '其他业务' })[value] || value
    },
    statusLabel(value) {
      return ({ ACTIVE: '已启用', DRAFT: '草稿', DISABLED: '已停用' })[value] || value
    },
    fieldTypeLabel(field) {
      const semantic = String((field && field.semanticType) || '').toUpperCase()
      if (semantic === 'USER_ID') return '人员'
      if (semantic === 'DEPARTMENT_ID') return '部门'
      if (semantic === 'DICT') return '业务选项'
      if (semantic === 'BUSINESS_OBJECT_ID') return '业务对象'
      return ({ string: '文本', integer: '整数', number: '数字', boolean: '是/否', object: '业务对象' })[
        String((field && field.type) || '').toLowerCase()
      ] || '业务信息'
    },
    focusField(fieldPath) {
      const target = repairFocusTarget(fieldPath)
      this.$nextTick(() => {
        const reference = this.$refs[target]
        const element = reference && (reference.$el || reference)
        if (!element) return
        if (typeof reference.focus === 'function') reference.focus()
        else if (typeof element.focus === 'function') element.focus()
        if (typeof element.scrollIntoView === 'function') {
          element.scrollIntoView({ behavior: 'smooth', block: 'center' })
        }
        this.focusRestoredTarget = target
        if (this.focusTimer) clearTimeout(this.focusTimer)
        this.focusTimer = setTimeout(() => { this.focusRestoredTarget = '' }, 1800)
      })
    }
  }
}
</script>

<style scoped lang="scss">
.journey-step__header {
  margin-bottom: 22px;

  h2 {
    margin: 5px 0 8px;
    font-size: 22px;
    line-height: 32px;
    color: #0B2A55;
  }

  p {
    max-width: 760px;
    margin: 0;
    font-size: 14px;
    line-height: 23px;
    color: #65758A;
  }
}

.journey-step__eyebrow {
  font-size: 12px;
  color: #C89A3D;
}

.event-step__filters {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) 150px 130px;
  gap: 10px;
  margin-bottom: 16px;
}

.event-detail__technical {
  margin-top: 12px;
  font-size: 12px;
  color: #7B8898;

  summary { cursor: pointer; }
  p { margin: 6px 0 0; }
}

.is-focus-restored {
  outline: 3px solid rgba(200, 154, 61, 0.34);
  outline-offset: 3px;
  transition: outline-color 180ms ease;
}

.event-step__layout {
  display: grid;
  grid-template-columns: minmax(300px, .9fr) minmax(340px, 1.1fr);
  gap: 16px;
  align-items: start;
}

.event-step__catalog {
  max-height: 570px;
  overflow: auto;
  border: 1px solid #D9E1EA;
  border-radius: 8px;
}

.event-group__title {
  position: sticky;
  top: 0;
  z-index: 1;
  display: flex;
  justify-content: space-between;
  padding: 9px 12px;
  font-size: 12px;
  color: #53667C;
  background: #F4F7FA;
  border-bottom: 1px solid #D9E1EA;

  small {
    color: #7B8898;
  }
}

.event-option {
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  width: 100%;
  padding: 12px;
  color: #34465B;
  text-align: left;
  cursor: pointer;
  background: #FFFFFF;
  border: 0;
  border-left: 3px solid transparent;
  border-bottom: 1px solid #EDF1F5;

  &:hover,
  &.is-selected {
    background: #F3F7FC;
  }

  &.is-selected {
    border-left-color: #C89A3D;
  }

  &.is-unavailable {
    color: #7B8898;
  }
}

.event-option__marker {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: #0B2A55;
  background: #EAF0F7;
  border-radius: 8px;
}

.event-option__content {
  strong,
  small {
    display: block;
  }

  strong {
    font-size: 14px;
    line-height: 21px;
  }

  small {
    margin-top: 3px;
    font-size: 12px;
    color: #718096;
  }
}

.event-detail {
  min-height: 400px;
  padding: 18px;
  background: #F9FBFD;
  border: 1px solid #D9E1EA;
  border-radius: 8px;
}

.event-detail__heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;

  span {
    font-size: 12px;
    color: #7B8898;
  }

  h3 {
    margin: 3px 0 0;
    font-size: 18px;
    line-height: 27px;
    color: #0B2A55;
  }
}

.event-detail__facts {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 18px;
  padding: 14px 0;
  margin: 12px 0;
  border-top: 1px solid #E0E6ED;
  border-bottom: 1px solid #E0E6ED;

  div {
    min-width: 0;
  }

  dt {
    font-size: 12px;
    color: #7B8898;
  }

  dd {
    margin: 3px 0 0;
    font-size: 13px;
    color: #34465B;
  }
}

.event-detail__description,
.event-detail__fields {
  margin-top: 14px;

  strong {
    font-size: 13px;
    color: #0B2A55;
  }

  p {
    margin: 5px 0 0;
    font-size: 13px;
    line-height: 21px;
    color: #65758A;
  }
}

.event-detail__field-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;

  span {
    padding: 5px 8px;
    font-size: 12px;
    color: #34465B;
    background: #FFFFFF;
    border: 1px solid #D9E1EA;
    border-radius: 8px;
  }

  small {
    margin-left: 5px;
    color: #7B8898;
  }
}

.event-detail__repair {
  position: relative;
  margin-top: 16px;

  > .el-button {
    position: absolute;
    right: 14px;
    bottom: 5px;
  }
}

@media (max-width: 960px) {
  .event-step__layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .event-step__filters,
  .event-detail__facts {
    grid-template-columns: 1fr;
  }
}
</style>
