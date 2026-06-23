<template>
  <div class="biz-page customer-page" :class="'biz-size-' + appSize">
    <biz-page-header eyebrow="CUSTOMER CENTER" :title="pageTitle" :description="pageDescription">
      <el-button v-if="mode === 'list'" v-hasPermi="['customer:import']" :size="controlSize" plain icon="el-icon-upload2" @click="openImport">导入</el-button>
      <el-button v-if="mode === 'list'" v-hasPermi="['customer:export']" :size="controlSize" plain icon="el-icon-download" @click="handleExport">导出</el-button>
      <el-button v-if="mode === 'list'" v-hasPermi="['customer:add']" :size="controlSize" type="primary" icon="el-icon-plus" @click="openCustomer()">新增客户</el-button>
      <el-button v-if="mode === 'contact'" v-hasPermi="['customer:contact:add']" :size="controlSize" type="primary" icon="el-icon-plus" @click="openContact()">新增联系人</el-button>
      <el-button v-if="mode === 'followup'" v-hasPermi="['customer:followup:add']" :size="controlSize" type="primary" icon="el-icon-plus" @click="openFollowup()">新增跟进</el-button>
      <el-button v-if="mode === 'tag'" v-hasPermi="['customer:tag:add']" :size="controlSize" type="primary" icon="el-icon-plus" @click="openTag()">新增标签</el-button>
    </biz-page-header>

    <biz-hero
      v-if="['list', 'contact', 'merge'].includes(mode)"
      :eyebrow="heroMeta.eyebrow"
      :title="heroMeta.title"
      :description="heroMeta.description"
    />

    <biz-metrics v-if="['list', 'contact', 'merge'].includes(mode)" :metrics="modeMetrics" :config="modeMetricConfig" />

    <biz-table-card
      v-if="mode === 'list'"
      :show-search.sync="showSearch"
      :total="total"
      :page.sync="query.pageNum"
      :limit.sync="query.pageSize"
      @query="loadPage"
      @pagination="loadCustomers"
    >
      <template #filters>
        <div class="biz-filter-main">
          <el-input v-model="query.customerName" :size="controlSize" prefix-icon="el-icon-search" placeholder="搜索客户名称、公司名称、手机号" clearable @clear="search" @keyup.enter.native="search" />
          <el-select v-model="query.customerType" :size="controlSize" placeholder="客户类型：全部" clearable @change="search">
            <el-option v-for="item in dict.type.law_customer_type" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="query.industry" :size="controlSize" placeholder="所属行业：全部" clearable @change="search">
            <el-option v-for="item in dict.type.law_customer_industry" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="query.tagId" :size="controlSize" placeholder="标签：全部" clearable @change="search">
            <el-option v-for="item in tagOptions" :key="tagValue(item)" :label="tagLabel(item)" :value="tagValue(item)" />
          </el-select>
        </div>
        <div class="biz-filter-actions">
          <el-popover v-model="advancedOpen" placement="bottom-end" width="360" trigger="click" popper-class="business-advanced-popover">
            <div class="advanced-filter-panel">
              <div class="advanced-title">
                <strong>高级筛选</strong>
                <span>组合客户等级与负责人快速定位</span>
              </div>
              <el-form label-position="top">
                <el-form-item label="客户等级">
                  <el-select v-model="query.customerLevel" :size="controlSize" placeholder="全部等级" clearable>
                    <el-option v-for="item in dict.type.law_customer_level" :key="item.value" :label="item.label" :value="item.value" />
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
      <el-table v-loading="loading" :data="list" :size="controlSize">
          <el-table-column type="selection" width="42" align="center" />
          <el-table-column label="客户编号" prop="customerNo" min-width="140" align="center" />
          <el-table-column label="客户信息" min-width="170">
            <template slot-scope="{ row }">
              <a class="biz-link" @click="openDetail(row)">{{ row.customerName }}</a>
              <span class="sub-text">{{ row.companyName || maskMobile(row.mobile) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="客户类型" width="96" align="center">
            <template slot-scope="{ row }"><dict-tag :options="dict.type.law_customer_type" :value="row.customerType" /></template>
          </el-table-column>
          <el-table-column label="行业" width="108" align="center">
            <template slot-scope="{ row }">{{ dictLabel('law_customer_industry', row.industry) }}</template>
          </el-table-column>
          <el-table-column label="等级" width="96" align="center">
            <template slot-scope="{ row }"><dict-tag :options="dict.type.law_customer_level" :value="row.customerLevel" /></template>
          </el-table-column>
          <el-table-column label="状态" width="88" align="center">
            <template slot-scope="{ row }"><dict-tag :options="dict.type.law_customer_status" :value="customerStatusOf(row)" /></template>
          </el-table-column>
          <el-table-column label="标签" min-width="150">
            <template slot-scope="{ row }">
              <span v-if="customerTags(row).length" class="tag-pills">
                <i v-for="item in customerTags(row)" :key="item.name" :style="{ borderColor: item.color, color: item.color }">{{ item.name }}</i>
              </span>
              <span v-else class="sub-text">未设置</span>
            </template>
          </el-table-column>
          <el-table-column label="最近跟进" prop="lastFollowTime" min-width="132" align="center" />
          <el-table-column label="负责人" width="104" align="center">
            <template slot-scope="{ row }"><span class="owner-cell"><i>{{ avatar(row.ownerName) }}</i>{{ row.ownerName || '-' }}</span></template>
          </el-table-column>
          <el-table-column label="关联合同" width="90" align="center">
            <template slot-scope="{ row }">
              <span class="contract-count">{{ customerContractCountOf(row) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="268" align="center" class-name="small-padding fixed-width biz-operation-column" fixed="right">
            <template slot-scope="{ row }">
              <span class="action-buttons">
                <el-button v-hasPermi="['customer:query']" :size="controlSize" type="text" icon="el-icon-view" @click="openDetail(row)">详情</el-button>
                <el-button v-hasPermi="['customer:edit']" :size="controlSize" type="text" icon="el-icon-edit" :disabled="!canOperateCustomer(row)" @click="openCustomer(row)">编辑</el-button>
                <el-button v-hasPermi="['customer:tag:assign']" :size="controlSize" type="text" icon="el-icon-price-tag" :disabled="!canOperateCustomer(row)" @click="openCustomerTags(row)">标签</el-button>
                <el-button v-if="canCreateContract" v-hasPermi="['contract:add']" :size="controlSize" type="text" icon="el-icon-document-add" :disabled="!canOperateCustomer(row)" @click="newContract(row)">新建合同</el-button>
                <el-button v-hasPermi="['customer:remove']" :size="controlSize" type="text" icon="el-icon-delete" class="danger-text" :disabled="!canRemoveCustomer(row)" :title="customerRemoveTip(row)" @click="removeCustomer(row)">删除</el-button>
              </span>
            </template>
          </el-table-column>
      </el-table>
    </biz-table-card>

    <biz-table-card
      v-else-if="mode === 'contact'"
      :show-search.sync="showSearch"
      :total="total"
      :page.sync="query.pageNum"
      :limit.sync="query.pageSize"
      @query="loadPage"
      @pagination="loadContacts"
    >
      <template #filters>
        <div class="biz-filter-main">
          <el-input v-model="query.contactName" :size="controlSize" prefix-icon="el-icon-search" placeholder="搜索联系人" clearable @clear="search" @keyup.enter.native="search" />
          <el-input v-model="query.customerName" :size="controlSize" placeholder="客户名称" clearable @clear="search" @keyup.enter.native="search" />
          <el-input v-model="query.mobile" :size="controlSize" placeholder="手机号" clearable @clear="search" @keyup.enter.native="search" />
        </div>
        <div class="biz-filter-actions">
          <el-button :size="controlSize" plain icon="el-icon-refresh" @click="reset">重置</el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="contacts" :size="controlSize">
          <el-table-column label="联系人" prop="contact_name" min-width="120" />
          <el-table-column label="所属客户" prop="customerName" min-width="150" />
          <el-table-column label="职务" prop="position_name" width="110" align="center" />
          <el-table-column label="手机号" width="130" align="center"><template slot-scope="{ row }">{{ maskMobile(row.mobile) }}</template></el-table-column>
          <el-table-column label="关系" width="110" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_contact_relation" :value="row.relation_type" /></template></el-table-column>
          <el-table-column label="关键联系人" width="110" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_yes_no_flag" :value="row.key_contact" /></template></el-table-column>
          <el-table-column label="操作" width="120" align="center" class-name="small-padding fixed-width">
            <template slot-scope="{ row }">
              <el-button v-hasPermi="['customer:contact:edit']" :size="controlSize" type="text" icon="el-icon-edit" @click="openContact(row)">编辑</el-button>
              <el-button v-hasPermi="['customer:contact:remove']" :size="controlSize" type="text" icon="el-icon-delete" class="danger-text" @click="removeContact(row)">删除</el-button>
            </template>
          </el-table-column>
      </el-table>
    </biz-table-card>

    <div v-else-if="mode === 'followup'" class="table-card timeline-page">
      <div class="section-title"><h3>客户跟进</h3><p>沉淀每一次沟通内容和下次跟进计划</p></div>
      <div v-loading="loading" class="biz-timeline-list follow-list">
        <article v-for="item in followups" :key="item.followup_id">
          <i :class="'timeline-icon type-' + item.follow_type"><svg-icon :icon-class="followIcon(item.follow_type)" /></i>
          <div>
            <h4>{{ item.customerName || '-' }} <span>{{ dictLabel('law_customer_follow_type', item.follow_type) }}</span></h4>
            <p>{{ item.content || '暂无跟进内容' }}</p>
            <small>{{ item.followUserName || '-' }} · {{ item.create_time || '-' }}<em v-if="item.next_follow_time">下次跟进：{{ item.next_follow_time }}</em></small>
          </div>
          <el-button v-hasPermi="['customer:followup:remove']" :size="controlSize" type="text" class="danger-text" @click="removeFollowup(item)">删除</el-button>
        </article>
        <el-empty v-if="!followups.length" description="暂无跟进记录" />
      </div>
      <pagination v-show="total > 0" :total="total" :page.sync="query.pageNum" :limit.sync="query.pageSize" @pagination="loadFollowups" />
    </div>

    <div v-else-if="mode === 'tag'" class="setting-grid">
      <article v-for="item in tags" :key="item.tag_id">
        <i :style="{ background: item.tag_color || '#3b82f6' }" />
        <div><h4>{{ item.tag_name }}</h4><p>排序 {{ item.order_num || 0 }}</p></div>
        <span :class="{ disabled: !sameValue(item.status, dictValue('sys_normal_disable', '0')) }">{{ dictLabel('sys_normal_disable', item.status) }}</span>
        <footer>
          <el-button v-hasPermi="['customer:tag:edit']" :size="controlSize" type="text" @click="openTag(item)">编辑</el-button>
          <el-button v-hasPermi="['customer:tag:remove']" :size="controlSize" type="text" class="danger-text" @click="removeTag(item)">删除</el-button>
        </footer>
      </article>
      <el-empty v-if="!tags.length" description="暂无客户标签" />
    </div>

    <biz-table-card v-else-if="mode === 'merge'" :show-search.sync="showSearch" :pagination="false" @query="loadMerge">
      <template #header>
        <div class="section-title"><h3>去重合并</h3><p>按客户名称、手机号、企业名称辅助识别重复客户</p></div>
      </template>
      <template #filters>
        <div class="biz-filter-main">
          <el-input v-model="mergeQuery.customerName" :size="controlSize" prefix-icon="el-icon-search" placeholder="客户名称" clearable @clear="loadMerge" @keyup.enter.native="loadMerge" />
          <el-input v-model="mergeQuery.mobile" :size="controlSize" placeholder="手机号" clearable @clear="loadMerge" @keyup.enter.native="loadMerge" />
          <el-input v-model="mergeQuery.creditCode" :size="controlSize" placeholder="信用代码" clearable @clear="loadMerge" @keyup.enter.native="loadMerge" />
        </div>
        <div class="biz-filter-actions">
          <el-button :size="controlSize" plain icon="el-icon-refresh" @click="resetMerge">重置</el-button>
        </div>
      </template>
      <el-table :data="mergeList" :size="controlSize">
          <el-table-column label="客户编号" prop="customerNo" min-width="140" />
          <el-table-column label="客户名称" prop="customerName" min-width="150" />
          <el-table-column label="手机号" width="130"><template slot-scope="{ row }">{{ maskMobile(row.mobile) }}</template></el-table-column>
          <el-table-column label="信用代码" prop="creditCode" min-width="160" show-overflow-tooltip />
          <el-table-column label="操作" width="120" align="center"><template slot-scope="{ row }"><el-button v-hasPermi="['customer:merge:merge']" :size="controlSize" type="text" @click="openMerge(row)">合并</el-button></template></el-table-column>
      </el-table>
    </biz-table-card>

    <excel-import-dialog ref="importRef" title="客户导入" action="/customer/importData" template-action="/customer/importTemplate" template-file-name="customer_template" update-support-label="按客户名称重复时更新已有客户" @success="loadPage" />
    <customer-detail-drawer
      :visible.sync="detailOpen"
      :customer="detailCustomer"
      :contacts="detailContacts"
      :followups="detailFollowups"
      :contracts="detailContracts"
      :merge-logs="detailMergeLogs"
      :can-create-contract="canCreateContract"
      :can-operate="canOperateCustomer(detailCustomer)"
      :size-class="'biz-size-' + appSize"
      @edit="openCustomer"
      @contact="openContact"
      @follow="openFollowup"
      @tags="openCustomerTags"
      @new-contract="newContract"
      @matter="viewMatter"
    />

    <el-dialog :title="form.customerId ? '编辑客户' : '新增客户'" :visible.sync="customerOpen" width="760px" :custom-class="dialogClass" append-to-body>
      <el-form ref="customerForm" :model="form" :rules="customerRules" label-width="110px">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="客户名称" prop="customerName"><el-input v-model="form.customerName" :size="controlSize" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="客户类型" prop="customerType"><el-select v-model="form.customerType" :size="controlSize"><el-option v-for="item in dict.type.law_customer_type" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="手机号" prop="mobile"><el-input v-model="form.mobile" :size="controlSize" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="微信"><el-input v-model="form.wechat" :size="controlSize" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="客户来源" prop="sourceCode"><el-select v-model="form.sourceCode" :size="controlSize"><el-option v-for="item in leadSourceOptions" :key="item.settingCode" :label="item.settingName" :value="item.settingCode" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="负责人" prop="ownerId"><el-select v-model="form.ownerId" :size="controlSize" filterable><el-option v-for="item in ownerOptions" :key="item.userId" :label="item.nickName" :value="item.userId" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="公司名称" prop="companyName"><el-input v-model="form.companyName" :size="controlSize" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="信用代码"><el-input v-model="form.creditCode" :size="controlSize" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="行业"><el-select v-model="form.industry" :size="controlSize"><el-option v-for="item in dict.type.law_customer_industry" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="等级" prop="customerLevel"><el-select v-model="form.customerLevel" :size="controlSize"><el-option v-for="item in dict.type.law_customer_level" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="主要需求" prop="mainDemand"><el-input v-model="form.mainDemand" :size="controlSize" type="textarea" :rows="3" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" :size="controlSize" type="textarea" :rows="2" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <div slot="footer"><el-button :size="controlSize" @click="customerOpen = false">取消</el-button><el-button :size="controlSize" type="primary" @click="saveCustomer">确定</el-button></div>
    </el-dialog>

    <el-dialog :title="contactForm.contactId ? '编辑联系人' : '新增联系人'" :visible.sync="contactOpen" width="560px" :custom-class="dialogClass" append-to-body>
      <el-form ref="contactFormRef" :model="contactForm" :rules="contactRules" label-width="100px">
        <el-form-item label="所属客户" prop="customerId">
          <el-select v-model="contactForm.customerId" :size="controlSize" filterable remote reserve-keyword placeholder="搜索并选择客户" :remote-method="searchCustomerOptions" :loading="customerSelectLoading" @change="selectCustomerForContact">
            <el-option v-for="item in customerOptions" :key="item.customerId" :label="item.customerName" :value="item.customerId" :disabled="!canOperateCustomer(item)">
              <span>{{ item.customerName }}</span>
              <span class="select-sub">{{ item.mobile || item.companyName || item.customerNo }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="联系人" prop="contactName"><el-input v-model="contactForm.contactName" :size="controlSize" /></el-form-item>
        <el-form-item label="手机号" prop="mobile"><el-input v-model="contactForm.mobile" :size="controlSize" /></el-form-item>
        <el-form-item label="关系" prop="relationType"><el-select v-model="contactForm.relationType" :size="controlSize"><el-option v-for="item in dict.type.law_contact_relation" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="关键联系人"><el-switch v-model="contactForm.keyContact" :active-value="dictValue('law_yes_no_flag', '1')" :inactive-value="dictValue('law_yes_no_flag', '0')" /></el-form-item>
      </el-form>
      <div slot="footer"><el-button :size="controlSize" @click="contactOpen = false">取消</el-button><el-button :size="controlSize" type="primary" @click="saveContact">确定</el-button></div>
    </el-dialog>

    <el-dialog title="新增跟进" :visible.sync="followOpen" width="560px" :custom-class="dialogClass" append-to-body>
      <el-form ref="followFormRef" :model="followForm" :rules="followRules" label-width="100px">
        <el-form-item label="所属客户" prop="customerId">
          <el-select v-model="followForm.customerId" :size="controlSize" filterable remote reserve-keyword placeholder="搜索并选择客户" :remote-method="searchCustomerOptions" :loading="customerSelectLoading" @change="selectCustomerForFollow">
            <el-option v-for="item in customerOptions" :key="item.customerId" :label="item.customerName" :value="item.customerId" :disabled="!canOperateCustomer(item)">
              <span>{{ item.customerName }}</span>
              <span class="select-sub">{{ item.mobile || item.companyName || item.customerNo }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="方式" prop="followType"><el-select v-model="followForm.followType" :size="controlSize"><el-option v-for="item in dict.type.law_customer_follow_type" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="内容" prop="content"><el-input v-model="followForm.content" :size="controlSize" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="下次跟进"><el-date-picker v-model="followForm.nextFollowTime" :size="controlSize" value-format="yyyy-MM-dd HH:mm:ss" type="datetime" /></el-form-item>
      </el-form>
      <div slot="footer"><el-button :size="controlSize" @click="followOpen = false">取消</el-button><el-button :size="controlSize" type="primary" @click="saveFollowup">确定</el-button></div>
    </el-dialog>

    <el-dialog :title="tagForm.tagId ? '编辑标签' : '新增标签'" :visible.sync="tagOpen" width="460px" :custom-class="dialogClass" append-to-body>
      <el-form ref="tagFormRef" :model="tagForm" :rules="tagRules" label-width="90px">
        <el-form-item label="标签名" prop="tagName"><el-input v-model="tagForm.tagName" :size="controlSize" /></el-form-item>
        <el-form-item label="颜色"><el-color-picker v-model="tagForm.tagColor" :size="controlSize" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="tagForm.orderNum" :size="controlSize" :min="0" /></el-form-item>
        <el-form-item label="状态"><el-radio-group v-model="tagForm.status"><el-radio v-for="item in dict.type.sys_normal_disable" :key="item.value" :label="item.value">{{ item.label }}</el-radio></el-radio-group></el-form-item>
      </el-form>
      <div slot="footer"><el-button :size="controlSize" @click="tagOpen = false">取消</el-button><el-button :size="controlSize" type="primary" @click="saveTag">确定</el-button></div>
    </el-dialog>

    <el-dialog title="客户标签" :visible.sync="customerTagOpen" width="520px" :custom-class="dialogClass" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="客户">
          <el-input :value="customerTagForm.customerName" :size="controlSize" disabled />
        </el-form-item>
        <el-form-item label="标签">
          <el-checkbox-group v-model="customerTagForm.tagIds">
            <el-checkbox v-for="item in customerTagOptions" :key="item.tag_id" :label="item.tag_id">
              <span class="tag-dot" :style="{ background: item.tag_color || '#3b82f6' }" />{{ item.tag_name }}
            </el-checkbox>
          </el-checkbox-group>
          <el-empty v-if="!customerTagOptions.length" description="暂无启用标签" :image-size="72" />
        </el-form-item>
      </el-form>
      <div slot="footer"><el-button :size="controlSize" @click="customerTagOpen = false">取消</el-button><el-button :size="controlSize" type="primary" @click="saveCustomerTags">确定</el-button></div>
    </el-dialog>

    <el-dialog title="客户合并" :visible.sync="mergeOpen" width="560px" :custom-class="dialogClass" append-to-body>
      <el-alert title="合并后，待合并客户的联系人、跟进、标签和合同将迁移到主客户，原客户会标记为已合并。" type="warning" :closable="false" show-icon />
      <el-form ref="mergeFormRef" :model="mergeForm" :rules="mergeRules" label-width="110px" class="merge-form">
        <el-form-item label="待合并客户" prop="mergedCustomerId">
          <el-input :value="mergeForm.mergedCustomerName" :size="controlSize" disabled />
        </el-form-item>
        <el-form-item label="主客户" prop="mainCustomerId">
          <el-select v-model="mergeForm.mainCustomerId" :size="controlSize" filterable remote reserve-keyword placeholder="搜索并选择主客户" :remote-method="searchCustomerOptions" :loading="customerSelectLoading" @change="selectMainCustomer">
            <el-option v-for="item in customerOptions" :key="item.customerId" :label="item.customerName" :value="item.customerId" :disabled="item.customerId === mergeForm.mergedCustomerId || !canOperateCustomer(item)">
              <span>{{ item.customerName }}</span>
              <span class="select-sub">{{ item.mobile || item.companyName || item.customerNo }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="合并说明">
          <el-input v-model="mergeForm.content" :size="controlSize" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <div slot="footer"><el-button :size="controlSize" @click="mergeOpen = false">取消</el-button><el-button :size="controlSize" type="primary" @click="saveMerge">确认合并</el-button></div>
    </el-dialog>
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
import CustomerDetailDrawer from './components/CustomerDetailDrawer'
import { getCustomerDashboard, listCustomer, getCustomer, addCustomer, updateCustomer, delCustomer, listContact, addContact, updateContact, delContact, listFollowup, addFollowup, delFollowup, listTag, addTag, updateTag, delTag, getCustomerTags, setCustomerTags, listMergeCandidates, listMergeLogs, mergeCustomer, listCustomerOwner } from '@/api/customer'
import { listContract } from '@/api/contract'
import { listSetting } from '@/api/lead'

export default {
  name: 'Customer',
  mixins: [businessUi, customerLifecycle],
  components: { ExcelImportDialog, BizHero, BizMetrics, BizPageHeader, BizTableCard, CustomerDetailDrawer },
  dicts: ['law_customer_type', 'law_yes_no_flag', 'law_customer_level', 'law_customer_industry', 'law_contact_relation', 'law_customer_follow_type', 'law_customer_status', 'sys_normal_disable'],
  data() {
    const companyRequired = (rule, value, callback) => {
      if (this.form.customerType === 'enterprise' && !value) {
        callback(new Error('企业客户请输入公司名称'))
      } else {
        callback()
      }
    }
    return {
      mode: 'list',
      availableModes: ['list', 'contact', 'followup', 'tag', 'merge'],
      showSearch: true,
      loading: false,
      total: 0,
      list: [],
      contacts: [],
      followups: [],
      tags: [],
      metrics: [],
      contactMetrics: [],
      mergeMetrics: [],
      query: { pageNum: 1, pageSize: 10, customerName: '', customerType: '', industry: '', tagId: '', ownerId: '', customerLevel: '' },
      ownerOptions: [],
      leadSourceOptions: [],
      tagOptions: [],
      advancedOpen: false,
      form: {},
      contactForm: {},
      followForm: {},
      tagForm: {},
      customerTagForm: {},
      customerTagOptions: [],
      mergeQuery: {},
      mergeForm: {},
      mergeList: [],
      customerOptions: [],
      customerSelectLoading: false,
      businessPageMeta: {
        defaultTitle: '客户中心',
        titles: { list: '客户列表', contact: '联系人管理', followup: '客户跟进', tag: '客户标签', merge: '去重合并' },
        descriptions: {
          list: '统一维护客户档案、联系人、跟进与合同转化',
          contact: '管理客户下的关键联系人和沟通关系',
          followup: '记录客户沟通内容与下次跟进计划',
          tag: '维护客户分群标签，用于后续经营',
          merge: '识别重复客户并迁移联系人、跟进、标签和合同关联'
        }
      },
      detailOpen: false,
      detailCustomerId: null,
      detailCustomer: {},
      detailContacts: [],
      detailFollowups: [],
      detailContracts: [],
      detailMergeLogs: [],
      customerOpen: false,
      contactOpen: false,
      followOpen: false,
      tagOpen: false,
      customerTagOpen: false,
      mergeOpen: false,
      customerRules: {
        customerName: [{ required: true, message: '请输入客户名称', trigger: 'blur' }],
        customerType: [{ required: true, message: '请选择客户类型', trigger: 'change' }],
        mobile: [{ required: true, message: '请输入手机号', trigger: 'blur' }],
        sourceCode: [{ required: true, message: '请选择客户来源', trigger: 'change' }],
        customerLevel: [{ required: true, message: '请选择客户等级', trigger: 'change' }],
        mainDemand: [{ required: true, message: '请输入主要需求', trigger: 'blur' }],
        ownerId: [{ required: true, message: '请选择负责人', trigger: 'change' }],
        companyName: [{ validator: companyRequired, trigger: 'blur' }]
      },
      contactRules: {
        customerId: [{ required: true, message: '请选择所属客户', trigger: 'change' }],
        contactName: [{ required: true, message: '请输入联系人', trigger: 'blur' }],
        mobile: [{ required: true, message: '请输入手机号', trigger: 'blur' }],
        relationType: [{ required: true, message: '请选择关系', trigger: 'change' }]
      },
      followRules: {
        customerId: [{ required: true, message: '请选择所属客户', trigger: 'change' }],
        followType: [{ required: true, message: '请选择跟进方式', trigger: 'change' }],
        content: [{ required: true, message: '请输入跟进内容', trigger: 'blur' }]
      },
      tagRules: {
        tagName: [{ required: true, message: '请输入标签名', trigger: 'blur' }]
      },
      mergeRules: {
        mergedCustomerId: [{ required: true, message: '请选择待合并客户', trigger: 'change' }],
        mainCustomerId: [{ required: true, message: '请选择主客户', trigger: 'change' }]
      },
      metricConfig: [
        { key: 'total', label: '客户总数', hint: '全部有效客户', icon: 'peoples', color: 'blue' },
        { key: 'monthNew', label: '本月新增', hint: '新沉淀客户', icon: 'date', color: 'cyan' },
        { key: 'highValue', label: '高价值客户', hint: '重点经营客户', icon: 'star', color: 'violet' },
        { key: 'active', label: '活跃客户', hint: '已有跟进记录', icon: 'time', color: 'green' }
      ],
      contactMetricConfig: [
        { key: 'total', label: '联系人总数', hint: '符合当前筛选', icon: 'peoples', color: 'blue' },
        { key: 'keyContacts', label: '关键联系人', hint: '当前页关键联系人', icon: 'star', color: 'violet' },
        { key: 'customers', label: '关联客户', hint: '当前页覆盖客户', icon: 'tree', color: 'cyan' },
        { key: 'withMobile', label: '留存手机号', hint: '当前页有手机号', icon: 'phone', color: 'green' }
      ],
      mergeMetricConfig: [
        { key: 'candidates', label: '候选客户', hint: '当前识别结果', icon: 'peoples', color: 'blue' },
        { key: 'withMobile', label: '手机号线索', hint: '可按手机号比对', icon: 'phone', color: 'cyan' },
        { key: 'withCompany', label: '企业客户', hint: '含公司名称', icon: 'documentation', color: 'violet' },
        { key: 'withCreditCode', label: '信用代码', hint: '可按信用代码比对', icon: 'education', color: 'green' }
      ]
    }
  },
  computed: {
    heroMeta() {
      const meta = {
        list: {
          eyebrow: '客户资产沉淀',
          title: '统一维护客户档案、联系人与业务转化',
          description: '线索转化后形成客户资产，持续记录沟通、合同与服务状态。'
        },
        contact: {
          eyebrow: 'CONTACT MANAGEMENT',
          title: '统一管理客户联系人与关键沟通关系',
          description: '沉淀客户联系人、关系类型与关键联系人标识，方便后续跟进和合同协同。'
        },
        merge: {
          eyebrow: 'DEDUPLICATION',
          title: '识别重复客户并合并业务资产',
          description: '按客户名称、手机号和信用代码辅助识别重复记录，合并联系人、跟进、标签与合同关联。'
        }
      }
      return meta[this.mode] || meta.list
    },
    modeMetrics() {
      if (this.mode === 'contact') return this.contactMetrics
      if (this.mode === 'merge') return this.mergeMetrics
      return this.metrics
    },
    modeMetricConfig() {
      if (this.mode === 'contact') return this.contactMetricConfig
      if (this.mode === 'merge') return this.mergeMetricConfig
      return this.metricConfig
    },
    canReadContract() {
      return this.$auth.hasPermiOr(['contract:list', 'contract:query'])
    },
    canReadContact() {
      return this.$auth.hasPermi('customer:contact:list')
    },
    canReadFollowup() {
      return this.$auth.hasPermi('customer:followup:list')
    },
    canCreateContract() {
      return this.$auth.hasPermi('contract:add') && this.canReadContract
    },
    canReadMergeLog() {
      return this.$auth.hasPermi('customer:merge:list')
    }
  },
  watch: {
    '$route.query.module': {
      immediate: true,
      handler(value) {
        this.mode = this.normalizeMode(value)
        this.loadPage()
      }
    }
  },
  created() {
    // 负责人下拉：仅对有新增/编辑/分配权限的用户加载（参考 lead/index.vue）
    if (this.$auth.hasPermiOr(['customer:add', 'customer:edit', 'customer:assign'])) {
      listCustomerOwner().then(res => { this.ownerOptions = res.data || [] })
    }
    listSetting({ settingType: 'source' }).then(res => {
      this.leadSourceOptions = (res.data || []).filter(item => item.status === '0')
    })
    // 启用中的标签下拉
    listTag({ status: this.dictValue('sys_normal_disable', '0') }).then(res => {
      this.tagOptions = res.data || []
    })
  },
  methods: {
    normalizeMode(value) {
      return this.availableModes.includes(value) ? value : 'list'
    },
    loadPage() {
      if (this.mode === 'list') this.loadCustomers()
      else if (this.mode === 'contact') this.loadContacts()
      else if (this.mode === 'followup') this.loadFollowups()
      else if (this.mode === 'tag') this.loadTags()
      else if (this.mode === 'merge') this.loadMerge()
    },
    loadCustomers() {
      this.loading = true
      getCustomerDashboard().then(res => { this.metrics = (res.data && res.data.cards) || [] })
      listCustomer(this.query).then(res => {
        this.list = res.rows || []
        this.total = res.total || 0
      }).finally(() => { this.loading = false })
    },
    loadContacts() {
      this.loading = true
      listContact(this.query).then(res => {
        this.contacts = res.rows || []
        this.total = res.total || 0
        const customerIds = new Set(this.contacts.map(item => item.customer_id || item.customerId).filter(Boolean))
        this.contactMetrics = [
          { metricKey: 'total', metricValue: this.total },
          { metricKey: 'keyContacts', metricValue: this.contacts.filter(item => this.sameValue(item.key_contact, this.dictValue('law_yes_no_flag', '1'))).length },
          { metricKey: 'customers', metricValue: customerIds.size },
          { metricKey: 'withMobile', metricValue: this.contacts.filter(item => item.mobile).length }
        ]
      }).finally(() => { this.loading = false })
    },
    loadFollowups() {
      this.loading = true
      listFollowup(this.query).then(res => {
        this.followups = res.rows || []
        this.total = res.total || 0
      }).finally(() => { this.loading = false })
    },
    loadTags() { listTag({}).then(res => { this.tags = res.data || [] }) },
    loadMerge() {
      listMergeCandidates(this.mergeQuery).then(res => {
        this.mergeList = res.data || []
        this.mergeMetrics = [
          { metricKey: 'candidates', metricValue: this.mergeList.length },
          { metricKey: 'withMobile', metricValue: this.mergeList.filter(item => item.mobile).length },
          { metricKey: 'withCompany', metricValue: this.mergeList.filter(item => item.companyName || item.company_name).length },
          { metricKey: 'withCreditCode', metricValue: this.mergeList.filter(item => item.creditCode || item.credit_code).length }
        ]
      })
    },
    search() { this.query.pageNum = 1; this.loadPage() },
    reset() {
      const pageSize = this.query.pageSize || 10
      if (this.mode === 'list') this.query = { pageNum: 1, pageSize, customerName: '', customerType: '', industry: '', tagId: '', ownerId: '', customerLevel: '' }
      else if (this.mode === 'contact') this.query = { pageNum: 1, pageSize, contactName: '', customerName: '', mobile: '' }
      else this.query = { pageNum: 1, pageSize }
      this.loadPage()
    },
    openAdvanced() { this.advancedOpen = true },
    applyAdvanced() { this.advancedOpen = false; this.search() },
    resetAdvanced() { this.query.ownerId = ''; this.query.customerLevel = ''; this.applyAdvanced() },
    resetMerge() { this.mergeQuery = {}; this.loadMerge() },
    tagValue(item) { return item.tag_id !== undefined ? item.tag_id : item.tagId },
    tagLabel(item) { return item.tag_name || item.tagName },
    customerTags(row) {
      const names = String(row.tagNames || '').split(',').filter(Boolean)
      const colors = String(row.tagColors || '').split(',')
      return names.map((name, index) => ({ name, color: colors[index] || '#2563eb' }))
    },
    customerContractCountOf(row) {
      const value = row.contractCount !== undefined ? row.contractCount : row.contract_count
      const numberValue = Number(value)
      return Number.isNaN(numberValue) ? 0 : numberValue
    },
    followIcon(type) { return ({ wechat: 'message', meeting: 'peoples', email: 'email' })[type] || 'phone' },
    searchCustomerOptions(keyword) {
      this.customerSelectLoading = true
      listCustomer({ pageNum: 1, pageSize: 20, customerName: keyword || '' }).then(res => {
        this.customerOptions = res.rows || []
      }).finally(() => { this.customerSelectLoading = false })
    },
    clearFormValidate(refName, props) {
      this.$nextTick(() => {
        if (this.$refs[refName]) {
          this.$refs[refName].clearValidate(props)
        }
      })
    },
    normalizeMapRow(row, fields) {
      const result = { ...row }
      fields.forEach(([camel, snake]) => {
        if (result[camel] === undefined && result[snake] !== undefined) {
          result[camel] = result[snake]
        }
      })
      return result
    },
    ensureCustomerOption(row) {
      const customerId = row && (row.customerId || row.customer_id)
      const customerName = row && (row.customerName || row.customer_name)
      if (customerId && customerName && !this.customerOptions.some(item => item.customerId === customerId)) {
        this.customerOptions.unshift({ customerId, customerName, mobile: row.mobile, companyName: row.companyName || row.company_name, customerNo: row.customerNo || row.customer_no, status: row.status })
      }
    },
    selectCustomerForContact(customerId) {
      const item = this.customerOptions.find(customer => String(customer.customerId) === String(customerId))
      if (item) this.contactForm.customerName = item.customerName
      this.clearFormValidate('contactFormRef', ['customerId'])
    },
    selectCustomerForFollow(customerId) {
      const item = this.customerOptions.find(customer => String(customer.customerId) === String(customerId))
      if (item) this.followForm.customerName = item.customerName
      this.clearFormValidate('followFormRef', ['customerId'])
    },
    selectMainCustomer(customerId) {
      const item = this.customerOptions.find(customer => String(customer.customerId) === String(customerId))
      if (item) this.mergeForm.mainCustomerName = item.customerName
      this.clearFormValidate('mergeFormRef', ['mainCustomerId'])
    },
    defaultLeadSource() {
      const item = this.leadSourceOptions.find(option => option.status === '0') || this.leadSourceOptions[0]
      return item ? item.settingCode : ''
    },
    defaultOwnerId() {
      const item = this.ownerOptions[0]
      return item ? item.userId : ''
    },
    openCustomer(row) {
      if (row && !this.canOperateCustomer(row)) return
      this.form = row ? this.normalizeMapRow(row, [
        ['customerId', 'customer_id'],
        ['customerName', 'customer_name'],
        ['customerType', 'customer_type'],
        ['companyName', 'company_name'],
        ['creditCode', 'credit_code'],
        ['customerLevel', 'customer_level'],
        ['mainDemand', 'main_demand'],
        ['sourceCode', 'source_code'],
        ['ownerId', 'owner_id']
      ]) : {
        customerType: this.dictDefault('law_customer_type'),
        customerLevel: this.dictDefault('law_customer_level'),
        industry: this.dictDefault('law_customer_industry'),
        sourceCode: this.defaultLeadSource(),
        ownerId: this.defaultOwnerId()
      }
      this.customerOpen = true
      this.clearFormValidate('customerForm')
    },
    openDetail(row) {
      const customerId = row.customerId
      this.detailOpen = true
      this.loadDetail(customerId, row)
    },
    loadDetail(customerId, seed = {}) {
      this.detailCustomerId = customerId
      this.detailCustomer = { ...seed }
      this.detailContacts = []
      this.detailFollowups = []
      this.detailContracts = []
      this.detailMergeLogs = []
      const contactRequest = this.canReadContact ? listContact({ customerId, pageNum: 1, pageSize: 5 }) : Promise.resolve({ rows: [] })
      const followupRequest = this.canReadFollowup ? listFollowup({ customerId, pageNum: 1, pageSize: 5 }) : Promise.resolve({ rows: [] })
      const contractRequest = this.canReadContract ? listContract({ customerId, pageNum: 1, pageSize: 5 }) : Promise.resolve({ rows: [] })
      const mergeLogRequest = this.canReadMergeLog ? listMergeLogs({ customerId, pageNum: 1, pageSize: 5 }) : Promise.resolve({ rows: [] })
      Promise.all([
        getCustomer(customerId),
        contactRequest,
        followupRequest,
        contractRequest,
        mergeLogRequest
      ]).then(([detail, contacts, followups, contracts, mergeLogs]) => {
        this.detailCustomer = detail.data || {}
        this.detailContacts = contacts.rows || []
        this.detailFollowups = followups.rows || []
        this.detailContracts = contracts.rows || []
        this.detailMergeLogs = mergeLogs.rows || []
      })
    },
    refreshDetailIfOpen(customerId) {
      const targetId = customerId || this.detailCustomerId
      if (this.detailOpen && targetId) {
        this.loadDetail(targetId, this.detailCustomer)
      }
    },
    saveCustomer() {
      this.$refs.customerForm.validate(valid => {
        if (!valid) return
        ;(this.form.customerId ? updateCustomer : addCustomer)(this.form).then(() => {
          this.$modal.msgSuccess('保存成功')
          this.customerOpen = false
          this.loadPage()
          this.refreshDetailIfOpen(this.form.customerId)
        })
      })
    },
    removeCustomer(row) { this.$modal.confirm('确认删除该客户吗？').then(() => delCustomer(row.customerId)).then(() => { this.$modal.msgSuccess('删除成功'); this.loadPage() }).catch(() => {}) },
    openImport() { this.$refs.importRef.open() },
    handleExport() { this.download('customer/export', { ...this.query }, `customer_${Date.now()}.xlsx`) },
    openContact(row) {
      this.ensureCustomerOption(row)
      const isContactRow = row && row.contact_id
      this.contactForm = isContactRow
        ? { contactId: row.contact_id, customerId: row.customer_id || row.customerId, contactName: row.contact_name, mobile: row.mobile, relationType: row.relation_type, keyContact: row.key_contact }
        : { customerId: row && row.customerId, customerName: row && row.customerName, keyContact: this.dictDefault('law_yes_no_flag'), relationType: this.dictDefault('law_contact_relation') }
      if (!row) this.searchCustomerOptions('')
      this.contactOpen = true
      this.clearFormValidate('contactFormRef')
    },
    saveContact() {
      this.$refs.contactFormRef.validate(valid => {
        if (!valid) return
        ;(this.contactForm.contactId ? updateContact : addContact)(this.contactForm).then(() => { this.$modal.msgSuccess('保存成功'); this.contactOpen = false; this.loadPage(); this.refreshDetailIfOpen(this.contactForm.customerId) })
      })
    },
    removeContact(row) {
      this.$modal.confirm('确认删除该联系人吗？').then(() => delContact(row.contact_id)).then(() => {
        this.$modal.msgSuccess('删除成功')
        this.loadPage()
        this.refreshDetailIfOpen(row.customer_id || row.customerId)
      }).catch(() => {})
    },
    openFollowup(row) {
      if (row && !this.canOperateCustomer(row)) return
      this.ensureCustomerOption(row)
      const followType = this.dictDefault('law_customer_follow_type')
      this.followForm = row ? { customerId: row.customerId || row.customer_id, followType } : { followType }
      if (!row) this.searchCustomerOptions('')
      this.followOpen = true
      this.clearFormValidate('followFormRef')
    },
    saveFollowup() {
      this.$refs.followFormRef.validate(valid => {
        if (!valid) return
        addFollowup(this.followForm).then(() => { this.$modal.msgSuccess('保存成功'); this.followOpen = false; this.loadPage(); this.refreshDetailIfOpen(this.followForm.customerId) })
      })
    },
    removeFollowup(row) {
      this.$modal.confirm('确认删除该跟进记录吗？').then(() => delFollowup(row.followup_id)).then(() => {
        this.$modal.msgSuccess('删除成功')
        this.loadPage()
        this.refreshDetailIfOpen(row.customer_id || row.customerId)
      }).catch(() => {})
    },
    openTag(row) {
      this.tagForm = row ? { tagId: row.tag_id, tagName: row.tag_name, tagColor: row.tag_color, orderNum: row.order_num, status: row.status } : {
        tagColor: '#3b82f6',
        orderNum: 0,
        status: this.dictDefault('sys_normal_disable')
      }
      this.tagOpen = true
      this.clearFormValidate('tagFormRef')
    },
    saveTag() {
      this.$refs.tagFormRef.validate(valid => {
        if (!valid) return
        ;(this.tagForm.tagId ? updateTag : addTag)(this.tagForm).then(() => { this.$modal.msgSuccess('保存成功'); this.tagOpen = false; this.loadPage() })
      })
    },
    removeTag(row) { this.$modal.confirm('确认删除该客户标签吗？').then(() => delTag(row.tag_id)).then(() => { this.$modal.msgSuccess('删除成功'); this.loadPage() }).catch(() => {}) },
    openCustomerTags(row) {
      if (!this.canOperateCustomer(row)) return
      this.customerTagForm = { customerId: row.customerId, customerName: row.customerName, tagIds: [] }
      this.customerTagOptions = []
      Promise.all([
        listTag({ status: this.dictValue('sys_normal_disable', '0') }),
        getCustomerTags(row.customerId)
      ]).then(([tags, selected]) => {
        this.customerTagOptions = tags.data || []
        this.customerTagForm.tagIds = (selected.data || []).map(item => Number(item))
        this.customerTagOpen = true
      })
    },
    saveCustomerTags() {
      setCustomerTags(this.customerTagForm.customerId, this.customerTagForm.tagIds).then(() => {
        this.$modal.msgSuccess('标签保存成功')
        this.customerTagOpen = false
        this.loadPage()
        this.refreshDetailIfOpen(this.customerTagForm.customerId)
      })
    },
    openMerge(row) {
      this.ensureCustomerOption(row)
      this.mergeForm = {
        mainCustomerId: null,
        mainCustomerName: '',
        mergedCustomerId: row.customerId,
        mergedCustomerName: row.customerName,
        content: '手工合并'
      }
      this.searchCustomerOptions('')
      this.mergeOpen = true
      this.clearFormValidate('mergeFormRef')
    },
    saveMerge() {
      this.$refs.mergeFormRef.validate(valid => {
        if (!valid) return
        if (this.mergeForm.mainCustomerId === this.mergeForm.mergedCustomerId) {
          this.$modal.msgError('主客户和待合并客户不能相同')
          return
        }
        mergeCustomer(this.mergeForm).then(() => {
          this.$modal.msgSuccess('合并成功')
          this.mergeOpen = false
          this.loadMerge()
          this.refreshDetailIfOpen(this.mergeForm.mainCustomerId)
        })
      })
    },
    newContract(row) {
      if (!this.canOperateCustomer(row)) return
      this.$router.push({ path: '/contract/list', query: { module: 'list', customerId: row.customerId, customerName: row.customerName, createContract: '1' } })
    },
    viewMatter(row) {
      this.$router.push({ path: '/matter/list', query: { module: 'list', customerId: row.customerId } })
    }
  }
}
</script>

<style scoped lang="scss">
@import "../business/business.scss";

.customer-page {
  --biz-filter-input-width: 210px;
  --biz-filter-select-width: 128px;
}

.type-wechat{background:#10b981}.type-meeting{background:#f59e0b}.type-email{background:#8b5cf6}
.tag-pills{display:flex;flex-wrap:wrap;gap:5px}.tag-pills i{padding:2px 7px;border:1px solid;border-radius:10px;background:#fff;font-size:var(--biz-font-mini);font-style:normal;line-height:1.4}
.contract-count{display:inline-flex;align-items:center;justify-content:center;min-width:28px;height:22px;padding:0 8px;border-radius:999px;color:#2563eb;background:#eef4ff;font-weight:600;font-size:var(--biz-font-small)}
</style>

<style lang="scss">
@import "../business/business-dialog.scss";
</style>
