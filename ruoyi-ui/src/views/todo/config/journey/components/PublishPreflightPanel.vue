<template>
  <section class="preflight-panel" data-testid="publish-preflight-panel">
    <div class="preflight-panel__heading">
      <div>
        <h3>发布预检</h3>
        <p>每次发布前都从服务器重新校验当前草稿，不复用旧结果。</p>
      </div>
      <el-button :loading="loading" @click="$emit('reload')">重新预检</el-button>
    </div>

    <el-alert v-if="!preflight" title="尚未执行发布预检" type="info" :closable="false" show-icon />
    <template v-else>
      <div class="preflight-panel__counts">
        <span class="is-blocker">{{ errors.length }} 项阻塞</span>
        <span class="is-warning">{{ warnings.length }} 项警告</span>
        <span v-if="gate.hashMatches" class="is-ready">草稿版本一致</span>
      </div>
      <div v-for="issue in errors" :key="issueKey(issue)" class="preflight-panel__issue is-blocker">
        <strong>{{ issue.message || issue.code }}</strong>
        <el-button type="text" @click="$emit('repair', repairStep(issue))">返回修复</el-button>
      </div>
      <div v-for="issue in warnings" :key="issueKey(issue)" class="preflight-panel__issue is-warning">
        <strong>{{ issue.message || issue.code }}</strong>
      </div>
      <el-form v-if="warnings.length" label-position="top" class="preflight-panel__warning-form">
        <el-form-item label="警告复核说明" required>
          <el-input
            :value="warningReason"
            maxlength="300"
            show-word-limit
            placeholder="说明接受警告并发布的业务原因"
            data-testid="publish-warning-reason"
            @input="$emit('update:warningReason', $event)"
          />
        </el-form-item>
      </el-form>

      <details v-if="diff && diff.changes && diff.changes.length" class="preflight-panel__diff">
        <summary>查看草稿与已发布版本差异（{{ diff.changes.length }}）</summary>
        <ul>
          <li v-for="change in diff.changes" :key="`${change.section}-${change.path}`">
            <strong>{{ change.section }}</strong>
            <span>{{ change.path }} · {{ change.type }}</span>
            <el-tag size="mini" :type="change.risk === 'HIGH' ? 'danger' : 'info'">{{ change.risk || 'LOW' }}</el-tag>
          </li>
        </ul>
      </details>
    </template>
  </section>
</template>

<script>
import { localizedJourneyIssue } from '../journey-step-model'

export default {
  name: 'PublishPreflightPanel',
  props: {
    preflight: { type: Object, default: null },
    gate: { type: Object, default: () => ({}) },
    warningReason: { type: String, default: '' },
    diff: { type: Object, default: null },
    fields: { type: Array, default: () => [] },
    loading: Boolean
  },
  computed: {
    errors() {
      return ((this.preflight && this.preflight.errors) || [])
        .map(issue => localizedJourneyIssue(issue, { fields: this.fields }))
    },
    warnings() {
      return ((this.preflight && this.preflight.warnings) || [])
        .map(issue => localizedJourneyIssue(issue, { fields: this.fields }))
    }
  },
  methods: {
    issueKey(issue) { return `${issue.code || 'issue'}-${issue.fieldPath || issue.path || ''}` },
    repairStep(issue) { return String(issue.stepCode || issue.section || 'EVENT').toUpperCase() }
  }
}
</script>

<style scoped>
.preflight-panel { border: 1px solid #D9E1EA; border-radius: 8px; padding: 20px; background: #FFFFFF; }
.preflight-panel__heading { display: flex; justify-content: space-between; gap: 16px; margin-bottom: 16px; }
.preflight-panel__heading h3 { margin: 0 0 6px; color: #0B2A55; }
.preflight-panel__heading p { margin: 0; color: #66758A; }
.preflight-panel__counts { display: flex; gap: 18px; margin-bottom: 12px; font-weight: 700; }
.is-blocker { color: #B42318; }
.is-warning { color: #A15C00; }
.is-ready { color: #2E7D4F; }
.preflight-panel__issue { display: flex; justify-content: space-between; align-items: center; padding: 10px 12px; margin-bottom: 8px; background: #F8FAFC; }
.preflight-panel__warning-form { margin-top: 16px; }
.preflight-panel__diff { margin-top: 16px; color: #0B2A55; }
.preflight-panel__diff li { display: grid; grid-template-columns: 130px 1fr auto; gap: 10px; padding: 8px 0; border-top: 1px solid #E8EDF3; }
</style>
