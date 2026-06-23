<template>
  <div class="matter-material-list" :class="{ 'is-readonly': readonly }">
    <div v-for="(item, index) in innerValue" :key="index" class="matter-material-row">
      <div class="material-main">
        <el-input
          v-if="!readonly"
          v-model="item.materialName"
          :size="controlSize"
          placeholder="资料名称"
          @input="emitChange"
        />
        <strong v-else>{{ item.materialName || item.material_name || '-' }}</strong>
        <small v-if="item.remark">{{ item.remark }}</small>
      </div>

      <el-select
        v-if="!readonly"
        v-model="item.materialStatus"
        :size="controlSize"
        placeholder="状态"
        @change="emitChange"
      >
        <el-option v-for="option in options" :key="option.value" :label="option.label" :value="option.value" />
      </el-select>
      <dict-tag v-else :options="options" :value="item.materialStatus || item.material_status" />

      <file-upload
        v-if="showUpload && !readonly"
        v-model="item.fileUrl"
        :limit="1"
        @input="syncFile(index, $event)"
      />
      <a v-else-if="showUpload && (item.fileUrl || item.file_url)" class="material-file" @click="$emit('open', item.fileUrl || item.file_url)">
        {{ item.fileName || item.file_name || '查看附件' }}
      </a>
      <span v-else-if="showUpload" class="material-file muted">暂无附件</span>

      <el-button v-if="!readonly" :size="controlSize" type="text" class="danger-text" @click="remove(index)">删除</el-button>
    </div>

    <el-empty v-if="!innerValue.length && readonly" description="暂无材料" :image-size="64" />
    <el-button v-if="!readonly" :size="controlSize" type="text" icon="el-icon-plus" @click="add">添加材料</el-button>
  </div>
</template>

<script>
export default {
  name: 'MatterMaterialList',
  props: {
    value: { type: Array, default: () => [] },
    options: { type: Array, default: () => [] },
    controlSize: { type: String, default: 'mini' },
    defaultStatus: { type: String, default: 'pending' },
    showUpload: { type: Boolean, default: false },
    readonly: { type: Boolean, default: false }
  },
  computed: {
    innerValue: {
      get() {
        return this.value || []
      },
      set(value) {
        this.$emit('input', value)
      }
    }
  },
  methods: {
    emitChange() {
      this.$emit('input', this.innerValue)
    },
    add() {
      this.innerValue = this.innerValue.concat({ materialName: '', materialStatus: this.defaultStatus })
    },
    remove(index) {
      const next = this.innerValue.slice()
      next.splice(index, 1)
      this.innerValue = next
    },
    syncFile(index, value) {
      const next = this.innerValue.slice()
      const item = { ...next[index] }
      item.fileUrl = value
      item.fileName = this.fileNameFromUrl(value)
      next.splice(index, 1, item)
      this.innerValue = next
    },
    fileNameFromUrl(value) {
      if (!value) return ''
      return String(value).split('/').pop()
    }
  }
}
</script>

<style scoped lang="scss">
.matter-material-list {
  display: grid;
  gap: 7px;
}

.matter-material-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 112px auto auto;
  align-items: center;
  gap: 8px;
  padding: 8px 9px;
  border: 1px solid #edf1f7;
  border-radius: 9px;
  background: #fff;
}

.material-main {
  min-width: 0;

  strong,
  small {
    display: block;
  }

  strong {
    color: #334155;
    font-size: var(--biz-font-small, 12px);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  small {
    margin-top: 3px;
    color: #94a3b8;
    font-size: var(--biz-font-mini, 11px);
  }
}

.material-file {
  color: #2563eb;
  font-size: var(--biz-font-small, 12px);
  cursor: pointer;
  white-space: nowrap;
}

.material-file.muted {
  color: #94a3b8;
  cursor: default;
}

.is-readonly .matter-material-row {
  grid-template-columns: minmax(0, 1fr) auto auto;
  background: #f8fbff;
}

.danger-text {
  color: #ef4444 !important;
}

@media (max-width: 760px) {
  .matter-material-row,
  .is-readonly .matter-material-row {
    grid-template-columns: 1fr;
  }
}
</style>
