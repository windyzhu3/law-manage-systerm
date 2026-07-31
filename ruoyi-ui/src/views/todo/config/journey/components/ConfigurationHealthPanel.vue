<template>
  <section class="configuration-health" aria-labelledby="configuration-health-title">
    <header class="configuration-health__header">
      <div>
        <span class="configuration-health__eyebrow">配置健康度</span>
        <h3 id="configuration-health-title">需要处理的问题</h3>
      </div>
      <span class="configuration-health__count">{{ issues.length }}</span>
    </header>

    <div v-if="issues.length" class="configuration-health__list">
      <article
        v-for="(issue, index) in issues"
        :key="issue.code || `${issue.stepCode}-${index}`"
        class="configuration-health__issue"
        :class="`is-${severity(issue).toLowerCase()}`"
      >
        <i :class="severity(issue) === 'BLOCKER' ? 'el-icon-circle-close' : 'el-icon-warning-outline'" aria-hidden="true" />
        <div>
          <strong>{{ issueMessage(issue) }}</strong>
          <small>{{ severity(issue) === 'BLOCKER' ? '阻塞发布' : '建议修复' }}</small>
          <details class="configuration-health__technical">
            <summary>技术详情</summary>
            <small>错误码：{{ issue.code || '无' }}<template v-if="issue.fieldPath"> · 位置：{{ issue.fieldPath }}</template></small>
          </details>
        </div>
        <el-button type="text" @click="$emit('repair', { ...issue })">去修复</el-button>
      </article>
    </div>
    <div v-else class="configuration-health__empty">
      <i class="el-icon-circle-check" aria-hidden="true" />
      <div>
        <strong>当前步骤配置正常</strong>
        <span>继续配置后续步骤即可。</span>
      </div>
    </div>
    <p class="configuration-health__authority">保存后以服务端校验结果为准，页面输入提示仅用于即时辅助。</p>
  </section>
</template>

<script>
export default {
  name: 'ConfigurationHealthPanel',
  props: {
    issues: { type: Array, default: () => [] }
  },
  methods: {
    issueMessage(issue) {
      const messages = {
        TODO_REQUIRED_SIMULATION_SCENARIOS_INCOMPLETE: '还有必测场景未通过',
        TODO_FULL_SIMULATION_REQUIRED: '完整试运行尚未通过',
        TODO_FULL_SIMULATION_STALE: '完整试运行已失效，请使用当前草稿重新运行',
        TODO_JOURNEY_SIMULATION_REQUIRED: '完整试运行尚未通过',
        TODO_LEAD_RELEASE_EVIDENCE_INCOMPLETE: '四个线索待办版本尚未全部通过当前版本的场景验证和完整试运行',
        TODO_LEAD_RELEASE_ROUTING_MISMATCH: '首联待办的后续路由与本次启用版本不一致',
        TODO_LEAD_RELEASE_DEFINITION_STALE: '首联待办已发生变化，请刷新后重新检查'
      }
      const code = String((issue && issue.code) || '')
      return messages[code] || (issue && (issue.message || issue.title)) || '此处配置需要检查'
    },
    severity(issue) {
      return String((issue && issue.severity) || 'WARNING').toUpperCase() === 'BLOCKER' ? 'BLOCKER' : 'WARNING'
    }
  }
}
</script>

<style scoped lang="scss">
.configuration-health {
  padding: 20px;
  background: #FFFFFF;
  border: 1px solid #D9E1EA;
  border-radius: 8px;
}

.configuration-health__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;

  h3 {
    margin: 2px 0 0;
    font-size: 16px;
    line-height: 24px;
    color: #0B2A55;
  }
}

.configuration-health__eyebrow {
  font-size: 12px;
  line-height: 18px;
  color: #7B8898;
}

.configuration-health__count {
  min-width: 28px;
  padding: 2px 8px;
  font-size: 12px;
  line-height: 20px;
  color: #0B2A55;
  text-align: center;
  background: #EDF2F7;
  border-radius: 10px;
}

.configuration-health__list {
  display: grid;
  gap: 12px;
}

.configuration-health__issue {
  display: grid;
  grid-template-columns: 20px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: start;
  padding: 12px;
  color: #7A5210;
  background: #FFF9EC;
  border-left: 3px solid #C89A3D;
  border-radius: 8px;

  &.is-blocker {
    color: #9F2F2F;
    background: #FFF1F1;
    border-left-color: #C43D3D;
  }

  i {
    margin-top: 3px;
    font-size: 16px;
  }

  strong,
  small {
    display: block;
    line-height: 20px;
  }

  strong {
    font-size: 13px;
  }

  small {
    margin-top: 2px;
    font-size: 12px;
    opacity: .78;
  }
}

.configuration-health__empty {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 12px;
  color: #256B4A;
  background: #F0F8F4;
  border-radius: 8px;

  i {
    margin-top: 2px;
    font-size: 18px;
  }

  strong,
  span {
    display: block;
    font-size: 13px;
    line-height: 20px;
  }

  span {
    color: #617369;
  }
}

.configuration-health__authority {
  margin: 12px 0 0;
  font-size: 12px;
  line-height: 18px;
  color: #7B8898;
}

.configuration-health__technical {
  margin-top: 5px;
  opacity: .78;

  summary { font-size: 11px; cursor: pointer; }
}
</style>
