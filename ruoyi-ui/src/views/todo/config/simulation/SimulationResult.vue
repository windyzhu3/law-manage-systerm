<template>
  <el-card shadow="never" class="simulation-result" v-loading="loading">
    <div slot="header">执行结果</div>
    <el-empty v-if="!simulation && !loading" description="填写模拟输入后开始执行" :image-size="88" />
    <template v-else-if="simulation">
      <section><h3>1. 状态变化</h3><el-descriptions :column="3" border size="small"><el-descriptions-item label="命中状态">{{ simulation.trigger && simulation.trigger.status }}</el-descriptions-item><el-descriptions-item label="事件">{{ simulation.trigger && simulation.trigger.eventType }}</el-descriptions-item><el-descriptions-item label="耗时">{{ result.durationMs }} ms</el-descriptions-item></el-descriptions></section>
      <section><h3>2. 命中模板</h3><el-descriptions :column="2" border size="small"><el-descriptions-item label="版本 ID">{{ simulation.versionId }}</el-descriptions-item><el-descriptions-item label="定义哈希"><span class="hash">{{ simulation.definitionHash }}</span></el-descriptions-item></el-descriptions></section>
      <section><h3>3. 负责人</h3><el-descriptions :column="3" border size="small"><el-descriptions-item label="解析状态">{{ simulation.owner && simulation.owner.status }}</el-descriptions-item><el-descriptions-item label="负责人 ID">{{ (simulation.owner && simulation.owner.ownerId) || '-' }}</el-descriptions-item><el-descriptions-item label="是否兜底">{{ simulation.owner && simulation.owner.fallbackUsed ? '是' : '否' }}</el-descriptions-item></el-descriptions></section>
      <section><h3>4. SLA</h3><el-descriptions :column="2" border size="small"><el-descriptions-item label="截止时间">{{ format(simulation.sla && simulation.sla.dueAt) }}</el-descriptions-item><el-descriptions-item label="工作日历">{{ (simulation.sla && simulation.sla.calendarCode) || '-' }}</el-descriptions-item></el-descriptions></section>
      <section><h3>5. DoD</h3><pre>{{ pretty(simulation.form && simulation.form.dod) }}</pre></section>
      <section><h3>6. 下一步路由</h3><el-table :data="simulation.routes || []" size="mini"><el-table-column prop="order" label="顺序" width="64" /><el-table-column prop="nodeKey" label="节点" /><el-table-column prop="nodeType" label="类型" /><el-table-column prop="status" label="状态" /></el-table></section>
      <section><h3>7. 待办卡片预览</h3><div class="todo-card"><strong>{{ card.title || '待办卡片' }}</strong><p>{{ card.description || '模板未配置卡片描述' }}</p><el-tag size="small">{{ card.priority || 'NORMAL' }}</el-tag></div></section>
      <section><h3>8. 技术日志</h3><pre>{{ pretty({ trigger: simulation.trigger && simulation.trigger.trace, owner: simulation.owner && simulation.owner.trace, sla: simulation.sla && simulation.sla.trace, handlers: simulation.handlers, autoActions: simulation.autoActions, issues: simulation.issues }) }}</pre></section>
    </template>
  </el-card>
</template>
<script>
export default { name: 'SimulationResult', props: { result: { type: Object, default: null }, loading: Boolean }, computed: { simulation() { return this.result && this.result.simulation }, card() { return (this.simulation && this.simulation.form && this.simulation.form.ui) || {} } }, methods: { pretty(value) { return JSON.stringify(value || {}, null, 2) }, format(value) { return value ? this.parseTime(value, '{y}-{m}-{d} {h}:{i}:{s}') : '-' } } }
</script>
<style scoped lang="scss">@import '../styles/config-center.scss';.simulation-result section+section{margin-top:18px}.simulation-result h3{margin:0 0 10px;color:#253858;font-size:14px}.simulation-result pre{max-height:210px;margin:0;padding:12px;overflow:auto;border:1px solid #e8edf6;border-radius:6px;background:#f8fafc;white-space:pre-wrap;word-break:break-word}.hash{font-family:monospace;font-size:12px}.todo-card{padding:14px;border:1px solid #e8edf6;border-left:4px solid #2563eb;border-radius:6px}.todo-card p{margin:8px 0;color:#64748b}</style>
