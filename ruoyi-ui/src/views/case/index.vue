<template>
  <div class="biz-page case-page" :class="'biz-size-' + appSize">
    <biz-page-header eyebrow="CASE CENTER" :title="pageTitle" :description="pageDescription">
      <el-button v-if="mode === 'pending' && selectedRows.length <= 1" v-hasPermi="['case:pending:assign']" :size="controlSize" type="primary" icon="el-icon-user" :disabled="!selectedRows.length" @click="openAssign(selectedRows[0], selectedRows)">立即分案</el-button>
      <el-button v-if="mode === 'pending' && selectedRows.length > 1" v-hasPermi="['case:pending:batchAssign']" :size="controlSize" type="primary" icon="el-icon-user" @click="openAssign(selectedRows[0], selectedRows)">批量分案</el-button>
      <el-button v-if="mode === 'pending'" :size="controlSize" plain icon="el-icon-time" @click="goMode('status')">状态记录</el-button>
      <el-button v-if="mode === 'transfer'" v-hasPermi="['case:transfer:add']" :size="controlSize" type="primary" icon="el-icon-refresh" @click="openTransfer()">发起转案</el-button>
      <el-button v-if="mode === 'lawyer'" v-hasPermi="['case:lawyer:config']" :size="controlSize" type="primary" icon="el-icon-setting" @click="openProfileConfig">律师配置</el-button>
    </biz-page-header>

    <biz-hero v-if="heroModes.includes(mode)" :eyebrow="heroMeta.eyebrow" :title="heroMeta.title" :description="heroMeta.description" />
    <biz-metrics v-if="heroModes.includes(mode)" :metrics="modeMetrics" :config="modeMetricConfig" />

    <biz-table-card v-if="mode === 'pending'" :show-search.sync="showSearch" :total="total" :page.sync="query.pageNum" :limit.sync="query.pageSize" @query="search" @pagination="loadCases">
        <template #filters>
          <div class="biz-filter-main">
            <el-input v-model="query.keyword" :size="controlSize" prefix-icon="el-icon-search" placeholder="搜索案件编号、客户、案件名称、律师姓名" clearable @clear="search" @keyup.enter.native="search" />
            <el-select v-model="query.caseType" :size="controlSize" placeholder="案件类型：全部" clearable @change="search"><el-option v-for="item in dict.type.law_case_type" :key="item.value" :label="item.label" :value="item.value" /></el-select>
            <el-select v-model="query.urgency" :size="controlSize" placeholder="紧急程度：全部" clearable @change="search"><el-option v-for="item in dict.type.law_case_urgency" :key="item.value" :label="item.label" :value="item.value" /></el-select>
            <el-select v-model="query.mainLawyerId" :size="controlSize" placeholder="推荐律师：全部" clearable filterable @change="search"><el-option v-for="item in lawyerOptions" :key="item.userId" :label="item.nickName" :value="item.userId" /></el-select>
            <el-input v-model="query.contractNo" :size="controlSize" prefix-icon="el-icon-document" placeholder="来源合同" clearable @clear="search" @keyup.enter.native="search" />
          </div>
          <div class="biz-filter-actions"><el-button :size="controlSize" plain icon="el-icon-refresh" @click="reset">重置</el-button></div>
        </template>
        <el-table v-loading="loading" :data="cases" :size="controlSize" @selection-change="selectedRows = $event">
          <el-table-column type="selection" width="42" align="center" />
          <el-table-column label="案件编号" prop="case_no" min-width="145" align="center" />
          <el-table-column label="案件信息" min-width="190">
            <template slot-scope="{ row }">
              <a class="biz-link" @click="openDetail(row)">{{ row.case_name }}</a>
              <span class="sub-text">{{ row.customer_name || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="案件类型" width="105" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_case_type" :value="row.case_type" /></template></el-table-column>
          <el-table-column label="紧急程度" width="105" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_case_urgency" :value="row.urgency" /></template></el-table-column>
          <el-table-column label="来源合同" prop="contract_no" min-width="135" align="center" />
          <el-table-column label="当前状态" width="105" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_case_status" :value="row.case_status" /></template></el-table-column>
          <el-table-column label="推荐律师" width="120" align="center"><template slot-scope="{ row }"><span class="owner-cell"><i>{{ avatar(row.main_lawyer_name || recommendedLawyerName) }}</i>{{ row.main_lawyer_name || recommendedLawyerName }}</span></template></el-table-column>
          <el-table-column label="预计工作量" width="110" align="center"><template slot-scope="{ row }">{{ row.estimated_workload || 0 }} 小时</template></el-table-column>
          <el-table-column label="创建时间" prop="create_time" width="150" align="center" />
          <el-table-column label="操作" width="210" align="center" class-name="small-padding fixed-width biz-operation-column" fixed="right">
            <template slot-scope="{ row }">
              <el-button v-hasPermi="['case:pending:query']" :size="controlSize" type="text" icon="el-icon-view" @click="openDetail(row)">查看</el-button>
              <el-button v-hasPermi="['case:status:list']" :size="controlSize" type="text" icon="el-icon-time" @click="openCaseFlow(row)">流转</el-button>
              <el-button v-hasPermi="['case:pending:assign']" :size="controlSize" type="text" icon="el-icon-user" @click="openAssign(row)">分配</el-button>
            </template>
          </el-table-column>
        </el-table>
    </biz-table-card>

    <biz-table-card v-else-if="mode === 'assign'" :show-search.sync="showSearch" :total="total" :page.sync="query.pageNum" :limit.sync="query.pageSize" @query="search" @pagination="loadAssignments">
      <template #filters>
        <div class="biz-filter-main">
          <el-input v-model="query.keyword" :size="controlSize" prefix-icon="el-icon-search" placeholder="搜索案件编号、名称、主办律师" clearable @clear="search" @keyup.enter.native="search" />
          <el-select v-model="query.mainLawyerId" :size="controlSize" placeholder="主办律师：全部" clearable filterable @change="search"><el-option v-for="item in lawyerOptions" :key="item.userId" :label="item.nickName" :value="item.userId" /></el-select>
        </div>
        <div class="biz-filter-actions"><el-button :size="controlSize" plain icon="el-icon-refresh" @click="reset">重置</el-button></div>
      </template>
      <el-table v-loading="loading" :data="assignments" :size="controlSize">
        <el-table-column label="案件编号" prop="caseNo" min-width="140" />
        <el-table-column label="案件名称" prop="caseName" min-width="180" show-overflow-tooltip />
        <el-table-column label="客户名称" prop="customerName" min-width="150" />
        <el-table-column label="主办律师" prop="main_lawyer_name" width="110" align="center" />
        <el-table-column label="协办律师" prop="assistant_lawyer_names" min-width="140" show-overflow-tooltip />
        <el-table-column label="优先级" width="90" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_case_priority" :value="row.priority" /></template></el-table-column>
        <el-table-column label="分配原因" width="110" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_case_assign_reason" :value="row.assign_reason" /></template></el-table-column>
        <el-table-column label="分配时间" prop="create_time" width="150" align="center" />
      </el-table>
    </biz-table-card>

    <biz-table-card v-else-if="mode === 'transfer'" :show-search.sync="showSearch" :total="total" :page.sync="query.pageNum" :limit.sync="query.pageSize" @query="search" @pagination="loadTransfers">
        <template #filters>
          <div class="biz-filter-main">
            <el-input v-model="query.keyword" :size="controlSize" prefix-icon="el-icon-search" placeholder="搜索转案单号、案件、律师" clearable @clear="search" @keyup.enter.native="search" />
            <el-select v-model="query.riskLevel" :size="controlSize" placeholder="风险等级：全部" clearable @change="search"><el-option v-for="item in dict.type.law_case_risk_level" :key="item.value" :label="item.label" :value="item.value" /></el-select>
            <el-select v-model="query.transferStatus" :size="controlSize" placeholder="审批状态：全部" clearable @change="search"><el-option v-for="item in dict.type.law_case_transfer_status" :key="item.value" :label="item.label" :value="item.value" /></el-select>
          </div>
          <div class="biz-filter-actions"><el-button :size="controlSize" plain icon="el-icon-refresh" @click="reset">重置</el-button></div>
        </template>
        <el-table v-loading="loading" :data="transfers" :size="controlSize">
          <el-table-column label="转案单号" prop="transfer_no" min-width="150" />
          <el-table-column label="案件编号" prop="caseNo" min-width="140" />
          <el-table-column label="案件名称" prop="caseName" min-width="170" show-overflow-tooltip />
          <el-table-column label="当前律师" prop="from_lawyer_name" width="110" align="center" />
          <el-table-column label="拟转入律师" prop="to_lawyer_name" width="120" align="center" />
          <el-table-column label="风险等级" width="100" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_case_risk_level" :value="row.risk_level" /></template></el-table-column>
          <el-table-column label="当前节点" prop="current_node" min-width="120" />
          <el-table-column label="操作" width="150" align="center" class-name="small-padding fixed-width">
            <template slot-scope="{ row }">
              <el-button :size="controlSize" type="text" @click="openApproval(row)">查看</el-button>
              <el-button v-if="row.transfer_status === 'pending'" v-hasPermi="['case:transfer:approve']" :size="controlSize" type="text" @click="openApproval(row)">审批</el-button>
            </template>
          </el-table-column>
        </el-table>
    </biz-table-card>

    <biz-table-card v-else-if="mode === 'lawyer'" :show-search.sync="showSearch" :total="total" :page.sync="query.pageNum" :limit.sync="query.pageSize" @query="search" @pagination="loadLawyers">
        <template #filters>
          <div class="biz-filter-main">
            <el-input v-model="query.keyword" :size="controlSize" prefix-icon="el-icon-search" placeholder="搜索律师姓名、团队、专业方向" clearable @clear="search" @keyup.enter.native="search" />
            <el-select v-model="query.lawyerRole" :size="controlSize" placeholder="律师角色：全部" clearable @change="search"><el-option v-for="item in dict.type.law_lawyer_role" :key="item.value" :label="item.label" :value="item.value" /></el-select>
            <el-select v-model="query.specialty" :size="controlSize" placeholder="专业方向：全部" clearable @change="search"><el-option v-for="item in dict.type.law_case_type" :key="item.value" :label="item.label" :value="item.value" /></el-select>
          </div>
          <div class="biz-filter-actions"><el-button :size="controlSize" plain icon="el-icon-refresh" @click="reset">重置</el-button></div>
        </template>
        <el-table v-loading="loading" :data="lawyerLoads" :size="controlSize">
          <el-table-column label="律师姓名" min-width="120"><template slot-scope="{ row }"><span class="owner-cell"><i>{{ avatar(row.lawyerName) }}</i>{{ row.lawyerName }}</span></template></el-table-column>
          <el-table-column label="角色" width="100" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_lawyer_role" :value="row.lawyerRole" /></template></el-table-column>
          <el-table-column label="所属团队" prop="deptName" min-width="130" />
          <el-table-column label="专业方向" min-width="160"><template slot-scope="{ row }"><span class="tag-pills"><i v-for="item in specialtyList(row.specialties)" :key="item">{{ dictLabel('law_case_type', item) }}</i></span></template></el-table-column>
          <el-table-column label="当前负载" width="150"><template slot-scope="{ row }"><el-progress :percentage="Number(row.loadRate || 0)" :color="loadColor(row.loadRate)" /></template></el-table-column>
          <el-table-column label="在办案件" prop="activeCases" width="90" align="center" />
          <el-table-column label="本月分案" prop="monthAssigned" width="90" align="center" />
          <el-table-column label="平均响应" width="100" align="center"><template slot-scope="{ row }">{{ row.avgResponseHours || 0 }}h</template></el-table-column>
          <el-table-column label="匹配倾向" width="105" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_lawyer_match_level" :value="matchLevel(row)" /></template></el-table-column>
          <el-table-column label="状态" width="100" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_lawyer_load_status" :value="loadStatus(row.loadRate)" /></template></el-table-column>
          <el-table-column label="操作" width="135" align="center" class-name="small-padding fixed-width">
            <template slot-scope="{ row }">
              <el-button :size="controlSize" type="text" @click="openLawyerDetail(row)">查看详情</el-button>
              <el-button v-hasPermi="['case:lawyer:config']" :size="controlSize" type="text" @click="openProfileForm(row)">编辑档案</el-button>
              <el-button v-hasPermi="['case:pending:assign']" :size="controlSize" type="text" @click="assignRecommended(row)">分配</el-button>
            </template>
          </el-table-column>
        </el-table>
    </biz-table-card>

    <biz-table-card v-else-if="mode === 'confirm'" :show-search.sync="showSearch" :total="total" :page.sync="query.pageNum" :limit.sync="query.pageSize" @query="search" @pagination="loadConfirms">
      <template #filters>
        <div class="biz-filter-main">
          <el-input v-model="query.keyword" :size="controlSize" prefix-icon="el-icon-search" placeholder="搜索案件、确认人" clearable @clear="search" @keyup.enter.native="search" />
          <el-select v-model="query.confirmStatus" :size="controlSize" placeholder="确认状态：全部" clearable @change="search"><el-option v-for="item in dict.type.law_case_confirm_status" :key="item.value" :label="item.label" :value="item.value" /></el-select>
        </div>
        <div class="biz-filter-actions"><el-button :size="controlSize" plain icon="el-icon-refresh" @click="reset">重置</el-button></div>
      </template>
      <el-table v-loading="loading" :data="confirms" :size="controlSize">
        <el-table-column label="案件编号" prop="caseNo" min-width="140" />
        <el-table-column label="案件名称" prop="caseName" min-width="180" />
        <el-table-column label="确认人" prop="confirm_user_name" width="120" align="center" />
        <el-table-column label="确认内容" prop="content" min-width="220" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_case_confirm_status" :value="row.confirm_status" /></template></el-table-column>
        <el-table-column label="操作" width="170" align="center" class-name="small-padding fixed-width">
          <template slot-scope="{ row }">
            <el-button v-hasPermi="['case:confirm:handle']" :size="controlSize" type="text" :disabled="row.confirm_status !== 'pending'" @click="handleConfirmRow(row, 'accepted')">确认接收</el-button>
            <el-button v-hasPermi="['case:confirm:handle']" :size="controlSize" type="text" :disabled="row.confirm_status !== 'pending'" @click="handleConfirmRow(row, 'rejected')">拒绝</el-button>
          </template>
        </el-table-column>
      </el-table>
    </biz-table-card>

    <div v-else-if="mode === 'status'" class="table-card timeline-page">
      <div class="section-title"><h3>状态记录</h3><p>记录案件生成、分案、确认、转案审批等关键节点</p></div>
      <div v-loading="loading" class="biz-timeline-list two-column status-list">
        <article v-for="item in statuses" :key="item.log_id || item.create_time">
          <i class="timeline-icon el-icon-time" />
          <div><h4>{{ item.caseNo || '-' }} <span>{{ dictLabel('law_case_status_action', item.action_type) }}</span></h4><p>{{ item.content || '-' }}</p><small>{{ item.create_by || '-' }} · {{ item.create_time || '-' }}</small></div>
        </article>
        <el-empty v-if="!statuses.length" description="暂无状态记录" />
      </div>
      <pagination v-show="total > 0" :total="total" :page.sync="query.pageNum" :limit.sync="query.pageSize" @pagination="loadStatuses" />
    </div>

    <el-drawer :visible.sync="transferDetailOpen" size="520px" custom-class="case-transfer-drawer" append-to-body>
      <div slot="title" class="drawer-title" :class="'biz-size-' + appSize">
        <div>
          <span>转案详情</span>
          <h3>{{ approvalTransfer.caseName || '-' }}</h3>
          <p>{{ approvalTransfer.transfer_no || '-' }} · {{ approvalTransfer.caseNo || '-' }}</p>
        </div>
      </div>
      <div class="transfer-drawer-body" :class="'biz-size-' + appSize">
        <section class="drawer-card">
          <div class="section-title"><h3>转案申请信息</h3><p>查看申请来源、风险等级与当前节点</p></div>
          <dl class="drawer-kv">
            <div><dt>申请人</dt><dd>{{ approvalTransfer.applicant_name || '-' }}</dd></div>
            <div><dt>申请时间</dt><dd>{{ approvalTransfer.create_time || '-' }}</dd></div>
            <div><dt>转案原因</dt><dd>{{ dictLabel('law_case_transfer_reason', approvalTransfer.transfer_reason) }}</dd></div>
            <div><dt>风险等级</dt><dd><dict-tag :options="dict.type.law_case_risk_level" :value="approvalTransfer.risk_level" /></dd></div>
            <div><dt>当前节点</dt><dd>{{ approvalTransfer.current_node || '-' }}</dd></div>
            <div><dt>审批状态</dt><dd><dict-tag :options="dict.type.law_case_transfer_status" :value="approvalTransfer.transfer_status" /></dd></div>
          </dl>
        </section>
        <section class="drawer-card">
          <div class="section-title"><h3>律师信息</h3><p>当前承办律师与拟转入律师对比</p></div>
          <div class="lawyer-compare">
            <span>{{ approvalTransfer.from_lawyer_name || '-' }}</span>
            <i class="el-icon-right" />
            <span>{{ approvalTransfer.to_lawyer_name || '-' }}</span>
          </div>
        </section>
        <section class="drawer-card">
          <div class="section-title"><h3>转案详情</h3><p>转案说明与审批处理意见</p></div>
          <p class="approval-detail-text">{{ approvalTransfer.detail || '-' }}</p>
          <div v-if="approvalTransfer.transfer_status !== 'pending'" class="approval-result">
            {{ dictLabel('law_case_transfer_status', approvalTransfer.transfer_status) }}：{{ approvalTransfer.approval_opinion || '-' }}
          </div>
        </section>
        <section class="drawer-card">
          <div class="section-title"><h3>审批流程</h3><p>当前转案审批流转节点</p></div>
          <div class="approval-flow">
            <span class="done">转案申请</span>
            <span class="active">{{ approvalTransfer.current_node || '审批中' }}</span>
            <span>案件办理</span>
          </div>
        </section>
        <section v-if="approvalTransfer.transfer_status === 'pending'" class="drawer-card">
          <div class="section-title"><h3>审批意见</h3><p>选择审批结果并填写处理意见</p></div>
          <el-form ref="transferApprovalFormRef" :model="approvalForm" :rules="approvalRules" label-width="88px">
            <el-form-item label="审批动作" prop="action">
              <el-radio-group v-model="approvalForm.action" :size="controlSize" class="approval-action-group">
                <el-radio-button label="passed">同意转案</el-radio-button>
                <el-radio-button label="rejected">驳回</el-radio-button>
                <el-radio-button label="supplement">补充材料</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="审批意见" prop="opinion">
              <el-input v-model="approvalForm.opinion" :size="controlSize" type="textarea" :rows="5" placeholder="请填写审批意见" />
            </el-form-item>
          </el-form>
        </section>
      </div>
      <div v-if="approvalTransfer.transfer_status === 'pending'" class="drawer-footer">
        <el-button :size="controlSize" @click="transferDetailOpen = false">取消</el-button>
        <el-button v-hasPermi="['case:transfer:approve']" :size="controlSize" type="primary" @click="saveApproval">提交审批</el-button>
      </div>
    </el-drawer>

    <case-detail-drawer
      :visible.sync="detailOpen"
      :case-data="detailCase"
      :size-class="'biz-size-' + appSize"
      @assign="openAssign"
      @flow="openCaseFlow"
    />

    <case-flow-drawer
      :visible.sync="flowOpen"
      :case-data="flowCase"
      :statuses="flowStatuses"
      :loading="flowLoading"
      :size-class="'biz-size-' + appSize"
    />

    <el-drawer :visible.sync="lawyerDetailOpen" size="560px" custom-class="case-lawyer-detail-drawer" append-to-body>
      <div slot="title" class="drawer-title" :class="'biz-size-' + appSize">
        <div>
          <span>LAWYER LOAD</span>
          <h3>{{ lawyerDetail.lawyerName || lawyerDetail.nickName || '-' }}</h3>
          <p>{{ lawyerDetail.deptName || '-' }} · {{ dictLabel('law_lawyer_role', lawyerDetail.lawyerRole) }}</p>
        </div>
      </div>
      <div class="lawyer-detail-body" :class="'biz-size-' + appSize">
        <section class="lawyer-profile-hero">
          <i>{{ avatar(lawyerDetail.lawyerName || lawyerDetail.nickName) }}</i>
          <div>
            <h3>{{ lawyerDetail.lawyerName || lawyerDetail.nickName || '-' }}</h3>
            <p>{{ lawyerDetail.deptName || '未设置部门' }}</p>
            <span :class="['load-badge', loadStatus(lawyerDetail.loadRate)]">{{ dictLabel('law_lawyer_load_status', loadStatus(lawyerDetail.loadRate)) }}</span>
          </div>
        </section>

        <section class="drawer-card">
          <div class="section-title"><h3>负载概览</h3><p>结合当前在办案件、月度分案与负载上限评估承接能力</p></div>
          <div class="lawyer-load-ring">
            <el-progress type="circle" :percentage="Number(lawyerDetail.loadRate || 0)" :width="112" :color="loadColor(lawyerDetail.loadRate)" />
            <dl>
              <div><dt>当前负载</dt><dd>{{ lawyerDetail.currentLoad || 0 }} / {{ lawyerDetail.loadLimit || 100 }}</dd></div>
              <div><dt>在办案件</dt><dd>{{ lawyerDetail.activeCases || 0 }} 件</dd></div>
              <div><dt>本月分案</dt><dd>{{ lawyerDetail.monthAssigned || 0 }} 件</dd></div>
              <div><dt>平均响应</dt><dd>{{ lawyerDetail.avgResponseHours || 0 }}h</dd></div>
            </dl>
          </div>
        </section>

        <section class="drawer-card">
          <div class="section-title"><h3>专业与分案资格</h3><p>主办律师不允许选择实习律师，协办律师可选择实习律师</p></div>
          <dl class="drawer-kv">
            <div><dt>业务角色</dt><dd><dict-tag :options="dict.type.law_lawyer_role" :value="lawyerDetail.lawyerRole" /></dd></div>
            <div><dt>可分案</dt><dd>{{ lawyerDetail.assignEnabled === 'N' ? '否' : '是' }}</dd></div>
            <div><dt>匹配倾向</dt><dd><dict-tag :options="dict.type.law_lawyer_match_level" :value="matchLevel(lawyerDetail)" /></dd></div>
            <div><dt>主办资格</dt><dd>{{ lawyerDetail.lawyerRole === 'assistant' ? '不可作为主办' : '可作为主办' }}</dd></div>
          </dl>
          <div class="detail-specialties">
            <span v-for="item in specialtyList(lawyerDetail.specialties)" :key="item">{{ dictLabel('law_case_type', item) }}</span>
            <em v-if="!specialtyList(lawyerDetail.specialties).length">未配置专业方向</em>
          </div>
        </section>

        <section class="drawer-card">
          <div class="section-title"><h3>分案建议</h3><p>基于负载率生成轻量提示，不替代案管员判断</p></div>
          <div class="lawyer-suggestion" :class="loadStatus(lawyerDetail.loadRate)">
            {{ lawyerSuggestion(lawyerDetail) }}
          </div>
        </section>
      </div>
      <div class="drawer-footer">
        <el-button v-hasPermi="['case:lawyer:config']" :size="controlSize" plain icon="el-icon-setting" @click="openProfileForm(lawyerDetail)">编辑档案</el-button>
        <el-button v-hasPermi="['case:pending:assign']" :size="controlSize" type="primary" icon="el-icon-user" :disabled="lawyerDetail.lawyerRole === 'assistant' || lawyerDetail.assignEnabled === 'N'" @click="assignRecommended(lawyerDetail)">分配案件</el-button>
      </div>
    </el-drawer>

    <el-dialog :title="assignForm.batch ? '批量分配案件' : '案件分配'" :visible.sync="assignOpen" width="980px" :custom-class="dialogClass" append-to-body>
      <div class="assign-dialog-grid">
        <section>
          <div class="case-summary">
            <span>案件编号<b>{{ assignCaseRow.case_no }}</b></span>
            <span>案件名称<b>{{ assignCaseRow.case_name }}</b></span>
            <span>客户名称<b>{{ assignCaseRow.customer_name }}</b></span>
            <span>案件类型<b>{{ dictLabel('law_case_type', assignCaseRow.case_type) }}</b></span>
            <span>紧急程度<b>{{ dictLabel('law_case_urgency', assignCaseRow.urgency) }}</b></span>
            <span>来源合同<b>{{ assignCaseRow.contract_no }}</b></span>
            <span v-if="assignForm.batch">批量数量<b>{{ assignForm.caseIds.length }} 件</b></span>
          </div>
          <el-form ref="assignFormRef" :model="assignForm" :rules="assignRules" label-width="110px">
            <el-form-item label="分配方式" prop="assignMethod"><el-radio-group v-model="assignForm.assignMethod"><el-radio v-for="item in dict.type.law_case_assign_method" :key="item.value" :label="item.value">{{ item.label }}</el-radio></el-radio-group></el-form-item>
            <el-form-item label="主办律师" prop="mainLawyerId"><el-select v-model="assignForm.mainLawyerId" :size="controlSize" filterable placeholder="请选择主办律师"><el-option v-for="item in mainLawyerOptions" :key="item.userId" :label="item.nickName" :value="item.userId" /></el-select></el-form-item>
            <el-form-item label="协办律师"><el-select v-model="assignForm.assistantLawyerIds" :size="controlSize" multiple filterable placeholder="请选择协办律师"><el-option v-for="item in assistantLawyerOptions" :key="item.userId" :label="item.nickName" :value="item.userId" /></el-select></el-form-item>
            <el-row :gutter="12">
              <el-col :span="12"><el-form-item label="优先级" prop="priority"><el-select v-model="assignForm.priority" :size="controlSize"><el-option v-for="item in dict.type.law_case_priority" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="预计周期"><el-input v-model="assignForm.estimatedCycle" :size="controlSize" placeholder="如 7 天" /></el-form-item></el-col>
            </el-row>
            <el-row :gutter="12">
              <el-col :span="12"><el-form-item label="开案日期"><el-date-picker v-model="assignForm.planStartDate" :size="controlSize" value-format="yyyy-MM-dd" type="date" placeholder="请选择" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="预计工作量"><el-input-number v-model="assignForm.estimatedWorkload" :size="controlSize" :min="1" /></el-form-item></el-col>
            </el-row>
            <el-form-item label="分配原因" prop="assignReason"><el-select v-model="assignForm.assignReason" :size="controlSize"><el-option v-for="item in dict.type.law_case_assign_reason" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
            <el-form-item label="备注"><el-input v-model="assignForm.remark" :size="controlSize" type="textarea" :rows="3" maxlength="200" show-word-limit /></el-form-item>
            <el-form-item label="站内通知"><el-radio-group v-model="assignForm.notifyFlag"><el-radio label="Y">是</el-radio><el-radio label="N">否</el-radio></el-radio-group></el-form-item>
          </el-form>
        </section>
        <section class="lawyer-reference">
          <header><b>人员参考</b><el-button :size="controlSize" type="text" @click="loadLawyers">换一批</el-button></header>
          <article v-for="item in mainLawyerOptions.slice(0, 5)" :key="item.userId" :class="{ active: sameValue(assignForm.mainLawyerId, item.userId) }" @click="selectAssignLawyer(item)">
            <span class="owner-cell"><i>{{ avatar(item.lawyerName) }}</i>{{ item.lawyerName }}</span>
            <dict-tag :options="dict.type.law_lawyer_role" :value="item.lawyerRole" />
            <span class="tag-pills compact"><i v-for="specialty in specialtyList(item.specialties)" :key="specialty">{{ dictLabel('law_case_type', specialty) }}</i></span>
            <el-progress :percentage="Number(item.loadRate || 0)" :color="loadColor(item.loadRate)" />
            <small>在办 {{ item.activeCases || 0 }} · 本月分案 {{ item.monthAssigned || 0 }} · {{ dictLabel('law_lawyer_match_level', matchLevel(item)) }}</small>
          </article>
        </section>
      </div>
      <div slot="footer"><el-button :size="controlSize" @click="assignOpen = false">取消</el-button><el-button :size="controlSize" type="primary" @click="saveAssign">确认分案</el-button></div>
    </el-dialog>

    <el-dialog title="发起转案" :visible.sync="transferOpen" width="620px" :custom-class="dialogClass" append-to-body>
      <el-form ref="transferFormRef" :model="transferForm" :rules="transferRules" label-width="110px">
        <el-form-item label="案件" prop="caseId"><el-select v-model="transferForm.caseId" :size="controlSize" filterable remote reserve-keyword placeholder="搜索并选择办理中案件" :remote-method="searchProcessingCases"><el-option v-for="item in processingCases" :key="item.case_id" :label="item.case_name" :value="item.case_id"><span>{{ item.case_name }}</span><span class="select-sub">{{ item.case_no }} · {{ item.main_lawyer_name }}</span></el-option></el-select></el-form-item>
        <el-form-item label="拟转入律师" prop="toLawyerId"><el-select v-model="transferForm.toLawyerId" :size="controlSize" filterable placeholder="请选择律师"><el-option v-for="item in mainLawyerOptions" :key="item.userId" :label="item.nickName" :value="item.userId" /></el-select></el-form-item>
        <el-form-item label="转案原因" prop="transferReason"><el-select v-model="transferForm.transferReason" :size="controlSize"><el-option v-for="item in dict.type.law_case_transfer_reason" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="风险等级" prop="riskLevel"><el-select v-model="transferForm.riskLevel" :size="controlSize"><el-option v-for="item in dict.type.law_case_risk_level" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="转案详情" prop="detail"><el-input v-model="transferForm.detail" :size="controlSize" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <div slot="footer"><el-button :size="controlSize" @click="transferOpen = false">取消</el-button><el-button :size="controlSize" type="primary" @click="saveTransfer">提交</el-button></div>
    </el-dialog>

    <el-drawer :visible.sync="profileOpen" size="780px" custom-class="case-profile-drawer" append-to-body>
      <div slot="title" class="drawer-title" :class="'biz-size-' + appSize">
        <div>
          <span>LAWYER PROFILE</span>
          <h3>律师档案配置</h3>
          <p>维护业务角色、专业方向、负载上限与是否可分案</p>
        </div>
      </div>
      <div class="profile-drawer-body" :class="'biz-size-' + appSize">
        <section class="drawer-card">
          <div class="profile-toolbar">
            <div class="profile-search">
              <el-input v-model="profileQuery.keyword" :size="controlSize" prefix-icon="el-icon-search" placeholder="搜索律师、账号、部门或专业方向" clearable @clear="loadProfiles" @keyup.enter.native="loadProfiles" />
              <el-button :size="controlSize" type="primary" icon="el-icon-search" @click="loadProfiles">搜索</el-button>
            </div>
            <div class="profile-quick-tabs">
              <button :class="{ active: !profileQuery.assignEnabled }" type="button" @click="setProfileAssignable('')">全部</button>
              <button :class="{ active: profileQuery.assignEnabled === 'Y' }" type="button" @click="setProfileAssignable('Y')">可分案</button>
              <button :class="{ active: profileQuery.assignEnabled === 'N' }" type="button" @click="setProfileAssignable('N')">已停分案</button>
            </div>
            <el-popover placement="bottom-end" width="360" trigger="click" popper-class="profile-filter-popover">
              <div class="profile-advanced-filter">
                <label>业务角色</label>
                <el-select v-model="profileQuery.lawyerRole" :size="controlSize" placeholder="全部角色" clearable @change="loadProfiles"><el-option v-for="item in dict.type.law_lawyer_role" :key="item.value" :label="item.label" :value="item.value" /></el-select>
                <label>专业方向</label>
                <el-select v-model="profileQuery.specialty" :size="controlSize" placeholder="全部专业" clearable @change="loadProfiles"><el-option v-for="item in dict.type.law_case_type" :key="item.value" :label="item.label" :value="item.value" /></el-select>
                <div class="profile-advanced-actions">
                  <el-button :size="controlSize" plain icon="el-icon-refresh" @click="resetProfileQuery">重置筛选</el-button>
                </div>
              </div>
              <el-button slot="reference" :size="controlSize" plain icon="el-icon-s-operation">高级筛选</el-button>
            </el-popover>
          </div>
          <el-table v-loading="profileLoading" :data="profileList" :size="controlSize">
            <el-table-column label="律师" min-width="130"><template slot-scope="{ row }"><span class="owner-cell"><i>{{ avatar(row.nickName) }}</i>{{ row.nickName }}</span><span class="sub-text">{{ row.userName }}</span></template></el-table-column>
            <el-table-column label="部门" prop="deptName" min-width="120" show-overflow-tooltip />
            <el-table-column label="业务角色" width="100" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_lawyer_role" :value="row.lawyerRole" /></template></el-table-column>
            <el-table-column label="专业方向" min-width="150"><template slot-scope="{ row }"><span class="tag-pills"><i v-for="item in specialtyList(row.specialties)" :key="item">{{ dictLabel('law_case_type', item) }}</i></span></template></el-table-column>
            <el-table-column label="负载上限" prop="loadLimit" width="90" align="center" />
            <el-table-column label="平均响应" width="90" align="center"><template slot-scope="{ row }">{{ row.avgResponseHours || 0 }}h</template></el-table-column>
            <el-table-column label="可分案" width="90" align="center">
              <template slot-scope="{ row }">
                <el-switch v-model="row.assignEnabled" active-value="Y" inactive-value="N" @change="changeProfileStatus(row)" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90" align="center" class-name="small-padding fixed-width">
              <template slot-scope="{ row }"><el-button :size="controlSize" type="text" @click="openProfileForm(row)">编辑</el-button></template>
            </el-table-column>
          </el-table>
          <pagination v-show="profileTotal > 0" :total="profileTotal" :page.sync="profileQuery.pageNum" :limit.sync="profileQuery.pageSize" @pagination="loadProfiles" />
        </section>
      </div>
    </el-drawer>

    <el-dialog title="编辑律师档案" :visible.sync="profileFormOpen" width="560px" :custom-class="dialogClass" append-to-body>
      <el-form ref="profileFormRef" :model="profileForm" :rules="profileRules" label-width="110px">
        <el-form-item label="律师"><el-input :value="profileForm.nickName || profileForm.lawyerName || profileForm.userName" :size="controlSize" disabled /></el-form-item>
        <el-form-item label="业务角色" prop="lawyerRole"><el-select v-model="profileForm.lawyerRole" :size="controlSize" placeholder="请选择业务角色"><el-option v-for="item in dict.type.law_lawyer_role" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="专业方向" prop="specialties"><el-select v-model="profileForm.specialties" :size="controlSize" multiple placeholder="请选择专业方向"><el-option v-for="item in dict.type.law_case_type" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="负载上限" prop="loadLimit"><el-input-number v-model="profileForm.loadLimit" :size="controlSize" :min="1" :max="999" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="平均响应" prop="avgResponseHours"><el-input-number v-model="profileForm.avgResponseHours" :size="controlSize" :min="0" :max="999" :precision="1" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="是否可分案" prop="assignEnabled"><el-radio-group v-model="profileForm.assignEnabled"><el-radio label="Y">是</el-radio><el-radio label="N">否</el-radio></el-radio-group></el-form-item>
        <el-form-item label="备注"><el-input v-model="profileForm.remark" :size="controlSize" type="textarea" :rows="3" maxlength="200" show-word-limit /></el-form-item>
      </el-form>
      <div slot="footer"><el-button :size="controlSize" @click="profileFormOpen = false">取消</el-button><el-button :size="controlSize" type="primary" @click="saveProfile">保存</el-button></div>
    </el-dialog>
  </div>
</template>

<script>
import businessUi from '@/views/business/mixins/businessUi'
import BizHero from '@/views/business/components/BizHero'
import BizMetrics from '@/views/business/components/BizMetrics'
import BizPageHeader from '@/views/business/components/BizPageHeader'
import BizTableCard from '@/views/business/components/BizTableCard'
import CaseDetailDrawer from './components/CaseDetailDrawer'
import CaseFlowDrawer from './components/CaseFlowDrawer'
import { getCaseDashboard, listCaseLawyer, listCase, getCase, assignCase, batchAssignCase, listAssignment, listLawyerLoad, listLawyerSpecialty, updateLawyerProfile, updateLawyerProfileStatus, listTransfer, requestTransfer, approveTransfer, listConfirm, handleConfirm, listCaseStatus } from '@/api/case'

export default {
  name: 'CaseCenter',
  mixins: [businessUi],
  components: { BizHero, BizMetrics, BizPageHeader, BizTableCard, CaseDetailDrawer, CaseFlowDrawer },
  dicts: ['law_case_type', 'law_case_urgency', 'law_case_status', 'law_case_priority', 'law_case_assign_method', 'law_case_assign_reason', 'law_lawyer_role', 'law_lawyer_match_level', 'law_lawyer_load_status', 'law_case_transfer_status', 'law_case_transfer_reason', 'law_case_risk_level', 'law_case_status_action', 'law_case_confirm_status'],
  data() {
    return {
      mode: 'pending',
      showSearch: true,
      loading: false,
      total: 0,
      query: this.defaultQuery(),
      cases: [],
      assignments: [],
      transfers: [],
      lawyerLoads: [],
      specialtyStats: [],
      confirms: [],
      statuses: [],
      selectedRows: [],
      lawyerOptions: [],
      processingCases: [],
      metrics: [],
      assignOpen: false,
      assignCaseRow: {},
      assignForm: {},
      transferOpen: false,
      transferForm: {},
      transferDetailOpen: false,
      approvalTransfer: {},
      approvalForm: {},
      detailOpen: false,
      detailCase: {},
      lawyerDetailOpen: false,
      lawyerDetail: {},
      flowOpen: false,
      flowCase: {},
      flowStatuses: [],
      flowLoading: false,
      profileOpen: false,
      profileFormOpen: false,
      profileLoading: false,
      profileList: [],
      profileTotal: 0,
      profileQuery: { pageNum: 1, pageSize: 10 },
      profileForm: {},
      assignRules: {
        mainLawyerId: [{ required: true, message: '请选择主办律师', trigger: 'change' }],
        assignMethod: [{ required: true, message: '请选择分配方式', trigger: 'change' }],
        priority: [{ required: true, message: '请选择优先级', trigger: 'change' }],
        assignReason: [{ required: true, message: '请选择分配原因', trigger: 'change' }]
      },
      transferRules: {
        caseId: [{ required: true, message: '请选择案件', trigger: 'change' }],
        toLawyerId: [{ required: true, message: '请选择拟转入律师', trigger: 'change' }],
        transferReason: [{ required: true, message: '请选择转案原因', trigger: 'change' }],
        riskLevel: [{ required: true, message: '请选择风险等级', trigger: 'change' }],
        detail: [{ required: true, message: '请输入转案详情', trigger: 'blur' }]
      },
      approvalRules: {
        action: [{ required: true, message: '请选择审批动作', trigger: 'change' }],
        opinion: [{ required: true, message: '请输入审批意见', trigger: 'blur' }]
      },
      profileRules: {
        lawyerRole: [{ required: true, message: '请选择业务角色', trigger: 'change' }],
        specialties: [{ required: true, message: '请选择专业方向', trigger: 'change' }],
        loadLimit: [{ required: true, message: '请输入负载上限', trigger: 'blur' }],
        avgResponseHours: [{ required: true, message: '请输入平均响应时长', trigger: 'blur' }],
        assignEnabled: [{ required: true, message: '请选择是否可分案', trigger: 'change' }]
      }
    }
  },
  computed: {
    pageTitle() {
      const titles = { pending: '待分案列表', assign: '分案记录', transfer: '转案审批', lawyer: '律师负载', confirm: '待确认信息', status: '状态记录' }
      return titles[this.mode] || '案管中心'
    },
    pageDescription() {
      const descriptions = {
        pending: '集中管理待分案案件，支持筛选、查询与分案，合理匹配专业律师。',
        assign: '沉淀案件分配记录，追踪主办律师、协办律师与分配原因。',
        transfer: '管理律师转案申请，规范转案流程与风险控制。',
        lawyer: '动态掌握律师负载，辅助案管员合理分配案件资源。',
        confirm: '处理律师接案确认与资料补充确认。',
        status: '追踪案件关键状态流转记录。'
      }
      return descriptions[this.mode] || descriptions.pending
    },
    heroModes() {
      return ['pending', 'assign', 'transfer', 'lawyer', 'confirm']
    },
    heroMeta() {
      const metas = {
        pending: { eyebrow: '案件调度', title: '高效协调分案流转，提升办案协同效率', description: '精准匹配合适律师，均衡工作负载，全程跟踪流转状态。' },
        assign: { eyebrow: '分案记录', title: '沉淀分案过程，提升案件调度可追溯性', description: '集中记录主办律师、协办律师、分配原因与分配时间，方便后续复盘。' },
        transfer: { eyebrow: '转案风控', title: '规范处理转案申请，确保案件平稳流转', description: '多维风险识别，审批意见留痕，保障案件合规流转。' },
        lawyer: { eyebrow: '律师负载', title: '动态掌握律师负载，提升分案均衡度', description: '通过负载数据实时洞察律师工作状态，合理分配案件。' },
        confirm: { eyebrow: '接案确认', title: '集中处理确认消息，保证分案闭环', description: '跟进律师接案确认、拒绝与资料补充处理，避免案件流转停滞。' }
      }
      return metas[this.mode] || metas.pending
    },
    modeMetrics() {
      if (this.mode === 'pending') return this.metrics
      if (this.mode === 'assign') {
        return [
          { metricKey: 'assignTotal', metricValue: this.total || this.assignments.length },
          { metricKey: 'assignToday', metricValue: this.assignments.filter(item => String(item.create_time || '').slice(0, 10) === new Date().toISOString().slice(0, 10)).length },
          { metricKey: 'mainLawyers', metricValue: new Set(this.assignments.map(item => item.main_lawyer_name).filter(Boolean)).size },
          { metricKey: 'batchAssign', metricValue: this.assignments.filter(item => String(item.remark || '').indexOf('批量') > -1).length },
          { metricKey: 'avgWorkload', metricValue: Math.round(this.assignments.reduce((total, item) => total + Number(item.estimated_workload || 0), 0) / (this.assignments.length || 1)) + 'h' }
        ]
      }
      if (this.mode === 'lawyer') {
        return [
          { metricKey: 'totalLawyers', metricValue: this.lawyerLoads.length },
          { metricKey: 'highLoad', metricValue: this.lawyerLoads.filter(item => Number(item.loadRate || 0) >= 80).length },
          { metricKey: 'idle', metricValue: this.lawyerLoads.filter(item => Number(item.loadRate || 0) < 20).length },
          { metricKey: 'monthAssigned', metricValue: this.lawyerLoads.reduce((total, item) => total + Number(item.monthAssigned || 0), 0) },
          { metricKey: 'avgLoad', metricValue: Math.round(this.lawyerLoads.reduce((total, item) => total + Number(item.loadRate || 0), 0) / (this.lawyerLoads.length || 1)) + '%' }
        ]
      }
      if (this.mode === 'confirm') {
        return [
          { metricKey: 'confirmTotal', metricValue: this.total || this.confirms.length },
          { metricKey: 'confirmPending', metricValue: this.confirms.filter(item => item.confirm_status === 'pending').length },
          { metricKey: 'confirmAccepted', metricValue: this.confirms.filter(item => item.confirm_status === 'accepted').length },
          { metricKey: 'confirmRejected', metricValue: this.confirms.filter(item => item.confirm_status === 'rejected').length },
          { metricKey: 'confirmToday', metricValue: this.confirms.filter(item => String(item.create_time || '').slice(0, 10) === new Date().toISOString().slice(0, 10)).length }
        ]
      }
      return [
        { metricKey: 'pendingTransfer', metricValue: this.transfers.filter(item => item.transfer_status === 'pending').length },
        { metricKey: 'todayApply', metricValue: this.transfers.filter(item => String(item.create_time || '').slice(0, 10) === new Date().toISOString().slice(0, 10)).length },
        { metricKey: 'passed', metricValue: this.transfers.filter(item => item.transfer_status === 'passed').length },
        { metricKey: 'rejected', metricValue: this.transfers.filter(item => item.transfer_status === 'rejected').length },
        { metricKey: 'highRisk', metricValue: this.transfers.filter(item => item.risk_level === 'high').length }
      ]
    },
    modeMetricConfig() {
      const configs = {
        pending: { label: '待分案案件', icon: 'peoples', color: 'blue' },
        todayNew: { label: '今日新转入', icon: 'documentation', color: 'cyan' },
        transferPending: { label: '待审批转案', icon: 'validCode', color: 'orange' },
        highLoadLawyers: { label: '高负载律师', icon: 'peoples', color: 'violet' },
        avgAssignDays: { label: '平均分案时长', icon: 'time', color: 'green' },
        totalLawyers: { label: '律师总数', icon: 'peoples', color: 'blue' },
        highLoad: { label: '高负载律师', icon: 'peoples', color: 'orange' },
        idle: { label: '空闲律师', icon: 'peoples', color: 'green' },
        monthAssigned: { label: '本月已分案', icon: 'documentation', color: 'violet' },
        avgLoad: { label: '平均负载率', icon: 'time', color: 'green' },
        pendingTransfer: { label: '待审批转案', icon: 'peoples', color: 'blue' },
        todayApply: { label: '今日新增申请', icon: 'documentation', color: 'cyan' },
        passed: { label: '已通过', icon: 'validCode', color: 'green' },
        rejected: { label: '已驳回', icon: 'close', color: 'orange' },
        highRisk: { label: '高风险转案', icon: 'warning', color: 'orange' },
        assignTotal: { label: '分案记录', icon: 'documentation', color: 'blue' },
        assignToday: { label: '今日分案', icon: 'time', color: 'cyan' },
        mainLawyers: { label: '主办律师', icon: 'peoples', color: 'green' },
        batchAssign: { label: '批量分案', icon: 'tree', color: 'violet' },
        avgWorkload: { label: '平均工作量', icon: 'time', color: 'orange' },
        confirmTotal: { label: '确认消息', icon: 'message', color: 'blue' },
        confirmPending: { label: '待确认', icon: 'time', color: 'orange' },
        confirmAccepted: { label: '已确认', icon: 'validCode', color: 'green' },
        confirmRejected: { label: '已拒绝', icon: 'close', color: 'orange' },
        confirmToday: { label: '今日新增', icon: 'documentation', color: 'cyan' }
      }
      const keys = this.mode === 'lawyer'
        ? ['totalLawyers', 'highLoad', 'idle', 'monthAssigned', 'avgLoad']
        : (this.mode === 'transfer'
          ? ['pendingTransfer', 'todayApply', 'passed', 'rejected', 'highRisk']
          : (this.mode === 'assign'
            ? ['assignTotal', 'assignToday', 'mainLawyers', 'batchAssign', 'avgWorkload']
            : (this.mode === 'confirm'
              ? ['confirmTotal', 'confirmPending', 'confirmAccepted', 'confirmRejected', 'confirmToday']
              : ['pending', 'todayNew', 'transferPending', 'highLoadLawyers', 'avgAssignDays'])))
      return keys.map(key => ({ key, hint: '实时统计', ...configs[key] }))
    },
    dashboardCards() {
      return (this.metrics || []).reduce((result, item) => {
        result[item.metricKey] = item.metricValue
        return result
      }, {})
    },
    recommendedLawyerName() {
      return this.lawyerLoads[0] ? this.lawyerLoads[0].lawyerName : '-'
    },
    mainLawyerOptions() {
      return (this.lawyerOptions || []).filter(item => item.assignEnabled !== 'N' && item.lawyerRole !== 'assistant')
    },
    assistantLawyerOptions() {
      return (this.lawyerOptions || []).filter(item => item.assignEnabled !== 'N')
    }
  },
  watch: {
    '$route.query.module': {
      immediate: true,
      handler(value) {
        this.mode = value || 'pending'
        this.reset(false)
      }
    }
  },
  methods: {
    defaultQuery() {
      const query = { pageNum: 1, pageSize: 10 }
      if (this.$route.query.caseId) query.caseId = this.$route.query.caseId
      return query
    },
    goMode(mode) {
      this.$router.push({ path: this.$route.path, query: { module: mode } })
    },
    reset(keepMode = true) {
      this.query = this.defaultQuery()
      if (keepMode) this.loadPage()
      else this.$nextTick(this.loadPage)
    },
    search() {
      this.query.pageNum = 1
      this.loadPage()
    },
    loadPage() {
      this.loadDashboard()
      this.loadLawyerOptions()
      if (this.mode === 'assign') return this.loadAssignments()
      if (this.mode === 'transfer') return this.loadTransfers()
      if (this.mode === 'lawyer') return this.loadLawyers()
      if (this.mode === 'confirm') return this.loadConfirms()
      if (this.mode === 'status') return this.loadStatuses()
      return this.loadCases()
    },
    loadDashboard() {
      getCaseDashboard().then(res => {
        const data = res.data || {}
        this.metrics = data.cards || []
        this.lawyerLoads = data.lawyers || this.lawyerLoads
        this.specialtyStats = data.specialties || this.specialtyStats
      })
    },
    loadLawyerOptions() {
      listCaseLawyer().then(res => { this.lawyerOptions = res.data || [] })
    },
    loadCases() {
      this.loading = true
      listCase({ ...this.query, mode: 'pending' }).then(res => {
        this.cases = res.rows || []
        this.total = res.total || 0
      }).finally(() => { this.loading = false })
    },
    loadAssignments() {
      this.loading = true
      listAssignment(this.query).then(res => {
        this.assignments = res.rows || []
        this.total = res.total || 0
      }).finally(() => { this.loading = false })
    },
    loadTransfers() {
      this.loading = true
      listTransfer(this.query).then(res => {
        this.transfers = res.rows || []
        this.total = res.total || 0
      }).finally(() => { this.loading = false })
    },
    loadLawyers() {
      this.loading = true
      listLawyerLoad(this.query).then(res => {
        this.lawyerLoads = res.rows || []
        this.total = res.total || 0
      }).finally(() => { this.loading = false })
      listLawyerSpecialty(this.query).then(res => { this.specialtyStats = res.data || [] })
    },
    openProfileConfig() {
      this.profileOpen = true
      this.resetProfileQuery()
    },
    resetProfileQuery(load = true) {
      this.profileQuery = { pageNum: 1, pageSize: 10 }
      if (load) this.loadProfiles()
    },
    setProfileAssignable(value) {
      this.$set(this.profileQuery, 'assignEnabled', value)
      this.profileQuery.pageNum = 1
      this.loadProfiles()
    },
    loadProfiles() {
      this.profileLoading = true
      this.$nextTick(() => {
        this.profileList = this.filterProfileFallback()
        this.profileTotal = this.profileList.length
        this.profileLoading = false
      })
    },
    openProfileForm(row) {
      const userId = row && row.userId
      if (!userId) return
      this.setProfileForm(row)
      this.profileFormOpen = true
      this.$nextTick(() => this.$refs.profileFormRef && this.$refs.profileFormRef.clearValidate())
    },
    setProfileForm(data) {
      this.profileForm = {
        ...data,
        specialties: this.specialtyList(data.specialties || 'business'),
        loadLimit: Number(data.loadLimit || 100),
        avgResponseHours: Number(data.avgResponseHours || 4),
        assignEnabled: data.assignEnabled || 'Y'
      }
    },
    filterProfileFallback() {
      const keyword = String(this.profileQuery.keyword || '').trim()
      const role = this.profileQuery.lawyerRole
      const specialty = this.profileQuery.specialty
      const assignEnabled = this.profileQuery.assignEnabled
      return (this.lawyerLoads || []).filter(item => {
        const keywordMatched = !keyword || [item.userName, item.nickName, item.lawyerName, item.deptName, item.specialties].some(value => String(value || '').indexOf(keyword) > -1)
        const roleMatched = !role || item.lawyerRole === role
        const specialtyMatched = !specialty || this.specialtyList(item.specialties).includes(specialty)
        const assignMatched = !assignEnabled || (item.assignEnabled || 'Y') === assignEnabled
        return keywordMatched && roleMatched && specialtyMatched && assignMatched
      })
    },
    saveProfile() {
      this.$refs.profileFormRef.validate(valid => {
        if (!valid) return
        const payload = {
          ...this.profileForm,
          specialties: (this.profileForm.specialties || []).join(',')
        }
        updateLawyerProfile(payload).then(() => {
          this.$modal.msgSuccess('律师档案已保存')
          this.profileFormOpen = false
          this.loadProfiles()
          this.loadLawyers()
          this.loadLawyerOptions()
          this.loadDashboard()
        }).catch(() => {
          this.$modal.msgError('律师档案保存失败，请确认后端已重启并且 case:lawyer:config 权限已初始化')
        })
      })
    },
    changeProfileStatus(row) {
      updateLawyerProfileStatus({ userId: row.userId, assignEnabled: row.assignEnabled }).then(() => {
        this.$modal.msgSuccess(row.assignEnabled === 'Y' ? '已启用分案' : '已禁用分案')
        this.loadProfiles()
        this.loadLawyers()
        this.loadLawyerOptions()
      }).catch(() => {
        row.assignEnabled = row.assignEnabled === 'Y' ? 'N' : 'Y'
      })
    },
    loadConfirms() {
      this.loading = true
      listConfirm(this.query).then(res => {
        this.confirms = res.rows || []
        this.total = res.total || 0
      }).finally(() => { this.loading = false })
    },
    loadStatuses() {
      this.loading = true
      listCaseStatus(this.query).then(res => {
        this.statuses = res.rows || []
        this.total = res.total || 0
      }).finally(() => { this.loading = false })
    },
    openDetail(row) {
      const caseId = this.caseIdOf(row)
      this.detailCase = row || {}
      this.detailOpen = true
      if (!caseId) return
      getCase(caseId).then(res => {
        this.detailCase = res.data || row || {}
      })
    },
    openCaseFlow(row) {
      const caseId = this.caseIdOf(row)
      this.flowCase = row || {}
      this.flowStatuses = []
      this.flowOpen = true
      if (!caseId) return
      this.flowLoading = true
      listCaseStatus({ caseId, pageNum: 1, pageSize: 20 }).then(res => {
        this.flowStatuses = res.rows || []
      }).finally(() => { this.flowLoading = false })
    },
    openAssign(row, rows) {
      const selected = rows && rows.length ? rows : (row ? [row] : [])
      this.assignCaseRow = row || {}
      this.assignForm = {
        caseId: row && row.case_id,
        caseIds: selected.map(item => item.case_id),
        batch: selected.length > 1,
        assignMethod: this.dictDefault('law_case_assign_method'),
        priority: row && row.priority || this.dictDefault('law_case_priority'),
        assignReason: this.dictDefault('law_case_assign_reason'),
        estimatedWorkload: row && Number(row.estimated_workload || 24),
        notifyFlag: 'Y',
        assistantLawyerIds: []
      }
      this.assignOpen = true
      this.$nextTick(() => this.$refs.assignFormRef && this.$refs.assignFormRef.clearValidate())
    },
    selectAssignLawyer(item) {
      this.$set(this.assignForm, 'mainLawyerId', item.userId)
    },
    saveAssign() {
      this.$refs.assignFormRef.validate(valid => {
        if (!valid) return
        const assistants = this.lawyerOptions.filter(item => (this.assignForm.assistantLawyerIds || []).some(id => String(id) === String(item.userId)))
        const payload = {
          ...this.assignForm,
          assistantLawyerIds: (this.assignForm.assistantLawyerIds || []).join(','),
          assistantLawyerNames: assistants.map(item => item.nickName).join(',')
        }
        const request = payload.batch ? batchAssignCase : assignCase
        request(payload).then(() => {
          this.assignOpen = false
          this.assignCaseRow = {}
          this.$modal.msgSuccess('分案成功')
          this.assignOpen = false
          this.selectedRows = []
          this.loadPage()
          this.$nextTick(() => { this.assignOpen = false })
        })
      })
    },
    assignRecommended(lawyer) {
      listCase({ pageNum: 1, pageSize: 1, mode: 'pending' }).then(res => {
        const row = (res.rows || [])[0]
        if (!row) {
          this.$modal.msgWarning('暂无待分案案件')
          return
        }
        this.openAssign(row)
        this.$set(this.assignForm, 'mainLawyerId', lawyer.userId)
      })
    },
    openLawyerDetail(row) {
      this.lawyerDetail = row || {}
      this.lawyerDetailOpen = true
    },
    searchProcessingCases(keyword) {
      listCase({ pageNum: 1, pageSize: 20, mode: 'processing', keyword }).then(res => { this.processingCases = res.rows || [] })
    },
    openTransfer() {
      this.transferForm = { transferReason: this.dictDefault('law_case_transfer_reason'), riskLevel: this.dictDefault('law_case_risk_level') }
      this.searchProcessingCases('')
      this.transferOpen = true
      this.$nextTick(() => this.$refs.transferFormRef && this.$refs.transferFormRef.clearValidate())
    },
    saveTransfer() {
      this.$refs.transferFormRef.validate(valid => {
        if (!valid) return
        requestTransfer(this.transferForm).then(() => {
          this.$modal.msgSuccess('转案申请已提交')
          this.transferOpen = false
          this.loadPage()
        })
      })
    },
    openApproval(row) {
      this.approvalTransfer = row
      this.approvalForm = { transferId: row.transfer_id, action: 'passed', opinion: '' }
      this.transferDetailOpen = true
      this.$nextTick(() => { if (this.$refs.transferApprovalFormRef) this.$refs.transferApprovalFormRef.clearValidate() })
    },
    saveApproval() {
      if (!this.approvalForm.transferId) return
      this.$refs.transferApprovalFormRef.validate(valid => {
        if (!valid) return
        approveTransfer(this.approvalForm).then(() => {
          this.$modal.msgSuccess('审批完成')
          this.transferDetailOpen = false
          this.approvalForm = {}
          this.approvalTransfer = {}
          this.loadTransfers()
          this.loadDashboard()
        })
      })
    },
    handleConfirmRow(row, result) {
      const text = result === 'accepted' ? '确认接收' : '拒绝接案'
      handleConfirm({ confirmId: row.confirm_id, confirmResult: result, remark: text }).then(() => {
        this.$modal.msgSuccess(text + '成功')
        this.loadConfirms()
        this.loadDashboard()
      })
    },
    avatar(name) {
      return (name || '-').slice(0, 1)
    },
    dictDefault(type) {
      const item = (this.dict.type[type] || []).find(item => item.raw && item.raw.isDefault === 'Y') || (this.dict.type[type] || [])[0]
      return item ? item.value : undefined
    },
    dictLabel(type, value) {
      const item = (this.dict.type[type] || []).find(item => item.value === value)
      return item ? item.label : value || '-'
    },
    specialtyList(value) {
      return String(value || '').split(',').filter(Boolean)
    },
    sameValue(a, b) {
      return String(a) === String(b)
    },
    caseIdOf(row) {
      return row && (row.case_id || row.caseId)
    },
    loadColor(value) {
      const rate = Number(value || 0)
      if (rate >= 80) return '#ef4444'
      if (rate >= 50) return '#f59e0b'
      return '#10b981'
    },
    loadStatus(value) {
      const rate = Number(value || 0)
      if (rate >= 80) return 'high'
      if (rate < 20) return 'idle'
      return 'normal'
    },
    matchLevel(row) {
      const rate = Number(row && row.loadRate || 0)
      if (rate < 50) return 'high'
      if (rate < 80) return 'medium'
      return 'low'
    },
    lawyerSuggestion(row) {
      const rate = Number(row && row.loadRate || 0)
      if (row && row.assignEnabled === 'N') return '该律师当前已关闭可分案，不建议继续分配新案件。'
      if (row && row.lawyerRole === 'assistant') return '该人员为实习律师，适合作为协办律师参与案件，不建议作为主办律师。'
      if (rate >= 80) return '当前负载较高，建议仅分配紧急且专业高度匹配的案件，或优先考虑其他律师。'
      if (rate >= 50) return '当前负载适中，可承接普通案件，建议关注预计工作量和响应时长。'
      return '当前负载较低，适合优先分配专业匹配的新案件。'
    }
  }
}
</script>

<style scoped lang="scss">
@import "../business/business.scss";

.case-page {
  --biz-filter-input-width: 220px;
  --biz-filter-select-width: 126px;
}

.sub-text {
  display: block;
  margin-top: 3px;
  color: #8a98ad;
  font-size: var(--biz-font-mini, 11px);
}

.owner-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.owner-cell i {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  color: #1d4ed8;
  background: #eaf1ff;
  font-style: normal;
}

.biz-link {
  color: #1d4ed8;
  cursor: pointer;
}

.tag-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.tag-pills i {
  padding: 2px 7px;
  border-radius: 999px;
  color: #4b5d78;
  background: #f1f5fb;
  font-style: normal;
}

.drawer-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.drawer-title span {
  color: #2563eb;
  font-size: var(--biz-font-mini, 11px);
  font-weight: 700;
  letter-spacing: .08em;
}

.drawer-title h3 {
  margin: 4px 0;
  color: #172b4d;
  font-size: var(--biz-font-section, 18px);
}

.drawer-title p {
  margin: 0;
  color: #7a869a;
  font-size: var(--biz-font-small, 12px);
}

.transfer-drawer-body {
  padding: 0 18px 74px;
}

.lawyer-detail-body,
.profile-drawer-body {
  padding: 0 18px 74px;
}

.drawer-card {
  margin-bottom: 14px;
  padding: 16px;
  border: 1px solid #e8edf6;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 8px 18px rgba(36, 73, 135, .05);
}

.lawyer-profile-hero {
  display: grid;
  grid-template-columns: 66px minmax(0, 1fr);
  gap: 14px;
  align-items: center;
  margin-bottom: 14px;
  padding: 18px;
  border-radius: 16px;
  color: #fff;
  background: linear-gradient(135deg, #1557ff 0%, #19b7f0 50%, #7c3aed 100%);
  box-shadow: 0 16px 36px rgba(37, 99, 235, .22);
}

.lawyer-profile-hero > i {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 66px;
  height: 66px;
  border-radius: 20px;
  color: #1d4ed8;
  background: rgba(255, 255, 255, .92);
  font-size: 28px;
  font-style: normal;
  font-weight: 800;
}

.lawyer-profile-hero h3 {
  margin: 0 0 6px;
  font-size: var(--biz-font-title, 22px);
}

.lawyer-profile-hero p {
  margin: 0 0 10px;
  color: rgba(255, 255, 255, .78);
}

.load-badge {
  display: inline-flex;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, .18);
  font-size: var(--biz-font-mini, 11px);
  font-weight: 700;
}

.lawyer-load-ring {
  display: grid;
  grid-template-columns: 128px minmax(0, 1fr);
  gap: 16px;
  align-items: center;
}

.lawyer-load-ring dl {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
}

.lawyer-load-ring div {
  padding: 10px;
  border-radius: 12px;
  background: #f7faff;
}

.lawyer-load-ring dt {
  margin-bottom: 5px;
  color: #8a98ad;
  font-size: var(--biz-font-mini, 11px);
}

.lawyer-load-ring dd {
  margin: 0;
  color: #172b4d;
  font-weight: 800;
}

.detail-specialties {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.detail-specialties span,
.detail-specialties em {
  padding: 5px 10px;
  border-radius: 999px;
  color: #1d4ed8;
  background: #eaf1ff;
  font-style: normal;
  font-size: var(--biz-font-small, 12px);
}

.lawyer-suggestion {
  padding: 12px;
  border-radius: 12px;
  color: #1d4ed8;
  background: #f1f6ff;
  line-height: 1.7;
}

.lawyer-suggestion.high {
  color: #dc2626;
  background: #fff1f2;
}

.lawyer-suggestion.normal {
  color: #b45309;
  background: #fff7ed;
}

.lawyer-suggestion.idle {
  color: #059669;
  background: #ecfdf5;
}

.profile-toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: 12px;
  align-items: center;
  margin-bottom: 14px;
  padding: 12px;
  border-radius: 14px;
  background: linear-gradient(135deg, #f7faff 0%, #eef6ff 100%);
}

.profile-search {
  display: flex;
  gap: 8px;
  min-width: 0;
}

.profile-search .el-input {
  flex: 1;
}

.profile-quick-tabs {
  display: inline-flex;
  gap: 4px;
  padding: 4px;
  border: 1px solid #dfe8f7;
  border-radius: 999px;
  background: #fff;
}

.profile-quick-tabs button {
  border: 0;
  border-radius: 999px;
  padding: 6px 12px;
  color: #64748b;
  background: transparent;
  cursor: pointer;
  font-size: var(--biz-font-small, 12px);
}

.profile-quick-tabs button.active {
  color: #fff;
  background: #2563eb;
  box-shadow: 0 6px 14px rgba(37, 99, 235, .22);
}

.profile-advanced-filter {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 12px;
  align-items: center;
}

.profile-advanced-filter label {
  color: #6b7a90;
  font-size: var(--biz-font-small, 12px);
}

.profile-advanced-actions {
  grid-column: 1 / -1;
  display: flex;
  justify-content: flex-end;
}

.drawer-kv {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
}

.drawer-kv div {
  margin: 0;
  padding: 10px;
  border-radius: 10px;
  background: #f7faff;
}

.drawer-kv dt {
  display: block;
  margin-bottom: 5px;
  color: #8a98ad;
  font-size: var(--biz-font-mini, 11px);
}

.drawer-kv dd {
  margin: 0;
  color: #172b4d;
  font-size: var(--biz-font-small, 12px);
  font-weight: 600;
}

.lawyer-compare {
  display: grid;
  grid-template-columns: 1fr 24px 1fr;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.lawyer-compare span {
  padding: 10px;
  border: 1px solid #dbe7ff;
  border-radius: 10px;
  color: #1d4ed8;
  background: #f1f6ff;
  text-align: center;
  font-weight: 600;
}

.lawyer-compare i {
  color: #1d4ed8;
  text-align: center;
}

.approval-detail-text,
.approval-result {
  margin: 0;
  color: #52627a;
  font-size: var(--biz-font-small, 12px);
  line-height: 1.6;
}

.approval-result {
  margin-top: 10px;
  padding: 10px;
  border-radius: 10px;
  color: #1d4ed8;
  background: #f1f6ff;
}

.approval-flow {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.approval-flow span {
  flex: 1;
  padding: 7px 8px;
  border-radius: 999px;
  color: #8a98ad;
  background: #f1f5fb;
  text-align: center;
  font-size: var(--biz-font-mini, 11px);
}

.approval-flow .done {
  color: #0f9f6e;
  background: #eafaf3;
}

.approval-flow .active {
  color: #1d4ed8;
  background: #eaf1ff;
}

.approval-action-group {
  margin-bottom: 12px;
}

.drawer-footer {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 14px 18px;
  border-top: 1px solid #edf1f8;
  background: #fff;
  box-shadow: 0 -8px 18px rgba(36, 73, 135, .06);
}

.case-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  padding: 12px;
  border-radius: 10px;
  background: #f6f9ff;
  margin-bottom: 14px;
}

.case-summary span {
  color: #708099;
  font-size: var(--biz-font-small, 12px);
}

.case-summary b {
  display: block;
  margin-top: 4px;
  color: #172b4d;
}

.assign-dialog-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 18px;
}

.lawyer-reference header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 10px;
}

.lawyer-reference article {
  padding: 12px;
  border: 1px solid #edf1f8;
  border-radius: 10px;
  cursor: pointer;
  margin-bottom: 10px;
}

.lawyer-reference article.active {
  border-color: #2563eb;
  background: #f5f8ff;
}

.lawyer-reference small {
  color: #6b7a90;
}

.select-sub {
  float: right;
  color: #8492a6;
  font-size: 12px;
}

@media (max-width: 1200px) {
  .assign-dialog-grid {
    grid-template-columns: 1fr;
  }

  .profile-toolbar {
    grid-template-columns: 1fr;
  }

  .profile-search {
    flex-wrap: wrap;
  }

  .lawyer-load-ring {
    grid-template-columns: 1fr;
  }
}
</style>

<style lang="scss">
@import "../business/business-dialog.scss";

.profile-filter-popover {
  border-radius: 14px;
  box-shadow: 0 18px 42px rgba(36, 73, 135, .16);
}
</style>
