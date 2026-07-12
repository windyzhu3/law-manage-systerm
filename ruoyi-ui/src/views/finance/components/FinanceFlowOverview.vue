<template>
  <section class="finance-flow-card finance-card">
    <div class="finance-card-title">
      <div>
        <h3>业务财务流程总览</h3>
        <p>合同签署、应收计划、回款、开票、费用与净收入全链路概览</p>
      </div>
    </div>
    <div class="finance-flow">
      <div v-for="(item, index) in cards" :key="item.key" class="finance-flow-item">
        <span :class="['flow-icon', item.color]"><i :class="item.icon" /></span>
        <div>
          <b>{{ item.title }}</b>
          <strong>{{ moneyFormatter(item.value) }}</strong>
        </div>
        <em v-if="index < cards.length - 1">{{ item.rate }}</em>
      </div>
    </div>
  </section>
</template>

<script>
export default {
  name: 'FinanceFlowOverview',
  props: {
    cards: { type: Array, default: () => [] },
    moneyFormatter: { type: Function, required: true }
  }
}
</script>

<style scoped lang="scss">
.finance-flow-card { margin-top: 16px; }
.finance-flow { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 12px; }
.finance-flow-item {
  position: relative; display: grid; grid-template-columns: 42px 1fr; align-items: center;
  gap: 10px; min-height: 70px; padding: 10px 8px; border-radius: 12px;
  background: linear-gradient(180deg, #f8fbff 0%, #fff 100%);
  > div { min-width: 0; }
  b, strong { display: block; }
  b { color: #64748b; font-size: var(--biz-font-small); }
  strong { margin-top: 4px; color: #0f172a; font-size: var(--biz-font-card); white-space: nowrap; }
  > em {
    position: absolute; right: -16px; top: 50%; z-index: 1; min-width: 38px;
    transform: translateY(-50%); color: #64748b; font-size: var(--biz-font-mini);
    font-style: normal; text-align: center;
    &::after { content: ''; display: block; width: 24px; height: 1px; margin: 3px auto 0; background: #cbd5e1; }
  }
}
.flow-icon {
  display: flex; align-items: center; justify-content: center; width: 42px; height: 42px;
  border-radius: 50%; color: #2563eb; background: #eaf2ff; font-size: 18px;
  &.green { color: #16a34a; background: #eafaf1; }
  &.purple { color: #7c3aed; background: #f3e8ff; }
  &.orange { color: #f97316; background: #fff3e7; }
  &.cyan { color: #0891b2; background: #e6fbff; }
}
@media (max-width: 1180px) {
  .finance-flow { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .finance-flow-item > em { display: none; }
}
</style>
