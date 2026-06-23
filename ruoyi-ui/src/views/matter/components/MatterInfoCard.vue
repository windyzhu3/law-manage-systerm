<template>
  <section class="info-card" :class="{ 'span-2': span }">
    <header>
      <h4>{{ title }}</h4>
      <slot name="extra" />
    </header>
    <dl class="info-list" :class="{ single }">
      <div v-for="item in normalizedItems" :key="item.label" :class="{ wide: item.wide }">
        <dt>{{ item.label }}</dt>
        <dd :class="item.className">{{ item.value || '-' }}</dd>
      </div>
      <el-empty v-if="!normalizedItems.length" :description="emptyText" :image-size="72" />
    </dl>
    <slot />
  </section>
</template>

<script>
export default {
  name: 'MatterInfoCard',
  props: {
    title: { type: String, required: true },
    items: { type: Array, default: () => [] },
    span: { type: Boolean, default: false },
    single: { type: Boolean, default: false },
    emptyText: { type: String, default: '暂无数据' }
  },
  computed: {
    normalizedItems() {
      return (this.items || []).filter(Boolean).map(item => ({
        label: item.label,
        value: item.value,
        wide: item.wide,
        className: item.className
      }))
    }
  }
}
</script>
