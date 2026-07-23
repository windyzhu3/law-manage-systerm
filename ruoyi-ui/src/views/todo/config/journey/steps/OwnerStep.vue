<template>
  <section class="journey-step owner-step">
    <header class="journey-step__header">
      <span class="journey-step__eyebrow">第三步 · 负责人</span>
      <h2>待办创建后，优先交给谁？</h2>
      <p>先选择最符合业务职责的解析方式，再设置人员不可用时的兜底负责人。</p>
    </header>

    <el-alert
      v-if="unsupportedExisting"
      title="当前模板使用了高级负责人规则"
      description="原规则已保留。选择下方任一业务策略后，将改为标准负责人配置。"
      type="info"
      :closable="false"
      show-icon
    />

    <div class="owner-strategies" role="radiogroup" aria-label="负责人策略">
      <button
        v-for="item in strategies"
        :key="item.value"
        type="button"
        class="owner-strategy"
        :class="{ 'is-selected': strategy === item.value }"
        :disabled="readonly || item.disabled"
        @click="chooseStrategy(item.value)"
      >
        <i :class="item.icon" />
        <span><strong>{{ item.label }}</strong><small>{{ item.description }}</small></span>
        <i class="owner-strategy__check" :class="strategy === item.value ? 'el-icon-circle-check' : 'el-icon-circle'" />
      </button>
    </div>

    <el-alert
      v-if="strategy === 'EVENT_OWNER' && !ownerFields.length"
      title="当前事件版本没有可用的负责人字段"
      description="请返回事件步骤维护当前 Payload 版本的数值型人员 ID 字段，或改用其他负责人策略。"
      type="error"
      :closable="false"
      show-icon
    />

    <section v-if="strategy" class="owner-config">
      <div class="owner-config__main">
        <h3>负责人来源</h3>
        <el-form label-position="top">
          <el-form-item v-if="strategy === 'EVENT_OWNER'" label="事件中的人员字段">
            <el-select v-model="selection" :disabled="readonly" filterable placeholder="选择负责人字段" @change="commit">
              <el-option v-for="field in ownerFields" :key="field.code" :label="field.name" :value="field.code">
                <span>{{ field.name }}</span><small class="owner-option-note">{{ field.code }}</small>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item v-else-if="strategy === 'ROLE'" label="指定角色">
            <el-select v-model="selection" :disabled="readonly" filterable placeholder="选择角色" @change="commit">
              <el-option v-for="item in roleOptions" :key="item.value" :label="item.label" :value="item.value">
                <span>{{ item.label }}</span><small class="owner-option-note">{{ item.secondaryLabel }}</small>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item v-else-if="strategy === 'USER'" label="指定人员">
            <el-select v-model="selection" :disabled="readonly" filterable placeholder="选择人员" @change="commit">
              <el-option v-for="item in userOptions" :key="item.value" :label="item.label" :value="item.value">
                <span>{{ item.label }}</span><small class="owner-option-note">{{ item.secondaryLabel }}</small>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item v-else-if="strategy === 'CANDIDATE_POOL'" label="候选池范围">
            <el-select v-model="selection" :disabled="readonly" filterable placeholder="选择候选角色" @change="commit">
              <el-option v-for="item in roleOptions" :key="item.value" :label="`${item.label}候选池`" :value="item.value" />
            </el-select>
          </el-form-item>
          <p v-else class="owner-config__automatic">系统会根据当前业务对象记录的负责人自动解析。</p>
          <el-checkbox v-model="skipUnavailable" :disabled="readonly" @change="commit">自动跳过离职、停用或请假人员</el-checkbox>
          <el-checkbox v-model="useDelegation" :disabled="readonly" @change="commit">存在有效委托时优先交给受托人</el-checkbox>
        </el-form>
      </div>

      <div class="owner-config__fallback">
        <h3>兜底负责人</h3>
        <p>主负责人无法解析或不可用时，系统按此规则继续分配。</p>
        <el-select v-model="fallbackType" :disabled="readonly" placeholder="选择兜底方式" @change="fallbackChanged">
          <el-option label="不设置兜底" value="" />
          <el-option label="业务对象负责人" value="BUSINESS_OWNER" />
          <el-option label="指定角色" value="ROLE" />
          <el-option label="指定人员" value="USER" />
        </el-select>
        <el-select
          v-if="fallbackType === 'ROLE'"
          v-model="fallbackSelection"
          :disabled="readonly"
          filterable
          placeholder="选择兜底角色"
          @change="commit"
        >
          <el-option v-for="item in roleOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select
          v-if="fallbackType === 'USER'"
          v-model="fallbackSelection"
          :disabled="readonly"
          filterable
          placeholder="选择兜底人员"
          @change="commit"
        >
          <el-option v-for="item in userOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </div>
    </section>

    <section class="owner-explanation">
      <div>
        <span>解析顺序</span>
        <strong>{{ explanation }}</strong>
      </div>
      <i class="el-icon-right" />
      <div>
        <span>当前模拟结果</span>
        <strong>{{ preview.assigneeSummary || '保存后可在模拟步骤查看具体人员' }}</strong>
      </div>
    </section>

    <el-alert
      v-if="blocker"
      title="负责人配置会阻塞发布"
      :description="blocker.message"
      type="error"
      :closable="false"
      show-icon
    />
  </section>
</template>

<script>
import {
  buildOwnerPatch,
  buildOwnerConfig,
  ownerStrategy,
  ownerBlocker,
  scopeOwnerFields,
  ownerSelectionStillValid
} from '../journey-step-model'

const KNOWN_STRATEGIES = ['EVENT_OWNER', 'BUSINESS_OWNER', 'ROLE', 'USER', 'CANDIDATE_POOL']

export default {
  name: 'OwnerStep',
  props: {
    value: { type: Object, default: () => ({}) },
    resources: { type: Object, default: () => ({}) },
    event: { type: Object, default: () => ({}) },
    preview: { type: Object, default: () => ({}) },
    readonly: Boolean
  },
  data() {
    return {
      strategy: '',
      selection: '',
      fallbackType: '',
      fallbackSelection: '',
      skipUnavailable: true,
      useDelegation: true,
      syncing: false,
      strategies: [
        { value: 'EVENT_OWNER', label: '事件中的负责人', description: '使用业务事件携带的人员字段', icon: 'el-icon-user' },
        { value: 'BUSINESS_OWNER', label: '业务对象负责人', description: '使用线索、合同或案件当前负责人', icon: 'el-icon-s-custom' },
        { value: 'ROLE', label: '指定角色', description: '从具有指定职责的人员中分配', icon: 'el-icon-office-building' },
        { value: 'USER', label: '指定人员', description: '始终交给同一位业务人员', icon: 'el-icon-user-solid' },
        { value: 'CANDIDATE_POOL', label: '候选池', description: '运行时能力建设中，当前版本暂不可选', icon: 'el-icon-s-grid', disabled: true }
      ]
    }
  },
  computed: {
    config() { return this.value.config || {} },
    owners() { return this.resources.owners || [] },
    roleOptions() { return this.owners.filter(item => item.type === 'ROLE') },
    userOptions() { return this.owners.filter(item => item.type === 'USER') },
    ownerFields() {
      return scopeOwnerFields(this.resources.fields || [], this.event)
    },
    ownerFieldKeys() { return this.ownerFields.map(field => field.code).join('|') },
    eventKey() { return `${this.event.eventType || ''}@${Number(this.event.payloadVersion || 0)}` },
    unsupportedExisting() {
      return Boolean(this.config.type && !KNOWN_STRATEGIES.includes(ownerStrategy(this.config)))
    },
    currentConfig() { return this.composeConfig() },
    blocker() { return ownerBlocker(this.currentConfig, this.ownerFields) },
    explanation() {
      const primary = {
        EVENT_OWNER: `先读取事件字段“${this.fieldLabel(this.selection)}”`,
        BUSINESS_OWNER: '先查找当前业务对象负责人',
        ROLE: `先查找角色“${this.ownerLabel('ROLE', this.selection)}”中的可用人员`,
        USER: `优先分配给“${this.ownerLabel('USER', this.selection)}”`,
        CANDIDATE_POOL: `先进入“${this.ownerLabel('ROLE', this.selection)}”候选池`
      }[this.strategy] || '尚未设置主负责人'
      const fallback = this.fallbackType
        ? `；无法解析时，改用${this.fallbackLabel}`
        : '；无法解析时没有兜底'
      return `${primary}${fallback}`
    },
    fallbackLabel() {
      if (this.fallbackType === 'BUSINESS_OWNER') return '业务对象负责人'
      if (this.fallbackType === 'ROLE') return `角色“${this.ownerLabel('ROLE', this.fallbackSelection)}”`
      if (this.fallbackType === 'USER') return `人员“${this.ownerLabel('USER', this.fallbackSelection)}”`
      return '未设置'
    }
  },
  watch: {
    value: {
      immediate: true,
      deep: true,
      handler() { if (!this.syncing) this.hydrate() }
    },
    eventKey() { this.reconcileOwnerField() },
    ownerFieldKeys() { this.reconcileOwnerField() }
  },
  methods: {
    hydrate() {
      const config = this.config
      const strategy = ownerStrategy(config)
      this.strategy = KNOWN_STRATEGIES.includes(strategy) ? strategy : ''
      const selection = this.strategy === 'EVENT_OWNER'
        ? (config.field || '')
        : this.strategy === 'ROLE' || this.strategy === 'CANDIDATE_POOL'
          ? (config.roleKey || config.value || '')
          : this.strategy === 'USER' ? String(config.value || config.operand || '') : ''
      this.selection = this.strategy === 'EVENT_OWNER' && !ownerSelectionStillValid(selection, this.ownerFields)
        ? ''
        : selection
      this.skipUnavailable = config.skipUnavailable !== false
      this.useDelegation = config.useDelegation !== false
      const fallback = config.fallback || {}
      this.fallbackType = ['BUSINESS_OWNER', 'ROLE', 'USER'].includes(fallback.type) ? fallback.type : ''
      this.fallbackSelection = fallback.type === 'ROLE'
        ? (fallback.roleKey || fallback.value || '')
        : fallback.type === 'USER' ? String(fallback.value || fallback.operand || '') : ''
      if (this.strategy === 'EVENT_OWNER' && selection && !this.selection && !this.readonly) {
        this.$nextTick(() => this.commit())
      }
    },
    reconcileOwnerField() {
      if (this.strategy !== 'EVENT_OWNER' || ownerSelectionStillValid(this.selection, this.ownerFields)) return
      this.selection = ''
      if (!this.readonly) this.commit()
    },
    chooseStrategy(strategy) {
      if (this.readonly) return
      this.strategy = strategy
      this.selection = strategy === 'EVENT_OWNER' ? ((this.ownerFields[0] || {}).code || '') : ''
      this.commit()
    },
    fallbackChanged() {
      this.fallbackSelection = ''
      this.commit()
    },
    fallbackConfig() {
      if (this.fallbackType === 'BUSINESS_OWNER') return { type: 'BUSINESS_OWNER' }
      if (this.fallbackType === 'ROLE' && this.fallbackSelection) return { type: 'ROLE', roleKey: this.fallbackSelection }
      if (this.fallbackType === 'USER' && this.fallbackSelection) return { type: 'USER', value: Number(this.fallbackSelection) }
      return {}
    },
    composeConfig() {
      if (!this.strategy) return this.config
      const config = buildOwnerConfig(this.strategy, {
        field: this.selection,
        value: this.selection,
        skipUnavailable: this.skipUnavailable,
        useDelegation: this.useDelegation
      }, this.fallbackConfig())
      if (Array.isArray(this.config.cc)) config.cc = this.config.cc
      return config
    },
    commit() {
      this.syncing = true
      this.$emit('change', buildOwnerPatch(this.composeConfig()))
      this.$nextTick(() => { this.syncing = false })
    },
    ownerLabel(type, value) {
      const item = this.owners.find(owner => owner.type === type && String(owner.value) === String(value))
      return item ? item.label : (value || '未选择')
    },
    fieldLabel(code) {
      const item = this.ownerFields.find(field => field.code === code)
      return item ? item.name : (code || '未选择')
    },
    focusField() {}
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

.owner-strategies {
  display: grid;
  grid-template-columns: repeat(5, minmax(130px, 1fr));
  gap: 10px;
}

.owner-strategy {
  position: relative;
  display: flex;
  min-height: 126px;
  flex-direction: column;
  gap: 8px;
  align-items: flex-start;
  padding: 14px;
  color: #34465B;
  text-align: left;
  cursor: pointer;
  background: #FFFFFF;
  border: 1px solid #D9E1EA;
  border-radius: 8px;

  &:hover,
  &.is-selected {
    border-color: #C89A3D;
  }

  &.is-selected {
    background: #FFF9EC;
  }

  > i:first-child {
    font-size: 22px;
    color: #0B2A55;
  }

  strong,
  small {
    display: block;
  }

  strong {
    font-size: 13px;
    color: #0B2A55;
  }

  small {
    margin-top: 5px;
    font-size: 12px;
    line-height: 18px;
    color: #65758A;
  }
}

.owner-strategy__check {
  position: absolute;
  top: 12px;
  right: 12px;
  color: #C89A3D;
}

.owner-config {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-top: 18px;
}

.owner-config__main,
.owner-config__fallback {
  padding: 16px;
  background: #F7F9FC;
  border: 1px solid #D9E1EA;
  border-radius: 8px;

  h3 {
    margin: 0 0 12px;
    font-size: 15px;
    color: #0B2A55;
  }

  > p {
    margin: -5px 0 12px;
    font-size: 12px;
    line-height: 19px;
    color: #65758A;
  }

  .el-select {
    width: 100%;
  }
}

.owner-config__main .el-checkbox {
  display: block;
  margin: 8px 0 0;
}

.owner-config__fallback .el-select + .el-select {
  margin-top: 10px;
}

.owner-config__automatic {
  min-height: 40px;
  padding: 10px 12px;
  font-size: 13px;
  color: #34465B;
  background: #FFFFFF;
  border-radius: 8px;
}

.owner-option-note {
  float: right;
  color: #8A98A8;
}

.owner-explanation {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 14px;
  align-items: center;
  padding: 14px 16px;
  margin-top: 18px;
  background: #EEF3F8;
  border-radius: 8px;

  span,
  strong {
    display: block;
  }

  span {
    margin-bottom: 4px;
    font-size: 12px;
    color: #718096;
  }

  strong {
    font-size: 13px;
    line-height: 20px;
    color: #0B2A55;
  }

  > i {
    color: #C89A3D;
  }
}

.owner-step > .el-alert {
  margin: 14px 0;
}

@media (max-width: 960px) {
  .owner-strategies {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 640px) {
  .owner-strategies,
  .owner-config,
  .owner-explanation {
    grid-template-columns: 1fr;
  }

  .owner-explanation > i {
    transform: rotate(90deg);
  }
}
</style>
