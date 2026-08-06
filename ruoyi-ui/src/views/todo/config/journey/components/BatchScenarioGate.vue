<template>
  <section class="batch-gate" data-testid="batch-scenario-gate">
    <div class="batch-gate__content">
      <h3>发布前场景门禁</h3>
      <p v-if="gate.publicationReady">三个必测场景均已通过，可以重新执行发布预检。</p>
      <template v-else>
        <p>还需验证：{{ missingLabels }}</p>
        <ul v-if="blockerPresentations.length" class="batch-gate__blockers">
          <li v-for="blocker in blockerPresentations" :key="blocker.scenarioCode">
            <strong>{{ blocker.scenarioName }}</strong>
            <span>{{ blocker.reasonLabel }}</span>
            <small v-if="blocker.versionText">{{ blocker.versionText }}</small>
          </li>
        </ul>
      </template>
    </div>
    <div class="batch-gate__actions">
      <el-button
        plain
        :loading="loading"
        :disabled="batchDisabled"
        @click="$emit('run-batch')"
      >
        批量验证三个场景
      </el-button>
      <el-button
        type="primary"
        :loading="revalidating"
        :disabled="runAllDisabled"
        @click="$emit('run-all')"
      >
        一键重新验证
      </el-button>
    </div>
  </section>
</template>

<script>
import { scenarioBlockerPresentation } from '../simulation-workbench-model'

export default {
  name: 'BatchScenarioGate',
  props: {
    gate: { type: Object, default: () => ({ publicationReady: false, blockingScenarioCodes: [] }) },
    scenarios: { type: Array, default: () => [] },
    loading: Boolean,
    revalidating: Boolean,
    batchDisabled: Boolean,
    runAllDisabled: Boolean
  },
  computed: {
    blockerPresentations() {
      const blockers = this.gate.blockingScenarios || (this.gate.blockingScenarioCodes || [])
        .map(code => ({ scenarioCode: code, reason: 'MISSING' }))
      return blockers.map(blocker => scenarioBlockerPresentation(blocker, this.scenarios))
    },
    missingLabels() {
      return this.blockerPresentations.map(blocker =>
        `${blocker.scenarioName}（${blocker.reasonLabel}）`
      ).join('、') || '有效首联、疑似无效、无法联系'
    }
  },
  methods: {
    reasonLabel(reason) {
      return scenarioBlockerPresentation({ reason }, []).reasonLabel
    }
  }
}
</script>

<style scoped>
.batch-gate { display: flex; align-items: center; justify-content: space-between; gap: 18px; border: 1px solid #C89A3D; border-radius: 8px; padding: 18px 20px; background: #FFFCF5; }
.batch-gate h3 { margin: 0 0 6px; color: #0B2A55; }
.batch-gate p { margin: 0; color: #66758A; }
.batch-gate__content { min-width: 0; }
.batch-gate__blockers { display: grid; gap: 6px; margin: 12px 0 0; padding: 0; list-style: none; }
.batch-gate__blockers li { display: flex; flex-wrap: wrap; gap: 8px; align-items: baseline; color: #66758A; font-size: 12px; }
.batch-gate__blockers strong { color: #243B5A; }
.batch-gate__blockers small { color: #8A96A6; }
.batch-gate__actions { display: flex; flex: none; gap: 10px; }
@media (max-width: 720px) { .batch-gate { align-items: stretch; flex-direction: column; } }
</style>
