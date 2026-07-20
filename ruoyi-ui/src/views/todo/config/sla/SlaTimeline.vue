<template>
  <section class="sla-timeline" :class="{ 'sla-timeline--preview': preview }" aria-label="SLA 80 100 150 阈值说明">
    <p v-if="preview" class="sla-timeline__hint">预览仅展示固定阈值；保存后可使用工作日历进行权威测算。</p>
    <div class="sla-timeline__rail" aria-hidden="true"><span /></div>
    <div class="sla-timeline__points">
      <article class="sla-timeline__point sla-timeline__point--remind">
        <strong>80%</strong><span>提醒</span><time>{{ format(result && result.remind80At) }}</time>
      </article>
      <article class="sla-timeline__point sla-timeline__point--overdue">
        <strong>100%</strong><span>超时</span><time>{{ format(result && result.overdue100At) }}</time>
      </article>
      <article class="sla-timeline__point sla-timeline__point--escalate">
        <strong>150%</strong><span>升级</span><time>{{ format(result && result.escalate150At) }}</time>
      </article>
    </div>
    <p v-if="result && result.createdAt" class="sla-timeline__started">计时起点：{{ format(result.createdAt) }}</p>
  </section>
</template>

<script>
export default {
  name: 'SlaTimeline',
  props: { result: { type: Object, default: null }, preview: Boolean },
  methods: {
    format(value) { return value ? this.parseTime(value, '{y}-{m}-{d} {h}:{i}:{s}') : (this.preview ? '保存后测算' : '尚未测算') }
  }
}
</script>

<style scoped lang="scss">
.sla-timeline { padding: 14px 0 4px; }
.sla-timeline__hint,.sla-timeline__started { margin: 0 0 10px; color: #64748b; font-size: 12px; }
.sla-timeline__rail { height: 3px; margin: 18px 8% 0; border-radius: 2px; background: linear-gradient(90deg,#f59e0b 0%,#ef4444 50%,#7c3aed 100%); }
.sla-timeline__points { display: grid; grid-template-columns: repeat(3,1fr); gap: 10px; margin-top: -9px; }
.sla-timeline__point { position: relative; padding-top: 20px; text-align: center; color: #475569; font-size: 12px; }
.sla-timeline__point::before { position: absolute; top: 0; left: calc(50% - 8px); width: 16px; height: 16px; border: 3px solid #fff; border-radius: 50%; background: currentColor; box-shadow: 0 0 0 1px currentColor; content: ''; }
.sla-timeline__point strong,.sla-timeline__point span,.sla-timeline__point time { display: block; }
.sla-timeline__point strong { font-size: 15px; }
.sla-timeline__point time { min-height: 18px; margin-top: 3px; color: #94a3b8; font-size: 11px; }
.sla-timeline__point--remind { color: #d97706; }.sla-timeline__point--overdue { color: #dc2626; }.sla-timeline__point--escalate { color: #7c3aed; }
.sla-timeline--preview { opacity: .82; }
</style>
