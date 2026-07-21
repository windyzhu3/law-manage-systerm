<template>
  <section class="definition-section" data-testid="routing-graph-editor">
    <header>
      <div><h4>路由图</h4><small>由服务端预检验证可达性、分支汇合与循环边界</small></div>
      <div>
        <el-button data-testid="routing-add-node" size="mini" :disabled="readonly" @click="addNode">新增节点</el-button>
        <el-button size="mini" :disabled="readonly || !graph.nodes.length" @click="addEdge">新增连线</el-button>
      </div>
    </header>

    <el-form-item label="起始节点">
      <el-select v-model="graph.start" :disabled="readonly" placeholder="请选择 TASK 起点" @change="commit">
        <el-option v-for="node in graph.nodes" :key="node.key" :label="node.key" :value="node.key" />
      </el-select>
    </el-form-item>

    <el-table :data="graph.nodes" size="mini" border row-key="key">
      <el-table-column label="节点 Key" min-width="135">
        <template slot-scope="{ row }"><el-input v-model.trim="row.key" :disabled="readonly" @change="commit" /></template>
      </el-table-column>
      <el-table-column label="类型" width="130">
        <template slot-scope="{ row }">
          <el-select v-model="row.type" :disabled="readonly" @change="nodeTypeChanged(row)">
            <el-option v-for="type in nodeTypes" :key="type" :label="type" :value="type" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="类型配置" min-width="270">
        <template slot-scope="{ row }">
          <el-select v-if="row.type === 'TASK'" v-model="row.templateVersionId" :disabled="readonly" placeholder="模板版本" @change="commit">
            <el-option v-for="version in versionOptions(row)" :key="version.id" :label="version.label" :value="version.id" />
          </el-select>
          <el-select v-else-if="row.type === 'JOIN'" v-model="row.joinMode" :disabled="readonly" placeholder="汇合模式" @change="commit"><el-option label="ANY" value="ANY" /><el-option label="ALL" value="ALL" /></el-select>
          <el-input v-if="row.type === 'JOIN'" :value="joinBranches(row)" :disabled="readonly" placeholder="分支 Key，逗号分隔" @change="setBranches(row, $event)" />
          <el-input-number v-else-if="row.type === 'LOOP'" v-model="row.maxOccurrences" :disabled="readonly" :min="1" controls-position="right" @change="commit" />
          <el-input v-if="row.type === 'LOOP'" :value="json(row.endCondition)" :disabled="readonly" placeholder="结束条件 JSON（可选）" @change="setJson(row, 'endCondition', $event)" />
          <span v-if="row.type === 'DECISION'">一条默认边，其余边填写规范条件</span>
          <span v-if="row.type === 'FORK'">每条出边填写唯一 branchKey</span>
          <span v-if="row.type === 'END'">终点不能有出边</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="65">
        <template slot-scope="{ $index }"><el-button type="text" :disabled="readonly" @click="removeNode($index)">删除</el-button></template>
      </el-table-column>
    </el-table>

    <h5>连线</h5>
    <el-table :data="graph.edges" size="mini" border row-key="key">
      <el-table-column label="边 Key" min-width="120"><template slot-scope="{ row }"><el-input v-model.trim="row.key" :disabled="readonly" @change="commit" /></template></el-table-column>
      <el-table-column label="从" width="120"><template slot-scope="{ row }"><el-select v-model="row.from" :disabled="readonly" @change="commit"><el-option v-for="node in graph.nodes" :key="node.key" :label="node.key" :value="node.key" /></el-select></template></el-table-column>
      <el-table-column label="到" width="120"><template slot-scope="{ row }"><el-select v-model="row.to" :disabled="readonly" @change="commit"><el-option v-for="node in graph.nodes" :key="node.key" :label="node.key" :value="node.key" /></el-select></template></el-table-column>
      <el-table-column label="分支配置" min-width="240">
        <template slot-scope="{ row }">
          <el-checkbox v-model="row.default" :disabled="readonly" @change="commit">默认</el-checkbox>
          <el-input v-if="nodeType(row.from) !== 'LOOP'" v-model.trim="row.branchKey" :disabled="readonly" placeholder="branchKey" @change="commit" />
          <el-select v-else v-model="row.branchKey" :disabled="readonly" clearable placeholder="LOOP 分支" @change="commit"><el-option label="BODY" value="BODY" /><el-option label="EXIT" value="EXIT" /></el-select>
          <trigger-condition-builder v-if="nodeType(row.from) === 'DECISION'" :value="conditionJson(row)" :payload-schema-json="payloadSchemaJson" :readonly="readonly" @input="setEdgeCondition(row,$event)" />
          <span v-else class="condition-note">仅 DECISION 连线需要条件；END 节点表示流程 termination。</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="65"><template slot-scope="{ $index }"><el-button type="text" :disabled="readonly" @click="removeEdge($index)">删除</el-button></template></el-table-column>
    </el-table>
    <el-alert v-for="message in localErrors" :key="message" :title="message" type="warning" :closable="false" />
    <el-alert v-if="jsonError" :title="jsonError" type="error" :closable="false" />
  </section>
</template>

<script>
import TriggerConditionBuilder from '../trigger/TriggerConditionBuilder'
const NODE_TYPES = Object.freeze(['TASK', 'DECISION', 'FORK', 'JOIN', 'LOOP', 'END'])

function clone(value) { return JSON.parse(JSON.stringify(value == null ? {} : value)) }
function normalize(value) {
  const source = value && typeof value === 'object' ? clone(value) : {}
  return { ...source, start: source.start || '', nodes: Array.isArray(source.nodes) ? source.nodes : [], edges: Array.isArray(source.edges) ? source.edges : [] }
}

export default {
  name: 'RoutingGraphEditor',
  components: { TriggerConditionBuilder },
  props: { value: { type: Object, default: () => ({}) }, readonly: Boolean, currentVersionId: [Number, String], versions: { type: Array, default: () => [] }, routingTargets: { type: Array, default: () => [] }, payloadSchemaJson: { type: String, default: '{}' } },
  data() { return { nodeTypes: NODE_TYPES, graph: normalize(this.value), jsonError: '' } },
  computed: {
    localErrors() {
      const errors = []
      const nodeKeys = this.graph.nodes.map(node => node.key).filter(Boolean)
      const edgeKeys = this.graph.edges.map(edge => edge.key).filter(Boolean)
      if (!this.graph.start || !nodeKeys.includes(this.graph.start)) errors.push('起始节点必须存在')
      if (new Set(nodeKeys).size !== nodeKeys.length) errors.push('节点 Key 必须唯一')
      if (new Set(edgeKeys).size !== edgeKeys.length) errors.push('边 Key 必须唯一')
      this.graph.nodes.forEach(node => {
        if (node.type === 'TASK' && (!(Number(node.templateVersionId) > 0) || !this.versionOptions(node).some(item => item.id === Number(node.templateVersionId)))) errors.push(`${node.key || 'TASK'} 必须选择允许的模板版本（起点为当前版本，下游为已发布版本）`)
        if (node.type === 'JOIN' && (!['ANY', 'ALL'].includes(node.joinMode) || !Array.isArray(node.branches) || !node.branches.length)) errors.push(`${node.key || 'JOIN'} 必须设置汇合模式和分支`)
        if (node.type === 'LOOP' && !(Number(node.maxOccurrences) > 0) && !node.endCondition) errors.push(`${node.key || 'LOOP'} 必须设置最大次数或结束条件`)
        if (node.type === 'DECISION') {
          const outgoing = this.graph.edges.filter(edge => edge.from === node.key)
          const defaults = outgoing.filter(edge => edge.default)
          if (defaults.length !== 1) errors.push(`${node.key || 'DECISION'} 必须恰有一条默认边`)
          if (outgoing.some(edge => !edge.default && !edge.condition)) errors.push(`${node.key || 'DECISION'} 的非默认边必须填写条件`)
        }
        if (node.type === 'FORK') {
          const outgoing = this.graph.edges.filter(edge => edge.from === node.key)
          const branches = outgoing.map(edge => edge.branchKey).filter(Boolean)
          if (!branches.length || branches.length !== outgoing.length || branches.length !== new Set(branches).size) errors.push(`${node.key || 'FORK'} 的出边必须填写唯一 branchKey`)
        }
        if (node.type === 'LOOP') {
          const branches = this.graph.edges.filter(edge => edge.from === node.key).map(edge => edge.branchKey)
          if (branches.length !== 2 || !branches.includes('BODY') || !branches.includes('EXIT')) errors.push(`${node.key || 'LOOP'} 必须恰有 BODY 和 EXIT 两条出边`)
        }
        if (node.type === 'END' && this.graph.edges.some(edge => edge.from === node.key)) errors.push(`${node.key || 'END'} 不能有出边`)
      })
      return [...new Set(errors)]
    }
  },
  watch: { value: { deep: true, handler(value) { this.graph = normalize(value) } } },
  methods: {
    json(value) { return value && Object.keys(value).length ? JSON.stringify(value) : '' },
    nodeType(key) { const node = this.graph.nodes.find(item => item.key === key); return node && node.type },
    versionOptions(node) {
      const current = Number(this.currentVersionId)
      if (node.key === this.graph.start) return current ? [{ id: current, label: '当前定义版本（起点）' }] : []
      const options = this.routingTargets.map(row => ({ id: Number(row.versionId || row.version_id), label: `${row.templateName || row.template_name} · v${row.versionNo || row.version_no} · 已发布` }))
      return options
    },
    conditionJson(row) { return row.condition && Object.keys(row.condition).length ? JSON.stringify(row.condition) : '' },
    setEdgeCondition(row, value) { try { this.$set(row, 'condition', value ? JSON.parse(value) : {}); this.jsonError = ''; this.commit() } catch (_) { this.jsonError = '连线条件不是有效 JSON' } },
    joinBranches(node) { return Array.isArray(node.branches) ? node.branches.join(',') : '' },
    setBranches(node, value) { this.$set(node, 'branches', String(value || '').split(',').map(item => item.trim()).filter(Boolean)); this.commit() },
    setJson(target, field, value) {
      this.jsonError = ''
      if (!String(value || '').trim()) { this.$delete(target, field); this.commit(); return }
      try { this.$set(target, field, JSON.parse(value)); this.commit() } catch (_) { this.jsonError = `${field} 不是有效 JSON` }
    },
    nodeTypeChanged(node) {
      if (node.type === 'TASK' && !(Number(node.templateVersionId) > 0)) { const option = this.versionOptions(node)[0]; this.$set(node, 'templateVersionId', option ? option.id : null) }
      if (node.type === 'JOIN') { this.$set(node, 'joinMode', node.joinMode || 'ALL'); this.$set(node, 'branches', node.branches || []) }
      if (node.type === 'LOOP' && !(Number(node.maxOccurrences) > 0)) this.$set(node, 'maxOccurrences', 1)
      this.commit()
    },
    addNode() {
      const index = this.graph.nodes.length + 1
      const published = this.routingTargets[0]
      const node = { key: `node_${index}`, type: 'TASK', templateVersionId: published ? Number(published.version_id || published.versionId) : null }
      this.graph.nodes.push(node)
      if (!this.graph.start) this.graph.start = node.key
      this.commit()
    },
    addEdge() {
      const first = this.graph.nodes[0] && this.graph.nodes[0].key
      this.graph.edges.push({ key: `edge_${this.graph.edges.length + 1}`, from: first || '', to: first || '' })
      this.commit()
    },
    removeNode(index) {
      const removed = this.graph.nodes.splice(index, 1)[0]
      this.graph.edges = this.graph.edges.filter(edge => edge.from !== removed.key && edge.to !== removed.key)
      if (this.graph.start === removed.key) this.graph.start = this.graph.nodes[0] ? this.graph.nodes[0].key : ''
      this.commit()
    },
    removeEdge(index) { this.graph.edges.splice(index, 1); this.commit() },
    commit() { this.$emit('input', clone(this.graph)); this.$emit('change') },
    validate() { return this.jsonError || this.localErrors.length ? Promise.reject(new Error(this.jsonError || this.localErrors[0])) : Promise.resolve() }
  }
}
</script>

<style scoped>
.definition-section{margin:18px 0;padding:14px;border:1px solid #dfe6ef;border-radius:8px}.definition-section header{display:flex;align-items:center;justify-content:space-between;margin-bottom:12px}.definition-section h4,.definition-section h5{margin:0 0 8px}.definition-section small{color:#64748b}.definition-section .el-input,.definition-section .el-select{margin:2px 0}.definition-section .el-alert{margin-top:8px}
</style>
