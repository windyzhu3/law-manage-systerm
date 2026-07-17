<template>
  <div class="business-file-picker">
    <el-upload
      v-if="!disabled"
      action="#"
      :http-request="uploadFile"
      :file-list="fileList"
      :limit="limit"
      :multiple="limit > 1"
      :on-remove="removeFile"
      :on-exceed="onExceed"
      :show-file-list="true"
    >
      <el-button size="mini" type="primary" :loading="uploading">选择文件</el-button>
      <span slot="tip" class="el-upload__tip">文件将保存到统一文件中心</span>
    </el-upload>
    <ul v-else class="readonly-files">
      <li v-for="file in selections" :key="file.fileObjectId">{{ file.fileName || `文件 #${file.fileObjectId}` }}</li>
    </ul>
  </div>
</template>

<script>
import { registerBusinessFile, completeBusinessFileUpload } from '@/api/todo'

export default {
  name: 'BusinessFilePicker',
  props: {
    value: { type: [Number, String, Object, Array], default: null },
    businessType: { type: String, required: true },
    businessId: { type: [Number, String], required: true },
    materialType: { type: String, default: 'TODO_MATERIAL' },
    visibility: { type: String, default: 'BUSINESS' },
    limit: { type: Number, default: 1 },
    disabled: Boolean
  },
  data() { return { uploading: false } },
  computed: {
    selections() {
      const values = this.value == null ? [] : (Array.isArray(this.value) ? this.value : [this.value])
      return values.map(value => typeof value === 'object' ? value : { fileObjectId: Number(value) })
        .filter(value => Number(value.fileObjectId) > 0)
    },
    fileList() {
      return this.selections.map(value => ({
        name: value.fileName || `文件 #${value.fileObjectId}`,
        uid: String(value.fileObjectId),
        status: 'success',
        fileObjectId: Number(value.fileObjectId)
      }))
    }
  },
  methods: {
    async sha256(file) {
      if (!window.crypto || !window.crypto.subtle) throw new Error('当前浏览器不支持安全文件校验')
      const digest = await window.crypto.subtle.digest('SHA-256', await file.arrayBuffer())
      return Array.from(new Uint8Array(digest)).map(value => value.toString(16).padStart(2, '0')).join('')
    },
    async uploadFile(options) {
      this.uploading = true
      try {
        const file = options.file
        const actionId = `file-${Date.now()}-${Math.random().toString(16).slice(2)}`
        const registered = await registerBusinessFile({
          actionId,
          originalFileName: file.name,
          contentType: file.type || 'application/octet-stream',
          expectedSize: file.size,
          expectedSha256: await this.sha256(file),
          businessType: this.businessType,
          businessId: Number(this.businessId),
          materialType: this.materialType,
          visibility: this.visibility
        })
        const intent = registered.data || registered
        const completed = await completeBusinessFileUpload(intent.uploadIntentId, file)
        const version = completed.data || completed
        const selected = {
          fileObjectId: Number(version.fileObjectId || intent.fileObjectId),
          fileVersionId: version.fileVersionId,
          fileName: version.originalFileName || file.name,
          materialType: this.materialType
        }
        const next = this.limit === 1 ? selected : this.selections.concat(selected).slice(0, this.limit)
        this.$emit('input', next)
        this.$emit('change', next)
        options.onSuccess(completed)
      } catch (error) {
        options.onError(error)
      } finally {
        this.uploading = false
      }
    },
    removeFile(file) {
      const remaining = this.selections.filter(value => Number(value.fileObjectId) !== Number(file.fileObjectId))
      const next = this.limit === 1 ? null : remaining
      this.$emit('input', next)
      this.$emit('change', next)
    },
    onExceed() { this.$modal.msgError(`最多选择 ${this.limit} 个文件`) }
  }
}
</script>

<style scoped>.business-file-picker{width:100%}.el-upload__tip{margin-left:10px;color:#909399}.readonly-files{margin:0;padding-left:18px;color:#606266}</style>
