<template>
  <section class="sla-timeline" aria-label="办理时限时间线">
    <header>
      <div><h3>员工办理时间线</h3><p>时间点会随时长、工作日历和起算时间实时更新。</p></div>
      <el-tag size="small" type="warning">80% / 100% / 150%</el-tag>
    </header>
    <div class="sla-timeline__track">
      <article v-for="point in displayPoints" :key="point.semantic" class="sla-point" :class="`is-${point.semantic.toLowerCase()}`">
        <div class="sla-point__marker"><i :class="icon(point.semantic)" /></div>
        <span class="sla-point__percent">{{ point.percent ? `${point.percent}%` : '创建' }}</span>
        <strong>{{ title(point) }}</strong>
        <time>{{ format(point.at) }}</time>
        <ul>
          <li v-for="action in point.actions" :key="action">{{ actionLabel(action) }}</li>
          <li v-if="!point.actions.length">{{ defaultAction(point.semantic) }}</li>
        </ul>
      </article>
    </div>
  </section>
</template>

<script>
const FALLBACK_POINTS = [
  { percent: 0, semantic: 'CREATED', title: '创建待办', at: null, actions: [] },
  { percent: 80, semantic: 'REMINDER', title: '80% 提醒', at: null, actions: [] },
  { percent: 100, semantic: 'OVERDUE', title: '100% 超时', at: null, actions: [] },
  { percent: 150, semantic: 'ESCALATION', title: '150% 升级', at: null, actions: [] }
]

export default {
  name: 'SlaTimelinePreview',
  props: { points: { type: Array, default: () => [] } },
  computed: {
    displayPoints() { return this.points.length === 4 ? this.points : FALLBACK_POINTS }
  },
  methods: {
    title(point) {
      return point.title || {
        CREATED: '创建待办',
        REMINDER: '80% 提醒',
        OVERDUE: '100% 超时',
        ESCALATION: '150% 升级'
      }[point.semantic]
    },
    icon(semantic) {
      return {
        CREATED: 'el-icon-plus',
        REMINDER: 'el-icon-bell',
        OVERDUE: 'el-icon-time',
        ESCALATION: 'el-icon-top-right'
      }[semantic] || 'el-icon-circle-check'
    },
    format(value) {
      if (!value) return '完成配置后计算'
      const date = new Date(value)
      if (Number.isNaN(date.getTime())) return String(value)
      const pad = part => String(part).padStart(2, '0')
      return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
    },
    actionLabel(action) {
      return {
        START_TIMER: '开始计时',
        REMIND_OWNER: '提醒当前负责人',
        MARK_OVERDUE: '标记为超时',
        NOTIFY_MANAGER: '通知负责人上级',
        ESCALATE: '升级给主管处理',
        TRANSFER: '自动转派',
        RETURN_TO_POOL: '自动回到候选池'
      }[action] || String(action)
    },
    defaultAction(semantic) {
      return {
        CREATED: '开始计时',
        REMINDER: '提醒负责人尽快办理',
        OVERDUE: '标记超时并通知主管',
        ESCALATION: '升级、转派或回池'
      }[semantic]
    }
  }
}
</script>

<style scoped lang="scss">
.sla-timeline {
  padding: 16px;
  background: #F7F9FC;
  border: 1px solid #D9E1EA;
  border-radius: 8px;

  > header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;

    h3 { margin: 0; font-size: 16px; color: #0B2A55; }
    p { margin: 4px 0 0; font-size: 12px; color: #65758A; }
  }
}

.sla-timeline__track {
  position: relative;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0;
  margin-top: 22px;

  &::before {
    position: absolute;
    top: 17px;
    right: 10%;
    left: 10%;
    height: 2px;
    content: '';
    background: #C9D5E3;
  }
}

.sla-point {
  position: relative;
  z-index: 1;
  min-width: 0;
  padding: 0 8px;
  text-align: center;

  strong,
  time,
  span {
    display: block;
  }

  strong { margin-top: 7px; font-size: 13px; color: #0B2A55; }
  time { margin-top: 3px; font-size: 12px; color: #65758A; }

  ul {
    min-height: 42px;
    padding: 8px;
    margin: 9px 0 0;
    font-size: 11px;
    line-height: 18px;
    color: #53667C;
    list-style: none;
    background: #FFFFFF;
    border-radius: 8px;
  }
}

.sla-point__marker {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  margin: 0 auto;
  color: #FFFFFF;
  background: #0B2A55;
  border: 4px solid #F7F9FC;
  border-radius: 50%;
}

.sla-point__percent {
  margin-top: 4px;
  font-size: 11px;
  font-weight: 700;
  color: #C89A3D;
}

.sla-point.is-overdue .sla-point__marker { background: #B7791F; }
.sla-point.is-escalation .sla-point__marker { background: #B83232; }

@media (max-width: 760px) {
  .sla-timeline__track {
    grid-template-columns: 1fr;
    gap: 12px;

    &::before { display: none; }
  }
}
</style>
