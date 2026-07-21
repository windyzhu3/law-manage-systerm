<template>
  <section class="semantic-diff">
    <el-empty v-if="!value" description="选择版本后生成语义差异" :image-size="72" />
    <el-alert v-else-if="!changes.length" title="无语义差异" type="success" :closable="false" show-icon />
    <template v-else>
      <el-alert :title="`整体风险：${value.overallRisk || 'NONE'}`" :type="riskType(value.overallRisk)" :closable="false" show-icon />
      <el-table :data="changes" size="mini" class="diff-table">
        <el-table-column prop="section" label="配置分区" width="110" /><el-table-column prop="path" label="路径" min-width="180" show-overflow-tooltip /><el-table-column prop="type" label="变化" width="90" />
        <el-table-column label="变更前" min-width="160"><template slot-scope="{row}"><code>{{ display(row.before) }}</code></template></el-table-column>
        <el-table-column label="变更后" min-width="160"><template slot-scope="{row}"><code>{{ display(row.after) }}</code></template></el-table-column>
        <el-table-column prop="risk" label="风险" width="90" /><el-table-column prop="reason" label="说明" min-width="180" />
      </el-table>
    </template>
  </section>
</template>
<script>export default { name:'VersionSemanticDiff', props:{ value:{type:Object,default:null} }, computed:{ changes(){return (this.value&&this.value.changes)||[]} }, methods:{ display(value){return typeof value==='string'?value:JSON.stringify(value)}, riskType(risk){return ['HIGH','BLOCKING'].includes(risk)?'error':risk==='MEDIUM'?'warning':'info'} } }</script>
<style scoped lang="scss">@import '../styles/config-center.scss';.diff-table{margin-top:12px}.diff-table code{white-space:pre-wrap;word-break:break-word}</style>
