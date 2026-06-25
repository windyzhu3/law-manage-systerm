<template>
  <div class="biz-page matter-page" :class="'biz-size-' + appSize">
    <biz-page-header :title="pageTitle" :description="pageDescription">
      <template slot="actions">
        <el-button v-if="isMatterList" v-hasPermi="['matter:add']" :size="controlSize" type="primary" icon="el-icon-plus" @click="openMatter()">新增案件</el-button>
        <el-button v-if="mode === 'progress'" v-hasPermi="['matter:progress:add']" :size="controlSize" type="primary" icon="el-icon-plus" @click="openProgress()">新增进度</el-button>
        <el-button v-if="mode === 'node'" v-hasPermi="['matter:node:add']" :size="controlSize" type="primary" icon="el-icon-date" @click="openNode()">新增节点</el-button>
        <el-button v-if="mode === 'expense'" v-hasPermi="['matter:expense:add']" :size="controlSize" type="primary" icon="el-icon-money" @click="openExpense()">新增费用</el-button>
        <el-button v-if="mode === 'document'" v-hasPermi="['matter:document:add']" :size="controlSize" type="primary" icon="el-icon-upload" @click="openDocument()">上传文档</el-button>
      </template>
    </biz-page-header>

    <biz-hero :eyebrow="heroMeta.eyebrow" :title="heroMeta.title" :description="heroMeta.description" />
    <biz-metrics :metrics="metrics" :config="metricConfig" />

    <biz-table-card v-if="isMatterList" :show-search.sync="showSearch" :total="total" :page.sync="query.pageNum" :limit.sync="query.pageSize" @query="search" @pagination="loadMatters">
          <template slot="filters">
            <div class="biz-filter-main">
              <el-input v-model="query.keyword" :size="controlSize" clearable placeholder="搜索案件编号/名称/客户/律师" prefix-icon="el-icon-search" @keyup.enter.native="search" />
              <el-select v-model="query.caseType" :size="controlSize" clearable placeholder="案件类型" @change="search"><el-option v-for="item in dict.type.law_case_type" :key="item.value" :label="item.label" :value="item.value" /></el-select>
              <el-select v-model="query.caseStage" :size="controlSize" clearable placeholder="办理阶段" @change="search"><el-option v-for="item in dict.type.law_case_stage" :key="item.value" :label="item.label" :value="item.value" /></el-select>
              <el-select v-model="query.riskLevel" :size="controlSize" clearable placeholder="风险等级" @change="search"><el-option v-for="item in dict.type.law_case_risk_level" :key="item.value" :label="item.label" :value="item.value" /></el-select>
              <el-select v-model="query.urgency" :size="controlSize" clearable placeholder="紧急程度" @change="search"><el-option v-for="item in dict.type.law_case_urgency" :key="item.value" :label="item.label" :value="item.value" /></el-select>
            </div>
            <div class="biz-filter-actions">
              <el-button :size="controlSize" icon="el-icon-refresh" @click="reset">重置</el-button>
            </div>
          </template>
          <el-table v-loading="loading" :data="list">
            <el-table-column label="案件编号" prop="case_no" min-width="120" show-overflow-tooltip />
            <el-table-column label="案件名称" min-width="180" show-overflow-tooltip><template slot-scope="{ row }"><span class="biz-link" @click="openDetail(row)">{{ row.case_name }}</span><small class="sub-text">{{ row.customer_name || '-' }}</small></template></el-table-column>
            <el-table-column label="案件类型" width="110" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_case_type" :value="row.case_type" /></template></el-table-column>
            <el-table-column label="当前阶段" width="110" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_case_stage" :value="row.case_stage" /></template></el-table-column>
            <el-table-column label="风险等级" width="105" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_case_risk_level" :value="row.risk_level" /></template></el-table-column>
            <el-table-column label="承办律师" prop="main_lawyer_name" width="110" align="center" />
            <el-table-column label="下次关键日期" prop="next_key_date" width="120" align="center"><template slot-scope="{ row }"><span :class="{ overdue: isOverdue(row.next_key_date) }">{{ row.next_key_date || '-' }}</span></template></el-table-column>
            <el-table-column label="最近进度" prop="recent_progress" min-width="180" show-overflow-tooltip />
            <el-table-column label="材料" width="140" align="center">
              <template slot-scope="{ row }">
                <div class="material-progress">
                  <el-progress :percentage="rowMaterialStats(row).percent" :stroke-width="6" :show-text="false" />
                  <small>{{ rowMaterialStats(row).ready }}/{{ rowMaterialStats(row).total || 0 }}</small>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="费用状态" width="110" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_case_fee_status" :value="row.fee_status" /></template></el-table-column>
            <el-table-column label="操作" width="292" align="center" class-name="small-padding fixed-width biz-operation-column" fixed="right">
              <template slot-scope="{ row }">
                <span class="action-buttons">
                  <el-button v-hasPermi="['matter:query']" :size="controlSize" type="text" @click="openDetail(row)">查看</el-button>
                  <el-button v-hasPermi="['matter:progress:add']" :size="controlSize" type="text" :disabled="!canOperate(row)" @click="openProgress(row)">进度</el-button>
                  <el-button v-hasPermi="['matter:node:add']" :size="controlSize" type="text" :disabled="!canOperate(row)" @click="openNode(row)">节点</el-button>
                  <el-button v-hasPermi="['matter:expense:add']" :size="controlSize" type="text" :disabled="!canOperate(row)" @click="openExpense(row)">费用</el-button>
                  <el-button v-hasPermi="['matter:document:add']" :size="controlSize" type="text" :disabled="!canOperate(row)" @click="openDocument(row)">文档</el-button>
                  <el-button v-hasPermi="['matter:archive:apply']" :size="controlSize" type="text" :disabled="!canOperate(row)" @click="openArchive(row)">结案</el-button>
                </span>
              </template>
            </el-table-column>
          </el-table>
    </biz-table-card>

    <biz-table-card v-else :show-search.sync="showSearch" :total="total" :page.sync="query.pageNum" :limit.sync="query.pageSize" @query="search" @pagination="loadPage">
      <template slot="filters">
        <div class="biz-filter-main">
          <el-input v-model="query.keyword" :size="controlSize" clearable placeholder="搜索案件编号/名称" prefix-icon="el-icon-search" @keyup.enter.native="search" />
          <el-select v-if="mode === 'node'" v-model="query.nodeStatus" :size="controlSize" clearable placeholder="节点状态" @change="search"><el-option v-for="item in dict.type.law_case_node_status" :key="item.value" :label="item.label" :value="item.value" /></el-select>
          <el-select v-if="mode === 'expense'" v-model="query.expenseType" :size="controlSize" clearable placeholder="费用类型" @change="search"><el-option v-for="item in dict.type.law_case_expense_type" :key="item.value" :label="item.label" :value="item.value" /></el-select>
          <el-select v-if="mode === 'document'" v-model="query.documentType" :size="controlSize" clearable placeholder="文档类型" @change="search"><el-option v-for="item in dict.type.law_case_document_type" :key="item.value" :label="item.label" :value="item.value" /></el-select>
          <el-select v-if="mode === 'archive'" v-model="query.archiveStatus" :size="controlSize" clearable placeholder="归档状态" @change="search"><el-option v-for="item in dict.type.law_case_archive_status" :key="item.value" :label="item.label" :value="item.value" /></el-select>
        </div>
        <div class="biz-filter-actions"><el-button :size="controlSize" icon="el-icon-refresh" @click="reset">重置</el-button></div>
      </template>

      <el-table v-if="mode === 'progress'" v-loading="loading" :data="progressList">
        <el-table-column label="案件" min-width="210" show-overflow-tooltip><template slot-scope="{ row }">{{ row.caseNo || row.case_name }}<small class="sub-text">{{ row.customerName || '-' }}</small></template></el-table-column>
        <el-table-column label="记录时间" prop="record_time" width="160" />
        <el-table-column label="记录人" prop="record_user_name" width="110" />
        <el-table-column label="进展内容" prop="content" min-width="260" show-overflow-tooltip />
        <el-table-column label="下一步计划" prop="next_plan" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="150" align="center" class-name="small-padding fixed-width biz-operation-column" fixed="right"><template slot-scope="{ row }"><span class="action-buttons"><el-button v-hasPermi="['matter:progress:edit']" :size="controlSize" type="text" :disabled="!canOperate(row)" @click="openProgress(row)">编辑</el-button><el-button v-hasPermi="['matter:progress:remove']" :size="controlSize" type="text" class="danger-text" :disabled="!canOperate(row)" @click="removeProgress(row)">删除</el-button></span></template></el-table-column>
      </el-table>

      <el-table v-else-if="mode === 'node'" v-loading="loading" :data="nodeList">
        <el-table-column label="案件" min-width="190" show-overflow-tooltip><template slot-scope="{ row }">{{ row.caseNo || row.case_name }}<small class="sub-text">{{ row.customerName || '-' }}</small></template></el-table-column>
        <el-table-column label="节点名称" prop="node_name" min-width="150" />
        <el-table-column label="节点类型" width="110" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_case_node_type" :value="row.node_type" /></template></el-table-column>
        <el-table-column label="计划日期" prop="plan_date" width="120" align="center" />
        <el-table-column label="状态" width="110" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_case_node_status" :value="row.node_status" /></template></el-table-column>
        <el-table-column label="负责人" prop="owner_name" width="110" />
        <el-table-column label="地点" prop="court_place" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="150" align="center" class-name="small-padding fixed-width biz-operation-column" fixed="right"><template slot-scope="{ row }"><span class="action-buttons"><el-button v-hasPermi="['matter:node:edit']" :size="controlSize" type="text" :disabled="!canOperate(row)" @click="openNode(row)">编辑</el-button><el-button v-hasPermi="['matter:node:remove']" :size="controlSize" type="text" class="danger-text" :disabled="!canOperate(row)" @click="removeNode(row)">删除</el-button></span></template></el-table-column>
      </el-table>

      <el-table v-else-if="mode === 'expense'" v-loading="loading" :data="expenseList">
        <el-table-column label="费用编号" prop="expense_no" width="140" />
        <el-table-column label="案件" prop="caseName" min-width="170" show-overflow-tooltip />
        <el-table-column label="费用类型" width="110" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_case_expense_type" :value="row.expense_type" /></template></el-table-column>
        <el-table-column label="金额" width="120" align="right"><template slot-scope="{ row }">{{ formatMoney(row.amount) }}</template></el-table-column>
        <el-table-column label="发生日期" prop="occur_date" width="120" />
        <el-table-column label="付款" width="100" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_case_pay_status" :value="row.pay_status" /></template></el-table-column>
        <el-table-column label="报销" width="100" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_case_reimburse_status" :value="row.reimburse_status" /></template></el-table-column>
        <el-table-column label="操作" width="150" align="center" class-name="small-padding fixed-width biz-operation-column" fixed="right"><template slot-scope="{ row }"><span class="action-buttons"><el-button v-hasPermi="['matter:expense:edit']" :size="controlSize" type="text" :disabled="!canOperate(row)" @click="openExpense(row)">编辑</el-button><el-button v-hasPermi="['matter:expense:remove']" :size="controlSize" type="text" class="danger-text" :disabled="!canOperate(row)" @click="removeExpense(row)">删除</el-button></span></template></el-table-column>
      </el-table>

      <el-table v-else-if="mode === 'document'" v-loading="loading" :data="documentList">
        <el-table-column label="案件" min-width="210" show-overflow-tooltip><template slot-scope="{ row }">{{ row.caseNo || row.case_name }}<small class="sub-text">{{ row.customerName || row.customer_name || '-' }}</small></template></el-table-column>
        <el-table-column label="文档名称" min-width="210" show-overflow-tooltip><template slot-scope="{ row }"><span class="biz-link" @click="openBusinessFile(row.file_url || row.fileUrl)">{{ row.file_name || row.fileName }}</span><small class="sub-text">{{ row.remark || '-' }}</small></template></el-table-column>
        <el-table-column label="文档类型" width="120" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_case_document_type" :value="row.document_type" /></template></el-table-column>
        <el-table-column label="上传人" prop="create_by" width="110" align="center" />
        <el-table-column label="上传时间" prop="create_time" width="160" />
        <el-table-column label="操作" width="120" align="center" class-name="small-padding fixed-width biz-operation-column" fixed="right"><template slot-scope="{ row }"><span class="action-buttons"><el-button v-hasPermi="['matter:document:remove']" :size="controlSize" type="text" class="danger-text" :disabled="!canOperate(row)" @click="removeDocument(row)">删除</el-button></span></template></el-table-column>
      </el-table>

      <el-table v-else-if="mode === 'archive'" v-loading="loading" :data="list">
        <el-table-column label="案件编号" prop="case_no" width="130" />
        <el-table-column label="案件名称" prop="case_name" min-width="180" show-overflow-tooltip />
        <el-table-column label="客户名称" prop="customer_name" min-width="160" show-overflow-tooltip />
        <el-table-column label="归档状态" width="110" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_case_archive_status" :value="row.archive_status" /></template></el-table-column>
        <el-table-column label="材料" width="140" align="center">
          <template slot-scope="{ row }">
            <div class="material-progress">
              <el-progress :percentage="rowMaterialStats(row).percent" :stroke-width="6" :show-text="false" />
              <small>{{ rowMaterialStats(row).ready }}/{{ rowMaterialStats(row).total || 0 }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="承办律师" prop="main_lawyer_name" width="110" />
        <el-table-column label="操作" width="180" align="center" class-name="small-padding fixed-width biz-operation-column" fixed="right"><template slot-scope="{ row }"><span class="action-buttons"><el-button v-hasPermi="['matter:query']" :size="controlSize" type="text" @click="openDetail(row)">查看</el-button><el-button v-hasPermi="['matter:archive:apply', 'matter:archive:confirm']" :size="controlSize" type="text" :disabled="!canArchiveFlow(row)" @click="openArchive(row)">结案/归档</el-button></span></template></el-table-column>
      </el-table>

      <div v-else class="biz-timeline-list status-list">
        <article v-for="item in statusList" :key="item.log_id || item.create_time"><i class="el-icon-time" /><div><h4>{{ dictLabel('law_case_status_action', item.action_type) }} <span>{{ item.caseName || item.case_name }}</span></h4><p>{{ item.content || '-' }}</p><small>{{ item.create_by || '-' }} · {{ item.create_time || '-' }}</small></div></article>
        <el-empty v-if="!statusList.length" description="暂无状态记录" />
      </div>
    </biz-table-card>

    <matter-detail-drawer
      :visible.sync="detailOpen"
      :matter="detail"
      :size-class="'biz-size-' + appSize"
      @edit="openMatter"
      @progress="openProgress"
      @node="openNode"
      @expense="openExpense"
      @archive="openArchive"
      @document="openDocument"
      @finance="openCaseFinance"
      @remove-document="removeDocument"
      @open-file="openBusinessFile"
    />

    <case-finance-drawer
      :visible.sync="caseFinanceOpen"
      :case-id="caseFinanceCaseId"
      :control-size="controlSize"
      :dict-options="dict.type"
    />

    <el-dialog :title="matterForm.caseId ? '编辑案件' : '新增案件'" :visible.sync="matterOpen" width="760px" :custom-class="dialogClass" append-to-body>
      <el-form ref="matterFormRef" :model="matterForm" :rules="matterRules" label-width="100px"><el-row :gutter="12">
        <el-col :span="24" v-if="matterForm.contractId || matterForm.customerName || matterForm.contractNo">
          <div class="system-info-strip">
            <div><span>来源合同</span><b>{{ matterForm.contractNo || '-' }}</b></div>
            <div><span>客户</span><b>{{ matterForm.customerName || '-' }}</b></div>
            <div><span>案件类型</span><b>{{ dictLabel('law_case_type', matterForm.caseType) || '-' }}</b></div>
            <div><span>争议/合同金额</span><b>{{ formatMoney(matterForm.disputeAmount) }}</b></div>
          </div>
        </el-col>
        <el-col :span="12"><el-form-item label="来源合同" prop="contractId"><el-select v-model="matterForm.contractId" :size="controlSize" filterable remote reserve-keyword placeholder="搜索合同" :remote-method="searchContracts" :loading="contractLoading" :disabled="!!matterForm.caseId" @change="selectContract"><el-option v-for="item in contractOptions" :key="item.contractId" :label="item.contractName" :value="item.contractId"><span>{{ item.contractName }}</span><span class="select-sub">{{ item.contractNo || item.customerName }}</span></el-option></el-select></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="案件名称" prop="caseName"><el-input v-model="matterForm.caseName" :size="controlSize" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="案件类型" prop="caseType"><el-select v-model="matterForm.caseType" :size="controlSize" :disabled="!!matterForm.contractId" @change="handleMatterTypeChange"><el-option v-for="item in dict.type.law_case_type" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="办理阶段"><el-select v-model="matterForm.caseStage" :size="controlSize" disabled><el-option v-for="item in dict.type.law_case_stage" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="风险等级"><el-select v-model="matterForm.riskLevel" :size="controlSize"><el-option v-for="item in dict.type.law_case_risk_level" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="案由"><el-select v-model="matterForm.cause" :size="controlSize" filterable clearable placeholder="请选择案由"><el-option v-for="item in dict.type.law_case_cause" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="争议金额"><el-input-number v-model="matterForm.disputeAmount" :size="controlSize" :min="0" :precision="2" disabled /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="法院/机构"><el-input v-model="matterForm.courtName" :size="controlSize" /></el-form-item></el-col>
        <el-col :span="24"><el-form-item label="案件概况"><el-input v-model="matterForm.caseSummary" :size="controlSize" type="textarea" :rows="3" /></el-form-item></el-col>
        <el-col v-if="fieldConfigs.length" :span="24"><div class="form-section-title">专属信息 <span>{{ dictLabel('law_case_type', matterForm.caseType) }}</span></div></el-col>
        <el-col v-for="field in fieldConfigs" :key="field.field_code" :span="field.field_type === 'textarea' ? 24 : 12">
          <el-form-item :label="field.field_name" :required="field.required_flag === 'Y'">
            <el-input v-if="field.field_type === 'textarea'" v-model="field.fieldValue" :size="controlSize" type="textarea" :rows="3" :placeholder="field.placeholder || ('请输入' + field.field_name)" />
            <el-date-picker v-else-if="field.field_type === 'date'" v-model="field.fieldValue" :size="controlSize" value-format="yyyy-MM-dd" :placeholder="field.placeholder || ('请选择' + field.field_name)" />
            <el-input-number v-else-if="field.field_type === 'number'" v-model="field.fieldValue" :size="controlSize" :min="0" :precision="2" />
            <el-switch v-else-if="field.field_type === 'switch'" v-model="field.fieldValue" active-value="Y" inactive-value="N" />
            <el-input v-else v-model="field.fieldValue" :size="controlSize" :placeholder="field.placeholder || ('请输入' + field.field_name)" />
            <div v-if="field.help_text" class="field-help">{{ field.help_text }}</div>
          </el-form-item>
        </el-col>
      </el-row></el-form>
      <div slot="footer"><el-button :size="controlSize" @click="matterOpen=false">取消</el-button><el-button :size="controlSize" type="primary" @click="saveMatter">确定</el-button></div>
    </el-dialog>

    <el-dialog :title="progressForm.progressId ? '编辑进度' : '新增进度'" :visible.sync="progressOpen" width="620px" :custom-class="dialogClass" append-to-body>
      <el-form ref="progressFormRef" :model="progressForm" :rules="progressRules" label-width="100px">
        <el-form-item label="所属案件" prop="caseId"><el-select v-model="progressForm.caseId" :size="controlSize" filterable remote reserve-keyword placeholder="搜索案件" :remote-method="searchMatterOptions" :loading="matterSelectLoading"><el-option v-for="item in matterOptions" :key="item.case_id" :label="item.case_name" :value="item.case_id"><span>{{ item.case_name }}</span><span class="select-sub">{{ item.case_no }}</span></el-option></el-select></el-form-item>
        <el-form-item label="进展内容" prop="content"><el-input v-model="progressForm.content" :size="controlSize" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="下一步计划"><el-input v-model="progressForm.nextPlan" :size="controlSize" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="同步客户"><el-switch v-model="progressForm.syncCustomer" active-value="Y" inactive-value="N" /></el-form-item>
        <el-form-item label="附件"><file-upload v-model="progressForm.attachmentUrl" :limit="1" @input="syncProgressFile" /></el-form-item>
      </el-form>
      <div slot="footer"><el-button :size="controlSize" @click="progressOpen=false">取消</el-button><el-button :size="controlSize" type="primary" @click="saveProgress">确定</el-button></div>
    </el-dialog>

    <el-drawer :visible.sync="nodeOpen" size="560px" custom-class="matter-side-drawer" append-to-body>
      <div slot="title" class="drawer-title" :class="'biz-size-' + appSize"><span>CASE NODE</span><strong>{{ nodeForm.nodeId ? '编辑节点' : '新增节点' }}</strong></div>
      <el-form ref="nodeFormRef" :model="nodeForm" :rules="nodeRules" label-width="100px" class="drawer-form">
        <el-form-item label="所属案件" prop="caseId"><el-select v-model="nodeForm.caseId" :size="controlSize" filterable remote reserve-keyword placeholder="搜索案件" :remote-method="searchMatterOptions" :loading="matterSelectLoading"><el-option v-for="item in matterOptions" :key="item.case_id" :label="item.case_name" :value="item.case_id" /></el-select></el-form-item>
        <el-form-item label="节点名称" prop="nodeName"><el-input v-model="nodeForm.nodeName" :size="controlSize" /></el-form-item>
        <el-form-item label="节点类型" prop="nodeType"><el-select v-model="nodeForm.nodeType" :size="controlSize" @change="handleNodeTypeChange"><el-option v-for="item in dict.type.law_case_node_type" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="节点状态" prop="nodeStatus"><el-select v-model="nodeForm.nodeStatus" :size="controlSize"><el-option v-for="item in dict.type.law_case_node_status" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="计划日期" prop="planDate"><el-date-picker v-model="nodeForm.planDate" :size="controlSize" value-format="yyyy-MM-dd" /></el-form-item>
        <el-form-item label="实际日期"><el-date-picker v-model="nodeForm.actualDate" :size="controlSize" value-format="yyyy-MM-dd" /></el-form-item>
        <el-form-item label="法院/地点"><el-input v-model="nodeForm.courtPlace" :size="controlSize" /></el-form-item>
        <el-form-item label="法庭/庭号"><el-input v-model="nodeForm.courtRoom" :size="controlSize" /></el-form-item>
        <el-form-item label="材料清单">
          <matter-material-list v-model="nodeForm.materials" :options="dict.type.law_case_material_status" :control-size="controlSize" :default-status="dictDefault('law_case_material_status') || 'pending'" show-upload @input="markNodeMaterialsCustom" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="nodeForm.remark" :size="controlSize" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <div class="drawer-footer"><el-button :size="controlSize" @click="nodeOpen=false">取消</el-button><el-button :size="controlSize" type="primary" @click="saveNode">保存</el-button></div>
    </el-drawer>

    <el-dialog :title="expenseForm.expenseId ? '编辑费用' : '新增费用'" :visible.sync="expenseOpen" width="620px" :custom-class="dialogClass" append-to-body>
      <el-form ref="expenseFormRef" :model="expenseForm" :rules="expenseRules" label-width="100px">
        <el-form-item label="所属案件" prop="caseId"><el-select v-model="expenseForm.caseId" :size="controlSize" filterable remote reserve-keyword placeholder="搜索案件" :remote-method="searchMatterOptions" :loading="matterSelectLoading"><el-option v-for="item in matterOptions" :key="item.case_id" :label="item.case_name" :value="item.case_id" /></el-select></el-form-item>
        <el-form-item label="费用类型" prop="expenseType"><el-select v-model="expenseForm.expenseType" :size="controlSize"><el-option v-for="item in dict.type.law_case_expense_type" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="金额" prop="amount"><el-input-number v-model="expenseForm.amount" :size="controlSize" :min="0.01" :precision="2" /></el-form-item>
        <el-form-item label="发生日期" prop="occurDate"><el-date-picker v-model="expenseForm.occurDate" :size="controlSize" value-format="yyyy-MM-dd" /></el-form-item>
        <el-form-item label="付款状态"><el-select v-model="expenseForm.payStatus" :size="controlSize"><el-option v-for="item in dict.type.law_case_pay_status" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="报销状态"><el-select v-model="expenseForm.reimburseStatus" :size="controlSize"><el-option v-for="item in dict.type.law_case_reimburse_status" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="凭证"><file-upload v-model="expenseForm.voucherUrl" :limit="1" @input="syncExpenseFile" /></el-form-item>
      </el-form>
      <div slot="footer"><el-button :size="controlSize" @click="expenseOpen=false">取消</el-button><el-button :size="controlSize" type="primary" @click="saveExpense">确定</el-button></div>
    </el-dialog>

    <el-dialog title="案件文档" :visible.sync="documentOpen" width="560px" :custom-class="dialogClass" append-to-body>
      <el-form ref="documentFormRef" :model="documentForm" :rules="documentRules" label-width="100px">
        <el-form-item label="所属案件" prop="caseId"><el-select v-model="documentForm.caseId" :size="controlSize" filterable remote reserve-keyword placeholder="搜索案件" :remote-method="searchMatterOptions" :loading="matterSelectLoading"><el-option v-for="item in matterOptions" :key="item.case_id" :label="item.case_name" :value="item.case_id" /></el-select></el-form-item>
        <el-form-item label="文档名称" prop="fileName"><el-input v-model="documentForm.fileName" :size="controlSize" /></el-form-item>
        <el-form-item label="文档类型" prop="documentType"><el-select v-model="documentForm.documentType" :size="controlSize" placeholder="请选择文档类型"><el-option v-for="item in dict.type.law_case_document_type" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="文件上传" prop="fileUrl"><file-upload v-model="documentForm.fileUrl" :limit="1" @input="syncDocumentFile" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="documentForm.remark" :size="controlSize" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <div slot="footer"><el-button :size="controlSize" @click="documentOpen=false">取消</el-button><el-button :size="controlSize" type="primary" @click="saveDocument">确定</el-button></div>
    </el-dialog>

    <el-drawer :visible.sync="archiveOpen" size="560px" custom-class="matter-side-drawer" append-to-body>
      <div slot="title" class="drawer-title" :class="'biz-size-' + appSize"><span>CASE ARCHIVE</span><strong>结案归档</strong></div>
      <el-form ref="archiveFormRef" :model="archiveForm" :rules="archiveRules" label-width="110px" class="drawer-form">
        <el-form-item label="结案结果" prop="closeResult"><el-radio-group v-model="archiveForm.closeResult"><el-radio v-for="item in dict.type.law_case_close_result" :key="item.value" :label="item.value">{{ item.label }}</el-radio></el-radio-group></el-form-item>
        <el-form-item label="结案日期" prop="closeDate"><el-date-picker v-model="archiveForm.closeDate" :size="controlSize" value-format="yyyy-MM-dd" /></el-form-item>
        <el-form-item label="实际回款"><el-input-number v-model="archiveForm.actualReceivedAmount" :size="controlSize" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="费用结清"><el-radio-group v-model="archiveForm.feeClearStatus"><el-radio v-for="item in dict.type.law_case_fee_clear_status" :key="item.value" :label="item.value">{{ item.label }}</el-radio></el-radio-group></el-form-item>
        <el-form-item label="客户满意度"><el-rate v-model="archiveForm.satisfaction" /></el-form-item>
        <el-form-item label="办案总结" prop="summary"><el-input v-model="archiveForm.summary" :size="controlSize" type="textarea" :rows="5" /></el-form-item>
        <el-form-item label="归档资料">
          <matter-material-list v-model="archiveForm.materials" :options="dict.type.law_case_material_status" :control-size="controlSize" :default-status="dictDefault('law_case_material_status') || 'pending'" show-upload />
        </el-form-item>
        <el-form-item v-if="archiveForm.caseStatus === 'closed'" label="归档检查">
          <div class="archive-checklist">
            <p :class="{ ok: archiveMaterialStats.total > 0 && archiveMaterialStats.missing === 0 }">
              <span>归档资料</span><b>{{ archiveMaterialStats.ready }}/{{ archiveMaterialStats.total || 0 }}</b>
            </p>
            <p :class="{ ok: archiveForm.feeClearStatus === 'cleared' }">
              <span>费用结清</span><b>{{ dictLabel('law_case_fee_clear_status', archiveForm.feeClearStatus) || '-' }}</b>
            </p>
            <small v-if="archiveMaterialStats.missing > 0">仍有 {{ archiveMaterialStats.missing }} 项资料未准备完成，确认归档前需要补齐。</small>
          </div>
        </el-form-item>
        <el-form-item label="是否只读"><el-switch v-model="archiveForm.readonlyFlag" active-value="Y" inactive-value="N" /></el-form-item>
      </el-form>
      <div class="drawer-footer"><el-button :size="controlSize" @click="archiveOpen=false">取消</el-button><el-button v-if="archiveForm.caseStatus === 'processing'" v-hasPermi="['matter:archive:apply']" :size="controlSize" type="primary" @click="submitArchive">提交结案</el-button><el-button v-if="archiveForm.caseStatus === 'closing'" v-hasPermi="['matter:archive:confirm']" :size="controlSize" type="warning" @click="confirmCloseSubmit">确认结案</el-button><el-button v-if="archiveForm.caseStatus === 'closed'" v-hasPermi="['matter:archive:confirm']" :size="controlSize" type="success" @click="confirmArchiveSubmit">确认归档</el-button></div>
    </el-drawer>
  </div>
</template>

<script>
import BizHero from '@/views/business/components/BizHero'
import BizMetrics from '@/views/business/components/BizMetrics'
import BizPageHeader from '@/views/business/components/BizPageHeader'
import BizTableCard from '@/views/business/components/BizTableCard'
import MatterDetailDrawer from './components/MatterDetailDrawer'
import MatterMaterialList from './components/MatterMaterialList'
import CaseFinanceDrawer from '@/views/finance/components/CaseFinanceDrawer'
import businessUi from '@/views/business/mixins/businessUi'
import { listContract } from '@/api/contract'
import { getMatterDashboard, listMatter, getMatter, getMatterFieldConfigs, addMatter, updateMatter, listProgress, addProgress, updateProgress, delProgress, listNode, addNode, updateNode, delNode, listExpense, addExpense, updateExpense, delExpense, listDocument, addDocument, delDocument, getArchive, applyArchive, confirmClose, confirmArchive, listMatterStatus } from '@/api/matter'

export default {
  name: 'Matter',
  mixins: [businessUi],
  components: { BizHero, BizMetrics, BizPageHeader, BizTableCard, MatterDetailDrawer, MatterMaterialList, CaseFinanceDrawer },
  dicts: ['law_case_type', 'law_case_urgency', 'law_case_status', 'law_case_priority', 'law_case_risk_level', 'law_case_stage', 'law_case_cause', 'law_case_node_status', 'law_case_node_type', 'law_case_material_status', 'law_case_document_type', 'law_case_expense_type', 'law_case_pay_status', 'law_case_reimburse_status', 'law_case_voucher_status', 'law_case_fee_status', 'law_case_close_result', 'law_case_fee_clear_status', 'law_case_archive_status', 'law_case_status_action', 'law_finance_payment_method', 'law_finance_invoice_type'],
  data() {
    return {
      mode: 'list',
      showSearch: true,
      loading: false,
      total: 0,
      query: { pageNum: 1, pageSize: 10, keyword: '' },
      list: [],
      progressList: [],
      nodeList: [],
      expenseList: [],
      documentList: [],
      statusList: [],
      metrics: [],
      typeStats: [],
      reminders: [],
      matterOptions: [],
      contractOptions: [],
      matterSelectLoading: false,
      contractLoading: false,
      detailOpen: false,
      detail: {},
      caseFinanceOpen: false,
      caseFinanceCaseId: null,
      matterOpen: false,
      matterForm: {},
      fieldConfigs: [],
      progressOpen: false,
      progressForm: {},
      nodeOpen: false,
      nodeForm: {},
      expenseOpen: false,
      expenseForm: {},
      documentOpen: false,
      documentForm: {},
      archiveOpen: false,
      archiveForm: {},
      businessPageMeta: {
        defaultTitle: '案件中心',
        titles: { list: '案件列表', mine: '我的案件', progress: '进度记录', node: '关键节点', expense: '费用管理', document: '文档资料', archive: '结案归档', status: '状态记录' },
        descriptions: {
          list: '全面管理案件全生命周期，进度跟踪、关键节点、费用管控与结案归档一体化。',
          mine: '聚焦当前律师承办和协办的在办案件。',
          progress: '沉淀案件办理过程与下一步计划。',
          node: '管理立案、举证、开庭、判决、执行和归档等关键节点。',
          expense: '记录案件支出、付款、报销与凭证状态。',
          document: '集中维护案件文书、证据、材料和归档前资料。',
          archive: '处理结案申请、归档清单和只读归档。',
          status: '追踪案件全流程状态变化。'
        }
      },
      metricConfig: [
        { key: 'active', label: '在办案件', hint: '办理中/结案中', icon: 'documentation', color: 'blue' },
        { key: 'todayProgress', label: '今日新增进度', hint: '今日记录', icon: 'time', color: 'cyan' },
        { key: 'dueNodes', label: '临期节点', hint: '7日内到期', icon: 'date', color: 'orange' },
        { key: 'closing', label: '待结案件', hint: '结案申请中', icon: 'folder', color: 'violet' },
        { key: 'monthArchived', label: '本月归档', hint: '已归档案件', icon: 'validCode', color: 'green' }
      ],
      matterRules: {
        contractId: [{ required: true, message: '请选择来源合同', trigger: 'change' }],
        caseName: [{ required: true, message: '请输入案件名称', trigger: 'blur' }],
        caseType: [{ required: true, message: '请选择案件类型', trigger: 'change' }]
      },
      progressRules: {
        caseId: [{ required: true, message: '请选择案件', trigger: 'change' }],
        content: [{ required: true, message: '请输入进展内容', trigger: 'blur' }]
      },
      nodeRules: {
        caseId: [{ required: true, message: '请选择案件', trigger: 'change' }],
        nodeName: [{ required: true, message: '请输入节点名称', trigger: 'blur' }],
        nodeType: [{ required: true, message: '请选择节点类型', trigger: 'change' }],
        nodeStatus: [{ required: true, message: '请选择节点状态', trigger: 'change' }],
        planDate: [{ required: true, message: '请选择计划日期', trigger: 'change' }]
      },
      expenseRules: {
        caseId: [{ required: true, message: '请选择案件', trigger: 'change' }],
        expenseType: [{ required: true, message: '请选择费用类型', trigger: 'change' }],
        amount: [{ required: true, message: '请输入金额', trigger: 'blur' }],
        occurDate: [{ required: true, message: '请选择发生日期', trigger: 'change' }]
      },
      documentRules: {
        caseId: [{ required: true, message: '请选择案件', trigger: 'change' }],
        documentType: [{ required: true, message: '请选择文档类型', trigger: 'change' }],
        fileName: [{ required: true, message: '请输入文档名称', trigger: 'blur' }],
        fileUrl: [{ required: true, message: '请上传文档', trigger: 'change' }]
      },
      archiveRules: {
        closeResult: [{ required: true, message: '请选择结案结果', trigger: 'change' }],
        closeDate: [{ required: true, message: '请选择结案日期', trigger: 'change' }],
        summary: [{ required: true, message: '请输入办案总结', trigger: 'blur' }]
      }
    }
  },
  computed: {
    isMatterList() { return ['list', 'mine'].includes(this.mode) },
    heroMeta() {
      return {
        eyebrow: 'MATTER CENTER',
        title: this.mode === 'node' ? '精准管理关键节点，确保案件按节奏推进' : this.mode === 'archive' ? '规范完成结案归档，形成案件闭环' : '全流程掌控案件办理进度与结果',
        description: '复用合同、客户和案管链路，统一管理律师办案过程、文档资料和状态记录。'
      }
    },
    archiveMaterialStats() {
      const list = this.archiveForm.materials || []
      const ready = this.readyMaterialCount(list)
      const total = list.length
      return {
        total,
        ready,
        missing: Math.max(total - ready, 0),
        percent: total ? Math.round((ready / total) * 100) : 0
      }
    }
  },
  watch: {
    '$route.query.module': {
      immediate: true,
      handler(value) {
        this.mode = value || 'list'
        this.resetQuery()
        this.loadPage()
      }
    }
  },
  methods: {
    loadPage() {
      this.loadDashboard()
      if (this.isMatterList || this.mode === 'archive') return this.loadMatters()
      if (this.mode === 'progress') return this.loadProgress()
      if (this.mode === 'node') return this.loadNodes()
      if (this.mode === 'expense') return this.loadExpenses()
      if (this.mode === 'document') return this.loadDocuments()
      if (this.mode === 'status') return this.loadStatuses()
    },
    loadDashboard() { getMatterDashboard().then(res => { const data = res.data || {}; this.metrics = data.cards || []; this.typeStats = data.types || []; this.reminders = data.reminders || [] }) },
    loadMatters() { this.loading = true; listMatter({ ...this.query, mode: this.mode }).then(res => { this.list = res.rows || []; this.total = res.total || 0 }).finally(() => { this.loading = false }) },
    loadProgress() { this.loading = true; listProgress(this.query).then(res => { this.progressList = res.rows || []; this.total = res.total || 0 }).finally(() => { this.loading = false }) },
    loadNodes() { this.loading = true; listNode(this.query).then(res => { this.nodeList = res.rows || []; this.total = res.total || 0 }).finally(() => { this.loading = false }) },
    loadExpenses() { this.loading = true; listExpense(this.query).then(res => { this.expenseList = res.rows || []; this.total = res.total || 0 }).finally(() => { this.loading = false }) },
    loadDocuments() { this.loading = true; listDocument(this.query).then(res => { this.documentList = res.rows || []; this.total = res.total || 0 }).finally(() => { this.loading = false }) },
    loadStatuses() { this.loading = true; listMatterStatus(this.query).then(res => { this.statusList = res.rows || []; this.total = res.total || 0 }).finally(() => { this.loading = false }) },
    search() { this.query.pageNum = 1; this.loadPage() },
    reset() { this.resetQuery(); this.loadPage() },
    resetQuery() {
      this.query = {
        pageNum: 1,
        pageSize: 10,
        keyword: '',
        customerId: this.$route.query.customerId,
        contractId: this.$route.query.contractId
      }
    },
    isOverdue(date) { return date && new Date(date).getTime() < Date.now() },
    rowStatus(row = {}) { return row.case_status || row.caseStatus },
    isArchived(row) { return row && ['archived', 'terminated'].includes(this.rowStatus(row)) },
    canOperate(row) { return row && this.rowStatus(row) === 'processing' },
    canArchiveFlow(row) { return row && ['processing', 'closing', 'closed'].includes(this.rowStatus(row)) },
    canConfirmClose(row) { return row && this.rowStatus(row) === 'closing' },
    canConfirmArchive(row) { return row && this.rowStatus(row) === 'closed' },
    readyMaterialCount(list = []) {
      return (list || []).filter(item => {
        const status = String(item.materialStatus || item.material_status || '').toLowerCase()
        return ['ready', 'completed', 'done', 'uploaded'].includes(status)
      }).length
    },
    validateArchiveReadyClient() {
      if (!this.validateArchiveFeeReadyClient()) return false
      const materials = this.archiveForm.materials || []
      if (!materials.length) {
        this.$modal.msgError('请维护归档资料清单后再确认归档')
        return false
      }
      if (this.readyMaterialCount(materials) < materials.length) {
        this.$modal.msgError('仍有未准备完成的归档资料，不能确认归档')
        return false
      }
      return true
    },
    validateArchiveFeeReadyClient() {
      if (this.archiveForm.feeClearStatus !== 'cleared') {
        this.$modal.msgError('费用未结清，不能确认结案或归档')
        return false
      }
      return true
    },
    rowMaterialStats(row = {}) {
      const nodeTotal = Number(row.nodeMaterialTotal || row.node_material_total || 0)
      const nodeReady = Number(row.nodeMaterialReady || row.node_material_ready || 0)
      const archiveTotal = Number(row.archiveMaterialTotal || row.archive_material_total || 0)
      const archiveReady = Number(row.archiveMaterialReady || row.archive_material_ready || 0)
      const total = nodeTotal + archiveTotal
      const ready = nodeReady + archiveReady
      return {
        total,
        ready,
        percent: total ? Math.round((ready / total) * 100) : 0
      }
    },
    normalizeRow(row) {
      const result = { ...row }
      ;[['caseId', 'case_id'], ['caseName', 'case_name'], ['customerId', 'customer_id'], ['customerName', 'customer_name'], ['contractId', 'contract_id'], ['contractNo', 'contract_no'], ['caseType', 'case_type'], ['caseStage', 'case_stage'], ['riskLevel', 'risk_level'], ['disputeAmount', 'dispute_amount'], ['courtName', 'court_name'], ['caseFilingNo', 'case_filing_no'], ['caseSummary', 'case_summary'], ['expenseId', 'expense_id'], ['progressId', 'progress_id'], ['nodeId', 'node_id'], ['nodeName', 'node_name'], ['nodeType', 'node_type'], ['nodeStatus', 'node_status'], ['planDate', 'plan_date'], ['actualDate', 'actual_date'], ['courtPlace', 'court_place'], ['courtRoom', 'court_room'], ['expenseType', 'expense_type'], ['occurDate', 'occur_date'], ['payStatus', 'pay_status'], ['reimburseStatus', 'reimburse_status'], ['voucherStatus', 'voucher_status']].forEach(([camel, snake]) => { if (result[camel] === undefined && result[snake] !== undefined) result[camel] = result[snake] })
      return result
    },
    loadFieldConfigs(caseType, values = []) {
      if (!caseType) {
        this.fieldConfigs = []
        return
      }
      const valueMap = {}
      ;(values || []).forEach(item => { valueMap[item.fieldCode || item.field_code] = item.fieldValue || item.field_value })
      getMatterFieldConfigs(caseType).then(res => {
        this.fieldConfigs = (res.data || []).map(item => ({
          ...item,
          fieldValue: valueMap[item.field_code] !== undefined ? valueMap[item.field_code] : (item.field_type === 'switch' ? 'N' : '')
        }))
      })
    },
    collectFieldValues() {
      return this.fieldConfigs.map(item => ({
        fieldCode: item.field_code,
        fieldName: item.field_name,
        fieldValue: item.fieldValue,
        orderNum: item.order_num
      }))
    },
    validateFieldConfigs() {
      const missing = this.fieldConfigs.find(item => item.required_flag === 'Y' && (item.fieldValue === undefined || item.fieldValue === null || String(item.fieldValue).trim() === ''))
      if (missing) {
        this.$modal.msgError('请填写案件专属信息：' + missing.field_name)
        return false
      }
      const invalid = this.fieldConfigs.find(item => !this.isValidFieldValue(item))
      if (invalid) {
        this.$modal.msgError('案件专属信息格式不正确：' + invalid.field_name)
        return false
      }
      return true
    },
    isValidFieldValue(field) {
      const value = field.fieldValue
      if (value === undefined || value === null || String(value).trim() === '') return true
      const text = String(value).trim()
      if (field.field_type === 'number') return !Number.isNaN(Number(text))
      if (field.field_type === 'date') return /^\d{4}-\d{2}-\d{2}$/.test(text) && !Number.isNaN(new Date(text).getTime())
      if (field.field_type === 'switch') return ['Y', 'N'].includes(text)
      return true
    },
    openDetail(row) { getMatter(row.case_id || row.caseId).then(res => { this.detail = res.data || {}; this.detailOpen = true }) },
    openCaseFinance(row = {}) {
      const caseId = row.case_id || row.caseId
      if (!caseId) {
        this.$modal.msgError('当前案件缺少案件ID，无法打开财务视图')
        return
      }
      this.caseFinanceCaseId = caseId
      this.caseFinanceOpen = true
    },
    searchMatterOptions(keyword) { this.matterSelectLoading = true; listMatter({ pageNum: 1, pageSize: 20, keyword, mode: 'list' }).then(res => { this.matterOptions = res.rows || [] }).finally(() => { this.matterSelectLoading = false }) },
    searchContracts(keyword) {
      this.contractLoading = true
      listContract({ pageNum: 1, pageSize: 20, contractName: keyword, auditStatus: '2', signStatus: '1', contractStatus: '1' }).then(res => { this.contractOptions = res.rows || [] }).finally(() => { this.contractLoading = false })
    },
    selectContract(contractId) {
      const item = this.contractOptions.find(contract => contract.contractId === contractId)
      if (item) {
        this.matterForm.caseName = this.matterForm.caseName || item.contractName
        this.matterForm.caseType = item.caseType || this.matterForm.caseType
        this.matterForm.disputeAmount = item.signAmount || this.matterForm.disputeAmount
        this.matterForm.contractNo = item.contractNo
        this.matterForm.customerId = item.customerId
        this.matterForm.customerName = item.customerName
        this.loadFieldConfigs(this.matterForm.caseType, this.matterForm.fieldValues)
      }
    },
    handleMatterTypeChange(caseType) { this.loadFieldConfigs(caseType, this.matterForm.fieldValues) },
    openMatter(row) {
      this.matterForm = row ? this.normalizeRow(row) : { caseType: this.dictDefault('law_case_type'), caseStage: this.dictDefault('law_case_stage'), riskLevel: this.dictDefault('law_case_risk_level'), fieldValues: [] }
      if (!row) this.searchContracts('')
      if (row && this.matterForm.contractId && !this.contractOptions.some(item => item.contractId === this.matterForm.contractId)) {
        this.contractOptions.unshift({
          contractId: this.matterForm.contractId,
          contractName: this.matterForm.contractNo || this.matterForm.caseName || '来源合同',
          contractNo: this.matterForm.contractNo,
          customerName: this.matterForm.customerName,
          caseType: this.matterForm.caseType,
          signAmount: this.matterForm.disputeAmount
        })
      }
      this.loadFieldConfigs(this.matterForm.caseType, this.matterForm.fieldValues)
      this.matterOpen = true
      this.$nextTick(() => this.$refs.matterFormRef && this.$refs.matterFormRef.clearValidate())
    },
    saveMatter() {
      this.$refs.matterFormRef.validate(valid => {
        if (!valid || !this.validateFieldConfigs()) return
        this.matterForm.fieldValues = this.collectFieldValues()
        ;(this.matterForm.caseId ? updateMatter : addMatter)(this.matterForm).then(() => { this.$modal.msgSuccess('保存成功'); this.matterOpen = false; this.loadPage(); if (this.detailOpen) this.openDetail({ case_id: this.matterForm.caseId }) })
      })
    },
    ensureMatterOption(row) { const id = row && (row.case_id || row.caseId); const name = row && (row.case_name || row.caseName); if (id && name && !this.matterOptions.some(item => item.case_id === id)) this.matterOptions.unshift({ case_id: id, case_name: name, case_no: row.case_no || row.caseNo }) },
    openProgress(row) { this.ensureMatterOption(row); this.progressForm = row && (row.progress_id || row.progressId) ? this.normalizeRow(row) : { caseId: row && (row.case_id || row.caseId), syncCustomer: 'N' }; if (!row) this.searchMatterOptions(''); this.progressOpen = true; this.$nextTick(() => this.$refs.progressFormRef && this.$refs.progressFormRef.clearValidate()) },
    saveProgress() { this.$refs.progressFormRef.validate(valid => { if (!valid) return; (this.progressForm.progressId ? updateProgress : addProgress)(this.progressForm).then(() => { this.$modal.msgSuccess('保存成功'); this.progressOpen = false; this.loadPage(); if (this.detailOpen) this.openDetail({ case_id: this.progressForm.caseId }) }) }) },
    removeProgress(row) { this.$modal.confirm('确认删除该进度记录吗？').then(() => delProgress(row.progress_id)).then(() => { this.$modal.msgSuccess('删除成功'); this.loadPage() }).catch(() => {}) },
    openNode(row) {
      this.ensureMatterOption(row)
      if (row && (row.node_id || row.nodeId)) {
        this.nodeForm = { ...this.normalizeRow(row), materials: this.normalizeMaterials(row.materials || []) }
      } else {
        const caseType = this.resolveRowCaseType(row)
        const nodeType = this.dictDefault('law_case_node_type')
        this.nodeForm = {
          caseId: row && (row.case_id || row.caseId),
          caseType,
          nodeType,
          nodeName: this.defaultNodeName(nodeType),
          nodeStatus: this.dictDefault('law_case_node_status'),
          materials: this.defaultNodeMaterials(caseType, nodeType),
          _lastNodeType: nodeType,
          _materialTemplate: true
        }
      }
      if (!row) this.searchMatterOptions('')
      this.nodeOpen = true
      this.$nextTick(() => this.$refs.nodeFormRef && this.$refs.nodeFormRef.clearValidate())
    },
    handleNodeTypeChange(nodeType) {
      if (!this.nodeForm.nodeName || this.nodeForm.nodeName === this.defaultNodeName(this.nodeForm._lastNodeType)) {
        this.nodeForm.nodeName = this.defaultNodeName(nodeType)
      }
      if (!this.nodeForm.nodeId && this.nodeForm._materialTemplate) {
        this.nodeForm.materials = this.defaultNodeMaterials(this.nodeForm.caseType, nodeType)
      }
      this.nodeForm._lastNodeType = nodeType
    },
    markNodeMaterialsCustom() {
      if (this.nodeForm && this.nodeForm._suppressMaterialMark) {
        this.nodeForm._suppressMaterialMark = false
        return
      }
      if (this.nodeForm) this.nodeForm._materialTemplate = false
    },
    validateNodeBusiness() {
      if (this.nodeForm.nodeStatus === 'done' && !this.nodeForm.actualDate) {
        this.$modal.msgError('已完成节点必须填写实际日期')
        return false
      }
      if (this.nodeForm.actualDate && ['pending', 'current'].includes(this.nodeForm.nodeStatus)) {
        this.$modal.msgError('已填写实际日期的节点不能保持待开始或当前节点状态')
        return false
      }
      return true
    },
    saveNode() { this.$refs.nodeFormRef.validate(valid => { if (!valid || !this.validateNodeBusiness()) return; (this.nodeForm.nodeId ? updateNode : addNode)(this.nodeForm).then(() => { this.$modal.msgSuccess('保存成功'); this.nodeOpen = false; this.loadPage(); if (this.detailOpen) this.openDetail({ case_id: this.nodeForm.caseId }) }) }) },
    removeNode(row) { this.$modal.confirm('确认删除该关键节点吗？').then(() => delNode(row.node_id)).then(() => { this.$modal.msgSuccess('删除成功'); this.loadPage() }).catch(() => {}) },
    openExpense(row) { this.ensureMatterOption(row); this.expenseForm = row && (row.expense_id || row.expenseId) ? this.normalizeRow(row) : { caseId: row && (row.case_id || row.caseId), expenseType: this.dictDefault('law_case_expense_type'), payStatus: this.dictDefault('law_case_pay_status'), reimburseStatus: this.dictDefault('law_case_reimburse_status'), voucherStatus: this.dictDefault('law_case_voucher_status') }; if (!row) this.searchMatterOptions(''); this.expenseOpen = true; this.$nextTick(() => this.$refs.expenseFormRef && this.$refs.expenseFormRef.clearValidate()) },
    saveExpense() { this.$refs.expenseFormRef.validate(valid => { if (!valid) return; (this.expenseForm.expenseId ? updateExpense : addExpense)(this.expenseForm).then(() => { this.$modal.msgSuccess('保存成功'); this.expenseOpen = false; this.loadPage(); if (this.detailOpen) this.openDetail({ case_id: this.expenseForm.caseId }) }) }) },
    removeExpense(row) { this.$modal.confirm('确认删除该费用记录吗？').then(() => delExpense(row.expense_id)).then(() => { this.$modal.msgSuccess('删除成功'); this.loadPage() }).catch(() => {}) },
    openArchive(row) {
      const caseId = row.case_id || row.caseId
      this.archiveForm = { caseId, caseStatus: row.case_status || row.caseStatus, closeResult: this.dictDefault('law_case_close_result'), feeClearStatus: this.dictDefault('law_case_fee_clear_status'), readonlyFlag: 'Y', satisfaction: 5, materials: this.defaultArchiveMaterials() }
      this.archiveOpen = true
      getArchive(caseId).then(res => {
        if (res.data) {
          this.archiveForm = {
            ...this.archiveForm,
            ...this.normalizeArchive(res.data),
            caseId,
            materials: (res.data.materials && res.data.materials.length) ? this.normalizeMaterials(res.data.materials) : this.archiveForm.materials
          }
        }
      }).finally(() => this.$nextTick(() => this.$refs.archiveFormRef && this.$refs.archiveFormRef.clearValidate()))
    },
    submitArchive() { this.$refs.archiveFormRef.validate(valid => { if (!valid) return; applyArchive(this.archiveForm).then(() => { this.$modal.msgSuccess('结案申请已提交'); this.archiveOpen = false; this.loadPage(); if (this.detailOpen) this.openDetail({ case_id: this.archiveForm.caseId }) }) }) },
    confirmArchiveSubmit() { this.$refs.archiveFormRef.validate(valid => { if (!valid || !this.validateArchiveReadyClient()) return; confirmArchive(this.archiveForm).then(() => { this.$modal.msgSuccess('归档成功'); this.archiveOpen = false; this.loadPage(); if (this.detailOpen) this.openDetail({ case_id: this.archiveForm.caseId }) }) }) },
    confirmCloseSubmit() { this.$refs.archiveFormRef.validate(valid => { if (!valid || !this.validateArchiveFeeReadyClient()) return; confirmClose(this.archiveForm).then(() => { this.$modal.msgSuccess('确认结案成功'); this.archiveOpen = false; this.loadPage(); if (this.detailOpen) this.openDetail({ case_id: this.archiveForm.caseId }) }) }) },
    openDocument(row) { this.ensureMatterOption(row); this.documentForm = { caseId: row && (row.case_id || row.caseId), documentType: this.dictDefault('law_case_document_type') || 'case' }; if (!row) this.searchMatterOptions(''); this.documentOpen = true; this.$nextTick(() => this.$refs.documentFormRef && this.$refs.documentFormRef.clearValidate()) },
    saveDocument() { this.$refs.documentFormRef.validate(valid => { if (!valid) return; addDocument(this.documentForm).then(() => { this.$modal.msgSuccess('文档保存成功'); this.documentOpen = false; this.loadPage(); if (this.detailOpen) this.openDetail({ case_id: this.documentForm.caseId }) }) }) },
    removeDocument(row) { this.$modal.confirm('确认删除该案件文档吗？').then(() => delDocument(row.document_id)).then(() => { this.$modal.msgSuccess('删除成功'); this.loadPage(); if (this.detailOpen) this.openDetail({ case_id: row.case_id || this.detail.case_id }) }).catch(() => {}) },
    syncDocumentFile(value) { this.documentForm.fileName = this.documentForm.fileName || this.fileNameFromUrl(value) },
    normalizeArchive(row) { const result = { ...row }; [['archiveId', 'archive_id'], ['closeResult', 'close_result'], ['closeDate', 'close_date'], ['actualReceivedAmount', 'actual_received_amount'], ['feeClearStatus', 'fee_clear_status'], ['readonlyFlag', 'readonly_flag'], ['archiveStatus', 'archive_status'], ['archiveNo', 'archive_no']].forEach(([camel, snake]) => { if (result[camel] === undefined && result[snake] !== undefined) result[camel] = result[snake] }); return result },
    normalizeMaterials(list) { return (list || []).map(item => ({ ...item, materialName: item.materialName || item.material_name, materialStatus: item.materialStatus || item.material_status, fileUrl: item.fileUrl || item.file_url, fileName: item.fileName || item.file_name })) },
    defaultArchiveMaterials() { return ['结案报告', '判决书/裁定书', '客户确认单', '收费确认', '电子卷宗', '纸质材料'].map(name => ({ materialName: name, materialStatus: this.dictDefault('law_case_material_status') || 'pending' })) },
    resolveRowCaseType(row) {
      return row && (row.case_type || row.caseType) || this.detail.case_type || this.detail.caseType || this.query.caseType || this.dictDefault('law_case_type')
    },
    defaultNodeName(nodeType) {
      return this.dictLabel('law_case_node_type', nodeType) || '关键节点'
    },
    materialItems(names) {
      const status = this.dictDefault('law_case_material_status') || 'pending'
      return (names || []).map(name => ({ materialName: name, materialStatus: status }))
    },
    defaultNodeMaterials(caseType, nodeType) {
      const common = {
        filing: ['委托手续', '主体身份证明', '起诉/申请材料', '证据目录'],
        evidence: ['证据目录', '核心证据原件/复印件', '证据说明', '补充材料清单'],
        hearing: ['庭审提纲', '代理词/辩护词', '证据交换记录', '开庭传票/通知'],
        judgment: ['裁判文书', '送达回证', '履行/上诉期限记录', '客户告知记录'],
        execution: ['执行申请书', '生效证明', '财产线索', '执行进展记录'],
        archive: ['结案报告', '裁判/调解/和解文书', '费用结清确认', '电子卷宗']
      }
      const byCase = {
        criminal: {
          filing: ['委托手续', '家属授权材料', '嫌疑人/被告人身份信息', '案件受理/拘留通知'],
          evidence: ['阅卷材料目录', '会见笔录', '证据摘录', '辩护意见要点'],
          hearing: ['辩护词', '质证意见', '发问提纲', '量刑情节材料'],
          judgment: ['判决/裁定书', '上诉期限记录', '家属告知记录', '后续救济方案']
        },
        labor: {
          filing: ['劳动合同/用工证明', '工资流水', '社保/考勤记录', '仲裁申请书'],
          evidence: ['工资流水', '考勤记录', '聊天/通知记录', '解除/离职材料'],
          hearing: ['仲裁庭审提纲', '证据清单', '代理意见', '调解方案'],
          judgment: ['仲裁裁决/判决书', '履行期限记录', '强制执行评估', '客户告知记录']
        },
        ip: {
          filing: ['权属证明', '授权委托书', '侵权线索', '保全/公证计划'],
          evidence: ['公证材料', '侵权页面/样品', '购买取证记录', '损失计算材料'],
          hearing: ['权属说明', '侵权比对意见', '赔偿计算说明', '质证意见']
        },
        advisor: {
          filing: ['顾问合同', '服务范围确认', '客户对接人清单', '年度服务计划'],
          evidence: ['合同审查记录', '咨询答复记录', '培训材料', '合规问题清单'],
          archive: ['月度/年度服务报告', '客户确认记录', '续约建议', '服务成果归档']
        },
        company: {
          filing: ['公司主体资料', '章程/股东名册', '授权文件', '业务事项说明'],
          evidence: ['股东会/董事会文件', '交易文件', '工商档案', '往来沟通记录'],
          hearing: ['争议焦点梳理', '公司治理文件', '代理意见', '和解方案']
        },
        finance: {
          filing: ['交易合同', '放款/投资凭证', '担保文件', '违约说明'],
          evidence: ['流水凭证', '催收记录', '担保物资料', '风险测算表'],
          execution: ['财产线索', '担保物处置材料', '执行申请书', '回款记录']
        },
        admin_litigation: {
          filing: ['行政文书', '送达材料', '复议材料', '起诉状'],
          evidence: ['行政行为证据', '程序违法线索', '损害后果材料', '法律依据清单'],
          hearing: ['代理词', '质证意见', '行政程序审查清单', '庭审提纲']
        }
      }
      return this.materialItems((byCase[caseType] && byCase[caseType][nodeType]) || common[nodeType] || ['节点材料清单'])
    },
    syncProgressFile(value) { this.progressForm.attachmentName = this.fileNameFromUrl(value) },
    syncExpenseFile(value) { this.expenseForm.voucherName = this.fileNameFromUrl(value); this.expenseForm.voucherStatus = value ? 'uploaded' : 'missing' }
  }
}
</script>

<style scoped lang="scss">
@import "../business/business.scss";
@import "../business/detail-drawer.scss";

.matter-page {
  --biz-filter-input-width: 250px;
  --biz-filter-select-width: 132px;
}

.overdue {
  color: #ef4444;
}

.drawer-form {
  padding: 16px 20px 80px;
}

.form-section-title {
  margin: 4px 0 12px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f6f9ff;
  color: #1e3a8a;
  font-weight: 600;

  span {
    margin-left: 8px;
    color: #64748b;
    font-size: var(--biz-font-small);
    font-weight: 400;
  }
}

.system-info-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
  padding: 12px;
  border: 1px solid #e8edf6;
  border-radius: 12px;
  background: linear-gradient(135deg, #f8fbff, #f4f7ff);

  div {
    min-width: 0;
  }

  span,
  b {
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  span {
    color: #64748b;
    font-size: var(--biz-font-mini);
  }

  b {
    margin-top: 5px;
    color: #102a6b;
    font-size: var(--biz-font-small);
  }
}

.field-help {
  margin-top: 4px;
  color: #94a3b8;
  font-size: var(--biz-font-mini);
  line-height: 1.4;
}

.drawer-footer {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 12px 18px;
  border-top: 1px solid #edf1f7;
  background: #fff;
}

.status-list {
  min-height: 360px;
}

</style>

<style lang="scss">
@import "../business/business-dialog.scss";
</style>
