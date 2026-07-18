<template>
  <el-drawer title="定义模拟" :visible.sync="open" size="760px" append-to-body data-testid="simulation-drawer">
    <div class="body">
      <el-form label-width="110px">
        <el-form-item label="业务类型"><el-input v-model.trim="form.businessType" /></el-form-item>
        <el-form-item label="业务 ID"><el-input-number v-model="form.businessId" :min="1" /></el-form-item>
        <el-form-item label="生效时间"><el-date-picker v-model="form.effectiveAt" type="datetime" value-format="yyyy-MM-dd'T'HH:mm:ss" /></el-form-item>
        <el-form-item label="事件负载"><el-input v-model="form.payloadText" type="textarea" :rows="4" /></el-form-item>
        <el-divider>虚拟任务完成（可选）</el-divider>
        <el-form-item label="节点 Key"><el-input v-model.trim="form.nodeKey" data-testid="simulation-node-key" /></el-form-item>
        <el-form-item label="路由次数"><el-input-number v-model="form.occurrence" :min="0" /></el-form-item>
        <el-form-item label="完成时间"><el-date-picker v-model="form.completedAt" data-testid="simulation-completed-at" type="datetime" value-format="yyyy-MM-dd'T'HH:mm:ss" /></el-form-item>
        <el-form-item label="完成负载"><el-input v-model="form.completionPayloadText" data-testid="simulation-completion-payload" type="textarea" :rows="2" /></el-form-item>
        <el-form-item><el-button data-testid="simulation-run" type="primary" :loading="loading" @click="run">运行模拟</el-button></el-form-item>
      </el-form>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <div v-if="result" class="result" data-testid="simulation-result">
        <section v-for="name in scalarSections" :key="name"><h4>{{ labels[name] }}</h4><pre>{{ json(result[name]) }}</pre></section>
        <section v-for="name in listSections" :key="name"><h4>{{ labels[name] }}</h4><pre>{{ json(result[name] || []) }}</pre></section>
      </div>
    </div>
  </el-drawer>
</template>

<script>
import { simulateDefinition } from '@/api/todo-definition'

function nowText() {
  const date = new Date(Date.now() - new Date().getTimezoneOffset() * 60000)
  return date.toISOString().slice(0, 19)
}

export default {
  name: 'SimulationDrawer',
  props: { visible: Boolean, versionId: [Number, String] },
  data() {
    const now = nowText()
    return {
      loading: false, error: '', result: null,
      form: { businessType: 'LEAD', businessId: 1, effectiveAt: now, payloadText: '{"sample":true}', nodeKey: 'task', occurrence: 0, completedAt: now, completionPayloadText: '{}' },
      scalarSections: ['trigger', 'owner', 'sla', 'form'], listSections: ['routes', 'autoActions', 'handlers', 'issues'],
      labels: { trigger: '触发匹配', owner: 'Owner 解析', sla: 'SLA', form: '表单与 DoD', routes: '路由轨迹', autoActions: '自动动作', handlers: '处理器', issues: '问题' }
    }
  },
  computed: { open: { get() { return this.visible }, set(value) { this.$emit('update:visible', value) } } },
  watch: { visible(value) { if (!value) { this.error = ''; this.result = null } } },
  methods: {
    json(value) { return JSON.stringify(value == null ? null : value, null, 2) },
    parse(value, field) { try { const parsed = JSON.parse(value); if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error(); return parsed } catch (_) { throw new Error(`${field} 必须是非空 JSON 对象`) } },
    run() {
      this.error = ''; this.result = null
      let payload; let completionPayload = null
      try {
        payload = this.parse(this.form.payloadText, '事件负载')
        if (!Object.keys(payload).length) throw new Error('事件负载不能为空')
        if (!this.form.businessType || !(Number(this.form.businessId) > 0) || !this.form.effectiveAt) throw new Error('业务类型、正数业务 ID 和生效时间必填')
        if (this.form.nodeKey) {
          completionPayload = this.parse(this.form.completionPayloadText || '{}', '完成负载')
          if (!Number.isInteger(Number(this.form.occurrence)) || Number(this.form.occurrence) < 0 || !this.form.completedAt) throw new Error('虚拟任务完成需要非负整数路由次数和完成时间')
        }
      } catch (error) { this.error = error.message; return Promise.resolve() }
      const taskCompletions = this.form.nodeKey ? [{ nodeKey: this.form.nodeKey, occurrence: Number(this.form.occurrence), payload: completionPayload, completedAt: this.form.completedAt }] : []
      this.loading = true
      return Promise.resolve().then(() => simulateDefinition(this.versionId, { payload, businessType: this.form.businessType, businessId: Number(this.form.businessId), effectiveAt: this.form.effectiveAt, taskCompletions }))
        .then(response => { this.result = response.data || {}; return this.result })
        .catch(error => { this.error = error.msg || error.message || '定义模拟失败' })
        .finally(() => { this.loading = false })
    }
  }
}
</script>

<style scoped>
.body{padding:0 20px 24px}.result{display:grid;grid-template-columns:1fr 1fr;gap:10px}.result section{min-width:0;padding:10px;border:1px solid #e2e8f0;border-radius:6px}.result h4{margin:0 0 7px}.result pre{max-height:220px;overflow:auto;white-space:pre-wrap;word-break:break-word;background:#f8fafc}
</style>
