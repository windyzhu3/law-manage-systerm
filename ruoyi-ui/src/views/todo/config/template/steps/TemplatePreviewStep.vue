<template>
  <section>
    <el-alert v-if="!definitionReady" title="当前草稿已变更，请先保存并完成发布预检，再运行真实模拟。" type="warning" :closable="false" show-icon />
    <el-form label-width="110px">
      <el-row :gutter="16"><el-col :span="12"><el-form-item label="业务对象 ID"><el-input-number v-model="businessId" :min="1" :precision="0" class="full" /></el-form-item></el-col><el-col :span="12"><el-form-item label="生效时间"><el-date-picker v-model="effectiveAt" type="datetime" value-format="yyyy-MM-ddTHH:mm:ss" class="full" /></el-form-item></el-col></el-row>
      <el-form-item label="事件载荷 JSON"><el-input v-model="payloadText" type="textarea" :rows="8" placeholder='例如：{"ownerId":1}' /></el-form-item>
      <el-form-item><el-button v-hasPermi="['todo:simulation:simulate']" type="primary" :disabled="!versionId || !definitionReady" :loading="running" @click="runSimulation">运行真实模拟</el-button></el-form-item>
    </el-form>
    <el-alert title="只读模拟，不创建真实待办" type="info" :closable="false" show-icon class="simulation-notice" />
    <el-alert v-if="errorText" :title="errorText" type="error" :closable="false" show-icon />
    <simulation-result v-if="result" :result="result" :loading="running" />
    <el-card shadow="never" class="capability-card"><div slot="header">运行时能力目录</div><el-tag v-for="item in handlerCatalog" :key="`handler:${item.code}`" size="small">处理器 · {{ item.code }}</el-tag><el-tag v-for="item in autoActionCatalog" :key="`action:${item.code || item.actionType}`" size="small" type="success">自动动作 · {{ item.code || item.actionType }}</el-tag><p v-if="!handlerCatalog.length && !autoActionCatalog.length">当前没有可用的处理器或自动动作。</p></el-card>
  </section>
</template>
<script>
import { simulateConfiguration, listTemplateHandlerCatalog, listTemplateAutoActionCatalog } from '@/api/todo-config'
import SimulationResult from '../../simulation/SimulationResult'
export default {
  name: 'TemplatePreviewStep',
  components: { SimulationResult },
  props: { versionId: [Number, String], eventType: String, payloadVersion: [Number, String], businessType: String, definitionReady: Boolean, sourceToken: String, definitionHash: String },
  data() { return { businessId: 1, effectiveAt: '', payloadText: '{\n  "ownerId": 1\n}', running: false, result: null, errorText: '', handlerCatalog: [], autoActionCatalog: [] } },
  watch: { sourceToken() { this.result = null; this.errorText = '' }, definitionReady(value) { if (!value) this.result = null } },
  created() { this.effectiveAt = this.formatDate(new Date()); this.loadCatalog() },
  methods: { formatDate(date) { const pad = value => String(value).padStart(2, '0'); return `${date.getFullYear()}-${pad(date.getMonth()+1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}` }, async loadCatalog() { const responses = await Promise.all([listTemplateHandlerCatalog(), listTemplateAutoActionCatalog()]); this.handlerCatalog = responses[0].data || []; this.autoActionCatalog = responses[1].data || [] }, async runSimulation() { if (!this.definitionReady || !this.definitionHash) { this.errorText = '当前草稿已变更，请先保存并完成发布预检'; return } this.errorText = ''; const tokenAtStart = this.sourceToken; let payload; try { payload = JSON.parse(this.payloadText); if (!payload || Array.isArray(payload) || typeof payload !== 'object') throw new Error() } catch (_) { this.errorText = '事件载荷必须是 JSON 对象'; return } this.running = true; try { const response = await simulateConfiguration({ requestId: `template-sim-${Date.now()}`, versionId: Number(this.versionId), eventType: this.eventType, payloadVersion: Number(this.payloadVersion || 1), businessType: this.businessType, businessId: Number(this.businessId), payload, effectiveAt: this.effectiveAt, taskCompletions: [], expectedDefinitionHash: this.definitionHash }); if (tokenAtStart !== this.sourceToken || !this.definitionReady) return; this.result = response.data || {} } catch (error) { this.errorText = (error && (error.msg || error.message)) || '模拟失败' } finally { this.running = false } }, validate() { return Promise.resolve(true) } }
}
</script>
<style scoped>.full{width:100%}.simulation-notice{margin-bottom:16px}</style>
