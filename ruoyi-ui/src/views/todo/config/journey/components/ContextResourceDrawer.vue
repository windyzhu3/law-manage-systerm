<template>
  <div>
    <event-resource-drawer
      v-if="visible && access.allowed && resourceType === 'EVENT'"
      :visible="visible"
      :resource-id="eventId"
      :mode="eventMode"
      @update:visible="updateVisible"
      @saved="saved"
      @version-created="versionCreated"
    />
    <resource-item-drawer
      v-else-if="visible && access.allowed && itemResource"
      :visible="visible"
      :resource-type="resourceType"
      :business-type="request.businessType || businessType"
      :item="request.item || null"
      :field-options="resources.fields || []"
      :material-options="resources.materials || []"
      :validator-options="resources.validators || []"
      :focus-field="request.focusField || ''"
      @update:visible="updateVisible"
      @saved="saved"
    />
    <work-calendar-dialog
      v-else-if="visible && access.allowed && resourceType === 'CALENDAR'"
      ref="calendarDialog"
      :on-save="saveCalendar"
      @closed="calendarClosed"
    />
    <el-drawer
      v-else-if="visible"
      title="维护配置资源"
      :visible="visible"
      size="440px"
      :append-to-body="true"
      @close="updateVisible(false)"
    >
      <div class="context-resource-denied">
        <i class="el-icon-lock" />
        <h3>当前账号不能维护该资源</h3>
        <p>{{ access.message }}。模板草稿未发生变化，你可以联系具有资源维护权限的管理员处理。</p>
        <el-button @click="updateVisible(false)">返回模板配置</el-button>
      </div>
    </el-drawer>
  </div>
</template>

<script>
import EventResourceDrawer from '../../resource/EventResourceDrawer'
import ResourceItemDrawer from '../../resource/ResourceItemDrawer'
import WorkCalendarDialog from '../../components/WorkCalendarDialog'
import { saveWorkCalendar } from '@/api/todo-definition'
import { resourceRepairAccess } from '../journey-step-model'

export default {
  name: 'ContextResourceDrawer',
  components: { EventResourceDrawer, ResourceItemDrawer, WorkCalendarDialog },
  props: {
    visible: Boolean,
    request: { type: Object, default: () => ({}) },
    permissions: { type: Array, default: () => [] },
    resources: { type: Object, default: () => ({}) },
    businessType: { type: String, default: '' }
  },
  data() {
    return { eventId: null, eventMode: 'edit' }
  },
  computed: {
    resourceType() { return String(this.request.type || '').toUpperCase() },
    itemResource() { return ['FIELD', 'MATERIAL', 'DOD_RECIPE'].includes(this.resourceType) },
    access() { return resourceRepairAccess(this.permissions, this.request) }
  },
  watch: {
    visible(open) { if (open) this.$nextTick(() => this.hydrate()) },
    request: { deep: true, handler() { if (this.visible) this.$nextTick(() => this.hydrate()) } }
  },
  methods: {
    hydrate() {
      this.eventId = this.request.resourceId || null
      this.eventMode = this.eventId ? 'edit' : 'create'
      if (this.resourceType === 'CALENDAR' && this.access.allowed) {
        this.$nextTick(() => {
          if (this.$refs.calendarDialog) this.$refs.calendarDialog.show(this.request.item || {})
        })
      }
    },
    updateVisible(value) { this.$emit('update:visible', value) },
    saved(resourceId) {
      this.$emit('repaired', { request: { ...this.request }, resourceId })
      this.updateVisible(false)
    },
    versionCreated(resourceId) {
      this.eventId = resourceId
      this.eventMode = 'edit'
      this.$modal.msgSuccess('新版本已打开，请补齐字段后保存并启用')
    },
    async saveCalendar(value) {
      const response = await saveWorkCalendar(value)
      this.$emit('repaired', { request: { ...this.request }, resourceId: value.calendarId || null })
      this.updateVisible(false)
      return response
    },
    calendarClosed() {
      if (this.visible) this.updateVisible(false)
    }
  }
}
</script>

<style scoped lang="scss">
.context-resource-denied {
  padding: 48px 32px;
  text-align: center;

  > i {
    font-size: 42px;
    color: #C89A3D;
  }

  h3 {
    margin: 16px 0 8px;
    color: #0B2A55;
  }

  p {
    margin: 0 0 20px;
    font-size: 13px;
    line-height: 22px;
    color: #65758A;
  }
}
</style>
