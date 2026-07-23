<template>
  <el-dialog
    title="检测到配置冲突"
    :visible="visible"
    width="680px"
    append-to-body
    :close-on-click-modal="false"
    @close="$emit('update:visible', false)"
  >
    <el-alert
      title="其他用户已更新这个草稿。你的本地配置仍然保留，请选择如何继续。"
      type="warning"
      :closable="false"
      show-icon
    />
    <div class="journey-conflict__summaries">
      <section>
        <span>服务器版本</span>
        <h4>{{ summary(server).name }}</h4>
        <dl>
          <div><dt>版本</dt><dd>{{ summary(server).version }}</dd></div>
          <div><dt>配置摘要</dt><dd>{{ summary(server).progress }}</dd></div>
        </dl>
      </section>
      <section>
        <span>你的本地版本</span>
        <h4>{{ summary(local).name }}</h4>
        <dl>
          <div><dt>版本</dt><dd>{{ summary(local).version }}</dd></div>
          <div><dt>本地修改</dt><dd>{{ summary(local).progress }}</dd></div>
        </dl>
      </section>
    </div>
    <p class="journey-conflict__hint">
      “刷新并合并”会以服务器最新版本为基础重新应用你的本地修改；“另存副本”会保留当前内容并创建一个新模板。
    </p>
    <template #footer>
      <el-button @click="$emit('update:visible', false)">暂不处理</el-button>
      <el-button v-hasPermi="['todo:template:copy']" :loading="copying" @click="$emit('save-copy')">另存副本</el-button>
      <el-button type="primary" :loading="merging" @click="$emit('refresh-merge')">刷新并合并</el-button>
    </template>
  </el-dialog>
</template>

<script>
export default {
  name: 'JourneyConflictDialog',
  props: {
    visible: Boolean,
    conflict: { type: Object, default: () => ({}) },
    merging: Boolean,
    copying: Boolean
  },
  computed: {
    local() {
      return this.conflict.local || {}
    },
    server() {
      return this.conflict.server || {}
    }
  },
  methods: {
    summary(value) {
      const template = (value && value.template) || {}
      const steps = Array.isArray(value && value.steps) ? value.steps : []
      const completed = steps.filter(step => step.state === 'COMPLETED').length
      const changed = steps.filter(step => step.state === 'IN_PROGRESS').length
      return {
        name: template.templateName || template.templateCode || '待办模板',
        version: `V${template.versionNo || '-'} · 锁版本 ${template.lockVersion == null ? '-' : template.lockVersion}`,
        progress: changed ? `${changed} 个本地步骤已修改` : `${completed}/${steps.length || 7} 个步骤已完成`
      }
    }
  }
}
</script>

<style scoped lang="scss">
.journey-conflict__summaries {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-top: 18px;

  section {
    padding: 16px;
    background: #F4F7FA;
    border: 1px solid #D9E1EA;
    border-radius: 8px;
  }

  span {
    font-size: 12px;
    line-height: 18px;
    color: #7B8898;
  }

  h4 {
    margin: 4px 0 12px;
    font-size: 15px;
    line-height: 24px;
    color: #0B2A55;
  }

  dl {
    margin: 0;
  }

  dl div {
    display: flex;
    justify-content: space-between;
    font-size: 12px;
    line-height: 22px;
  }

  dt {
    color: #7B8898;
  }

  dd {
    margin: 0;
    color: #34465B;
  }
}

.journey-conflict__hint {
  margin: 16px 0 0;
  font-size: 13px;
  line-height: 21px;
  color: #586779;
}

@media (max-width: 640px) {
  .journey-conflict__summaries {
    grid-template-columns: 1fr;
  }
}
</style>
