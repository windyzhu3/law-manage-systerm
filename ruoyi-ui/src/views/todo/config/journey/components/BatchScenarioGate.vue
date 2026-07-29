<template>
  <section class="batch-gate" data-testid="batch-scenario-gate">
    <div>
      <h3>发布前场景门禁</h3>
      <p v-if="gate.publicationReady">三个必测场景均已通过，可以重新执行发布预检。</p>
      <p v-else>还需验证：{{ missingLabels }}</p>
    </div>
    <el-button
      type="primary"
      plain
      :loading="loading"
      :disabled="disabled"
      @click="$emit('run-batch')"
    >
      批量验证三个场景
    </el-button>
  </section>
</template>

<script>
export default {
  name: 'BatchScenarioGate',
  props: {
    gate: { type: Object, default: () => ({ publicationReady: false, blockingScenarioCodes: [] }) },
    scenarios: { type: Array, default: () => [] },
    loading: Boolean,
    disabled: Boolean
  },
  computed: {
    missingLabels() {
      const blockers = this.gate.blockingScenarios || (this.gate.blockingScenarioCodes || [])
        .map(code => ({ scenarioCode: code, reason: 'MISSING' }))
      return blockers.map(blocker => {
        const scenario = this.scenarios.find(item => item.scenarioCode === blocker.scenarioCode)
        const name = blocker.scenarioName || (scenario && scenario.scenarioName) || blocker.scenarioCode
        return `${name}（${this.reasonLabel(blocker.reason)}）`
      }).join('、') || '有效首联、疑似无效、无法联系'
    }
  },
  methods: {
    reasonLabel(reason) {
      return {
        DEFINITION_CHANGED: '配置已变更，请重新验证',
        LAST_RUN_FAILED: '最近一次验证未通过',
        EVIDENCE_EXPIRED: '验证结果已过期',
        EVIDENCE_UNAVAILABLE: '验证结果不可用',
        MISSING: '尚未验证'
      }[reason] || '尚未验证'
    }
  }
}
</script>

<style scoped>
.batch-gate { display: flex; align-items: center; justify-content: space-between; gap: 18px; border: 1px solid #C89A3D; border-radius: 8px; padding: 18px 20px; background: #FFFCF5; }
.batch-gate h3 { margin: 0 0 6px; color: #0B2A55; }
.batch-gate p { margin: 0; color: #66758A; }
@media (max-width: 720px) { .batch-gate { align-items: stretch; flex-direction: column; } }
</style>
