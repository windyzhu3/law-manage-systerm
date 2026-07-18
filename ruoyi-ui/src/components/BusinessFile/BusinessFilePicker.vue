<template>
  <div class="business-file-picker">
    <el-upload
      v-if="!disabled"
      action="#"
      :http-request="uploadFile"
      :limit="limit"
      :multiple="limit > 1"
      :on-exceed="onExceed"
      :show-file-list="false"
    >
      <el-button size="mini" type="primary" :loading="uploading">选择文件</el-button>
      <span slot="tip" class="el-upload__tip">文件将保存到统一文件中心</span>
    </el-upload>
    <div class="file-list">
      <div v-for="file in selections" :key="file.fileObjectId" class="file-row">
        <span class="file-name">{{ file.fileName || `文件 #${file.fileObjectId}` }}</span>
        <el-tag v-if="!canAccess(file)" size="mini" type="info">未授权</el-tag>
        <span v-else class="file-actions">
          <el-button type="text" size="mini" :loading="previewing === file.fileObjectId" @click="previewFile(file)">预览</el-button>
          <el-button type="text" size="mini" :loading="downloading === file.fileObjectId" @click="downloadFile(file)">下载</el-button>
          <el-button type="text" size="mini" :loading="versionLoading === file.fileObjectId" @click="showVersions(file)">版本</el-button>
        </span>
        <el-button v-if="!disabled" type="text" size="mini" class="remove" @click="removeFile(file)">移除</el-button>
      </div>
    </div>
    <el-dialog title="文件版本" :visible.sync="versionOpen" width="680px" append-to-body>
      <el-table v-loading="versionLoading !== null" :data="versions">
        <el-table-column label="版本" width="80"><template slot-scope="scope">v{{ scope.row.versionNo }}</template></el-table-column>
        <el-table-column prop="originalFileName" label="文件名" min-width="180" />
        <el-table-column prop="contentType" label="类型" min-width="150" />
        <el-table-column label="大小" width="110"><template slot-scope="scope">{{ formatSize(scope.row.sizeBytes) }}</template></el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="170" />
      </el-table>
      <span slot="footer"><el-button @click="versionOpen = false">关闭</el-button></span>
    </el-dialog>
  </div>
</template>

<script>
import { saveAs } from 'file-saver'
import {
  registerBusinessFile,
  completeBusinessFileUpload,
  getBusinessFilePreviewToken,
  getBusinessFileDownloadToken,
  listBusinessFileVersions,
  openBusinessFileContent
} from '@/api/todo'

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
  data() { return { uploading: false, previewing: null, downloading: null, versionLoading: null, versionOpen: false, versions: [] } },
  computed: {
    selections() {
      const values = this.value == null ? [] : (Array.isArray(this.value) ? this.value : [this.value])
      return values.map(value => typeof value === 'object' ? {
        ...value,
        fileObjectId: Number(value.fileObjectId || value.file_object_id),
        relationId: Number(value.relationId || value.relation_id) || null,
        fileName: value.fileName || value.file_name || value.originalFileName
      } : { fileObjectId: Number(value), relationId: null })
        .filter(value => Number(value.fileObjectId) > 0)
    }
  },
  methods: {
    canAccess(file) { return Number(file.fileObjectId) > 0 && Number(file.relationId) > 0 },
    data(response) { return (response && response.data) || response || {} },
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
          relationId: Number(intent.relationId) || null,
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
    async previewFile(file) {
      if (!this.canAccess(file)) return this.$modal.msgError('缺少业务关系授权，无法预览')
      const popup = window.open('', '_blank')
      if (!popup) return this.$modal.msgError('浏览器阻止了预览窗口，请允许弹出窗口后重试')
      this.previewing = file.fileObjectId
      try {
        popup.opener = null
        const issued = this.data(await getBusinessFilePreviewToken(file.fileObjectId, file.relationId))
        const blob = await openBusinessFileContent(issued.token)
        const url = URL.createObjectURL(blob)
        popup.location.replace(url)
        window.setTimeout(() => URL.revokeObjectURL(url), 60000)
      } catch (error) {
        popup.close()
        this.$modal.msgError((error && (error.msg || error.message)) || '文件预览失败')
      } finally {
        this.previewing = null
      }
    },
    async downloadFile(file) {
      if (!this.canAccess(file)) return this.$modal.msgError('缺少业务关系授权，无法下载')
      this.downloading = file.fileObjectId
      try {
        const issued = this.data(await getBusinessFileDownloadToken(file.fileObjectId, file.relationId))
        const blob = await openBusinessFileContent(issued.token)
        saveAs(blob, file.fileName || `文件-${file.fileObjectId}`)
      } catch (error) {
        this.$modal.msgError((error && (error.msg || error.message)) || '文件下载失败')
      } finally {
        this.downloading = null
      }
    },
    async showVersions(file) {
      if (!this.canAccess(file)) return this.$modal.msgError('缺少业务关系授权，无法查看版本')
      this.versionLoading = file.fileObjectId
      this.versionOpen = true
      this.versions = []
      try {
        const response = await listBusinessFileVersions(file.fileObjectId)
        this.versions = this.data(response)
      } catch (error) {
        this.versionOpen = false
        this.$modal.msgError((error && (error.msg || error.message)) || '文件版本加载失败')
      } finally {
        this.versionLoading = null
      }
    },
    formatSize(value) {
      const bytes = Number(value || 0)
      if (bytes < 1024) return `${bytes} B`
      if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
      return `${(bytes / 1024 / 1024).toFixed(1)} MB`
    },
    onExceed() { this.$modal.msgError(`最多选择 ${this.limit} 个文件`) }
  }
}
</script>

<style scoped>
.business-file-picker{width:100%}.el-upload__tip{margin-left:10px;color:#909399}.file-list{display:grid;gap:6px;margin-top:6px}.file-row{display:flex;align-items:center;gap:8px;min-height:32px;padding:4px 8px;border:1px solid #ebeef5;border-radius:4px}.file-name{min-width:0;flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.file-actions{white-space:nowrap}.remove{color:#f56c6c}
</style>
