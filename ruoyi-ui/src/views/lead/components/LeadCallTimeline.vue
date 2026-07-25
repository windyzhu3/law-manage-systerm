<template>
  <section class="call-timeline" aria-label="通话证据时间线">
    <header>
      <div><h3>通话记录</h3><p>人工补录仅代表业务证据，不会伪造外呼平台回执。</p></div>
      <el-button
        v-if="editable"
        v-hasPermi="['lead:call-record:add']"
        type="primary"
        plain
        size="small"
        icon="el-icon-plus"
        @click="openEditor"
      >补录通话</el-button>
    </header>
    <el-alert v-if="error" role="alert" :title="error" type="error" :closable="false" show-icon>
      <el-button type="text" @click="load">重试</el-button>
    </el-alert>
    <div v-loading="loading" class="call-timeline__body">
      <el-timeline v-if="rows.length">
        <el-timeline-item v-for="row in rows" :key="row.callRecordId" :timestamp="timeText(row.factTime)" placement="top">
          <article>
            <div class="call-timeline__title">
              <strong>{{ channelLabel(row.callChannel) }}</strong>
              <el-tag size="mini" :type="resultType(row.result)">{{ row.result || '未标注结果' }}</el-tag>
            </div>
            <p>{{ row.detail || '暂无通话说明' }}</p>
            <small>时长 {{ secondsText(row.durationSeconds) }} · 经办 {{ row.todoOwnerName || row.businessOwnerName || '-' }}</small>
            <span v-if="row.recordingFileObjectId" class="call-timeline__evidence">
              <i class="el-icon-paperclip" /> 已关联录音/截图证据，请在下方文件证据中预览
            </span>
          </article>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else-if="!loading" description="暂无通话证据" :image-size="64" />
      <business-file-evidence-list
        v-if="Number(leadId) > 0"
        business-type="LEAD"
        :business-id="leadId"
        :material-types="['CALL_EVIDENCE', 'CONTACT_PROOF']"
      />
    </div>

    <el-dialog title="人工补录通话证据" :visible.sync="dialog" width="600px" append-to-body :close-on-click-modal="false" @closed="discardDraft">
      <el-alert title="此记录将固定标记为“人工补录”，不会生成外呼供应商ID。" type="info" :closable="false" show-icon />
      <el-form ref="callForm" :model="form" :rules="rules" label-width="104px" class="call-form">
        <el-form-item label="开始时间" prop="startedAt">
          <el-date-picker v-model="form.startedAt" type="datetime" value-format="yyyy-MM-ddTHH:mm:ss" style="width:100%" @change="semanticChange" />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker v-model="form.endedAt" type="datetime" value-format="yyyy-MM-ddTHH:mm:ss" style="width:100%" @change="semanticChange" />
        </el-form-item>
        <el-form-item label="通话时长">
          <el-input-number v-model="form.durationSeconds" :min="0" :precision="0" style="width:100%" @change="semanticChange" />
        </el-form-item>
        <el-form-item label="通话结果"><el-input v-model.trim="form.callResult" maxlength="32" @input="semanticChange" /></el-form-item>
        <el-form-item label="录音/截图">
          <business-file-picker
            v-model="recordingFile"
            business-type="LEAD"
            :business-id="leadId"
            material-type="CALL_EVIDENCE"
            :limit="1"
            @change="semanticChange"
          />
        </el-form-item>
        <el-form-item label="人工说明"><el-input v-model.trim="form.manualNotes" type="textarea" :rows="3" maxlength="1000" show-word-limit @input="semanticChange" /></el-form-item>
      </el-form>
      <div v-if="submitError" role="alert"><el-alert :title="submitError" type="error" :closable="false" show-icon /></div>
      <div slot="footer">
        <el-button @click="discardDraft">取消</el-button>
        <el-button type="primary" :loading="submitting" :disabled="submitting" @click="submit">保存证据</el-button>
      </div>
    </el-dialog>
  </section>
</template>

<script>
import BusinessFilePicker from '@/components/BusinessFile/BusinessFilePicker'
import BusinessFileEvidenceList from '@/components/BusinessFile/BusinessFileEvidenceList'
import { listLeadCallRecords, addLeadCallRecord } from '@/api/lead'
import { errorMessage, secondsText, stableActionId, timeText } from '../lead-todo-ui'

export default {
  name: 'LeadCallTimeline',
  components: { BusinessFilePicker, BusinessFileEvidenceList },
  props: { leadId: { type: [Number, String], required: true }, editable: Boolean },
  data() {
    return {
      loading: false, submitting: false, error: '', submitError: '', rows: [], dialog: false, recordingFile: null,
      form: {}, actionId: '', rules: { startedAt: [{ required: true, message: '请选择开始时间', trigger: 'change' }] }
    }
  },
  watch: { leadId: { immediate: true, handler() { this.load() } } },
  methods: {
    timeText, secondsText,
    channelLabel(value) { return ({ MANUAL: '人工补录', APP: '移动应用', OUTBOUND_SYSTEM: '外呼系统' })[value] || value || '-' },
    resultType(value) { return value === 'CONNECTED' ? 'success' : (value ? 'warning' : 'info') },
    load() {
      if (!(Number(this.leadId) > 0)) return
      this.loading = true; this.error = ''
      listLeadCallRecords(this.leadId).then(response => { this.rows = response.data || [] })
        .catch(error => { this.error = errorMessage(error, '通话记录加载失败') })
        .finally(() => { this.loading = false })
    },
    openEditor() {
      this.form = { startedAt: '', endedAt: '', durationSeconds: 0, callResult: '', manualNotes: '' }
      this.actionId = stableActionId('MANUAL_CALL', this.leadId)
      this.recordingFile = null; this.submitError = ''; this.dialog = true
      this.$nextTick(() => this.$refs.callForm && this.$refs.callForm.clearValidate())
    },
    submit() {
      this.$refs.callForm.validate(valid => {
        if (!valid) return
        this.submitting = true; this.submitError = ''
        const evidence = Array.isArray(this.recordingFile) ? this.recordingFile[0] : this.recordingFile
        const command = {
          actionId: this.actionId,
          startedAt: this.form.startedAt,
          durationSeconds: this.form.durationSeconds,
          recordingFileObjectId: evidence && evidence.fileObjectId
        }
        if (this.form.endedAt) command.endedAt = this.form.endedAt
        if (this.form.callResult) command.callResult = this.form.callResult
        if (this.form.manualNotes) command.manualNotes = this.form.manualNotes
        addLeadCallRecord(this.leadId, command).then(() => {
          this.$modal.msgSuccess('通话证据已保存')
          this.dialog = false; this.actionId = ''; this.load(); this.$emit('changed')
        }).catch(error => { this.submitError = errorMessage(error, '通话证据保存失败') })
          .finally(() => { this.submitting = false })
      })
    },
    semanticChange() {
      if (this.submitError) {
        this.actionId = stableActionId('MANUAL_CALL', this.leadId)
        this.submitError = ''
      }
    },
    discardDraft() {
      this.dialog = false
      this.actionId = ''
      this.form = {}
      this.recordingFile = null
      this.submitError = ''
    }
  }
}
</script>

<style scoped>
.call-timeline header{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;margin-bottom:12px}.call-timeline h3{margin:0;color:#0f172a;font-size:16px}.call-timeline header p{margin:5px 0 0;color:#64748b;font-size:12px}.call-timeline__body{min-height:90px}.call-timeline article{padding:12px;border:1px solid #e2e8f0;border-radius:8px;background:#f8fafc}.call-timeline__title{display:flex;align-items:center;justify-content:space-between;gap:8px}.call-timeline article p{margin:8px 0;color:#475569}.call-timeline article small,.call-timeline__evidence{display:block;color:#64748b;font-size:12px}.call-timeline__evidence{margin-top:7px;color:#0369a1}.call-form{margin-top:16px}
</style>
