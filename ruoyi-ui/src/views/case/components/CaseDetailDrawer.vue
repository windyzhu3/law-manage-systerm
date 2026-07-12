<template>
  <el-drawer :visible.sync="innerVisible" size="74%" :custom-class="drawerClass" append-to-body @close="$emit('update:visible', false)">
    <div slot="title" class="drawer-title" :class="sizeClass">
      <span>CASE PROFILE</span>
      <strong>案件详情</strong>
    </div>
    <div class="detail-page" :class="sizeClass">
      <section class="summary-card">
        <div class="doc-icon"><svg-icon icon-class="documentation" /></div>
        <div class="summary-main">
          <h3>{{ pick('case_name', 'caseName') || '-' }}</h3>
          <p>{{ pick('case_no', 'caseNo') || '-' }} · {{ pick('customer_name', 'customerName') || '-' }}</p>
          <div class="summary-tags">
            <dict-tag :options="dict.type.law_case_status" :value="pick('case_status', 'caseStatus')" />
            <dict-tag :options="dict.type.law_case_type" :value="pick('case_type', 'caseType')" />
            <dict-tag :options="dict.type.law_case_urgency" :value="pick('urgency')" />
          </div>
        </div>
        <div class="summary-actions">
          <el-button v-hasPermi="['case:pending:assign']" :size="controlSize" type="primary" icon="el-icon-user" :disabled="!canAssign" @click="$emit('assign', caseData)">分配案件</el-button>
          <el-button v-hasPermi="['case:status:list']" :size="controlSize" icon="el-icon-time" @click="$emit('flow', caseData)">查看流转</el-button>
        </div>
      </section>

      <business-todo-summary v-if="pick('case_id','caseId')" business-type="CASE" :business-id="pick('case_id','caseId')" :business-no="pick('case_no','caseNo')" />

      <div class="detail-grid">
        <section class="info-card span-2">
          <header><h4>基础信息</h4></header>
          <dl class="info-list">
            <div><dt>案件编号</dt><dd>{{ pick('case_no', 'caseNo') || '-' }}</dd></div>
            <div><dt>案件名称</dt><dd>{{ pick('case_name', 'caseName') || '-' }}</dd></div>
            <div><dt>客户名称</dt><dd>{{ pick('customer_name', 'customerName') || '-' }}</dd></div>
            <div><dt>来源合同</dt><dd>{{ pick('contract_no', 'contractNo') || '-' }}</dd></div>
            <div><dt>案件类型</dt><dd>{{ dictLabel('law_case_type', pick('case_type', 'caseType')) }}</dd></div>
            <div><dt>紧急程度</dt><dd>{{ dictLabel('law_case_urgency', pick('urgency')) }}</dd></div>
            <div><dt>当前状态</dt><dd>{{ dictLabel('law_case_status', pick('case_status', 'caseStatus')) }}</dd></div>
            <div><dt>当前节点</dt><dd>{{ pick('current_node', 'currentNode') || '-' }}</dd></div>
          </dl>
        </section>

        <section class="info-card">
          <header><h4>办案安排</h4></header>
          <dl class="info-list single">
            <div><dt>主办律师</dt><dd>{{ pick('main_lawyer_name', 'mainLawyerName') || '-' }}</dd></div>
            <div><dt>协办律师</dt><dd>{{ pick('assistant_lawyer_names', 'assistantLawyerNames') || '-' }}</dd></div>
            <div><dt>优先级</dt><dd>{{ dictLabel('law_case_priority', pick('priority')) }}</dd></div>
            <div><dt>计划开案日期</dt><dd>{{ pick('plan_start_date', 'planStartDate') || '-' }}</dd></div>
            <div><dt>预计周期</dt><dd>{{ pick('estimated_cycle', 'estimatedCycle') || '-' }}</dd></div>
            <div><dt>预计工作量</dt><dd>{{ pick('estimated_workload', 'estimatedWorkload') || 0 }} 小时</dd></div>
          </dl>
        </section>

        <section class="info-card">
          <header><h4>负责人信息</h4></header>
          <dl class="info-list single">
            <div><dt>负责人</dt><dd>{{ pick('ownerName', 'owner_name') || '-' }}</dd></div>
            <div><dt>所属部门</dt><dd>{{ pick('deptName', 'dept_name') || '-' }}</dd></div>
            <div><dt>创建时间</dt><dd>{{ pick('create_time', 'createTime') || '-' }}</dd></div>
            <div><dt>更新时间</dt><dd>{{ pick('update_time', 'updateTime') || '-' }}</dd></div>
          </dl>
        </section>

        <section class="info-card span-2">
          <header><h4>备注说明</h4></header>
          <p class="detail-remark">{{ pick('remark') || '-' }}</p>
        </section>
      </div>
    </div>
  </el-drawer>
</template>

<script>
import businessUi from '@/views/business/mixins/businessUi'
import BusinessTodoSummary from '@/views/todo/components/BusinessTodoSummary'

export default {
  name: 'CaseDetailDrawer',
  components: { BusinessTodoSummary },
  mixins: [businessUi],
  dicts: ['law_case_type', 'law_case_urgency', 'law_case_status', 'law_case_priority'],
  props: {
    visible: { type: Boolean, default: false },
    caseData: { type: Object, default: () => ({}) },
    sizeClass: { type: String, default: '' }
  },
  computed: {
    innerVisible: {
      get() { return this.visible },
      set(value) { this.$emit('update:visible', value) }
    },
    drawerClass() {
      return ['contract-detail-drawer', this.sizeClass].filter(Boolean).join(' ')
    },
    canAssign() {
      return this.sameValue(this.pick('case_status', 'caseStatus'), 'pending')
    }
  },
  methods: {
    pick(...keys) {
      for (const key of keys) {
        if (this.caseData && this.caseData[key] !== undefined && this.caseData[key] !== null && this.caseData[key] !== '') {
          return this.caseData[key]
        }
      }
      return ''
    }
  }
}
</script>

<style scoped lang="scss">
@import "../../business/detail-drawer.scss";

.info-list.single {
  grid-template-columns: 1fr;
}

.detail-remark {
  margin: 0;
  color: #64748b;
  font-size: var(--biz-font-small);
  line-height: 1.8;
}
</style>
