<template>
  <el-dialog
    title="检测到配置冲突"
    :visible="visible"
    width="760px"
    append-to-body
    :show-close="collisions.length === 0"
    :close-on-click-modal="false"
    :close-on-press-escape="collisions.length === 0"
    @close="handleClose"
  >
    <el-alert
      :title="conflictAlert"
      :type="collisions.length ? 'error' : 'warning'"
      :closable="false"
      show-icon
    />

    <div class="journey-conflict__summaries">
      <section>
        <span>服务器版本</span>
        <h4>{{ summary(server).name }}</h4>
        <dl>
          <div><dt>版本</dt><dd>{{ summary(server).version }}</dd></div>
          <div><dt>配置摘要</dt><dd>{{ summary(server).progress }}</dd></div>
        </dl>
      </section>
      <section>
        <span>你的本地版本</span>
        <h4>{{ summary(local).name }}</h4>
        <dl>
          <div><dt>版本</dt><dd>{{ summary(local).version }}</dd></div>
          <div><dt>本地修改</dt><dd>{{ summary(local).progress }}</dd></div>
        </dl>
      </section>
    </div>

    <section v-if="differences.length" class="journey-conflict__differences">
      <header>
        <div>
          <h4>字段差异</h4>
          <p>系统会自动保留互不冲突的修改；同一字段冲突需要你选择如何处理。</p>
        </div>
        <el-tag v-if="collisions.length" type="danger" size="small">
          {{ collisions.length }} 个同一字段冲突
        </el-tag>
      </header>

      <div class="journey-conflict__table">
        <div class="journey-conflict__row journey-conflict__row--head">
          <span>配置项</span>
          <span>你的修改</span>
          <span>服务器修改</span>
          <span>处理方式</span>
        </div>
        <div
          v-for="(item, index) in visibleDifferences"
          :key="`${item.stepCode}-${item.path}-${index}`"
          class="journey-conflict__row"
          :class="{ 'is-collision': item.kind === 'COLLISION' }"
        >
          <span>
            <strong>{{ stepLabel(item.stepCode) }}</strong>
            <small>{{ fieldLabel(item.path) }}</small>
          </span>
          <span>{{ displayValue(item.localValue) }}</span>
          <span>{{ displayValue(item.serverValue) }}</span>
          <span>
            <el-tag :type="differenceTag(item.kind)" size="mini">
              {{ differenceLabel(item.kind) }}
            </el-tag>
          </span>
        </div>
      </div>
      <p v-if="differences.length > visibleDifferences.length" class="journey-conflict__more">
        另有 {{ differences.length - visibleDifferences.length }} 项差异未展开，请先处理当前冲突。
      </p>
    </section>

    <p class="journey-conflict__hint">
      “刷新并合并”会以服务器最新版本为基础，自动应用无冲突的本地修改；“另存副本”会完整保留你的当前配置并创建新模板。
    </p>
    <template #footer>
      <el-button v-if="collisions.length" @click="$emit('discard-local')">采用服务器版本</el-button>
      <el-button v-else @click="$emit('update:visible', false)">暂不处理</el-button>
      <el-button v-hasPermi="['todo:template:copy']" :loading="copying" @click="$emit('save-copy')">
        另存副本
      </el-button>
      <el-button v-if="!collisions.length" type="primary" :loading="merging" @click="$emit('refresh-merge')">
        刷新并合并
      </el-button>
    </template>
  </el-dialog>
</template>

<script>
const STEP_LABELS = {
  BASIC: '基本信息',
  TRIGGER: '触发规则',
  OWNER: '负责人',
  DOD: '完成条件',
  SLA: '时效规则',
  OUTPUT: '完成产物',
  PUBLISH: '发布检查'
}

const FIELD_LABELS = {
  'config.fallback.value': '兜底负责人',
  'config.skipUnavailable': '跳过不可用人员',
  'config.mode': '分配方式',
  'config.strategy': '分配策略'
}

export default {
  name: 'JourneyConflictDialog',
  props: {
    visible: Boolean,
    conflict: { type: Object, default: () => ({}) },
    merging: Boolean,
    copying: Boolean
  },
  computed: {
    local() {
      return this.conflict.local || {}
    },
    server() {
      return this.conflict.server || {}
    },
    differences() {
      return Array.isArray(this.conflict.differences) ? this.conflict.differences : []
    },
    collisions() {
      return Array.isArray(this.conflict.collisions) ? this.conflict.collisions : []
    },
    visibleDifferences() {
      return this.differences.slice(0, 8)
    },
    conflictAlert() {
      if (this.collisions.length) {
        return `发现 ${this.collisions.length} 个同一字段冲突。系统不会覆盖服务器值，请另存副本保留你的修改。`
      }
      return '其他用户已更新这个草稿。系统已识别双方差异，可安全合并互不冲突的修改。'
    }
  },
  methods: {
    handleClose() {
      if (!this.collisions.length) this.$emit('update:visible', false)
    },
    summary(value) {
      const template = (value && value.template) || {}
      const steps = Array.isArray(value && value.steps) ? value.steps : []
      const completed = steps.filter(step => step.state === 'COMPLETED').length
      const changed = steps.filter(step => step.state === 'IN_PROGRESS').length
      return {
        name: template.templateName || template.templateCode || '待办模板',
        version: `V${template.versionNo || '-'} · 锁版本 ${template.lockVersion == null ? '-' : template.lockVersion}`,
        progress: changed ? `${changed} 个本地步骤已修改` : `${completed}/${steps.length || 7} 个步骤已完成`
      }
    },
    stepLabel(code) {
      return STEP_LABELS[code] || code || '模板配置'
    },
    fieldLabel(path) {
      return FIELD_LABELS[path] || path || '配置内容'
    },
    displayValue(value) {
      if (value === undefined || value === null || value === '') return '未设置'
      if (typeof value === 'boolean') return value ? '是' : '否'
      if (Array.isArray(value)) return value.length ? value.join('、') : '未设置'
      if (typeof value === 'object') return '已配置内容'
      return String(value)
    },
    differenceLabel(kind) {
      return {
        LOCAL: '采用你的修改',
        SERVER: '保留服务器修改',
        BOTH: '双方修改一致',
        COLLISION: '同一字段冲突'
      }[kind] || '待确认'
    },
    differenceTag(kind) {
      return {
        LOCAL: 'success',
        SERVER: 'info',
        BOTH: '',
        COLLISION: 'danger'
      }[kind] || 'info'
    }
  }
}
</script>

<style scoped lang="scss">
.journey-conflict__summaries {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-top: 18px;

  section {
    padding: 16px;
    background: #F4F7FA;
    border: 1px solid #D9E1EA;
    border-radius: 8px;
  }

  span {
    font-size: 12px;
    line-height: 18px;
    color: #7B8898;
  }

  h4 {
    margin: 4px 0 12px;
    font-size: 15px;
    line-height: 24px;
    color: #0B2A55;
  }

  dl {
    margin: 0;
  }

  dl div {
    display: flex;
    justify-content: space-between;
    font-size: 12px;
    line-height: 22px;
  }

  dt {
    color: #7B8898;
  }

  dd {
    margin: 0;
    color: #34465B;
  }
}

.journey-conflict__differences {
  margin-top: 18px;
  border: 1px solid #D9E1EA;
  border-radius: 8px;
  overflow: hidden;

  header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
    padding: 14px 16px;
    background: #F8FAFC;
    border-bottom: 1px solid #D9E1EA;
  }

  h4,
  p {
    margin: 0;
  }

  h4 {
    color: #0B2A55;
    font-size: 14px;
    line-height: 22px;
  }

  p {
    margin-top: 2px;
    color: #7B8898;
    font-size: 12px;
    line-height: 18px;
  }
}

.journey-conflict__table {
  max-height: 280px;
  overflow-y: auto;
}

.journey-conflict__row {
  display: grid;
  grid-template-columns: 1.2fr 1fr 1fr 1fr;
  gap: 12px;
  align-items: center;
  min-height: 52px;
  padding: 8px 16px;
  border-bottom: 1px solid #EEF2F6;
  color: #34465B;
  font-size: 12px;

  &:last-child {
    border-bottom: 0;
  }

  &.is-collision {
    background: #FFF5F5;
  }

  strong,
  small {
    display: block;
  }

  strong {
    color: #1E3655;
    font-weight: 600;
  }

  small {
    margin-top: 2px;
    color: #7B8898;
    word-break: break-all;
  }
}

.journey-conflict__row--head {
  min-height: 36px;
  color: #7B8898;
  background: #FCFDFE;
  font-weight: 600;
}

.journey-conflict__more {
  padding: 8px 16px 12px;
}

.journey-conflict__hint {
  margin: 16px 0 0;
  font-size: 13px;
  line-height: 21px;
  color: #586779;
}

@media (max-width: 640px) {
  .journey-conflict__summaries {
    grid-template-columns: 1fr;
  }

  .journey-conflict__row {
    grid-template-columns: 1fr;
    gap: 4px;
  }

  .journey-conflict__row--head {
    display: none;
  }
}
</style>
