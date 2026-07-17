<template>
  <el-drawer title="模板版本" :visible.sync="open" size="620px" @close="$emit('update:visible', false)">
    <div class="drawer-body">
      <el-table :data="versions">
        <el-table-column prop="version_no" label="版本" width="80" />
        <el-table-column prop="status" label="状态" />
        <el-table-column prop="published_time" label="发布时间" />
        <el-table-column label="操作">
          <template slot-scope="{row}">
            <el-button type="text" @click="inspect(row)">查看</el-button>
            <el-button v-if="row.status === 'DRAFT'" v-hasPermi="['todo:definition:edit']" type="text" @click="edit(row)">编辑</el-button>
            <el-button v-if="row.status === 'DRAFT'" v-hasPermi="['todo:definition:publish']" type="text" @click="$emit('publish', row)">发布</el-button>
            <el-button v-hasPermi="['todo:definition:edit']" type="text" @click="$emit('copy-version', row)">复制草稿</el-button>
          </template>
        </el-table-column>
      </el-table>
      <definition-form v-if="editing" ref="definition" :value="editing" :readonly="editing.status !== 'DRAFT'" />
      <el-button v-if="editing && editing.status === 'DRAFT'" type="primary" @click="save">保存草稿</el-button>
    </div>
  </el-drawer>
</template>
<script>
import DefinitionForm from './DefinitionForm'
export default {
  name: 'VersionDrawer', components: { DefinitionForm }, props: { visible: Boolean, versions: { type: Array, default: () => [] } },
  data() { return { editing: null } },
  computed: { open: { get() { return this.visible }, set(value) { this.$emit('update:visible', value) } } },
  methods: {
    inspect(row) { this.editing = { ...row } },
    edit(row) { this.editing = { ...row } },
    save() { this.$refs.definition.validate().then(payload => this.$emit('save', { row: this.editing, payload })) }
  }
}
</script>
<style scoped>.drawer-body{padding:18px}</style>
