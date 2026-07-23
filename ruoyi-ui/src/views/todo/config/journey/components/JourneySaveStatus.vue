<template>
  <div class="journey-save-status" :class="`is-${normalized.toLowerCase()}`" role="status" aria-live="polite">
    <i :class="icon" aria-hidden="true" />
    <span>{{ label }}</span>
    <el-button v-if="normalized === 'FAILED'" type="text" @click="$emit('retry')">重新保存</el-button>
  </div>
</template>

<script>
export default {
  name: 'JourneySaveStatus',
  props: {
    state: { type: String, default: 'IDLE' }
  },
  computed: {
    normalized() {
      const state = String(this.state || 'IDLE').toUpperCase()
      return ['IDLE', 'SAVING', 'SAVED', 'FAILED'].includes(state) ? state : 'IDLE'
    },
    label() {
      return {
        IDLE: '有尚未保存的修改',
        SAVING: '正在保存…',
        SAVED: '所有修改已保存',
        FAILED: '保存失败，本地修改仍保留'
      }[this.normalized]
    },
    icon() {
      return {
        IDLE: 'el-icon-edit-outline',
        SAVING: 'el-icon-loading',
        SAVED: 'el-icon-circle-check',
        FAILED: 'el-icon-warning-outline'
      }[this.normalized]
    }
  }
}
</script>

<style scoped lang="scss">
.journey-save-status {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  min-height: 32px;
  font-size: 13px;
  line-height: 20px;
  color: #65758A;

  &.is-saved {
    color: #256B4A;
  }

  &.is-failed {
    color: #A23535;
  }

  i {
    font-size: 16px;
  }
}
</style>
