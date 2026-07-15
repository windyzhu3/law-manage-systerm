<template>
  <div class="biz-page contract-page" :class="'biz-size-' + appSize">
    <biz-page-header eyebrow="CONTRACT CENTER" :title="pageTitle" :description="pageDescription">
      <el-button v-if="mode === 'list'" v-hasPermi="['contract:import']" :size="controlSize" plain icon="el-icon-upload2" @click="openImport">导入</el-button>
      <el-button v-if="mode === 'list'" v-hasPermi="['contract:export']" :size="controlSize" plain icon="el-icon-download" @click="handleExport">导出</el-button>
      <el-button v-if="mode === 'list'" v-hasPermi="['contract:add']" :size="controlSize" type="primary" icon="el-icon-plus" @click="openContract()">新建合同</el-button>
      <el-button v-if="mode === 'template'" v-hasPermi="['contract:template:add']" :size="controlSize" type="primary" icon="el-icon-plus" @click="openTemplate()">新增模板</el-button>
      <el-button v-if="mode === 'fee'" v-hasPermi="['contract:fee:add']" :size="controlSize" type="primary" icon="el-icon-plus" @click="openFee()">新增收费计划</el-button>
      <el-button v-if="mode === 'attachment'" v-hasPermi="['contract:attachment:add']" :size="controlSize" type="primary" icon="el-icon-plus" @click="openAttachment()">上传附件</el-button>
    </biz-page-header>

    <biz-hero
      v-if="heroModes.includes(mode)"
      :eyebrow="heroMeta.eyebrow"
      :title="heroMeta.title"
      :description="heroMeta.description"
    />

    <biz-metrics v-if="heroModes.includes(mode)" :metrics="modeMetrics" :config="modeMetricConfig" />

    <biz-table-card
      v-if="mode === 'list' || mode === 'approval'"
      :show-search.sync="showSearch"
      :total="total"
      :page.sync="query.pageNum"
      :limit.sync="query.pageSize"
      @query="loadPage"
      @pagination="loadPage"
    >
      <template #filters>
        <div class="biz-filter-main">
          <el-input v-model="query.keyword" :size="controlSize" prefix-icon="el-icon-search" placeholder="搜索合同编号、合同名称、客户名称" clearable @clear="search" @keyup.enter.native="search" />
          <el-select v-model="query.caseType" :size="controlSize" placeholder="案件类型：全部" clearable @change="search">
            <el-option v-for="item in dict.type.law_contract_case_type" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-if="mode === 'list'" v-model="query.auditStatus" :size="controlSize" placeholder="审核状态：全部" clearable @change="search">
            <el-option v-for="item in dict.type.law_contract_audit_status" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="query.contractStatus" :size="controlSize" placeholder="合同状态：全部" clearable @change="search">
            <el-option v-for="item in dict.type.law_contract_status" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </div>
        <div class="biz-filter-actions">
          <el-popover v-model="advancedOpen" placement="bottom-end" width="380" trigger="click" popper-class="business-advanced-popover">
            <div class="advanced-filter-panel">
              <div class="advanced-title">
                <strong>高级筛选</strong>
                <span>组合签订状态与负责人定位合同</span>
              </div>
              <el-form label-position="top">
                <el-form-item label="签订状态">
                  <el-select v-model="query.signStatus" :size="controlSize" placeholder="全部签订状态" clearable>
                    <el-option v-for="item in dict.type.law_contract_sign_status" :key="item.value" :label="item.label" :value="item.value" />
                  </el-select>
                </el-form-item>
                <el-form-item label="负责人">
                  <el-select v-model="query.ownerId" :size="controlSize" placeholder="全部负责人" clearable filterable>
                    <el-option v-for="item in ownerOptions" :key="item.userId" :label="item.nickName" :value="item.userId" />
                  </el-select>
                </el-form-item>
              </el-form>
              <div class="advanced-actions">
                <el-button :size="controlSize" @click="resetAdvanced">重置</el-button>
                <el-button :size="controlSize" type="primary" @click="applyAdvanced">应用筛选</el-button>
              </div>
            </div>
            <el-button slot="reference" :size="controlSize" plain icon="el-icon-s-operation">高级筛选</el-button>
          </el-popover>
          <el-button :size="controlSize" plain icon="el-icon-refresh" @click="reset">重置</el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="contracts" :size="controlSize">
          <el-table-column label="合同编号" prop="contractNo" min-width="150" align="center" />
          <el-table-column label="合同信息" min-width="180">
            <template slot-scope="{ row }">
              <a class="biz-link" @click="openDetail(row)">{{ row.contractName }}</a>
              <span class="sub-text">{{ row.customerName || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="案件类型" width="105" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_contract_case_type" :value="row.caseType" /></template></el-table-column>
          <el-table-column label="签约金额" width="112" align="center"><template slot-scope="{ row }">{{ formatMoney(row.signAmount) }}</template></el-table-column>
          <el-table-column label="审核状态" width="105" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_contract_audit_status" :value="auditStatusOf(row)" /></template></el-table-column>
          <el-table-column label="签订状态" width="105" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_contract_sign_status" :value="signStatusOf(row)" /></template></el-table-column>
          <el-table-column label="合同状态" width="105" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_contract_status" :value="contractStatusOf(row)" /></template></el-table-column>
          <el-table-column label="负责人" width="104" align="center"><template slot-scope="{ row }"><span class="owner-cell"><i>{{ avatar(row.ownerName) }}</i>{{ row.ownerName || '-' }}</span></template></el-table-column>
          <el-table-column label="操作" :width="mode === 'approval' ? 128 : 318" align="center" class-name="small-padding fixed-width biz-operation-column" fixed="right">
            <template slot-scope="{ row }">
              <span class="action-buttons">
                <el-button v-hasPermi="['contract:query']" :size="controlSize" type="text" icon="el-icon-view" @click="openDetail(row)">详情</el-button>
                <el-button v-if="mode === 'list'" v-hasPermi="['contract:edit']" :size="controlSize" type="text" icon="el-icon-edit" :disabled="!canEditContract(row)" @click="openContract(row)">编辑</el-button>
                <el-button v-if="mode === 'list'" v-hasPermi="['contract:submit']" :size="controlSize" type="text" icon="el-icon-s-check" :disabled="!canSubmitContract(row)" @click="submitOne(row)">提交审批</el-button>
                <el-button v-if="mode === 'approval'" v-hasPermi="['contract:approval:handle']" :size="controlSize" type="text" icon="el-icon-check" :disabled="!isContractAuditReviewing(row)" @click="openApproval(row)">审批</el-button>
                <el-button v-if="mode === 'list' && canSignContract(row)" v-hasPermi="['contract:sign']" :size="controlSize" type="text" icon="el-icon-finished" @click="signOne(row)">{{ signActionText(row) }}</el-button>
                <el-button v-if="mode === 'list' && canArchiveContract(row)" v-hasPermi="['contract:archive']" :size="controlSize" type="text" icon="el-icon-folder-checked" @click="archiveOne(row)">归档</el-button>
                <el-button v-if="mode === 'list' && canVoidContract(row)" v-hasPermi="['contract:void']" :size="controlSize" type="text" icon="el-icon-circle-close" @click="voidOne(row)">作废</el-button>
                <el-button v-if="mode === 'list' && canTerminateContract(row)" v-hasPermi="['contract:terminate']" :size="controlSize" type="text" icon="el-icon-remove-outline" @click="terminateOne(row)">终止</el-button>
                <el-button v-if="mode === 'list'" v-hasPermi="['contract:remove']" :size="controlSize" type="text" icon="el-icon-delete" class="danger-text" :disabled="!canRemoveContract(row)" @click="removeContract(row)">删除</el-button>
              </span>
            </template>
          </el-table-column>
      </el-table>
    </biz-table-card>

    <biz-table-card
      v-else-if="mode === 'template'"
      :show-search.sync="showSearch"
      :total="total"
      :page.sync="query.pageNum"
      :limit.sync="query.pageSize"
      @query="loadTemplates"
      @pagination="loadTemplates"
    >
      <template #filters>
        <div class="biz-filter-main">
          <el-input v-model="query.templateName" :size="controlSize" prefix-icon="el-icon-search" placeholder="搜索模板名称" clearable @clear="search" @keyup.enter.native="search" />
          <el-select v-model="query.caseType" :size="controlSize" placeholder="案件类型：全部" clearable @change="search">
            <el-option v-for="item in dict.type.law_contract_case_type" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="query.status" :size="controlSize" placeholder="状态：全部" clearable @change="search">
            <el-option v-for="item in dict.type.sys_normal_disable" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </div>
        <div class="biz-filter-actions">
          <el-button :size="controlSize" plain icon="el-icon-refresh" @click="reset">重置</el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="templates" :size="controlSize">
          <el-table-column label="模板名称" prop="templateName" min-width="180" />
          <el-table-column label="案件类型" width="120" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_contract_case_type" :value="row.caseType" /></template></el-table-column>
          <el-table-column label="文件名" prop="fileName" min-width="170" show-overflow-tooltip>
            <template slot-scope="{ row }"><a class="biz-link" @click="openTemplateFile(row)">{{ row.fileName || fileNameFromUrl(row.fileUrl) }}</a></template>
          </el-table-column>
          <el-table-column label="版本" prop="versionNo" width="90" align="center" />
          <el-table-column label="状态" width="90" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.sys_normal_disable" :value="row.status" /></template></el-table-column>
          <el-table-column label="操作" width="164" align="center" class-name="small-padding fixed-width"><template slot-scope="{ row }"><el-button :size="controlSize" type="text" icon="el-icon-view" @click="openTemplateFile(row)">打开</el-button><el-button v-hasPermi="['contract:template:edit']" :size="controlSize" type="text" icon="el-icon-edit" @click="openTemplate(row)">编辑</el-button><el-button v-hasPermi="['contract:template:remove']" :size="controlSize" type="text" icon="el-icon-delete" class="danger-text" :disabled="isEnabled(row.status)" title="启用中的模板请先停用再删除" @click="removeTemplate(row)">删除</el-button></template></el-table-column>
      </el-table>
    </biz-table-card>

    <biz-table-card
      v-else-if="mode === 'fee'"
      :show-search.sync="showSearch"
      :total="total"
      :page.sync="query.pageNum"
      :limit.sync="query.pageSize"
      @query="loadFees"
      @pagination="loadFees"
    >
      <template #filters>
        <div class="biz-filter-main">
          <el-input v-model="query.keyword" :size="controlSize" prefix-icon="el-icon-search" placeholder="搜索合同编号、合同名称、客户名称" clearable @clear="search" @keyup.enter.native="search" />
          <el-select v-model="query.confirmStatus" :size="controlSize" placeholder="确认状态：全部" clearable @change="search">
            <el-option v-for="item in dict.type.law_contract_receive_status" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="query.invoiceStatus" :size="controlSize" placeholder="开票状态：全部" clearable @change="search">
            <el-option v-for="item in dict.type.law_contract_invoice_status" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </div>
        <div class="biz-filter-actions">
          <el-button :size="controlSize" plain icon="el-icon-refresh" @click="reset">重置</el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="fees" :size="controlSize">
          <el-table-column label="合同编号" prop="contractNo" min-width="150" />
          <el-table-column label="客户名称" prop="customerName" min-width="150" />
          <el-table-column label="期数" prop="period_no" width="80" align="center" />
          <el-table-column label="应收金额" width="110" align="center"><template slot-scope="{ row }">{{ formatMoney(row.receivable_amount) }}</template></el-table-column>
          <el-table-column label="实收金额" width="110" align="center"><template slot-scope="{ row }">{{ formatMoney(row.received_amount) }}</template></el-table-column>
          <el-table-column label="计划收款日" prop="plan_receive_date" width="120" align="center" />
          <el-table-column label="确认状态" width="110" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_contract_receive_status" :value="receiveStatusOf(row)" /></template></el-table-column>
          <el-table-column label="开票状态" width="110" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_contract_invoice_status" :value="invoiceStatusOf(row)" /></template></el-table-column>
          <el-table-column label="操作" width="230" align="center" class-name="small-padding fixed-width">
            <template slot-scope="{ row }">
              <el-button v-if="isFeePending(row)" v-hasPermi="['contract:fee:confirm']" :size="controlSize" type="text" icon="el-icon-check" :disabled="!canConfirmFee(row)" @click="confirmFeeOne(row)">确认</el-button>
              <el-button v-if="isFeePending(row)" v-hasPermi="['contract:fee:reject']" :size="controlSize" type="text" icon="el-icon-close" class="danger-text" :disabled="!canRejectFee(row)" @click="rejectFeeOne(row)">驳回</el-button>
              <el-button v-if="canInvoiceFee(row)" v-hasPermi="['contract:fee:invoice']" :size="controlSize" type="text" icon="el-icon-tickets" @click="invoiceFeeOne(row)">{{ invoiceActionText(row) }}</el-button>
              <el-button v-hasPermi="['contract:fee:edit']" :size="controlSize" type="text" icon="el-icon-edit" :disabled="!canEditFeePlan(row)" :title="feePlanEditTip(row)" @click="openFee(row)">编辑</el-button>
              <el-button v-hasPermi="['contract:fee:remove']" :size="controlSize" type="text" icon="el-icon-delete" class="danger-text" :disabled="!canEditFeePlan(row)" :title="feePlanEditTip(row)" @click="removeFee(row)">删除</el-button>
            </template>
          </el-table-column>
      </el-table>
    </biz-table-card>

    <biz-table-card
      v-else-if="mode === 'attachment'"
      :show-search.sync="showSearch"
      :total="total"
      :page.sync="query.pageNum"
      :limit.sync="query.pageSize"
      @query="loadAttachments"
      @pagination="loadAttachments"
    >
      <template #filters>
        <div class="biz-filter-main">
          <el-input v-model="query.keyword" :size="controlSize" prefix-icon="el-icon-search" placeholder="搜索合同编号、合同名称、文件名" clearable @clear="search" @keyup.enter.native="search" />
          <el-input v-model="query.fileType" :size="controlSize" placeholder="文件类型" clearable @clear="search" @keyup.enter.native="search" />
        </div>
        <div class="biz-filter-actions">
          <el-button :size="controlSize" plain icon="el-icon-refresh" @click="reset">重置</el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="attachments" :size="controlSize">
          <el-table-column label="合同编号" prop="contractNo" min-width="150" />
          <el-table-column label="合同名称" prop="contractName" min-width="170" />
          <el-table-column label="文件名" prop="file_name" min-width="180" show-overflow-tooltip>
            <template slot-scope="{ row }"><a class="biz-link" @click="openAttachmentFile(row)">{{ row.file_name || fileNameFromUrl(row.file_url) }}</a></template>
          </el-table-column>
          <el-table-column label="文件类型" prop="file_type" width="100" align="center" />
          <el-table-column label="上传时间" prop="create_time" width="150" align="center" />
          <el-table-column label="操作" width="130" align="center" class-name="small-padding fixed-width"><template slot-scope="{ row }"><el-button :size="controlSize" type="text" icon="el-icon-view" @click="openAttachmentFile(row)">打开</el-button><el-button v-hasPermi="['contract:attachment:remove']" :size="controlSize" type="text" icon="el-icon-delete" class="danger-text" :disabled="!canEditContractResource(row)" :title="contractResourceEditTip(row)" @click="removeAttachment(row)">删除</el-button></template></el-table-column>
      </el-table>
    </biz-table-card>

    <div v-else-if="mode === 'status'" class="table-card timeline-page">
      <div class="section-title"><h3>状态记录</h3><p>记录合同创建、提交审批、审批结果与后续状态变化</p></div>
      <div v-loading="loading" class="biz-timeline-list two-column status-list">
        <article v-for="item in statuses" :key="item.log_id || item.create_time">
          <i class="timeline-icon el-icon-time" />
          <div>
            <h4>{{ item.contractNo || '-' }} <span>{{ dictLabel('law_contract_status_action', item.action_type) }}</span></h4>
            <p>{{ item.content || '-' }}</p>
            <small>{{ item.create_by || '-' }} · {{ item.create_time || '-' }}</small>
          </div>
        </article>
        <el-empty v-if="!statuses.length" description="暂无状态记录" />
      </div>
      <pagination v-show="total > 0" :total="total" :page.sync="query.pageNum" :limit.sync="query.pageSize" @pagination="loadStatuses" />
    </div>

    <div v-else-if="mode === 'rule'" class="setting-grid">
      <article v-for="item in rules" :key="item.ruleId">
        <i />
        <div><h4>{{ item.ruleName }}</h4><p>{{ item.prefix }}{{ item.datePattern }} / {{ item.serialLength }} 位流水</p></div>
        <span :class="{ disabled: !sameValue(item.status, dictValue('sys_normal_disable', '0')) }">{{ dictLabel('sys_normal_disable', item.status) }}</span>
        <footer><el-button v-hasPermi="['contract:rule:edit']" :size="controlSize" type="text" @click="openRule(item)">编辑</el-button></footer>
      </article>
    </div>

    <excel-import-dialog ref="importRef" title="合同导入" action="/contract/importData" template-action="/contract/importTemplate" template-file-name="contract_template" update-support-label="按合同名称重复时更新已有合同" @success="loadPage" />
    <contract-detail-drawer
      :visible.sync="detailOpen"
      :contract="detailContract"
      :fees="detailFees"
      :attachments="detailAttachments"
      :approvals="detailApprovals"
      :statuses="detailStatuses"
      :size-class="'biz-size-' + appSize"
      @edit="openContract"
      @submit="handleDetailAction(submitOne, $event)"
      @approval="handleDetailAction(openApproval, $event)"
      @fee="openFee"
      @attachment="openAttachment"
      @sign="handleDetailAction(signOne, $event)"
      @archive="handleDetailAction(archiveOne, $event)"
      @void="handleDetailAction(voidOne, $event)"
      @terminate="handleDetailAction(terminateOne, $event)"
      @matter="viewMatter"
    />

    <contract-resource-dialogs
      ref="resourceDialogs"
      :contract-open.sync="contractOpen"
      :template-open.sync="templateOpen"
      :fee-open.sync="feeOpen"
      :attachment-open.sync="attachmentOpen"
      :rule-open.sync="ruleOpen"
      :contract-form="contractForm"
      :contract-rules="contractRules"
      :template-form="templateForm"
      :template-rules="templateRules"
      :fee-form="feeForm"
      :fee-rules="feeRules"
      :attachment-form="attachmentForm"
      :attachment-rules="attachmentRules"
      :rule-form="ruleForm"
      :rule-rules="ruleRules"
      :customer-options="customerOptions"
      :contract-options="contractOptions"
      :customer-select-loading="customerSelectLoading"
      :contract-select-loading="contractSelectLoading"
      :dict-options="dict.type"
      :control-size="controlSize"
      :dialog-class="dialogClass"
      :search-customer-options="searchCustomerOptions"
      :select-customer-for-contract="selectCustomerForContract"
      :can-operate-customer="canOperateCustomer"
      :sync-template-meta="syncTemplateMeta"
      :search-contract-options="searchContractOptions"
      :select-contract-for-fee="selectContractForFee"
      :select-contract-for-attachment="selectContractForAttachment"
      :can-edit-contract-resource="canEditContractResource"
      :sync-attachment-meta="syncAttachmentMeta"
      :save-contract="saveContract"
      :save-template="saveTemplate"
      :save-fee="saveFee"
      :save-attachment="saveAttachment"
      :save-rule="saveRule"
    />

    <contract-lifecycle-dialogs
      ref="lifecycleDialogs"
      :approval-open.sync="approvalOpen"
      :sign-open.sync="signOpen"
      :invoice-open.sync="invoiceOpen"
      :approval-form="approvalForm"
      :approval-rules="approvalRules"
      :approval-action-options="approvalActionOptions"
      :sign-form="signForm"
      :sign-action-options="signActionOptions"
      :sign-action-tip="signActionTip"
      :invoice-form="invoiceForm"
      :invoice-action-options="invoiceActionOptions"
      :invoice-action-tip="invoiceActionTip"
      :dict-options="dict.type"
      :control-size="controlSize"
      :dialog-class="dialogClass"
      :save-approval="saveApproval"
      :save-sign="saveSign"
      :save-invoice="saveInvoice"
    />
  </div>
</template>

<script>
import ExcelImportDialog from '@/components/ExcelImportDialog'
import BizHero from '@/views/business/components/BizHero'
import BizMetrics from '@/views/business/components/BizMetrics'
import BizPageHeader from '@/views/business/components/BizPageHeader'
import BizTableCard from '@/views/business/components/BizTableCard'
import businessUi from '@/views/business/mixins/businessUi'
import customerLifecycle from '@/views/business/mixins/customerLifecycle'
import contractLifecycle from '@/views/business/mixins/contractLifecycle'
import ContractDetailDrawer from './components/ContractDetailDrawer'
import ContractLifecycleDialogs from './components/ContractLifecycleDialogs'
import ContractResourceDialogs from './components/ContractResourceDialogs'
import contractPageActions from './contract-page-actions'

export default {
  name: 'Contract',
  mixins: [businessUi, customerLifecycle, contractLifecycle, contractPageActions],
  components: { ExcelImportDialog, BizHero, BizMetrics, BizPageHeader, BizTableCard, ContractDetailDrawer, ContractLifecycleDialogs, ContractResourceDialogs },
  dicts: ['law_customer_status', 'law_contract_case_type', 'law_contract_fee_type', 'law_contract_sign_method', 'law_contract_sign_status', 'law_contract_audit_status', 'law_contract_approval_action', 'law_contract_status', 'law_contract_status_action', 'law_contract_risk_level', 'law_contract_receive_status', 'law_contract_invoice_status', 'sys_normal_disable'],
  data() {
    const positiveAmount = (rule, value, callback) => {
      if (value === undefined || value === null || value === '' || Number(value) <= 0) {
        callback(new Error('金额必须大于0'))
      } else {
        callback()
      }
    }
    return {
      mode: 'list',
      availableModes: ['list', 'approval', 'template', 'fee', 'attachment', 'status', 'rule'],
      heroModes: ['list', 'approval', 'template', 'fee', 'attachment'],
      showSearch: true,
      loading: false,
      total: 0,
      contracts: [],
      templates: [],
      fees: [],
      attachments: [],
      statuses: [],
      rules: [],
      metrics: [],
      query: { pageNum: 1, pageSize: 10 },
      contractForm: {},
      templateForm: {},
      feeForm: {},
      attachmentForm: {},
      approvalForm: {},
      signForm: {},
      invoiceForm: {},
      ruleForm: {},
      customerOptions: [],
      contractOptions: [],
      ownerOptions: [],
      advancedOpen: false,
      customerSelectLoading: false,
      contractSelectLoading: false,
      businessPageMeta: {
        defaultTitle: '合同中心',
        titles: { list: '合同列表', approval: '合同审批', template: '合同模板', fee: '收费计划', attachment: '合同附件', status: '状态记录', rule: '编号规则' },
        descriptions: {
          list: '统一管理合同创建、审批、签订和履约状态',
          approval: '处理提交审核的合同，审批意见必须留痕',
          template: '维护合同模板文件、版本和启停状态',
          fee: '维护合同应收、实收、开票与确认状态',
          attachment: '复用系统上传能力管理合同附件',
          status: '追踪合同关键状态变化记录',
          rule: '维护合同自动编号规则'
        }
      },
      detailOpen: false,
      detailContract: {},
      detailFees: [],
      detailAttachments: [],
      detailApprovals: [],
      detailStatuses: [],
      contractOpen: false,
      templateOpen: false,
      feeOpen: false,
      attachmentOpen: false,
      approvalOpen: false,
      signOpen: false,
      invoiceOpen: false,
      ruleOpen: false,
      contractRules: {
        customerId: [{ required: true, message: '请选择客户', trigger: 'blur' }],
        customerName: [{ required: true, message: '请输入客户名称', trigger: 'blur' }],
        contractName: [{ required: true, message: '请输入合同名称', trigger: 'blur' }],
        caseType: [{ required: true, message: '请选择案件类型', trigger: 'change' }],
        signAmount: [{ required: true, message: '请输入签约金额', trigger: 'blur' }, { validator: positiveAmount, trigger: 'blur' }],
        feeType: [{ required: true, message: '请选择收费方式', trigger: 'change' }],
        signMethod: [{ required: true, message: '请选择签订方式', trigger: 'change' }],
        riskLevel: [{ required: true, message: '请选择风险等级', trigger: 'change' }]
      },
      approvalRules: {
        action: [{ required: true, message: '请选择审批动作', trigger: 'change' }],
        opinion: [{ required: true, message: '请输入审批意见', trigger: 'blur' }]
      },
      templateRules: {
        templateName: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
        caseType: [{ required: true, message: '请选择案件类型', trigger: 'change' }],
        fileUrl: [{ required: true, message: '请上传模板文件', trigger: 'change' }]
      },
      feeRules: {
        contractId: [{ required: true, message: '请选择所属合同', trigger: 'change' }],
        periodNo: [{ required: true, message: '请输入期数', trigger: 'blur' }],
        receivableAmount: [{ required: true, message: '请输入应收金额', trigger: 'blur' }],
        planReceiveDate: [{ required: true, message: '请选择计划收款日', trigger: 'change' }]
      },
      attachmentRules: {
        contractId: [{ required: true, message: '请选择所属合同', trigger: 'change' }],
        fileUrl: [{ required: true, message: '请上传附件', trigger: 'change' }],
        fileName: [{ required: true, message: '请输入文件名', trigger: 'blur' }]
      },
      ruleRules: {
        ruleName: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
        prefix: [{ required: true, message: '请输入前缀', trigger: 'blur' }],
        datePattern: [{ required: true, message: '请输入日期格式', trigger: 'blur' }],
        serialLength: [{ required: true, message: '请输入流水长度', trigger: 'blur' }],
        status: [{ required: true, message: '请选择状态', trigger: 'change' }]
      },
      metricConfig: [
        { key: 'total', label: '合同总数', hint: '全部有效合同', icon: 'documentation', color: 'blue' },
        { key: 'monthNew', label: '本月新增', hint: '新建合同', icon: 'date', color: 'cyan' },
        { key: 'pendingAudit', label: '待审批', hint: '审核中合同', icon: 'time', color: 'orange' },
        { key: 'performing', label: '履约中', hint: '已通过并签订', icon: 'chart', color: 'green' },
        { key: 'monthAmount', label: '本月金额', hint: '本月签约总额', icon: 'money', color: 'violet' }
      ],
      pageMetricConfigs: {
        approval: [
          { key: 'total', label: '待审批合同', hint: '当前筛选结果', icon: 'time', color: 'orange' },
          { key: 'reviewing', label: '审核中', hint: '等待处理', icon: 's-check', color: 'blue' },
          { key: 'customers', label: '关联客户', hint: '本页去重客户', icon: 'people', color: 'cyan' },
          { key: 'amount', label: '本页金额', hint: '本页签约金额', icon: 'money', color: 'violet' }
        ],
        template: [
          { key: 'total', label: '模板总数', hint: '当前筛选结果', icon: 'documentation', color: 'blue' },
          { key: 'enabled', label: '启用模板', hint: '可用于合同', icon: 'validCode', color: 'green' },
          { key: 'disabled', label: '停用模板', hint: '暂不可用', icon: 'time', color: 'orange' },
          { key: 'withFile', label: '已上传文件', hint: '本页模板文件', icon: 'upload', color: 'cyan' }
        ],
        fee: [
          { key: 'total', label: '计划总数', hint: '当前筛选结果', icon: 'money', color: 'blue' },
          { key: 'pending', label: '待确认', hint: '等待收款确认', icon: 'time', color: 'orange' },
          { key: 'confirmed', label: '已确认', hint: '已确认收款', icon: 'validCode', color: 'green' },
          { key: 'invoiced', label: '已开票', hint: '本页开票记录', icon: 'form', color: 'cyan' }
        ],
        attachment: [
          { key: 'total', label: '附件总数', hint: '当前筛选结果', icon: 'documentation', color: 'blue' },
          { key: 'contracts', label: '关联合同', hint: '本页去重合同', icon: 'nested', color: 'cyan' },
          { key: 'typed', label: '已识别类型', hint: '存在文件类型', icon: 'dict', color: 'green' },
          { key: 'files', label: '可打开文件', hint: '存在文件地址', icon: 'link', color: 'violet' }
        ]
      }
    }
  },
  created() {
    if (this.$auth.hasPermiOr(['contract:list', 'contract:query', 'contract:add', 'contract:edit', 'contract:approval:list'])) {
      this.loadOwnerOptions()
    }
  },
  computed: {
    heroMeta() {
      const metas = {
        list: {
          eyebrow: '合同全生命周期',
          title: '从客户签约到审批、收费、归档全程追踪',
          description: '合同必须关联客户，审批状态与履约状态互斥流转，关键变更留痕。'
        },
        approval: {
          eyebrow: '审批工作台',
          title: '集中处理合同审核与退回修改',
          description: '只聚焦审核中合同，审批意见必填，审批动作进入状态记录。'
        },
        template: {
          eyebrow: '模板资产',
          title: '统一维护合同模板、版本与启停状态',
          description: '模板文件复用系统上传能力，启用模板用于后续合同起草。'
        },
        fee: {
          eyebrow: '收费计划',
          title: '跟踪合同应收、实收、确认与开票状态',
          description: '收费计划严格受合同状态约束，终态合同禁止继续调整。'
        },
        attachment: {
          eyebrow: '合同附件',
          title: '归集合同文件与业务附件',
          description: '附件统一关联合同，复用系统上传能力并保留业务元数据。'
        }
      }
      return metas[this.mode] || metas.list
    },
    modeMetricConfig() {
      return this.mode === 'list' ? this.metricConfig : (this.pageMetricConfigs[this.mode] || [])
    },
    approvalActionOptions() {
      const options = this.dict.type.law_contract_approval_action || []
      return options.length ? options : [
        { label: '通过', value: 'pass' },
        { label: '驳回', value: 'reject' },
        { label: '退回修改', value: 'back' }
      ]
    },
    signActionOptions() {
      if (this.signForm && this.signForm.partial) {
        return [{ label: '补齐签署为已签订', value: this.contractStates.signSigned }]
      }
      return [
        { label: '部分签订', value: this.contractStates.signPartial },
        { label: '已签订', value: this.contractStates.signSigned }
      ]
    },
    signActionTip() {
      return this.signForm && this.signForm.partial
        ? '当前合同为部分签订，可继续补齐为已签订。'
        : '签署后合同进入履约中；如签署尚未完成，请选择部分签订。'
    },
    invoiceActionOptions() {
      if (this.invoiceForm && this.invoiceForm.partial) {
        return [{ label: '补齐开票为已开票', value: this.contractStates.invoiceIssued }]
      }
      return [
        { label: '部分开票', value: this.contractStates.invoicePartial },
        { label: '已开票', value: this.contractStates.invoiceIssued }
      ]
    },
    invoiceActionTip() {
      return this.invoiceForm && this.invoiceForm.partial
        ? '当前收费计划为部分开票，可继续补齐为已开票。'
        : '如本期发票尚未全部开具，请选择部分开票。'
    },
    modeMetrics() {
      if (this.mode === 'list') return this.metrics
      if (this.mode === 'approval') {
        const customerIds = new Set(this.contracts.map(item => item.customerId || item.customer_id).filter(Boolean))
        return [
          { metricKey: 'total', metricValue: this.total },
          { metricKey: 'reviewing', metricValue: this.contracts.length },
          { metricKey: 'customers', metricValue: customerIds.size },
          { metricKey: 'amount', metricValue: this.formatMoney(this.sumBy(this.contracts, item => item.signAmount || item.sign_amount)) }
        ]
      }
      if (this.mode === 'template') {
        return [
          { metricKey: 'total', metricValue: this.total },
          { metricKey: 'enabled', metricValue: this.templates.filter(item => this.isEnabled(item.status)).length },
          { metricKey: 'disabled', metricValue: this.templates.filter(item => !this.isEnabled(item.status)).length },
          { metricKey: 'withFile', metricValue: this.templates.filter(item => item.fileUrl || item.file_url).length }
        ]
      }
      if (this.mode === 'fee') {
        return [
          { metricKey: 'total', metricValue: this.total },
          { metricKey: 'pending', metricValue: this.fees.filter(item => this.isFeePending(item)).length },
          { metricKey: 'confirmed', metricValue: this.fees.filter(item => this.isFeeConfirmed(item)).length },
          { metricKey: 'invoiced', metricValue: this.fees.filter(item => this.isFeeInvoiced(item)).length }
        ]
      }
      if (this.mode === 'attachment') {
        const contractIds = new Set(this.attachments.map(item => item.contractId || item.contract_id).filter(Boolean))
        return [
          { metricKey: 'total', metricValue: this.total },
          { metricKey: 'contracts', metricValue: contractIds.size },
          { metricKey: 'typed', metricValue: this.attachments.filter(item => item.fileType || item.file_type).length },
          { metricKey: 'files', metricValue: this.attachments.filter(item => item.fileUrl || item.file_url).length }
        ]
      }
      return []
    },
    canReadApproval() {
      return this.$auth.hasPermi('contract:approval:list')
    },
    canReadFee() {
      return this.$auth.hasPermi('contract:fee:list')
    },
    canReadAttachment() {
      return this.$auth.hasPermi('contract:attachment:list')
    },
    canReadStatus() {
      return this.$auth.hasPermi('contract:status:list')
    }
  },
  watch: {
    '$route.query': {
      immediate: true,
      handler(query) {
        this.mode = this.normalizeMode(query.module)
        this.applyCustomerRouteQuery()
        this.loadPage()
      }
    }
  },
}
</script>

<style scoped lang="scss">
@import "./contract-page.scss";
</style>

<style lang="scss">
@import "../business/business-dialog.scss";
</style>
