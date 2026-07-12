<template>
  <div class="biz-page contract-page" :class="'biz-size-' + appSize">
    <biz-page-header eyebrow="CONTRACT CENTER" :title="pageTitle" :description="pageDescription">
      <el-button v-if="mode === 'list'" v-hasPermi="['contract:import']" :size="controlSize" plain icon="el-icon-upload2" @click="openImport">å¯¼å…¥</el-button>
      <el-button v-if="mode === 'list'" v-hasPermi="['contract:export']" :size="controlSize" plain icon="el-icon-download" @click="handleExport">å¯¼å‡º</el-button>
      <el-button v-if="mode === 'list'" v-hasPermi="['contract:add']" :size="controlSize" type="primary" icon="el-icon-plus" @click="openContract()">æ–°å»ºåˆåŒ</el-button>
      <el-button v-if="mode === 'template'" v-hasPermi="['contract:template:add']" :size="controlSize" type="primary" icon="el-icon-plus" @click="openTemplate()">æ–°å¢žæ¨¡æ¿</el-button>
      <el-button v-if="mode === 'fee'" v-hasPermi="['contract:fee:add']" :size="controlSize" type="primary" icon="el-icon-plus" @click="openFee()">æ–°å¢žæ”¶è´¹è®¡åˆ’</el-button>
      <el-button v-if="mode === 'attachment'" v-hasPermi="['contract:attachment:add']" :size="controlSize" type="primary" icon="el-icon-plus" @click="openAttachment()">ä¸Šä¼ é™„ä»¶</el-button>
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
          <el-input v-model="query.keyword" :size="controlSize" prefix-icon="el-icon-search" placeholder="æœç´¢åˆåŒç¼–å·ã€åˆåŒåç§°ã€å®¢æˆ·åç§°" clearable @clear="search" @keyup.enter.native="search" />
          <el-select v-model="query.caseType" :size="controlSize" placeholder="æ¡ˆä»¶ç±»åž‹ï¼šå…¨éƒ¨" clearable @change="search">
            <el-option v-for="item in dict.type.law_contract_case_type" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-if="mode === 'list'" v-model="query.auditStatus" :size="controlSize" placeholder="å®¡æ ¸çŠ¶æ€ï¼šå…¨éƒ¨" clearable @change="search">
            <el-option v-for="item in dict.type.law_contract_audit_status" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="query.contractStatus" :size="controlSize" placeholder="åˆåŒçŠ¶æ€ï¼šå…¨éƒ¨" clearable @change="search">
            <el-option v-for="item in dict.type.law_contract_status" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </div>
        <div class="biz-filter-actions">
          <el-popover v-model="advancedOpen" placement="bottom-end" width="380" trigger="click" popper-class="business-advanced-popover">
            <div class="advanced-filter-panel">
              <div class="advanced-title">
                <strong>é«˜çº§ç­›é€‰</strong>
                <span>ç»„åˆç­¾è®¢çŠ¶æ€ä¸Žè´Ÿè´£äººå®šä½åˆåŒ</span>
              </div>
              <el-form label-position="top">
                <el-form-item label="ç­¾è®¢çŠ¶æ€">
                  <el-select v-model="query.signStatus" :size="controlSize" placeholder="å…¨éƒ¨ç­¾è®¢çŠ¶æ€" clearable>
                    <el-option v-for="item in dict.type.law_contract_sign_status" :key="item.value" :label="item.label" :value="item.value" />
                  </el-select>
                </el-form-item>
                <el-form-item label="è´Ÿè´£äºº">
                  <el-select v-model="query.ownerId" :size="controlSize" placeholder="å…¨éƒ¨è´Ÿè´£äºº" clearable filterable>
                    <el-option v-for="item in ownerOptions" :key="item.userId" :label="item.nickName" :value="item.userId" />
                  </el-select>
                </el-form-item>
              </el-form>
              <div class="advanced-actions">
                <el-button :size="controlSize" @click="resetAdvanced">é‡ç½®</el-button>
                <el-button :size="controlSize" type="primary" @click="applyAdvanced">åº”ç”¨ç­›é€‰</el-button>
              </div>
            </div>
            <el-button slot="reference" :size="controlSize" plain icon="el-icon-s-operation">é«˜çº§ç­›é€‰</el-button>
          </el-popover>
          <el-button :size="controlSize" plain icon="el-icon-refresh" @click="reset">é‡ç½®</el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="contracts" :size="controlSize">
          <el-table-column label="åˆåŒç¼–å·" prop="contractNo" min-width="150" align="center" />
          <el-table-column label="åˆåŒä¿¡æ¯" min-width="180">
            <template slot-scope="{ row }">
              <a class="biz-link" @click="openDetail(row)">{{ row.contractName }}</a>
              <span class="sub-text">{{ row.customerName || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="æ¡ˆä»¶ç±»åž‹" width="105" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_contract_case_type" :value="row.caseType" /></template></el-table-column>
          <el-table-column label="ç­¾çº¦é‡‘é¢" width="112" align="center"><template slot-scope="{ row }">{{ formatMoney(row.signAmount) }}</template></el-table-column>
          <el-table-column label="å®¡æ ¸çŠ¶æ€" width="105" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_contract_audit_status" :value="auditStatusOf(row)" /></template></el-table-column>
          <el-table-column label="ç­¾è®¢çŠ¶æ€" width="105" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_contract_sign_status" :value="signStatusOf(row)" /></template></el-table-column>
          <el-table-column label="åˆåŒçŠ¶æ€" width="105" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_contract_status" :value="contractStatusOf(row)" /></template></el-table-column>
          <el-table-column label="è´Ÿè´£äºº" width="104" align="center"><template slot-scope="{ row }"><span class="owner-cell"><i>{{ avatar(row.ownerName) }}</i>{{ row.ownerName || '-' }}</span></template></el-table-column>
          <el-table-column label="æ“ä½œ" :width="mode === 'approval' ? 128 : 318" align="center" class-name="small-padding fixed-width biz-operation-column" fixed="right">
            <template slot-scope="{ row }">
              <span class="action-buttons">
                <el-button v-hasPermi="['contract:query']" :size="controlSize" type="text" icon="el-icon-view" @click="openDetail(row)">è¯¦æƒ…</el-button>
                <el-button v-if="mode === 'list'" v-hasPermi="['contract:edit']" :size="controlSize" type="text" icon="el-icon-edit" :disabled="!canEditContract(row)" @click="openContract(row)">ç¼–è¾‘</el-button>
                <el-button v-if="mode === 'list'" v-hasPermi="['contract:submit']" :size="controlSize" type="text" icon="el-icon-s-check" :disabled="!canSubmitContract(row)" @click="submitOne(row)">æäº¤å®¡æ‰¹</el-button>
                <el-button v-if="mode === 'approval'" v-hasPermi="['contract:approval:handle']" :size="controlSize" type="text" icon="el-icon-check" :disabled="!isContractAuditReviewing(row)" @click="openApproval(row)">å®¡æ‰¹</el-button>
                <el-button v-if="mode === 'list' && canSignContract(row)" v-hasPermi="['contract:sign']" :size="controlSize" type="text" icon="el-icon-finished" @click="signOne(row)">{{ signActionText(row) }}</el-button>
                <el-button v-if="mode === 'list' && canArchiveContract(row)" v-hasPermi="['contract:archive']" :size="controlSize" type="text" icon="el-icon-folder-checked" @click="archiveOne(row)">å½’æ¡£</el-button>
                <el-button v-if="mode === 'list' && canVoidContract(row)" v-hasPermi="['contract:void']" :size="controlSize" type="text" icon="el-icon-circle-close" @click="voidOne(row)">ä½œåºŸ</el-button>
                <el-button v-if="mode === 'list' && canTerminateContract(row)" v-hasPermi="['contract:terminate']" :size="controlSize" type="text" icon="el-icon-remove-outline" @click="terminateOne(row)">ç»ˆæ­¢</el-button>
                <el-button v-if="mode === 'list'" v-hasPermi="['contract:remove']" :size="controlSize" type="text" icon="el-icon-delete" class="danger-text" :disabled="!canRemoveContract(row)" @click="removeContract(row)">åˆ é™¤</el-button>
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
          <el-input v-model="query.templateName" :size="controlSize" prefix-icon="el-icon-search" placeholder="æœç´¢æ¨¡æ¿åç§°" clearable @clear="search" @keyup.enter.native="search" />
          <el-select v-model="query.caseType" :size="controlSize" placeholder="æ¡ˆä»¶ç±»åž‹ï¼šå…¨éƒ¨" clearable @change="search">
            <el-option v-for="item in dict.type.law_contract_case_type" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="query.status" :size="controlSize" placeholder="çŠ¶æ€ï¼šå…¨éƒ¨" clearable @change="search">
            <el-option v-for="item in dict.type.sys_normal_disable" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </div>
        <div class="biz-filter-actions">
          <el-button :size="controlSize" plain icon="el-icon-refresh" @click="reset">é‡ç½®</el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="templates" :size="controlSize">
          <el-table-column label="æ¨¡æ¿åç§°" prop="templateName" min-width="180" />
          <el-table-column label="æ¡ˆä»¶ç±»åž‹" width="120" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_contract_case_type" :value="row.caseType" /></template></el-table-column>
          <el-table-column label="æ–‡ä»¶å" prop="fileName" min-width="170" show-overflow-tooltip>
            <template slot-scope="{ row }"><a class="biz-link" @click="openTemplateFile(row)">{{ row.fileName || fileNameFromUrl(row.fileUrl) }}</a></template>
          </el-table-column>
          <el-table-column label="ç‰ˆæœ¬" prop="versionNo" width="90" align="center" />
          <el-table-column label="çŠ¶æ€" width="90" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.sys_normal_disable" :value="row.status" /></template></el-table-column>
          <el-table-column label="æ“ä½œ" width="164" align="center" class-name="small-padding fixed-width"><template slot-scope="{ row }"><el-button :size="controlSize" type="text" icon="el-icon-view" @click="openTemplateFile(row)">æ‰“å¼€</el-button><el-button v-hasPermi="['contract:template:edit']" :size="controlSize" type="text" icon="el-icon-edit" @click="openTemplate(row)">ç¼–è¾‘</el-button><el-button v-hasPermi="['contract:template:remove']" :size="controlSize" type="text" icon="el-icon-delete" class="danger-text" :disabled="isEnabled(row.status)" title="å¯ç”¨ä¸­çš„æ¨¡æ¿è¯·å…ˆåœç”¨å†åˆ é™¤" @click="removeTemplate(row)">åˆ é™¤</el-button></template></el-table-column>
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
          <el-input v-model="query.keyword" :size="controlSize" prefix-icon="el-icon-search" placeholder="æœç´¢åˆåŒç¼–å·ã€åˆåŒåç§°ã€å®¢æˆ·åç§°" clearable @clear="search" @keyup.enter.native="search" />
          <el-select v-model="query.confirmStatus" :size="controlSize" placeholder="ç¡®è®¤çŠ¶æ€ï¼šå…¨éƒ¨" clearable @change="search">
            <el-option v-for="item in dict.type.law_contract_receive_status" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="query.invoiceStatus" :size="controlSize" placeholder="å¼€ç¥¨çŠ¶æ€ï¼šå…¨éƒ¨" clearable @change="search">
            <el-option v-for="item in dict.type.law_contract_invoice_status" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </div>
        <div class="biz-filter-actions">
          <el-button :size="controlSize" plain icon="el-icon-refresh" @click="reset">é‡ç½®</el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="fees" :size="controlSize">
          <el-table-column label="åˆåŒç¼–å·" prop="contractNo" min-width="150" />
          <el-table-column label="å®¢æˆ·åç§°" prop="customerName" min-width="150" />
          <el-table-column label="æœŸæ•°" prop="period_no" width="80" align="center" />
          <el-table-column label="åº”æ”¶é‡‘é¢" width="110" align="center"><template slot-scope="{ row }">{{ formatMoney(row.receivable_amount) }}</template></el-table-column>
          <el-table-column label="å®žæ”¶é‡‘é¢" width="110" align="center"><template slot-scope="{ row }">{{ formatMoney(row.received_amount) }}</template></el-table-column>
          <el-table-column label="è®¡åˆ’æ”¶æ¬¾æ—¥" prop="plan_receive_date" width="120" align="center" />
          <el-table-column label="ç¡®è®¤çŠ¶æ€" width="110" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_contract_receive_status" :value="receiveStatusOf(row)" /></template></el-table-column>
          <el-table-column label="å¼€ç¥¨çŠ¶æ€" width="110" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_contract_invoice_status" :value="invoiceStatusOf(row)" /></template></el-table-column>
          <el-table-column label="æ“ä½œ" width="230" align="center" class-name="small-padding fixed-width">
            <template slot-scope="{ row }">
              <el-button v-if="isFeePending(row)" v-hasPermi="['contract:fee:confirm']" :size="controlSize" type="text" icon="el-icon-check" :disabled="!canConfirmFee(row)" @click="confirmFeeOne(row)">ç¡®è®¤</el-button>
              <el-button v-if="isFeePending(row)" v-hasPermi="['contract:fee:reject']" :size="controlSize" type="text" icon="el-icon-close" class="danger-text" :disabled="!canRejectFee(row)" @click="rejectFeeOne(row)">é©³å›ž</el-button>
              <el-button v-if="canInvoiceFee(row)" v-hasPermi="['contract:f×_{òÚ$z{-®éÜj×–6²‚‚’Óâ°¢–b‡F†—2âG&Vg5·&VdæÖUÒ’°¢F†—2âG&Vg5·&VdæÖUÒæ6ÆV%fÆ–FFR‡&÷2¢Ð¢Ò¢ÒÀ¢æ÷&ÖÆ—¦TÖ&÷r‡&÷rÂf–VÆG2’°¢6öç7B&W7VÇBÒ²ââç&÷rÐ¢f–VÆG2æf÷$V6‚‚…¶6ÖVÂÂ6æ¶UÒ’Óâ°¢–b‡&W7VÇE¶6ÖVÅÒÓÓÒVæFVf–æVBbb&W7VÇE·6æ¶UÒÓÒVæFVf–æVB’°¢&W7VÇE¶6ÖVÅÒÒ&W7VÇE·6æ¶UÐ¢Ð¢Ò¢&WGW&â&W7VÇ@¢ÒÀ¢6VÆV7D7W7FöÖW$f÷$6öçG&7B†7W7FöÖW$–B’°¢6öç7B—FVÒÒF†—2æ7W7FöÖW$÷F–öç2æf–æB†7W7FöÖW"Óâ7G&–ær†7W7FöÖW"æ7W7FöÖW$–B’ÓÓÒ7G&–ær†7W7FöÖW$–B’¢–b†—FVÒ’F†—2æ6öçG&7Df÷&Òæ7W7FöÖW$æÖRÒ—FVÒæ7W7FöÖW$æÖP¢F†—2æ6ÆV$f÷&ÕfÆ–FFR‚v6öçG&7Df÷&ÒrÂ²v7W7FöÖW$–BrÂv7W7FöÖW$æÖRuÒ¢ÒÀ¢6VÆV7D6öçG&7Df÷$fVR†6öçG&7D–B’°¢6öç7B—FVÒÒF†—2æ6öçG&7D÷F–öç2æf–æB†6öçG&7BÓâ7G&–ær†6öçG&7Bæ6öçG&7D–B’ÓÓÒ7G&–ær†6öçG&7D–B’¢–b†—FVÒ’F†—2æfVTf÷&Òæ6öçG&7DæÖRÒ—FVÒæ6öçG&7DæÖP¢F†—2æ6ÆV$f÷&ÕfÆ–FFR‚vfVTf÷&Õ&VbrÂ²v6öçG&7D–BuÒ¢ÒÀ¢6VÆV7D6öçG&7Df÷$GF6†ÖVçB†6öçG&7D–B’°¢6öç7B—FVÒÒF†—2æ6öçG&7D÷F–öç2æf–æB†6öçG&7BÓâ7G&–ær†6öçG&7Bæ6öçG&7D–B’ÓÓÒ7G&–ær†6öçG&7D–B’¢–b†—FVÒ’F†—2æGF6†ÖVçDf÷&Òæ6öçG&7DæÖRÒ—FVÒæ6öçG&7DæÖP¢F†—2æ6ÆV$f÷&ÕfÆ–FFR‚vGF6†ÖVçDf÷&Õ&VbrÂ²v6öçG&7D–BuÒ¢ÒÀ¢÷Vä6öçG&7B‡&÷r’°¢F†—2æVç7W&T7W7FöÖW$÷F–öâ‡&÷r¢F†—2æ6öçG&7Df÷&ÒÒ&÷rò°¢ââçF†—2ææ÷&ÖÆ—¦TÖ&÷r‡&÷rÂ°¢²v6öçG&7D–BrÂv6öçG&7Eö–BuÒÀ¢²v6öçG&7DæÖRrÂv6öçG&7EöæÖRuÒÀ¢²v7W7FöÖW$–BrÂv7W7FöÖW%ö–BuÒÀ¢²v7W7FöÖW$æÖRrÂv7W7FöÖW%öæÖRuÒÀ¢²v66UG—RrÂv66U÷G—RuÒÀ¢²w6–väÖ÷VçBrÂw6–våöÖ÷VçBuÒÀ¢²vfVUG—RrÂvfVU÷G—RuÒÀ¢²w6–väÖWF†öBrÂw6–våöÖWF†öBuÒÀ¢²w&—6´ÆWfVÂrÂw&—6µöÆWfVÂuÐ¢Ò’À¢6–vå7FGW3¢VæFVf–æV@¢Ò¢°¢ââçF†—2æ6öçG&7Df÷&ÒÀ¢66UG—S¢F†—2æF–7DFVfVÇB‚vÆuö6öçG&7Eö66U÷G—Rr’À¢fVUG—S¢F†—2æF–7DFVfVÇB‚vÆuö6öçG&7EöfVU÷G—Rr’À¢6–väÖWF†öC¢F†—2æF–7DFVfVÇB‚vÆuö6öçG&7E÷6–våöÖWF†öBr’À¢VF—E7FGW3¢F†—2æF–7DFVfVÇB‚vÆuö6öçG&7EöVF—E÷7FGW2r’À¢6öçG&7E7FGW3¢F†—2æF–7DFVfVÇB‚vÆuö6öçG&7E÷7FGW2r’À¢&—6´ÆWfVÃ¢F†—2æF–7DFVfVÇB‚vÆuö6öçG&7E÷&—6µöÆWfVÂr’À¢6–väÖ÷VçC¢ ¢Ð¢–b‚&÷rbbF†—2æ6öçG&7Df÷&Òæ7W7FöÖW$–B’F†—2ç6V&6„7W7FöÖW$÷F–öç2‚rr¢F†—2æ6öçG&7D÷VâÒG'VP¢F†—2æ6ÆV$f÷&ÕfÆ–FFR‚v6öçG&7Df÷&Òr¢ÒÀ¢÷VäFWF–Â‡&÷r’°¢6öç7B6öçG&7D–BÒ&÷ræ6öçG&7D–@¢F†—2æFWF–Ä÷VâÒG'VP¢F†—2æÆöDFWF–Â†6öçG&7D–BÂ&÷r¢ÒÀ¢ÆöDFWF–Â†6öçG&7D–BÂ6VVBÒ·Ò’°¢F†—2æFWF–Ä6öçG&7BÒ²ââç6VVBÐ¢F†—2æFWF–ÄfVW2ÒµÐ¢F†—2æFWF–ÄGF6†ÖVçG2ÒµÐ¢F†—2æFWF–Ä&÷fÇ2ÒµÐ¢F†—2æFWF–Å7FGW6W2ÒµÐ¢6öç7BfVU&WVW7BÒF†—2æ6å&VDfVRòÆ—7DfVR‡²6öçG&7D–BÂvTçVÓ¢ÂvU6—¦S¢RÒ’¢&öÖ—6Rç&W6öÇfR‡²&÷w3¢µÒÒ¢6öç7BGF6†ÖVçE&WVW7BÒF†—2æ6å&VDGF6†ÖVçBòÆ—7DGF6†ÖVçB‡²6öçG&7D–BÂvTçVÓ¢ÂvU6—¦S¢RÒ’¢&öÖ—6Rç&W6öÇfR‡²&÷w3¢µÒÒ¢6öç7B&÷fÅ&WVW7BÒF†—2æ6å&VD&÷fÂòÆ—7D&÷fÂ‡²6öçG&7D–BÂvTçVÓ¢ÂvU6—¦S¢RÒ’¢&öÖ—6Rç&W6öÇfR‡²&÷w3¢µÒÒ¢6öç7B7FGW5&WVW7BÒF†—2æ6å&VE7FGW2òÆ—7E7FGW2‡²6öçG&7D–BÂvTçVÓ¢ÂvU6—¦S¢RÒ’¢&öÖ—6Rç&W6öÇfR‡²&÷w3¢µÒÒ¢&öÖ—6RæÆÂ…°¢vWD6öçG&7B†6öçG&7D–B’À¢fVU&WVW7BÀ¢GF6†ÖVçE&WVW7BÀ¢&÷fÅ&WVW7BÀ¢7FGW5&WVW7@¢Ò’çF†Vâ‚…¶FWF–ÂÂfVW2ÂGF6†ÖVçG2Â&÷fÇ2Â7FGW6W5Ò’Óâ°¢F†—2æFWF–Ä6öçG&7BÒFWF–ÂæFFÇÂ·Ð¢F†—2æFWF–ÄfVW2ÒfVW2ç&÷w2ÇÂµÐ¢F†—2æFWF–ÄGF6†ÖVçG2ÒGF6†ÖVçG2ç&÷w2ÇÂµÐ¢F†—2æFWF–Ä&÷fÇ2Ò&÷fÇ2ç&÷w2ÇÂµÐ¢F†—2æFWF–Å7FGW6W2Ò7FGW6W2ç&÷w2ÇÂµÐ¢Ò¢ÒÀ¢&Vg&W6„FWF–Ä–d÷Vâ‡&÷r’°¢6öç7B6öçG&7D–BÒ&÷rbb‡&÷ræ6öçG&7D–BÇÂ&÷ræ6öçG&7Eö–B¢–b‡F†—2æFWF–Ä÷Vâbb6öçG&7D–B’°¢F†—2æÆöDFWF–Â†6öçG&7D–BÂ&÷r¢Ð¢ÒÀ¢f–WtÖGFW"‡&÷r’°¢F†—2âG&÷WFW"çW6‚‡²Fƒ¢röÖGFW"öÆ—7BrÂVW'“¢²ÖöGVÆS¢vÆ—7BrÂ6öçG&7D–C¢&÷ræ6öçG&7D–BÇÂ&÷ræ6öçG&7Eö–BÒÒ¢ÒÀ¢†æFÆTFWF–Ä7F–öâ†7F–öâÂ&÷r’°¢7F–öâ‡&÷r¢ÒÀ¢6fT6öçG&7B‚’°¢F†—2âG&Vg2æ6öçG&7Df÷&ÒçfÆ–FFR‡fÆ–BÓâ°¢–b‚fÆ–B’&WGW&à¢²‡F†—2æ6öçG&7Df÷&Òæ6öçG&7D–BòWFFT6öçG&7B¢FD6öçG&7B’‡F†—2æ6öçG&7Df÷&Ò’çF†Vâ‚‚’Óâ°¢F†—2âFÖöFÂæ×6u7V66W72‚~KùÞZÙŽh‰X©òr¢F†—2æ6öçG&7D÷VâÒfÇ6P¢F†—2æÆöEvR‚¢F†—2ç&Vg&W6„FWF–Ä–d÷Vâ‡²6öçG&7D–C¢F†—2æ6öçG&7Df÷&Òæ6öçG&7D–BÒ¢Ò¢Ò¢ÒÀ¢&VÖ÷fT6öçG&7B‡&÷r’²F†—2âFÖöFÂæ6öæf—&Ò‚~zîŠêNXŠ™šNŠú^YŽYÎY	~ûÉòr’çF†Vâ‚‚’ÓâFVÄ6öçG&7B‡&÷ræ6öçG&7D–B’’çF†Vâ‚‚’Óâ²F†—2âFÖöFÂæ×6u7V66W72‚~XŠ™šNh‰X©òr“²F†—2æÆöEvR‚’Ò’æ6F6‚‚‚’Óâ·Ò’ÒÀ¢7V&Ö—DöæR‡&÷r’°¢&WGW&â7V&Ö—D6öçG&7B‡&÷ræ6öçG&7D–B’çF†Vâ‚‚’Óâ°¢F†—2âFÖöFÂæ×6u7V66W72‚~[{.hùKªNZêh›’r¢F†—2æÆöEvR‚¢F†—2ç&Vg&W6„FWF–Ä–d÷Vâ‡&÷r¢Ò¢ÒÀ¢6–vä7F–öåFW‡B‡&÷r’°¢&WGW&âF†—2ç6ÖUfÇVR‡F†—2ç6–vå7FGW4öb‡&÷r’ÂF†—2æ6öçG&7E7FFW2ç6–vå'F–Â’ò~Š^›ÙzÛî{Û"r¢~zÛî{Û"p¢ÒÀ¢–çfö–6T7F–öåFW‡B‡&÷r’°¢&WGW&âF†—2æ—4fVT–çfö–6U'F–Â‡&÷r’ò~Š^›Ù[ÈzZ‚r¢~[ÈzZ‚p¢ÒÀ¢÷Vä&÷fÂ‡&÷r’°¢6öç7B7F–öâÒF†—2æF–7DFVfVÇB‚vÆuö6öçG&7Eö&÷fÅö7F–öâr’ÇÂ‡F†—2æ&÷fÄ7F–öä÷F–öç5³ÒbbF†—2æ&÷fÄ7F–öä÷F–öç5³ÒçfÇVR’ÇÂw72p¢F†—2æ&÷fÄf÷&ÒÒ²6öçG&7D–C¢&÷ræ6öçG&7D–BÇÂ&÷ræ6öçG&7Eö–BÂ7F–öâÂ÷–æ–öã¢rrÐ¢F†—2æ&÷fÄ÷VâÒG'VP¢F†—2âFæW‡EF–6²‚‚’ÓâF†—2âG&Vg2æÆ–fV7–6ÆTF–Æöw2bbF†—2âG&Vg2æÆ–fV7–6ÆTF–Æöw2æ6ÆV$&÷fÂ‚’¢ÒÀ¢6fT&÷fÂ‚’°¢F†—2âG&Vg2æÆ–fV7–6ÆTF–Æöw2çfÆ–FFT&÷fÂ‡fÆ–BÓâ°¢–b‚fÆ–B’&WGW&à¢&÷fÄ6öçG&7B‡F†—2æ&÷fÄf÷&Ò’çF†Vâ‚‚’Óâ²F†—2âFÖöFÂæ×6u7V66W72‚~Zêh›žZèÎh‰r“²F†—2æ&÷fÄ÷VâÒfÇ6S²F†—2æÆöEvR‚“²F†—2ç&Vg&W6„FWF–Ä–d÷Vâ‡²6öçG&7D–C¢F†—2æ&÷fÄf÷&Òæ6öçG&7D–BÒ’Ò¢Ò¢ÒÀ¢6–väöæR‡&÷r’°¢6öç7B'F–ÂÒF†—2ç6ÖUfÇVR‡F†—2ç6–vå7FGW4öb‡&÷r’ÂF†—2æ6öçG&7E7FFW2ç6–vå'F–Â¢F†—2ç6–väf÷&ÒÒ°¢6öçG&7D–C¢&÷ræ6öçG&7D–BÇÂ&÷ræ6öçG&7Eö–BÀ¢6–vå7FGW3¢'F–ÂòF†—2æ6öçG&7E7FFW2ç6–vå6–væVB¢F†—2æ6öçG&7E7FFW2ç6–vå'F–ÂÀ¢'F–ÂÀ¢&÷p¢Ð¢F†—2ç6–vä÷VâÒG'VP¢ÒÀ¢6fU6–vâ‚’°¢–b‚F†—2ç6–väf÷&Òç6–vå7FGW2’°¢F†—2âFÖöFÂæ×6uv&æ–ær‚~Šû~˜žhºžzÛî{Û.XªŽKÙÂr¢&WGW&à¢Ð¢6–vä6öçG&7B‡²6öçG&7D–C¢F†—2ç6–väf÷&Òæ6öçG&7D–BÂ6–vå7FGW3¢F†—2ç6–väf÷&Òç6–vå7FGW2Ò’çF†Vâ‚‚’Óâ°¢F†—2âFÖöFÂæ×6u7V66W72‚~zÛî{Û.h‰X©òr¢F†—2ç6–vä÷VâÒfÇ6P¢F†—2æÆöEvR‚¢F†—2ç&Vg&W6„FWF–Ä–d÷Vâ‡F†—2ç6–väf÷&Òç&÷r¢Ò’æ6F6‚‚‚’Óâ·Ò¢ÒÀ¢&6†—fTöæR‡&÷r’²F†—2âG&ö×B‚~Šû~‹é>XZ^[Ù.j>ŠûNiˆârÂ~YŽYÎ[Ù.j2rÂF†—2æÖW76vT&÷„÷F–öç2‡²–çWEfÇVS¢~YŽYÎ[Ù.j2rÂ–çWEGFW&ã¢õÅ2²òÂ–çWDW'&÷$ÖW76vS¢~[Ù.j>ŠûNiˆî[ø^Z²rÒ’’çF†Vâ‚‡²fÇVRÒ’Óâ&6†—fT6öçG&7B‡²6öçG&7D–C¢&÷ræ6öçG&7D–BÂ&V6öã¢fÇVRÒ’’çF†Vâ‚‚’Óâ²F†—2âFÖöFÂæ×6u7V66W72‚~[Ù.j>h‰X©òr“²F†—2æÆöEvR‚“²F†—2ç&Vg&W6„FWF–Ä–d÷Vâ‡&÷r’Ò’æ6F6‚‚‚’Óâ·Ò’ÒÀ¢fö–DöæR‡&÷r’²F†—2âG&ö×B‚~Šû~‹é>XZ^KÙÎ[©þXéþYºrÂ~YŽYÎKÙÎ[©òrÂF†—2æÖW76vT&÷„÷F–öç2‡²–çWEfÇVS¢~YŽYÎKÙÎ[©òrÂ–çWEGFW&ã¢õÅ2²òÂ–çWDW'&÷$ÖW76vS¢~KÙÎ[©þXéþYº[ø^Z²rÒ’’çF†Vâ‚‡²fÇVRÒ’Óâfö–D6öçG&7B‡²6öçG&7D–C¢&÷ræ6öçG&7D–BÂ&V6öã¢fÇVRÒ’’çF†Vâ‚‚’Óâ²F†—2âFÖöFÂæ×6u7V66W72‚~KÙÎ[©þh‰X©òr“²F†—2æÆöEvR‚“²F†—2ç&Vg&W6„FWF–Ä–d÷Vâ‡&÷r’Ò’æ6F6‚‚‚’Óâ·Ò’ÒÀ¢FW&Ö–æFTöæR‡&÷r’²F†—2âG&ö×B‚~Šû~‹é>XZ^{¸ŽjÚ.XéþYºrÂ~YŽYÎ{¸ŽjÚ"rÂF†—2æÖW76vT&÷„÷F–öç2‡²–çWEGFW&ã¢õÅ2²òÂ–çWDW'&÷$ÖW76vS¢~{¸ŽjÚ.XéþYº[ø^Z²rÒ’’çF†Vâ‚‡²fÇVRÒ’ÓâFW&Ö–æFT6öçG&7B‡²6öçG&7D–C¢&÷ræ6öçG&7D–BÂ&V6öã¢fÇVRÒ’’çF†Vâ‚‚’Óâ²F†—2âFÖöFÂæ×6u7V66W72‚~{¸ŽjÚ.h‰X©òr“²F†—2æÆöEvR‚“²F†—2ç&Vg&W6„FWF–Ä–d÷Vâ‡&÷r’Ò’æ6F6‚‚‚’Óâ·Ò’ÒÀ¢÷Vä–×÷'B‚’²F†—2âG&Vg2æ–×÷'E&Vbæ÷Vâ‚’ÒÀ¢†æFÆTW‡÷'B‚’²F†—2æF÷væÆöB‚v6öçG&7BöW‡÷'BrÂ²ââçF†—2çVW'’ÒÂ6öçG&7EòG´FFRææ÷r‚—Òç†Ç7†’ÒÀ¢÷VåFV×ÆFR‡&÷r’°¢F†—2çFV×ÆFTf÷&ÒÒ&÷ròF†—2ææ÷&ÖÆ—¦TÖ&÷r‡&÷rÂ°¢²wFV×ÆFT–BrÂwFV×ÆFUö–BuÒÀ¢²wFV×ÆFTæÖRrÂwFV×ÆFUöæÖRuÒÀ¢²v66UG—RrÂv66U÷G—RuÒÀ¢²vf–ÆTæÖRrÂvf–ÆUöæÖRuÒÀ¢²vf–ÆUW&ÂrÂvf–ÆU÷W&ÂuÒÀ¢²wfW'6–öäæòrÂwfW'6–öåöæòuÐ¢Ò’¢²7FGW3¢F†—2æF–7DFVfVÇB‚w7—5öæ÷&ÖÅöF—6&ÆRr’ÂfW'6–öäæó¢wcrÂ66UG—S¢F†—2æF–7DFVfVÇB‚vÆuö6öçG&7Eö66U÷G—Rr’Ð¢F†—2çFV×ÆFT÷VâÒG'VP¢F†—2æ6ÆV$f÷&ÕfÆ–FFR‚wFV×ÆFTf÷&Õ&Vbr¢ÒÀ¢7–æ4f–ÆTÖWF†f÷&ÒÂfÇVRÂæÖT¶W’Òvf–ÆTæÖRrÂG—T¶W’Òvf–ÆUG—Rr’°¢–b‚fÇVR’&WGW&à¢F†—2âG6WB†f÷&ÒÂæÖT¶W’ÂF†—2æf–ÆTæÖTg&öÕW&Â‡fÇVR’¢–b‡G—T¶W’’F†—2âG6WB†f÷&ÒÂG—T¶W’ÂF†—2æf–ÆTW‡Dg&öÕW&Â‡fÇVR’¢ÒÀ¢7–æ5FV×ÆFTÖWF‡fÇVR’°¢F†—2ç7–æ4f–ÆTÖWF‡F†—2çFV×ÆFTf÷&ÒÂfÇVRÂvf–ÆTæÖRrÂçVÆÂ¢F†—2æ6ÆV$f÷&ÕfÆ–FFR‚wFV×ÆFTf÷&Õ&VbrÂ²vf–ÆUW&ÂuÒ¢ÒÀ¢6fUFV×ÆFR‚’°¢F†—2âG&Vg2çFV×ÆFTf÷&Õ&VbçfÆ–FFR‡fÆ–BÓâ°¢–b‚fÆ–B’&WGW&à¢F†—2ç7–æ5FV×ÆFTÖWF‡F†—2çFV×ÆFTf÷&Òæf–ÆUW&Â¢²‡F†—2çFV×ÆFTf÷&ÒçFV×ÆFT–BòWFFUFV×ÆFR¢FEFV×ÆFR’‡F†—2çFV×ÆFTf÷&Ò’çF†Vâ‚‚’Óâ²F†—2âFÖöFÂæ×6u7V66W72‚~KùÞZÙŽh‰X©òr“²F†—2çFV×ÆFT÷VâÒfÇ6S²F†—2æÆöEvR‚’Ò¢Ò¢ÒÀ¢&VÖ÷fUFV×ÆFR‡&÷r’²F†—2âFÖöFÂæ6öæf—&Ò‚~zîŠêNXŠ™šNŠú^YŽYÎjŠiÛþY	~ûÉþY
þyJŽKŠÞy¨NjŠiÛþ™ÈŠhXXŽXÎyJŽ8"r’çF†Vâ‚‚’ÓâFVÅFV×ÆFR‡&÷rçFV×ÆFT–B’’çF†Vâ‚‚’Óâ²F†—2âFÖöFÂæ×6u7V66W72‚~XŠ™šNh‰X©òr“²F†—2æÆöEvR‚’Ò’æ6F6‚‚‚’Óâ·Ò’ÒÀ¢÷VäfVR‡&÷r’°¢F†—2æVç7W&T6öçG&7D÷F–öâ‡&÷r¢6öç7B—4fVU&÷rÒ&÷rbb&÷rçÆåö–@¢F†—2æfVTf÷&ÒÒ—4fVU&÷p¢ò²Æä–C¢&÷rçÆåö–BÂ6öçG&7D–C¢&÷ræ6öçG&7Eö–BÂW&–öDæó¢&÷rçW&–öEöæòÂ&V6V—f&ÆTÖ÷VçC¢&÷rç&V6V—f&ÆUöÖ÷VçBÂÆå&V6V—fTFFS¢&÷rçÆå÷&V6V—fUöFFRÂ&V6V—fVDÖ÷VçC¢&÷rç&V6V—fVEöÖ÷VçBÂ6öæf—&Õ7FGW3¢&÷ræ6öæf—&Õ÷7FGW2Â–çfö–6U7FGW3¢&÷ræ–çfö–6U÷7FGW2Ð¢¢²6öçG&7D–C¢&÷rbb&÷ræ6öçG&7D–BÂ6öçG&7DæÖS¢&÷rbb&÷ræ6öçG&7DæÖRÂW&–öDæó¢Â&V6V—f&ÆTÖ÷VçC¢Â&V6V—fVDÖ÷VçC¢Â6öæf—&Õ7FGW3¢F†—2æF–7DFVfVÇB‚vÆuö6öçG&7E÷&V6V—fU÷7FGW2r’Â–çfö–6U7FGW3¢F†—2æF–7DFVfVÇB‚vÆuö6öçG&7Eö–çfö–6U÷7FGW2r’Ð¢–b‚&÷r’F†—2ç6V&6„6öçG&7D÷F–öç2‚rr¢F†—2æfVT÷VâÒG'VP¢F†—2æ6ÆV$f÷&ÕfÆ–FFR‚vfVTf÷&Õ&Vbr¢ÒÀ¢6fTfVR‚’°¢F†—2âG&Vg2æfVTf÷&Õ&VbçfÆ–FFR‡fÆ–BÓâ°¢–b‚fÆ–B’&WGW&à¢²‡F†—2æfVTf÷&ÒçÆä–BòWFFTfVR¢FDfVR’‡F†—2æfVTf÷&Ò’çF†Vâ‚‚’Óâ²F†—2âFÖöFÂæ×6u7V66W72‚~KùÞZÙŽh‰X©òr“²F†—2æfVT÷VâÒfÇ6S²F†—2æÆöEvR‚“²F†—2ç&Vg&W6„FWF–Ä–d÷Vâ‡²6öçG&7D–C¢F†—2æfVTf÷&Òæ6öçG&7D–BÒ’Ò¢Ò¢ÒÀ¢&VÖ÷fTfVR‡&÷r’²F†—2âFÖöFÂæ6öæf—&Ò‚~zîŠêNXŠ™šNŠú^iKn‹KžŠêX‰.Y	~ûÉþ[{.zîŠêNh‰n[{.[ÈzZŽy¨NŠêX‰.KˆÞXúþXŠ™šN8"r’çF†Vâ‚‚’ÓâFVÄfVR‡&÷rçÆåö–B’’çF†Vâ‚‚’Óâ²F†—2âFÖöFÂæ×6u7V66W72‚~XŠ™šNh‰X©òr“²F†—2æÆöEvR‚“²F†—2ç&Vg&W6„FWF–Ä–d÷Vâ‡&÷r’Ò’æ6F6‚‚‚’Óâ·Ò’ÒÀ¢6öæf—&ÔfVTöæR‡&÷r’²F†—2âG&ö×B‚~Šû~‹é>XZ^ZéîiKn˜yš)ÒrÂ~zîŠêNiKnjËârÂF†—2æÖW76vT&÷„÷F–öç2‡²–çWEfÇVS¢7G&–ær‡&÷rç&V6V—f&ÆUöÖ÷VçBÇÂ’Â–çWEGFW&ã¢õâƒò²…Âã²“òB•ÆB²…ÂåÆG³Ã'Ò“òBòÂ–çWDW'&÷$ÖW76vS¢~Šû~‹é>XZ^ZJ~K¨ãy¨N˜yš)ÒrÒ’’çF†Vâ‚‡²fÇVRÒ’Óâ6öæf—&ÔfVR‡²Æä–C¢&÷rçÆåö–BÂ&V6V—fVDÖ÷VçC¢fÇVRÒ’’çF†Vâ‚‚’Óâ²F†—2âFÖöFÂæ×6u7V66W72‚~zîŠêNh‰X©òr“²F†—2æÆöEvR‚“²F†—2ç&Vg&W6„FWF–Ä–d÷Vâ‡&÷r’Ò’æ6F6‚‚‚’Óâ·Ò’ÒÀ¢&V¦V7DfVTöæR‡&÷r’²F†—2âG&ö×B‚~Šû~‹é>XZ^š›>Y¹îXéþYºrÂ~š›>Y¹îiKnjËârÂF†—2æÖW76vT&÷„÷F–öç2‡²–çWEGFW&ã¢õÅ2²òÂ–çWDW'&÷$ÖW76vS¢~š›>Y¹îXéþYº[ø^Z²rÒ’’çF†Vâ‚‡²fÇVRÒ’Óâ&V¦V7DfVR‡²Æä–C¢&÷rçÆåö–BÂ&V6öã¢fÇVRÒ’’çF†Vâ‚‚’Óâ²F†—2âFÖöFÂæ×6u7V66W72‚~[{.š›>Y¹âr“²F†—2æÆöEvR‚“²F†—2ç&Vg&W6„FWF–Ä–d÷Vâ‡&÷r’Ò’æ6F6‚‚‚’Óâ·Ò’ÒÀ¢–çfö–6TfVTöæR‡&÷r’°¢6öç7B'F–ÂÒF†—2æ—4fVT–çfö–6U'F–Â‡&÷r¢F†—2æ–çfö–6Tf÷&ÒÒ°¢Æä–C¢&÷rçÆåö–BÇÂ&÷rçÆä–BÀ¢–çfö–6U7FGW3¢'F–ÂòF†—2æ6öçG&7E7FFW2æ–çfö–6T—77VVB¢F†—2æ6öçG&7E7FFW2æ–çfö–6U'F–ÂÀ¢'F–ÂÀ¢&÷p¢Ð¢F†—2æ–çfö–6T÷VâÒG'VP¢ÒÀ¢6fT–çfö–6R‚’°¢–b‚F†—2æ–çfö–6Tf÷&Òæ–çfö–6U7FGW2’°¢F†—2âFÖöFÂæ×6uv&æ–ær‚~Šû~˜žhºž[ÈzZŽXªŽKÙÂr¢&WGW&à¢Ð¢–çfö–6TfVR‡²Æä–C¢F†—2æ–çfö–6Tf÷&ÒçÆä–BÂ–çfö–6U7FGW3¢F†—2æ–çfö–6Tf÷&Òæ–çfö–6U7FGW2Ò’çF†Vâ‚‚’Óâ°¢F†—2âFÖöFÂæ×6u7V66W72‚~[ÈzZŽx«nh[{.i»Nikr¢F†—2æ–çfö–6T÷VâÒfÇ6P¢F†—2æÆöEvR‚¢F†—2ç&Vg&W6„FWF–Ä–d÷Vâ‡F†—2æ–çfö–6Tf÷&Òç&÷r¢Ò’æ6F6‚‚‚’Óâ·Ò¢ÒÀ¢÷VäGF6†ÖVçB‡&÷r’°¢F†—2æVç7W&T6öçG&7D÷F–öâ‡&÷r¢6öç7B—4GF6†ÖVçE&÷rÒ&÷rbb&÷ræGF6†ÖVçEö–@¢F†—2æGF6†ÖVçDf÷&ÒÒ—4GF6†ÖVçE&÷p¢ò²GF6†ÖVçD–C¢&÷ræGF6†ÖVçEö–BÂ6öçG&7D–C¢&÷ræ6öçG&7Eö–BÂf–ÆTæÖS¢&÷ræf–ÆUöæÖRÂf–ÆUW&Ã¢&÷ræf–ÆU÷W&ÂÂf–ÆUG—S¢&÷ræf–ÆU÷G—RÂf–ÆU6—¦S¢&÷ræf–ÆU÷6—¦RÐ¢¢²6öçG&7D–C¢&÷rbb&÷ræ6öçG&7D–BÂ6öçG&7DæÖS¢&÷rbb&÷ræ6öçG&7DæÖRÐ¢–b‚&÷r’F†—2ç6V&6„6öçG&7D÷F–öç2‚rr¢F†—2æGF6†ÖVçD÷VâÒG'VP¢ÒÀ¢7–æ4GF6†ÖVçDÖWF‡fÇVR’°¢F†—2ç7–æ4f–ÆTÖWF‡F†—2æGF6†ÖVçDf÷&ÒÂfÇVR¢F†—2æ6ÆV$f÷&ÕfÆ–FFR‚vGF6†ÖVçDf÷&Õ&VbrÂ²vf–ÆUW&ÂrÂvf–ÆTæÖRuÒ¢ÒÀ¢6fTGF6†ÖVçB‚’°¢F†—2âG&Vg2æGF6†ÖVçDf÷&Õ&VbçfÆ–FFR‡fÆ–BÓâ°¢–b‚fÆ–B’&WGW&à¢F†—2ç7–æ4GF6†ÖVçDÖWF‡F†—2æGF6†ÖVçDf÷&Òæf–ÆUW&Â¢FDGF6†ÖVçB‡F†—2æGF6†ÖVçDf÷&Ò’çF†Vâ‚‚’Óâ²F†—2âFÖöFÂæ×6u7V66W72‚~KùÞZÙŽh‰X©òr“²F†—2æGF6†ÖVçD÷VâÒfÇ6S²F†—2æÆöEvR‚“²F†—2ç&Vg&W6„FWF–Ä–d÷Vâ‡²6öçG&7D–C¢F†—2æGF6†ÖVçDf÷&Òæ6öçG&7D–BÒ’Ò¢Ò¢ÒÀ¢&VÖ÷fTGF6†ÖVçB‡&÷r’°¢F†—2âFÖöFÂæ6öæf—&Ò‚~zîŠêNXŠ™šNŠú^YŽYÎ™˜NK»nY	~ûÉòr’çF†Vâ‚‚’ÓâFVÄGF6†ÖVçB‡&÷ræGF6†ÖVçEö–B’’çF†Vâ‚‚’Óâ°¢F†—2âFÖöFÂæ×6u7V66W72‚~XŠ™šNh‰X©òr¢F†—2æÆöEvR‚¢F†—2ç&Vg&W6„FWF–Ä–d÷Vâ‡&÷r¢Ò’æ6F6‚‚‚’Óâ·Ò¢ÒÀ¢÷Vå'VÆR‡&÷r’°¢F†—2ç'VÆTf÷&ÒÒ²ââç&÷rÂ7FGW3¢&÷rç7FGW2ÇÂF†—2æF–7DFVfVÇB‚w7—5öæ÷&ÖÅöF—6&ÆRr’Ð¢F†—2ç'VÆT÷VâÒG'VP¢F†—2æ6ÆV$f÷&ÕfÆ–FFR‚w'VÆTf÷&Õ&Vbr¢ÒÀ¢6fU'VÆR‚’°¢F†—2âG&Vg2ç'VÆTf÷&Õ&VbçfÆ–FFR‡fÆ–BÓâ°¢–b‚fÆ–B’&WGW&à¢WFFU'VÆR‡F†—2ç'VÆTf÷&Ò’çF†Vâ‚‚’Óâ²F†—2âFÖöFÂæ×6u7V66W72‚~KùÞZÙŽh‰X©òr“²F†—2ç'VÆT÷VâÒfÇ6S²F†—2æÆöEvR‚’Ò¢Ò¢Ð¢Ð§Ð£Â÷67&—Cà £Ç7G–ÆR66÷VBÆæsÒ'6772#à¤–×÷'B"ââö'W6–æW72ö'W6–æW72ç6772#° ¢æ6öçG&7B×vR°¢ÒÖ&—¢Öf–ÇFW"Ö–çWB×v–GFƒ¢##ƒ°¢ÒÖ&—¢Öf–ÇFW"×6VÆV7B×v–GFƒ¢#‡ƒ°§Ð ¢æ6öçG&7B×vRç6WGF–ærÖw&–B'F–6ÆR’°¢&6¶w&÷VæC¢3#Sc6V#°§Ð ¢æ&÷fÂÖ7F–öâÖw&÷W°¢F—7Æ“¢fÆWƒ°¢v–GFƒ¢S° ¢£§bÖFVWæVÂ×&F–òÖ'WGFöâ°¢fÆWƒ¢°¢Ð ¢£§bÖFVWæVÂ×&F–òÖ'WGFöåõö–ææW"°¢v–GFƒ¢S°¢Ð§Ð£Â÷7G–ÆSà £Ç7G–ÆRÆæsÒ'6772#à¤–×÷'B"ââö'W6–æW72ö'W6–æW72ÖF–Æörç6772#°£Â÷7G–ÆSà