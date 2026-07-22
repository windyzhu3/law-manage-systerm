<template>
  <el-card shadow="never" class="simulation-result" v-loading="loading">
    <div slot="header"><strong>模拟结果</strong><span v-if="result">耗时 {{ result.durationMs }} ms</span></div>
    <el-empty v-if="!simulation && !loading" description="选择模板、事件和测试对象后，点击“开始模拟”" :image-size="88" />
    <template v-else-if="simulation">
      <el-alert :title="overallPassed ? '模拟完成，配置链路可以执行' : '模拟完成，但存在需要处理的问题'" :type="overallPassed ? 'success' : 'warning'" :closable="false" show-icon />
      <article v-if="businessObject" class="object-context"><span>测试对象</span><strong>{{ businessObject.businessName }}</strong><small>{{ businessObject.businessNo }} · {{ businessObject.businessType }}</small><el-tag v-if="businessObject.sample" type="warning" size="mini">示例对象</el-tag></article>
      <ol class="step-list">
        <li v-for="step in orderedSteps" :key="step.key" :class="`step-list__item step-list__item--${step.tone}`">
          <div class="step-index"><i :class="step.icon" /></div>
          <div><strong>{{ step.label }}</strong><p>{{ step.summary }}</p><small v-if="step.detail">{{ step.detail }}</small></div>
          <el-tag :type="step.tagType" size="small">{{ step.status }}</el-tag>
        </li>
      </ol>

      <section class="result-section"><h3>完成条件</h3><pre>{{ pretty(simulation.form && simulation.form.dod) }}</pre></section>
      <section class="result-section"><h3>下一步路由</h3><el-table :data="simulation.routes || []" size="mini" empty-text="没有后续路由"><el-table-column prop="order" label="顺序" width="64" /><el-table-column prop="nodeKey" label="节点" /><el-table-column prop="nodeType" label="类型" /><el-table-column prop="status" label="状态" /></el-table></section>
      <section class="result-section"><h3>待办卡片预览</h3><div class="todo-card"><div><strong>{{ card.title || '待办卡片' }}</strong><p>{{ card.description || '模板未配置卡片描述' }}</p></div><el-tag size="small">{{ card.priority || 'NORMAL' }}</el-tag></div></section>
      <el-collapse class="technical-diagnostics"><el-collapse-item title="技术诊断" name="technical"><pre>{{ pretty({ definitionHash: simulation.definitionHash, trigger: simulation.trigger, owner: simulation.owner, sla: simulation.sla, handlers: simulation.handlers, autoActions: simulation.autoActions, issues: simulation.issues }) }}</pre></el-collapse-item></el-collapse>
    </template>
  </el-card>
</template>

<script>
export default {
  name: 'SimulationResult',
  props: { result: { type: Object, default: null }, loading: Boolean, businessObject: { type: Object, default: null } },
  computed: {
    simulation() { return this.result && this.result.simulation },
    card() { return (this.simulation && this.simulation.form && this.simulation.form.ui) || {} },
    issues() { return (this.simulation && this.simulation.issues) || [] },
    overallPassed() { return this.simulation && !this.issues.length && (!this.simulation.trigger || !['FAILED', 'NOT_MATCHED'].includes(this.simulation.trigger.status)) },
    orderedSteps() {
      const value = this.simulation || {}; const trigger = value.trigger || {}; const owner = value.owner || {}; const sla = value.sla || {}; const dod = value.form && value.form.dod
      return [
        this.step('event', '事件匹配', trigger.status || '已检查', trigger.eventType ? `已使用 ${trigger.eventType} 匹配触发规则` : '未返回事件匹配信息', trigger.trace && this.pretty(trigger.trace), !['FAILED', 'NOT_MATCHED'].includes(trigger.status)),
        this.step('template', '模板预检', value.versionId ? '已通过' : '待确认', value.versionId ? `模板版本 ${value.versionId} 已加载并完成预检` : '没有可用模板版本', value.definitionHash ? `定义哈希：${value.definitionHash}` : '', Boolean(value.versionId)),
        this.step('owner', '负责人解析', owner.status || '已检查', owner.ownerId ? `已解析负责人 #${owner.ownerId}` : (owner.fallbackUsed ? '已使用兜底负责人规则' : '未解析到具体负责人'), owner.fallbackUsed ? '本次使用了兜底策略' : '', !['FAILED', 'UNRESOLVED'].includes(owner.status)),
        this.step('sla', 'SLA 计算', sla.dueAt ? '已计算' : '待确认', sla.dueAt ? `截止时间 ${this.format(sla.dueAt)}` : '没有计算出截止时间', sla.calendarCode ? `工作日历：${sla.calendarCode}` : '', Boolean(sla.dueAt)),
        this.step('dod', '完成条件', dod ? '已装载' : '未配置', dod ? '已生成待办完成条件和表单要求' : '模板没有完成条件', '', Boolean(dod)),
        this.step('route', '下一步路由', '已计算', (value.routes || []).length ? `共 ${(value.routes || []).length} 条候选路由` : '当前节点没有后续路由', '', true),
        this.step('card', '待办卡片预览', this.card.title ? '已生成' : '使用默认值', this.card.title || '将使用默认待办标题', this.card.description || '', true)
      ]
    }
  },
  methods: {
    step(key, label, status, summary, detail, passed) { return { key, label, status, summary, detail, tone: passed ? 'success' : 'warning', tagType: passed ? 'success' : 'warning', icon: passed ? 'el-icon-check' : 'el-icon-warning-outline' } },
    pretty(value) { return JSON.stringify(value || {}, null, 2) },
    format(value) { return value ? this.parseTime(value, '{y}-{m}-{d} {h}:{i}:{s}') : '-' }
  }
}
</script>

<style scoped lang="scss">
@import '../styles/config-center.scss';.simulation-result [slot="header"]{display:flex;justify-content:space-between}.simulation-result [slot="header"] span{color:#64748b;font-size:12px}.simulation-result>.el-alert{margin-bottom:14px}.object-context{display:grid;grid-template-columns:1fr auto;align-items:center;margin-bottom:14px;padding:12px;border:1px solid #dbe5f1;border-radius:8px;background:#f8fafc}.object-context span,.object-context strong,.object-context small{grid-column:1;display:block}.object-context span,.object-context small{color:#64748b;font-size:12px}.object-context .el-tag{grid-column:2;grid-row:1/4}.step-list{margin:0;padding:0;list-style:none}.step-list__item{display:grid;grid-template-columns:34px 1fr auto;gap:10px;align-items:center;padding:12px 0;border-bottom:1px solid #eef2f7}.step-index{display:flex;justify-content:center;align-items:center;width:28px;height:28px;border-radius:50%;background:#dcfce7;color:#16a34a}.step-list__item--warning .step-index{background:#fef3c7;color:#d97706}.step-list__item p{margin:4px 0;color:#334155;font-size:13px}.step-list__item small{color:#94a3b8}.result-section{margin-top:18px}.result-section h3{margin:0 0 10px;color:#253858;font-size:14px}.result-section pre,.technical-diagnostics pre{max-height:210px;margin:0;padding:12px;overflow:auto;border:1px solid #e8edf6;border-radius:6px;background:#f8fafc;white-space:pre-wrap;word-break:break-word}.todo-card{display:flex;justify-content:space-between;align-items:flex-start;padding:14px;border:1px solid #e8edf6;border-left:4px solid #2563eb;border-radius:6px}.todo-card p{margin:8px 0;color:#64748b}.technical-diagnostics{margin-top:16px}
</style>
