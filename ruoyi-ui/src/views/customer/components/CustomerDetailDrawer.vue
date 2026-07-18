<template>
  <el-drawer :visible.sync="innerVisible" size="72%" :custom-class="drawerClass" append-to-body @close="$emit('update:visible', false)">
    <div slot="title" class="drawer-title" :class="sizeClass">
      <span>CUSTOMER PROFILE</span>
      <strong>客户详情</strong>
    </div>
    <div class="detail-page" :class="sizeClass">
      <section class="summary-card">
        <div class="avatar">{{ avatar(customer.customerName) }}</div>
        <div class="summary-main">
          <h3>{{ customer.customerName || '-' }}</h3>
          <p>{{ customer.companyName || '个人客户' }}</p>
          <div class="summary-tags summary-status">
            <dict-tag :options="dict.type.law_customer_type" :value="customer.customerType" />
            <dict-tag :options="dict.type.law_customer_level" :value="customer.customerLevel" />
            <dict-tag :options="dict.type.law_customer_status" :value="customerStatusOf(customer)" />
          </div>
          <div v-if="customerTags.length" class="summary-tags summary-labels">
            <span v-for="item in customerTags" :key="item.name" class="tag-pill" :style="{ borderColor: item.color, color: item.color }">{{ item.name }}</span>
          </div>
        </div>
        <div class="summary-actions managed-actions">
          <el-button v-hasPermi="['customer:edit']" :size="controlSize" type="primary" icon="el-icon-edit" :disabled="!canOperate" @click="$emit('edit', customer)">编辑</el-button>
          <el-button v-hasPermi="['customer:contact:add']" :size="controlSize" icon="el-icon-user" :disabled="!canOperate" @click="$emit('contact', customer)">新增联系人</el-button>
          <el-button v-hasPermi="['customer:followup:add']" :size="controlSize" icon="el-icon-chat-line-round" :disabled="!canOperate" @click="$emit('follow', customer)">新增跟进</el-button>
          <el-button v-hasPermi="['customer:tag:assign']" :size="controlSize" icon="el-icon-price-tag" :disabled="!canOperate" @click="$emit('tags', customer)">维护标签</el-button>
          <el-button v-if="canCreateContract" v-hasPermi="['contract:add']" :size="controlSize" icon="el-icon-document-add" :disabled="!canOperate" @click="$emit('new-contract', customer)">新建合同</el-button>
          <el-button v-hasPermi="['matter:list', 'matter:mine:list']" :size="controlSize" icon="el-icon-folder-opened" @click="$emit('matter', customer)">查看案件</el-button>
        </div>
      </section>

      <business-todo-summary v-if="Number(customer.customerId) > 0" business-type="CUSTOMER" :business-id="customer.customerId" :business-no="customer.customerNo" />
      <div class="detail-grid">
        <section class="info-card span-2">
          <header><h4>基础信息</h4></header>
          <dl class="info-list">
            <div><dt>客户编号</dt><dd>{{ customer.customerNo || '-' }}</dd></div>
            <div><dt>手机号</dt><dd>{{ maskMobile(customer.mobile) }}</dd></div>
            <div><dt>微信</dt><dd>{{ customer.wechat || '-' }}</dd></div>
            <div><dt>行业</dt><dd>{{ dictLabel('law_customer_industry', customer.industry) }}</dd></div>
            <div><dt>信用代码</dt><dd>{{ customer.creditCode || '-' }}</dd></div>
            <div><dt>负责人</dt><dd>{{ customer.ownerName || '-' }}</dd></div>
            <div><dt>所属部门</dt><dd>{{ customer.deptName || '-' }}</dd></div>
            <div><dt>关联合同</dt><dd>{{ customerContractCountOf(customer) }}</dd></div>
            <div><dt>最近跟进</dt><dd>{{ customer.lastFollowTime || '-' }}</dd></div>
            <div class="wide"><dt>主要需求</dt><dd>{{ customer.mainDemand || '-' }}</dd></div>
            <div class="wide"><dt>备注</dt><dd>{{ customer.remark || '-' }}</dd></div>
          </dl>
        </section>

        <section class="info-card">
          <header><h4>联系人</h4></header>
          <div v-if="contacts.length" class="mini-list">
            <article v-for="item in contacts" :key="item.contact_id">
              <b>{{ item.contact_name }}</b>
              <span>{{ maskMobile(item.mobile) }} · {{ dictLabel('law_contact_relation', item.relation_type) }}</span>
            </article>
          </div>
          <el-empty v-else description="暂无联系人" :image-size="80" />
        </section>

        <section class="info-card">
          <header><h4>关联合同</h4></header>
          <div v-if="contracts.length" class="mini-list">
            <article v-for="item in contracts" :key="item.contractId">
              <b>{{ item.contractName }}</b>
              <span>{{ item.contractNo || '-' }} · {{ formatMoney(item.signAmount) }} · {{ dictLabel('law_contract_status', item.contractStatus || item.contract_status) }}</span>
            </article>
          </div>
          <el-empty v-else description="暂无关联合同" :image-size="80" />
        </section>

        <section class="info-card span-2">
          <header><h4>跟进时间线</h4></header>
          <div v-if="followups.length" class="timeline">
            <article v-for="item in followups" :key="item.followup_id">
              <i class="el-icon-time" />
              <div>
                <h5>{{ dictLabel('law_customer_follow_type', item.follow_type) }} <span>{{ item.followUserName || item.create_by || '-' }}</span></h5>
                <p>{{ item.content || '-' }}</p>
                <small>{{ item.create_time || '-' }}<em v-if="item.next_follow_time">下次：{{ item.next_follow_time }}</em></small>
              </div>
            </article>
          </div>
          <el-empty v-else description="暂无跟进记录" :image-size="80" />
        </section>

        <section class="info-card span-2">
          <header><h4>合并记录</h4></header>
          <div v-if="mergeLogs.length" class="timeline">
            <article v-for="item in mergeLogs" :key="item.logId || item.log_id">
              <i class="el-icon-connection" />
              <div>
                <h5>{{ item.mergedCustomerName || '-' }} <span>合并至 {{ item.mainCustomerName || '-' }}</span></h5>
                <p>{{ item.mergeContent || '-' }}</p>
                <small>{{ item.createTime || item.create_time || '-' }}<em>{{ item.createBy || item.create_by || '-' }}</em></small>
              </div>
            </article>
          </div>
          <el-empty v-else description="暂无合并记录" :image-size="80" />
        </section>
      </div>
    </div>
  </el-drawer>
</template>

<script>
import businessUi from '@/views/business/mixins/businessUi'
import customerLifecycle from '@/views/business/mixins/customerLifecycle'
import BusinessTodoSummary from '@/views/todo/components/BusinessTodoSummary'

export default {
  name: 'CustomerDetailDrawer',
  components: { BusinessTodoSummary },
  mixins: [businessUi, customerLifecycle],
  dicts: ['law_customer_type', 'law_customer_level', 'law_customer_industry', 'law_contact_relation', 'law_customer_follow_type', 'law_customer_status', 'law_contract_status'],
  props: {
    visible: { type: Boolean, default: false },
    customer: { type: Object, default: () => ({}) },
    contacts: { type: Array, default: () => [] },
    followups: { type: Array, default: () => [] },
    contracts: { type: Array, default: () => [] },
    mergeLogs: { type: Array, default: () => [] },
    canCreateContract: { type: Boolean, default: false },
    canOperate: { type: Boolean, default: true },
    sizeClass: { type: String, default: '' }
  },
  computed: {
    innerVisible: {
      get() { return this.visible },
      set(value) { this.$emit('update:visible', value) }
    },
    customerTags() {
      const names = String(this.customer.tagNames || '').split(',').filter(Boolean)
      const colors = String(this.customer.tagColors || '').split(',')
      return names.map((name, index) => ({ name, color: colors[index] || '#2563eb' }))
    },
    drawerClass() {
      return ['customer-detail-drawer', this.sizeClass].filter(Boolean).join(' ')
    }
  }
}
</script>

<style scoped lang="scss">
@import "../../business/detail-drawer.scss";

.detail-page {
  --biz-detail-grid-columns: 1.35fr 1fr;
}
</style>
