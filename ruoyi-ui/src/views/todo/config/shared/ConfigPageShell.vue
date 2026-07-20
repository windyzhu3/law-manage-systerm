<template>
  <section class="config-page-shell" :class="{ 'config-page-shell--compact': compact }">
    <header class="config-page-shell__header">
      <div class="config-page-shell__heading">
        <h1>{{ title }}</h1>
        <p v-if="subtitle">{{ subtitle }}</p>
        <p v-else-if="help" class="config-page-shell__help">{{ help }}</p>
      </div>
      <div class="config-page-shell__actions">
        <slot name="secondary-actions" />
        <slot name="primary-action">
          <el-button v-if="primaryActionText" type="primary" size="small" @click="$emit('primary-action')">{{ primaryActionText }}</el-button>
        </slot>
      </div>
    </header>

    <div v-if="$slots.metrics" class="config-page-shell__metrics"><slot name="metrics" /></div>
    <div v-if="$slots.filters" class="config-page-shell__filters"><slot name="filters" /></div>

    <main class="config-page-shell__content" v-loading="loading" :aria-busy="String(loading)">
      <slot v-if="loading" name="loading"><div class="config-page-shell__loading">正在加载配置数据…</div></slot>
      <slot v-else-if="empty" name="empty"><el-empty :description="emptyText" :image-size="88" /></slot>
      <slot v-else />
    </main>
  </section>
</template>

<script>
export default {
  name: 'ConfigPageShell',
  props: {
    title: { type: String, required: true },
    subtitle: { type: String, default: '' },
    help: { type: String, default: '' },
    primaryActionText: { type: String, default: '' },
    loading: Boolean,
    empty: Boolean,
    emptyText: { type: String, default: '暂无配置数据' },
    compact: Boolean
  }
}
</script>

<style scoped lang="scss">
@import '../styles/config-center.scss';
</style>
