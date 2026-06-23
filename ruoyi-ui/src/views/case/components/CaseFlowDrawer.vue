<template>
  <el-drawer :visible.sync="innerVisible" size="560px" :custom-class="drawerClass" append-to-body @close="$emit('update:visible', false)">
    <div slot="title" class="drawer-title" :class="sizeClass">
      <span>CASE TIMELINE</span>
      <strong>案件流转</strong>
    </div>
    <div class="detail-page flow-page" :class="sizeClass">
      <section class="summary-card compact">
        <div class="doc-icon"><svg-icon icon-class="time" /></div>
        <div class="summary-main">
          <h3>{{ pick('case_name', 'caseName') || '-' }}</h3>
          <p>{{ pick('case_no', 'caseNo') || '-' }} · {{ pick('customer_name', 'customerName') || '-' }}</p>
        </div>
      </section>

      <section class="info-card">
        <header>
          <h4>流转时间轴</h4>
          <span>最近 20 条</span>
        </header>
        <div v-loading="loading" class="timeline">
          <article v-for="item in statuses" :key="item.log_id || item.create_time">
            <i class="el-icon-time" />
            <div>
              <h5>{{ dictLabel('law_case_status_action', item.action_type) }} <span>{{ item.create_by || '-' }}</span></h5>
              <p>{{ item.content || '-' }}</p>
              <small>{{ item.create_time || '-' }}</small>
            </div>
          </article>
          <el-empty v-if="!loading && !statuses.length" description="暂无流转记录" :image-size="90" />
        </div>
      </section>
    </div>
  </el-drawer>
</template>

<script>
import businessUi from '@/views/business/mixins/businessUi'

export default {
  name: 'CaseFlowDrawer',
  mixins: [businessUi],
  dicts: ['law_case_status_action'],
  props: {
    visible: { type: Boolean, default: false },
    caseData: { type: Object, default: () => ({}) },
    statuses: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false },
    sizeClass: { type: String, default: '' }
  },
  computed: {
    innerVisible: {
      get() { return this.visible },
      set(value) { this.$emit('update:visible', value) }
    },
    drawerClass() {
      return ['contract-detail-drawer', this.sizeClass].filter(Boolean).join(' ')
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

.flow-page {
  padding-bottom: 24px;
}

.summary-card.compact {
  align-items: center;
}

.info-card header span {
  color: #94a3b8;
  font-size: var(--biz-font-mini);
}
</style>
