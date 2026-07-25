<template>
  <div class="business-evidence-list" aria-label="业务证据文件">
    <el-alert v-if="error" role="alert" :title="error" type="error" :closable="false" show-icon>
      <el-button type="text" @click="load">重新加载</el-button>
    </el-alert>
    <div v-loading="loading">
      <business-file-picker
        v-if="files.length"
        :value="files"
        :business-type="businessType"
        :business-id="businessId"
        :limit="100"
        disabled
      />
      <el-empty v-else-if="!loading && !error" description="暂无可查看的文件证据" :image-size="56" />
    </div>
  </div>
</template>

<script>
import BusinessFilePicker from './BusinessFilePicker'
import { listBusinessFileMaterials } from '@/api/todo'

export default {
  name: 'BusinessFileEvidenceList',
  components: { BusinessFilePicker },
  props: {
    businessType: { type: String, required: true },
    businessId: { type: [Number, String], required: true },
    materialTypes: { type: Array, default: () => [] }
  },
  data() { return { loading: false, error: '', files: [] } },
  watch: {
    businessId: { immediate: true, handler() { this.load() } }
  },
  methods: {
    load() {
      if (!(Number(this.businessId) > 0)) return
      this.loading = true
      this.error = ''
      listBusinessFileMaterials(this.businessType, this.businessId).then(response => {
        const rows = response.data || []
        this.files = this.materialTypes.length
          ? rows.filter(file => this.materialTypes.includes(file.materialType))
          : rows
      }).catch(error => {
        this.error = (error && (error.msg || error.message)) || '文件证据加载失败'
      }).finally(() => { this.loading = false })
    }
  }
}
</script>

<style scoped>
.business-evidence-list{min-height:64px}
</style>
