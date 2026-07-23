<template>
  <section class="simulation-trace" data-testid="simulation-trace">
    <div class="simulation-trace__heading">
      <h3>试运行轨迹</h3>
      <el-button v-if="items.length" type="text" icon="el-icon-refresh" @click="$emit('rerun')">重新试运行</el-button>
    </div>
    <el-empty v-if="!items.length" description="完成对象选择后运行一次完整验证" :image-size="72" />
    <ol v-else>
      <li v-for="item in items" :key="item.code" :class="statusClass(item)">
        <span class="simulation-trace__index">{{ indexOf(item.code) }}</span>
        <div>
          <strong>{{ title(item.code) }}</strong>
          <p>{{ safeMessage(item) }}</p>
        </div>
        <el-button
          v-if="needsRepair(item)"
          type="text"
          @click="$emit('repair', item.repairStep)"
        >
          返回修复
        </el-button>
      </li>
    </ol>
  </section>
</template>

<script>
import { orderedSimulationTrace } from '../journey-step-model'

const ORDER = ['EVENT', 'OWNER', 'DOD', 'SLA', 'ROUTING', 'TODO_PREVIEW']
const TITLES = {
  EVENT: '1. 事件与触发',
  OWNER: '2. 负责人解析',
  DOD: '3. 完成标准',
  SLA: '4. 办理时限',
  ROUTING: '5. 后续路由',
  TODO_PREVIEW: '6. 员工待办预览'
}

export default {
  name: 'SimulationTrace',
  props: { trace: { type: Array, default: () => [] } },
  computed: {
    items() { return orderedSimulationTrace(this.trace) }
  },
  methods: {
    indexOf(code) { return ORDER.indexOf(code) + 1 },
    title(code) { return TITLES[code] || code },
    safeMessage(item) {
      return String(item.message || item.summary || (this.needsRepair(item) ? '需要修复后重新试运行' : '验证通过'))
    },
    needsRepair(item) {
      return ['BLOCKED', 'FAILED', 'ERROR'].includes(String(item.status || item.state || '').toUpperCase())
    },
    statusClass(item) { return this.needsRepair(item) ? 'is-blocked' : 'is-success' }
  }
}
</script>

<style scoped>
.simulation-trace { border: 1px solid #D9E1EA; border-radius: 8px; padding: 20px; background: #FFFFFF; }
.simulation-trace__heading { display: flex; align-items: center; justify-content: space-between; }
.simulation-trace__heading h3 { margin: 0; color: #0B2A55; }
.simulation-trace ol { list-style: none; padding: 0; margin: 16px 0 0; }
.simulation-trace li { display: grid; grid-template-columns: 34px 1fr auto; align-items: start; gap: 12px; padding: 13px 0; border-top: 1px solid #E8EDF3; }
.simulation-trace__index { width: 28px; height: 28px; border-radius: 50%; text-align: center; line-height: 28px; background: #EAF0F7; color: #0B2A55; font-weight: 700; }
.simulation-trace li.is-success .simulation-trace__index { background: #E9F7EF; color: #2E7D4F; }
.simulation-trace li.is-blocked .simulation-trace__index { background: #FDEDEC; color: #B42318; }
.simulation-trace p { margin: 5px 0 0; color: #66758A; }
</style>
