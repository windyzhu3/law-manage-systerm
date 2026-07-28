<template>
  <section class="scenario-selector" data-testid="scenario-selector">
    <div class="scenario-selector__heading">
      <div>
        <h3>完成场景验证</h3>
        <p>按真实业务结果验证待办完成后会进入正确的下一待办。</p>
      </div>
    </div>
    <div class="scenario-selector__cards">
      <button
        v-for="scenario in scenarios"
        :key="scenario.scenarioCode"
        type="button"
        class="scenario-card"
        :class="{ 'is-active': scenario.scenarioCode === selectedCode }"
        @click="$emit('select', scenario.scenarioCode)"
      >
        <span class="scenario-card__title">{{ scenario.scenarioName }}</span>
        <el-tag v-if="result(scenario).passed" size="mini" type="success">已通过</el-tag>
        <el-tag v-else size="mini" type="info">待验证</el-tag>
        <small>预期下一待办：{{ scenario.expectedNextTemplateCode }}</small>
        <small v-if="result(scenario).actualNextTemplateCode">
          实际下一待办：{{ result(scenario).actualNextTemplateCode }}
        </small>
      </button>
    </div>
    <p class="scenario-selector__legend">标准场景：有效首联、疑似无效、未接通</p>
  </section>
</template>

<script>
export default {
  name: 'ScenarioSelector',
  props: {
    scenarios: { type: Array, default: () => [] },
    selectedCode: { type: String, default: '' },
    results: { type: Object, default: () => ({}) }
  },
  methods: {
    result(scenario) { return this.results[scenario.scenarioCode] || {} }
  }
}
</script>

<style scoped>
.scenario-selector { border: 1px solid #D9E1EA; border-radius: 8px; padding: 20px; background: #fff; }
.scenario-selector__heading h3 { margin: 0 0 6px; color: #0B2A55; }
.scenario-selector__heading p, .scenario-selector__legend { margin: 0; color: #66758A; }
.scenario-selector__cards { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; margin: 16px 0 10px; }
.scenario-card { display: grid; grid-template-columns: 1fr auto; gap: 8px; text-align: left; padding: 16px; border: 1px solid #D9E1EA; border-radius: 8px; background: #fff; cursor: pointer; }
.scenario-card:hover, .scenario-card.is-active { border-color: #0B2A55; background: #F5F8FC; }
.scenario-card__title { font-weight: 700; color: #0B2A55; }
.scenario-card small { grid-column: 1 / -1; color: #66758A; }
@media (max-width: 860px) { .scenario-selector__cards { grid-template-columns: 1fr; } }
</style>
