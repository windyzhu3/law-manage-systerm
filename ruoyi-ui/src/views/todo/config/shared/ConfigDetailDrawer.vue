<template>
  <el-drawer
    :visible="opened"
    :title="title"
    direction="rtl"
    :size="size"
    append-to-body
    destroy-on-close
    :before-close="requestClose"
    @closed="handleClosed"
  >
    <template slot="title"><slot name="header"><span class="config-detail-drawer__title">{{ title }}</span></slot></template>
    <div class="config-detail-drawer">
      <div class="config-detail-drawer__body"><slot /></div>
      <footer class="config-detail-drawer__footer">
        <slot name="footer" :close="closeAfterSave">
          <el-button size="small" @click="requestProgrammaticClose">取消</el-button>
        </slot>
      </footer>
    </div>
  </el-drawer>
</template>

<script>
export default {
  name: 'ConfigDetailDrawer',
  props: {
    value: { type: Boolean, default: undefined },
    visible: { type: Boolean, default: undefined },
    title: { type: String, default: '配置详情' },
    size: { type: String, default: '680px' },
    dirty: { type: [Boolean, Function], default: false },
    confirmTitle: { type: String, default: '确认关闭' },
    confirmMessage: { type: String, default: '当前修改尚未保存，确认关闭吗？' }
  },
  computed: {
    opened() { return this.value !== undefined ? this.value : Boolean(this.visible) }
  },
  data() { return { closeCommitted: false } },
  methods: {
    hasDirtyState() { return typeof this.dirty === 'function' ? Boolean(this.dirty()) : Boolean(this.dirty) },
    requestClose(done) {
      if (!this.hasDirtyState()) return this.finishClose(done)
      return this.$confirm(this.confirmMessage, this.confirmTitle, { type: 'warning' })
        .then(() => this.finishClose(done))
        .catch(() => undefined)
    },
    closeAfterSave() { this.finishClose() },
    requestProgrammaticClose() { return this.requestClose(() => undefined) },
    finishClose(done) {
      this.closeCommitted = true
      this.emitCloseIntent()
      if (typeof done === 'function') done()
    },
    handleClosed() {
      if (!this.closeCommitted) this.emitCloseIntent()
      this.closeCommitted = false
      this.$emit('closed')
    },
    emitCloseIntent() {
      this.reset()
      this.$emit('input', false)
      this.$emit('update:visible', false)
    },
    reset() { this.$emit('reset') }
  }
}
</script>

<style scoped lang="scss">
@import '../styles/config-center.scss';
</style>
