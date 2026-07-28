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
      const codes = this.gate.blockingScenarioCodes || []
      return codes.map(code => {
        const scenario = this.scenarios.find(item => item.scenarioCode === code)
        return scenario ? scenario.scenarioName : code
      }).join('、') || '有效首联、疑似无效、未接通'
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
