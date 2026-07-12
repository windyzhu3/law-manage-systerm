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
              <el-button v-if="canInvoiceFee(row)" v-hasPermi="['contract:fÛ=¶‰žËkºwµçM•½É´ˆ€é¥¹Ù½¥”µ…Ñ¥½¸µ½ÁÑ¥½¹Ìô‰¥¹Ù½¥•Ñ¥½¹=ÁÑ¥½¹Ìˆ(€€€€€€é‘¥Ðµ½ÁÑ¥½¹Ìô‰‘¥Ð¹ÑåÁ”ˆ€é½¹ÑÉ½°µÍ¥é”ô‰½¹ÑÉ½±M¥é”ˆ€é‘¥…±½œµ±…ÍÌô‰‘¥…±½±…ÍÌˆ(€€€€€€éÍ…Ù”µ…ÁÁÉ½Ù…°ô‰Í…Ù•ÁÁÉ½Ù…°ˆ€éÍ…Ù”µÍ¥¸ô‰Í…Ù•M¥¸ˆ€éÍ…Ù”µ¥¹Ù½¥”ô‰Í…Ù•%¹Ù½¥”ˆ(€€€€¼ø((((€€ð½‘¥Øø(ð½Ñ•µÁ±…Ñ”ø((ñÍÉ¥ÁÐø)¥µÁ½ÉÐá•±%µÁ½ÉÑ¥…±½œ™É½´€ ½½µÁ½¹•¹ÑÌ½á•±%µÁ½ÉÑ¥…±½œœ)¥µÁ½ÉÐ	¥é!•É¼™É½´€ ½Ù¥•ÝÌ½‰ÕÍ¥¹•ÍÌ½½µÁ½¹•¹ÑÌ½	¥é!•É¼œ)¥µÁ½ÉÐ	¥é5•ÑÉ¥Ì™É½´€ ½Ù¥•ÝÌ½‰ÕÍ¥¹•ÍÌ½½µÁ½¹•¹ÑÌ½	¥é5•ÑÉ¥Ìœ)¥µÁ½ÉÐ	¥éA…•!•…‘•È™É½´€ ½Ù¥•ÝÌ½‰ÕÍ¥¹•ÍÌ½½µÁ½¹•¹ÑÌ½	¥éA…•!•…‘•Èœ)¥µÁ½ÉÐ	¥éQ…‰±•…É™É½´€ ½Ù¥•ÝÌ½‰ÕÍ¥¹•ÍÌ½½µÁ½¹•¹ÑÌ½	¥éQ…‰±•…Éœ)¥µÁ½ÉÐ‰ÕÍ¥¹•ÍÍU¤™É½´€ ½Ù¥•ÝÌ½‰ÕÍ¥¹•ÍÌ½µ¥á¥¹Ì½‰ÕÍ¥¹•ÍÍU¤œ)¥µÁ½ÉÐÕÍÑ½µ•É1¥™•å±”™É½´€ ½Ù¥•ÝÌ½‰ÕÍ¥¹•ÍÌ½µ¥á¥¹Ì½ÕÍÑ½µ•É1¥™•å±”œ)¥µÁ½ÉÐ½¹ÑÉ…Ñ1¥™•å±”™É½´€ ½Ù¥•ÝÌ½‰ÕÍ¥¹•ÍÌ½µ¥á¥¹Ì½½¹ÑÉ…Ñ1¥™•å±”œ)¥µÁ½ÉÐ½¹ÑÉ…ÑA…•Ñ¥½¹Ì™É½´€œ¸½½¹ÑÉ…ÐµÁ…”µ…Ñ¥½¹Ìœ)¥µÁ½ÉÐ½¹ÑÉ…Ñ•Ñ…¥±É…Ý•È™É½´€œ¸½½µÁ½¹•¹ÑÌ½½¹ÑÉ…Ñ•Ñ…¥±É…Ý•Èœ)¥µÁ½ÉÐ½¹ÑÉ…Ñ1¥™•å±•¥…±½Ì™É½´€œ¸½½µÁ½¹•¹ÑÌ½½¹ÑÉ…Ñ1¥™•å±•¥…±½Ìœ)¥µÁ½ÉÐ½¹ÑÉ…ÑI•Í½ÕÉ•¥…±½Ì™É½´€œ¸½½µÁ½¹•¹ÑÌ½½¹ÑÉ…ÑI•Í½ÕÉ•¥…±½Ìœ()•áÁ½ÉÐ‘•™…Õ±Ðì(€¹…µ”è€½¹ÑÉ…Ðœ°(€µ¥á¥¹Ìèm‰ÕÍ¥¹•ÍÍU¤°ÕÍÑ½µ•É1¥™•å±”°½¹ÑÉ…Ñ1¥™•å±”°½¹ÑÉ…ÑA…•Ñ¥½¹Ít°(€½µÁ½¹•¹ÑÌèìá•±%µÁ½ÉÑ¥…±½œ°	¥é!•É¼°	¥é5•ÑÉ¥Ì°	¥éA…•!•…‘•È°	¥éQ…‰±•…É°½¹ÑÉ…Ñ•Ñ…¥±É…Ý•È°½¹ÑÉ…Ñ1¥™•å±•¥…±½Ì°½¹ÑÉ…ÑI•Í½ÕÉ•¥…±½Ìô°(€‘¥ÑÌèl±…Ý}ÕÍÑ½µ•É}ÍÑ…ÑÕÌœ°€±…Ý}½¹ÑÉ…Ñ}…Í•}ÑåÁ”œ°€±…Ý}½¹ÑÉ…Ñ}™••}ÑåÁ”œ°€±…Ý}½¹ÑÉ…Ñ}Í¥¹}µ•Ñ¡½œ°€±…Ý}½¹ÑÉ…Ñ}Í¥¹}ÍÑ…ÑÕÌœ°€±…Ý}½¹ÑÉ…Ñ}…Õ‘¥Ñ}ÍÑ…ÑÕÌœ°€±…Ý}½¹ÑÉ…Ñ}…ÁÁÉ½Ù…±}…Ñ¥½¸œ°€±…Ý}½¹ÑÉ…Ñ}ÍÑ…ÑÕÌœ°€±…Ý}½¹ÑÉ…Ñ}ÍÑ…ÑÕÍ}…Ñ¥½¸œ°€±…Ý}½¹ÑÉ…Ñ}É¥Í­}±•Ù•°œ°€±…Ý}½¹ÑÉ…Ñ}É••¥Ù•}ÍÑ…ÑÕÌœ°€±…Ý}½¹ÑÉ…Ñ}¥¹Ù½¥•}ÍÑ…ÑÕÌœ°€ÍåÍ}¹½Éµ…±}‘¥Í…‰±”t°(€‘…Ñ„ ¤ì(€€€½¹ÍÐÁ½Í¥Ñ¥Ù•µ½Õ¹Ð€ô€¡ÉÕ±”°Ù…±Õ”°…±±‰…¬¤€ôøì(€€€€€¥˜€¡Ù…±Õ”€ôôôÕ¹‘•™¥¹•ñðÙ…±Õ”€ôôô¹Õ±°ñðÙ…±Õ”€ôôô€œœñð9Õµ‰•È¡Ù…±Õ”¤€ðô€À¤ì(€€€€€€€…±±‰…¬¡¹•ÜÉÉ½È Ÿ¦G¦Šw–þ¦†ï–’Ÿ’ê8Àœ¤¤(€€€€€ô•±Í”ì(€€€€€€€…±±‰…¬ ¤(€€€€€ô(€€€ô(€€€É•ÑÕÉ¸ì(€€€€€µ½‘”è€±¥ÍÐœ°(€€€€€…Ù…¥±…‰±•5½‘•Ìèl±¥ÍÐœ°€…ÁÁÉ½Ù…°œ°€Ñ•µÁ±…Ñ”œ°€™•”œ°€…ÑÑ…¡µ•¹Ðœ°€ÍÑ…ÑÕÌœ°€ÉÕ±”t°(€€€€€¡•É½5½‘•Ìèl±¥ÍÐœ°€…ÁÁÉ½Ù…°œ°€Ñ•µÁ±…Ñ”œ°€™•”œ°€…ÑÑ…¡µ•¹Ðt°(€€€€€Í¡½ÝM•…É èÑÉÕ”°(€€€€€±½…‘¥¹œè™…±Í”°(€€€€€Ñ½Ñ…°è€À°(€€€€€½¹ÑÉ…ÑÌèmt°(€€€€€Ñ•µÁ±…Ñ•Ìèmt°(€€€€€™••Ìèmt°(€€€€€…ÑÑ…¡µ•¹ÑÌèmt°(€€€€€ÍÑ…ÑÕÍ•Ìèmt°(€€€€€ÉÕ±•Ìèmt°(€€€€€µ•ÑÉ¥Ìèmt°(€€€€€ÅÕ•ÉäèìÁ…•9Õ´è€Ä°Á…•M¥é”è€ÄÀô°(€€€€€½¹ÑÉ…Ñ½É´èíô°(€€€€€Ñ•µÁ±…Ñ•½É´èíô°(€€€€€™••½É´èíô°(€€€€€…ÑÑ…¡µ•¹Ñ½É´èíô°(€€€€€…ÁÁÉ½Ù…±½É´èíô°(€€€€€Í¥¹½É´èíô°(€€€€€¥¹Ù½¥•½É´èíô°(€€€€€ÉÕ±•½É´èíô°(€€€€€ÕÍÑ½µ•É=ÁÑ¥½¹Ìèmt°(€€€€€½¹ÑÉ…Ñ=ÁÑ¥½¹Ìèmt°(€€€€€½Ý¹•É=ÁÑ¥½¹Ìèmt°(€€€€€…‘Ù…¹•‘=Á•¸è™…±Í”°(€€€€€ÕÍÑ½µ•ÉM•±•Ñ1½…‘¥¹œè™…±Í”°(€€€€€½¹ÑÉ…ÑM•±•Ñ1½…‘¥¹œè™…±Í”°(€€€€€‰ÕÍ¥¹•ÍÍA…•5•Ñ„èì(€€€€€€€‘•™…Õ±ÑQ¥Ñ±”è€Ÿ–B#–B3’â·–þœ°(€€€€€€€Ñ¥Ñ±•Ìèì±¥ÍÐè€Ÿ–B#–B3–"_¢† œ°…ÁÁÉ½Ù…°è€Ÿ–B#–B3–º‡š&äœ°Ñ•µÁ±…Ñ”è€Ÿ–B#–B3š¢‡švüœ°™•”è€ŸšRÛ¢Òç¢º‡–"Hœ°…ÑÑ…¡µ•¹Ðè€Ÿ–B#–B3¦f’îØœ°ÍÑ…ÑÕÌè€Ÿž*Ûš¢ºÃ–öTœ°ÉÕ±”è€Ÿžò[–>ß¢ž–"dœô°(€€€€€€€‘•ÍÉ¥ÁÑ¥½¹Ìèì(€€€€€€€€€±¥ÍÐè€Ÿžî’âžº‡žB–B#–B3–"o–îëŽ–º‡š&çŽž¶û¢º‹–J3–Æ—žê›ž*Ûšœ°(€€€€€€€€€…ÁÁÉ½Ù…°è€Ÿ–’žBš>C’ê“–º‡š‚ãžj–B#–B3¾ò3–º‡š&çš?¢ž–þ¦†ïžVgž^Tœ°(€€€€€€€€€Ñ•µÁ±…Ñ”è€ŸžîÓš*“–B#–B3š¢‡švÿšZ’îÛŽž&#šr³–J3–B¿–sž*Ûšœ°(€€€€€€€€€™•”è€ŸžîÓš*“–B#–B3–êSšRÛŽ–º{šRÛŽ–òž–£’â;ž†»¢º“ž*Ûšœ°(€€€€€€€€€…ÑÑ…¡µ•¹Ðè€Ÿ–’7žR£žÎïžî’â+’òƒ¢÷–*ožº‡žB–B#–B3¦f’îØœ°(€€€€€€€€€ÍÑ…ÑÕÌè€Ÿ¢þ÷¢â«–B#–B3–Ï¦R»ž*Ûš–>c–2[¢ºÃ–öTœ°(€€€€€€€€€ÉÕ±”è€ŸžîÓš*“–B#–B3¢«–*£žò[–>ß¢ž–"dœ(€€€€€€€ô(€€€€€ô°(€€€€€‘•Ñ…¥±=Á•¸è™…±Í”°(€€€€€‘•Ñ…¥±½¹ÑÉ…Ðèíô°(€€€€€‘•Ñ…¥±••Ìèmt°(€€€€€‘•Ñ…¥±ÑÑ…¡µ•¹ÑÌèmt°(€€€€€‘•Ñ…¥±ÁÁÉ½Ù…±Ìèmt°(€€€€€‘•Ñ…¥±MÑ…ÑÕÍ•Ìèmt°(€€€€€½¹ÑÉ…Ñ=Á•¸è™…±Í”°(€€€€€Ñ•µÁ±…Ñ•=Á•¸è™…±Í”°(€€€€€™••=Á•¸è™…±Í”°(€€€€€…ÑÑ…¡µ•¹Ñ=Á•¸è™…±Í”°(€€€€€…ÁÁÉ½Ù…±=Á•¸è™…±Í”°(€€€€€Í¥¹=Á•¸è™…±Í”°(€€€€€¥¹Ù½¥•=Á•¸è™…±Í”°(€€€€€ÉÕ±•=Á•¸è™…±Í”°(€€€€€½¹ÑÉ…ÑIÕ±•Ìèì(€€€€€€€ÕÍÑ½µ•É%èmìÉ•ÅÕ¥É•èÑÉÕ”°µ•ÍÍ…”è€Ÿ¢¾ß¦'š.§–º‹š"Üœ°ÑÉ¥•Èè€‰±ÕÈœõt°(€€€€€€€ÕÍÑ½µ•É9…µ”èmìÉ•ÅÕ¥É•èÑÉÕ”°µ•ÍÍ…”è€Ÿ¢¾ß¢úO–—–º‹š"ß–B7žžÀœ°ÑÉ¥•Èè€‰±ÕÈœõt°(€€€€€€€½¹ÑÉ…Ñ9…µ”èmìÉ•ÅÕ¥É•èÑÉÕ”°µ•ÍÍ…”è€Ÿ¢¾ß¢úO–—–B#–B3–B7žžÀœ°ÑÉ¥•Èè€‰±ÕÈœõt°(€€€€€€€…Í•QåÁ”èmìÉ•ÅÕ¥É•èÑÉÕ”°µ•ÍÍ…”è€Ÿ¢¾ß¦'š.§š†#’îÛžÆï–z,œ°ÑÉ¥•Èè€¡…¹”œõt°(€€€€€€€Í¥¹µ½Õ¹ÐèmìÉ•ÅÕ¥É•èÑÉÕ”°µ•ÍÍ…”è€Ÿ¢¾ß¢úO–—ž¶ûžê›¦G¦Štœ°ÑÉ¥•Èè€‰±ÕÈœô°ìÙ…±¥‘…Ñ½ÈèÁ½Í¥Ñ¥Ù•µ½Õ¹Ð°ÑÉ¥•Èè€‰±ÕÈœõt°(€€€€€€€™••QåÁ”èmìÉ•ÅÕ¥É•èÑÉÕ”°µ•ÍÍ…”è€Ÿ¢¾ß¦'š.§šRÛ¢ÒçšZç–ò<œ°ÑÉ¥•Èè€¡…¹”œõt°(€€€€€€€Í¥¹5•Ñ¡½èmìÉ•ÅÕ¥É•èÑÉÕ”°µ•ÍÍ…”è€Ÿ¢¾ß¦'š.§ž¶û¢º‹šZç–ò<œ°ÑÉ¥•Èè€¡…¹”œõt°(€€€€€€€É¥Í­1•Ù•°èmìÉ•ÅÕ¥É•èÑÉÕ”°µ•ÍÍ…”è€Ÿ¢¾ß¦'š.§¦Ž;¦f§ž¶'žêœœ°ÑÉ¥•Èè€¡…¹”œõt(€€€€€ô°(€€€€€…ÁÁÉ½Ù…±IÕ±•Ìèì(€€€€€€€…Ñ¥½¸èmìÉ•ÅÕ¥É•èÑÉÕ”°µ•ÍÍ…”è€Ÿ¢¾ß¦'š.§–º‡š&ç–*£’öpœ°ÑÉ¥•Èè€¡…¹”œõt°(€€€€€€€½Á¥¹¥½¸èmìÉ•ÅÕ¥É•èÑÉÕ”°µ•ÍÍ…”è€Ÿ¢¾ß¢úO–—–º‡š&çš?¢žœ°ÑÉ¥•Èè€‰±ÕÈœõt(€€€€€ô°(€€€€€Ñ•µÁ±…Ñ•IÕ±•Ìèì(€€€€€€€Ñ•µÁ±…Ñ•9…µ”èmìÉ•ÅÕ¥É•èÑÉÕ”°µ•ÍÍ…”è€Ÿ¢¾ß¢úO–—š¢‡švÿ–B7žžÀœ°ÑÉ¥•Èè€‰±ÕÈœõt°(€€€€€€€…Í•QåÁ”èmìÉ•ÅÕ¥É•èÑÉÕ”°µ•ÍÍ…”è€Ÿ¢¾ß¦'š.§š†#’îÛžÆï–z,œ°ÑÉ¥•Èè€¡…¹”œõt°(€€€€€€€™¥±•UÉ°èmìÉ•ÅÕ¥É•èÑÉÕ”°µ•ÍÍ…”è€Ÿ¢¾ß’â+’òƒš¢‡švÿšZ’îØœ°ÑÉ¥•Èè€¡…¹”œõt(€€€€€ô°(€€€€€™••IÕ±•Ìèì(€€€€€€€½¹ÑÉ…Ñ%èmìÉ•ÅÕ¥É•èÑÉÕ”°µ•ÍÍ…”è€Ÿ¢¾ß¦'š.§š&–Æ{–B#–B0œ°ÑÉ¥•Èè€¡…¹”œõt°(€€€€€€€Á•É¥½‘9¼èmìÉ•ÅÕ¥É•èÑÉÕ”°µ•ÍÍ…”è€Ÿ¢¾ß¢úO–—šršVÀœ°ÑÉ¥•Èè€‰±ÕÈœõt°(€€€€€€€É••¥Ù…‰±•µ½Õ¹ÐèmìÉ•ÅÕ¥É•èÑÉÕ”°µ•ÍÍ…”è€Ÿ¢¾ß¢úO–—–êSšRÛ¦G¦Štœ°ÑÉ¥•Èè€‰±ÕÈœõt°(€€€€€€€Á±…¹I••¥Ù•…Ñ”èmìÉ•ÅÕ¥É•èÑÉÕ”°µ•ÍÍ…”è€Ÿ¢¾ß¦'š.§¢º‡–"KšRÛš²ûš^”œ°ÑÉ¥•Èè€¡…¹”œõt(€€€€€ô°(€€€€€…ÑÑ…¡µ•¹ÑIÕ±•Ìèì(€€€€€€€½¹ÑÉ…Ñ%èmìÉ•ÅÕ¥É•èÑÉÕ”°µ•ÍÍ…”è€Ÿ¢¾ß¦'š.§š&–Æ{–B#–B0œ°ÑÉ¥•Èè€¡…¹”œõt°(€€€€€€€™¥±•UÉ°èmìÉ•ÅÕ¥É•èÑÉÕ”°µ•ÍÍ…”è€Ÿ¢¾ß’â+’òƒ¦f’îØœ°ÑÉ¥•Èè€¡…¹”œõt°(€€€€€€€™¥±•9…µ”èmìÉ•ÅÕ¥É•èÑÉÕ”°µ•ÍÍ…”è€Ÿ¢¾ß¢úO–—šZ’îÛ–B4œ°ÑÉ¥•Èè€‰±ÕÈœõt(€€€€€ô°(€€€€€ÉÕ±•IÕ±•Ìèì(€€€€€€€ÉÕ±•9…µ”èmìÉ•ÅÕ¥É•èÑÉÕ”°µ•ÍÍ…”è€Ÿ¢¾ß¢úO–—¢ž–"g–B7žžÀœ°ÑÉ¥•Èè€‰±ÕÈœõt°(€€€€€€€ÁÉ•™¥àèmìÉ•ÅÕ¥É•èÑÉÕ”°µ•ÍÍ…”è€Ÿ¢¾ß¢úO–—–&7žò œ°ÑÉ¥•Èè€‰±ÕÈœõt°(€€€€€€€‘…Ñ•A…ÑÑ•É¸èmìÉ•ÅÕ¥É•èÑÉÕ”°µ•ÍÍ…”è€Ÿ¢¾ß¢úO–—š^—šrš‚ó–ò<œ°ÑÉ¥•Èè€‰±ÕÈœõt°(€€€€€€€Í•É¥…±1•¹Ñ èmìÉ•ÅÕ¥É•èÑÉÕ”°µ•ÍÍ…”è€Ÿ¢¾ß¢úO–—šÖšÂÓ¦Vÿ–ê˜œ°ÑÉ¥•Èè€‰±ÕÈœõt°(€€€€€€€ÍÑ…ÑÕÌèmìÉ•ÅÕ¥É•èÑÉÕ”°µ•ÍÍ…”è€Ÿ¢¾ß¦'š.§ž*Ûšœ°ÑÉ¥•Èè€¡…¹”œõt(€€€€€ô°(€€€€€µ•ÑÉ¥½¹™¥œèl(€€€€€€€ì­•äè€Ñ½Ñ…°œ°±…‰•°è€Ÿ–B#–B3šïšVÀœ°¡¥¹Ðè€Ÿ–£¦£šr'šV#–B#–B0œ°¥½¸è€‘½Õµ•¹Ñ…Ñ¥½¸œ°½±½Èè€‰±Õ”œô°(€€€€€€€ì­•äè€µ½¹Ñ¡9•Üœ°±…‰•°è€Ÿšr³šr#šZÃ–Šxœ°¡¥¹Ðè€ŸšZÃ–îë–B#–B0œ°¥½¸è€‘…Ñ”œ°½±½Èè€å…¸œô°(€€€€€€€ì­•äè€Á•¹‘¥¹Õ‘¥Ðœ°±…‰•°è€Ÿ–ú–º‡š&äœ°¡¥¹Ðè€Ÿ–º‡š‚ã’â·–B#–B0œ°¥½¸è€Ñ¥µ”œ°½±½Èè€½É…¹”œô°(€€€€€€€ì­•äè€Á•É™½Éµ¥¹œœ°±…‰•°è€Ÿ–Æ—žê›’â´œ°¡¥¹Ðè€Ÿ–ÞË¦k¢þ–æÛž¶û¢ºˆœ°¥½¸è€¡…ÉÐœ°½±½Èè€É••¸œô°(€€€€€€€ì­•äè€µ½¹Ñ¡µ½Õ¹Ðœ°±…‰•°è€Ÿšr³šr#¦G¦Štœ°¡¥¹Ðè€Ÿšr³šr#ž¶ûžê›šï¦Štœ°¥½¸è€µ½¹•äœ°½±½Èè€Ù¥½±•Ðœô(€€€€€t°(€€€€€Á…•5•ÑÉ¥½¹™¥Ìèì(€€€€€€€…ÁÁÉ½Ù…°èl(€€€€€€€€€ì­•äè€Ñ½Ñ…°œ°±…‰•°è€Ÿ–ú–º‡š&ç–B#–B0œ°¡¥¹Ðè€Ÿ–öO–&7ž¶o¦'žîOšzpœ°¥½¸è€Ñ¥µ”œ°½±½Èè€½É…¹”œô°(€€€€€€€€€ì­•äè€É•Ù¥•Ý¥¹œœ°±…‰•°è€Ÿ–º‡š‚ã’â´œ°¡¥¹Ðè€Ÿž¶'–ú–’žBœ°¥½¸è€Ìµ¡•¬œ°½±½Èè€‰±Õ”œô°(€€€€€€€€€ì­•äè€ÕÍÑ½µ•ÉÌœ°±…‰•°è€Ÿ–Ï¢S–º‹š"Üœ°¡¥¹Ðè€Ÿšr³¦†×–:ï¦7–º‹š"Üœ°¥½¸è€Á•½Á±”œ°½±½Èè€å…¸œô°(€€€€€€€€€ì­•äè€…µ½Õ¹Ðœ°±…‰•°è€Ÿšr³¦†×¦G¦Štœ°¡¥¹Ðè€Ÿšr³¦†×ž¶ûžê›¦G¦Štœ°¥½¸è€µ½¹•äœ°½±½Èè€Ù¥½±•Ðœô(€€€€€€€t°(€€€€€€€Ñ•µÁ±…Ñ”èl(€€€€€€€€€ì­•äè€Ñ½Ñ…°œ°±…‰•°è€Ÿš¢‡švÿšïšVÀœ°¡¥¹Ðè€Ÿ–öO–&7ž¶o¦'žîOšzpœ°¥½¸è€‘½Õµ•¹Ñ…Ñ¥½¸œ°½±½Èè€‰±Õ”œô°(€€€€€€€€€ì­•äè€•¹…‰±•œ°±…‰•°è€Ÿ–B¿žR£š¢‡švüœ°¡¥¹Ðè€Ÿ–>¿žR£’ê;–B#–B0œ°¥½¸è€Ù…±¥‘½‘”œ°½±½Èè€É••¸œô°(€€€€€€€€€ì­•äè€‘¥Í…‰±•œ°±…‰•°è€Ÿ–sžR£š¢‡švüœ°¡¥¹Ðè€Ÿšj’â7–>¿žR œ°¥½¸è€Ñ¥µ”œ°½±½Èè€½É…¹”œô°(€€€€€€€€€ì­•äè€Ý¥Ñ¡¥±”œ°±…‰•°è€Ÿ–ÞË’â+’òƒšZ’îØœ°¡¥¹Ðè€Ÿšr³¦†×š¢‡švÿšZ’îØœ°¥½¸è€ÕÁ±½…œ°½±½Èè€å…¸œô(€€€€€€€t°(€€€€€€€™•”èl(€€€€€€€€€ì­•äè€Ñ½Ñ…°œ°±…‰•°è€Ÿ¢º‡–"KšïšVÀœ°¡¥¹Ðè€Ÿ–öO–&7ž¶o¦'žîOšzpœ°¥½¸è€µ½¹•äœ°½±½Èè€‰±Õ”œô°(€€€€€€€€€ì­•äè€Á•¹‘¥¹œœ°±…‰•°è€Ÿ–úž†»¢ºœ°¡¥¹Ðè€Ÿž¶'–úšRÛš²ûž†»¢ºœ°¥½¸è€Ñ¥µ”œ°½±½Èè€½É…¹”œô°(€€€€€€€€€ì­•äè€½¹™¥Éµ•œ°±…‰•°è€Ÿ–ÞËž†»¢ºœ°¡¥¹Ðè€Ÿ–ÞËž†»¢º“šRÛš²øœ°¥½¸è€Ù…±¥‘½‘”œ°½±½Èè€É••¸œô°(€€€€€€€€€ì­•äè€¥¹Ù½¥•œ°±…‰•°è€Ÿ–ÞË–òž– œ°¡¥¹Ðè€Ÿšr³¦†×–òž–£¢ºÃ–öTœ°¥½¸è€™½É´œ°½±½Èè€å…¸œô(€€€€€€€t°(€€€€€€€…ÑÑ…¡µ•¹Ðèl(€€€€€€€€€ì­•äè€Ñ½Ñ…°œ°±…‰•°è€Ÿ¦f’îÛšïšVÀœ°¡¥¹Ðè€Ÿ–öO–&7ž¶o¦'žîOšzpœ°¥½¸è€‘½Õµ•¹Ñ…Ñ¥½¸œ°½±½Èè€‰±Õ”œô°(€€€€€€€€€ì­•äè€½¹ÑÉ…ÑÌœ°±…‰•°è€Ÿ–Ï¢S–B#–B0œ°¡¥¹Ðè€Ÿšr³¦†×–:ï¦7–B#–B0œ°¥½¸è€¹•ÍÑ•œ°½±½Èè€å…¸œô°(€€€€€€€€€ì­•äè€ÑåÁ•œ°±…‰•°è€Ÿ–ÞË¢¾–"¯žÆï–z,œ°¡¥¹Ðè€Ÿ–¶c–r£šZ’îÛžÆï–z,œ°¥½¸è€‘¥Ðœ°½±½Èè€É••¸œô°(€€€€€€€€€ì­•äè€™¥±•Ìœ°±…‰•°è€Ÿ–>¿š&O–òšZ’îØœ°¡¥¹Ðè€Ÿ–¶c–r£šZ’îÛ–rÃ–v œ°¥½¸è€±¥¹¬œ°½±½Èè€Ù¥½±•Ðœô(€€€€€€€t(€€€€€ô(€€€ô(€ô°(€É•…Ñ• ¤ì(€€€¥˜€¡Ñ¡¥Ì¸‘…ÕÑ ¹¡…ÍA•Éµ¥=È¡l½¹ÑÉ…Ðé±¥ÍÐœ°€½¹ÑÉ…ÐéÅÕ•Éäœ°€½¹ÑÉ…Ðé…‘œ°€½¹ÑÉ…Ðé•‘¥Ðœ°€½¹ÑÉ…Ðé…ÁÁÉ½Ù…°é±¥ÍÐt¤¤ì(€€€€€±¥ÍÑ½¹ÑÉ…Ñ=Ý¹•È ¤¹Ñ¡•¸¡É•Ì€ôøìÑ¡¥Ì¹½Ý¹•É=ÁÑ¥½¹Ì€ôÉ•Ì¹‘…Ñ„ñðmtô¤(€€€ô(€ô°(€½µÁÕÑ•èì(€€€¡•É½5•Ñ„ ¤ì(€€€€€½¹ÍÐµ•Ñ…Ì€ôì(€€€€€€€±¥ÍÐèì(€€€€€€€€€•å•‰É½Üè€Ÿ–B#–B3–£žR–F÷–F£šr|œ°(€€€€€€€€€Ñ¥Ñ±”è€Ÿ’î;–º‹š"ßž¶ûžê›–"Ã–º‡š&çŽšRÛ¢ÒçŽ–öKš†–£ž¢/¢þ÷¢â¨œ°(€€€€€€€€€‘•ÍÉ¥ÁÑ¥½¸è€Ÿ–B#–B3–þ¦†ï–Ï¢S–º‹š"ß¾ò3–º‡š&çž*Ûš’â;–Æ—žê›ž*Ûš’êKšZ—šÖ¢ö³¾ò3–Ï¦R»–>cšnÓžVgž^WŽœ(€€€€€€€ô°(€€€€€€€…ÁÁÉ½Ù…°èì(€€€€€€€€€•å•‰É½Üè€Ÿ–º‡š&ç–Þ—’ös–>Àœ°(€€€€€€€€€Ñ¥Ñ±”è€Ÿ¦n’â·–’žB–B#–B3–º‡š‚ã’â;¦–n{’þ»šRäœ°(€€€€€€€€€‘•ÍÉ¥ÁÑ¥½¸è€Ÿ–>«¢kž›–º‡š‚ã’â·–B#–B3¾ò3–º‡š&çš?¢ž–þ–†¯¾ò3–º‡š&ç–*£’ös¢þo–—ž*Ûš¢ºÃ–öWŽœ(€€€€€€€ô°(€€€€€€€Ñ•µÁ±…Ñ”èì(€€€€€€€€€•å•‰É½Üè€Ÿš¢‡švÿ¢Ö’êœœ°(€€€€€€€€€Ñ¥Ñ±”è€Ÿžî’âžîÓš*“–B#–B3š¢‡švÿŽž&#šr³’â;–B¿–sž*Ûšœ°(€€€€€€€€€‘•ÍÉ¥ÁÑ¥½¸è€Ÿš¢‡švÿšZ’îÛ–’7žR£žÎïžî’â+’òƒ¢÷–*o¾ò3–B¿žR£š¢‡švÿžR£’ê;–B;žî·–B#–B3¢Öß¢6'Žœ(€€€€€€€ô°(€€€€€€€™•”èì(€€€€€€€€€•å•‰É½Üè€ŸšRÛ¢Òç¢º‡–"Hœ°(€€€€€€€€€Ñ¥Ñ±”è€Ÿ¢Þ¢â«–B#–B3–êSšRÛŽ–º{šRÛŽž†»¢º“’â;–òž–£ž*Ûšœ°(€€€€€€€€€‘•ÍÉ¥ÁÑ¥½¸è€ŸšRÛ¢Òç¢º‡–"K’â—š‚ó–>_–B#–B3ž*Ûšžê›šv¾ò3žî#š–B#–B3žšš¶‹žîŸžî·¢ÂšVÓŽœ(€€€€€€€ô°(€€€€€€€…ÑÑ…¡µ•¹Ðèì(€€€€€€€€€•å•‰É½Üè€Ÿ–B#–B3¦f’îØœ°(€€€€€€€€€Ñ¥Ñ±”è€Ÿ–öK¦n–B#–B3šZ’îÛ’â;’âk–*‡¦f’îØœ°(€€€€€€€€€‘•ÍÉ¥ÁÑ¥½¸è€Ÿ¦f’îÛžî’â–Ï¢S–B#–B3¾ò3–’7žR£žÎïžî’â+’òƒ¢÷–*o–æÛ’þwžVg’âk–*‡–šVÃš6»Žœ(€€€€€€€ô(€€€€€ô(€€€€€É•ÑÕÉ¸µ•Ñ…ÍmÑ¡¥Ì¹µ½‘•tñðµ•Ñ…Ì¹±¥ÍÐ(€€€ô°(€€€µ½‘•5•ÑÉ¥½¹™¥œ ¤ì(€€€€€É•ÑÕÉ¸Ñ¡¥Ì¹µ½‘”€ôôô€±¥ÍÐœ€üÑ¡¥Ì¹µ•ÑÉ¥½¹™¥œ€è€¡Ñ¡¥Ì¹Á…•5•ÑÉ¥½¹™¥ÍmÑ¡¥Ì¹µ½‘•tñðmt¤(€€€ô°(€€€…ÁÁÉ½Ù…±Ñ¥½¹=ÁÑ¥½¹Ì ¤ì(€€€€€½¹ÍÐ½ÁÑ¥½¹Ì€ôÑ¡¥Ì¹‘¥Ð¹ÑåÁ”¹±…Ý}½¹ÑÉ…Ñ}…ÁÁÉ½Ù…±}…Ñ¥½¸ñðmt(€€€€€É•ÑÕÉ¸½ÁÑ¥½¹Ì¹±•¹Ñ €ü½ÁÑ¥½¹Ì€èl(€€€€€€€ì±…‰•°è€Ÿ¦k¢þœ°Ù…±Õ”è€Á…ÍÌœô°(€€€€€€€ì±…‰•°è€Ÿ¦¦Ï–nxœ°Ù…±Õ”è€É•©•Ðœô°(€€€€€€€ì±…‰•°è€Ÿ¦–n{’þ»šRäœ°Ù…±Õ”è€‰…¬œô(€€€€€t(€€€ô°(€€€Í¥¹Ñ¥½¹=ÁÑ¥½¹Ì ¤ì(€€€€€¥˜€¡Ñ¡¥Ì¹Í¥¹½É´€˜˜Ñ¡¥Ì¹Í¥¹½É´¹Á…ÉÑ¥…°¤ì(€€€€€€€É•ÑÕÉ¸mì±…‰•°è€Ÿ¢†—¦öCž¶ûžöË’âë–ÞËž¶û¢ºˆœ°Ù…±Õ”èÑ¡¥Ì¹½¹ÑÉ…ÑMÑ…Ñ•Ì¹Í¥¹M¥¹•õt(€€€€€ô(€€€€€É•ÑÕÉ¸l(€€€€€€€ì±…‰•°è€Ÿ¦£–"ž¶û¢ºˆœ°Ù…±Õ”èÑ¡¥Ì¹½¹ÑÉ…ÑMÑ…Ñ•Ì¹Í¥¹A…ÉÑ¥…°ô°(€€€€€€€ì±…‰•°è€Ÿ–ÞËž¶û¢ºˆœ°Ù…±Õ”èÑ¡¥Ì¹½¹ÑÉ…ÑMÑ…Ñ•Ì¹Í¥¹M¥¹•ô(€€€€€t(€€€ô°(€€€Í¥¹Ñ¥½¹Q¥À ¤ì(€€€€€É•ÑÕÉ¸Ñ¡¥Ì¹Í¥¹½É´€˜˜Ñ¡¥Ì¹Í¥¹½É´¹Á…ÉÑ¥…°(€€€€€€€€ü€Ÿ–öO–&7–B#–B3’âë¦£–"ž¶û¢º‹¾ò3–>¿žîŸžî·¢†—¦öC’âë–ÞËž¶û¢º‹Žœ(€€€€€€€€è€Ÿž¶ûžöË–B;–B#–B3¢þo–—–Æ—žê›’â·¾òo–šž¶ûžöË–Âkšr«–º3š"C¾ò3¢¾ß¦'š.§¦£–"ž¶û¢º‹Žœ(€€€ô°(€€€¥¹Ù½¥•Ñ¥½¹=ÁÑ¥½¹Ì ¤ì(€€€€€¥˜€¡Ñ¡¥Ì¹¥¹Ù½¥•½É´€˜˜Ñ¡¥Ì¹¥¹Ù½¥•½É´¹Á…ÉÑ¥…°¤ì(€€€€€€€É•ÑÕÉ¸mì±…‰•°è€Ÿ¢†—¦öC–òž–£’âë–ÞË–òž– œ°Ù…±Õ”èÑ¡¥Ì¹½¹ÑÉ…ÑMÑ…Ñ•Ì¹¥¹Ù½¥•%ÍÍÕ•õt(€€€€€ô(€€€€€É•ÑÕÉ¸l(€€€€€€€ì±…‰•°è€Ÿ¦£–"–òž– œ°Ù…±Õ”èÑ¡¥Ì¹½¹ÑÉ…ÑMÑ…Ñ•Ì¹¥¹Ù½¥•A…ÉÑ¥…°ô°(€€€€€€€ì±…‰•°è€Ÿ–ÞË–òž– œ°Ù…±Õ”èÑ¡¥Ì¹½¹ÑÉ…ÑMÑ…Ñ•Ì¹¥¹Ù½¥•%ÍÍÕ•ô(€€€€€t(€€€ô°(€€€¥¹Ù½¥•Ñ¥½¹Q¥À ¤ì(€€€€€É•ÑÕÉ¸Ñ¡¥Ì¹¥¹Ù½¥•½É´€˜˜Ñ¡¥Ì¹¥¹Ù½¥•½É´¹Á…ÉÑ¥…°(€€€€€€€€ü€Ÿ–öO–&7šRÛ¢Òç¢º‡–"K’âë¦£–"–òž–£¾ò3–>¿žîŸžî·¢†—¦öC’âë–ÞË–òž–£Žœ(€€€€€€€€è€Ÿ–ššr³šr–>Gž–£–Âkšr«–£¦£–ò–ß¾ò3¢¾ß¦'š.§¦£–"–òž–£Žœ(€€€ô°(€€€µ½‘•5•ÑÉ¥Ì ¤ì(€€€€€¥˜€¡Ñ¡¥Ì¹µ½‘”€ôôô€±¥ÍÐœ¤É•ÑÕÉ¸Ñ¡¥Ì¹µ•ÑÉ¥Ì(€€€€€¥˜€¡Ñ¡¥Ì¹µ½‘”€ôôô€…ÁÁÉ½Ù…°œ¤ì(€€€€€€€½¹ÍÐÕÍÑ½µ•É%‘Ì€ô¹•ÜM•Ð¡Ñ¡¥Ì¹½¹ÑÉ…ÑÌ¹µ…À¡¥Ñ•´€ôø¥Ñ•´¹ÕÍÑ½µ•É%ñð¥Ñ•´¹ÕÍÑ½µ•É}¥¤¹™¥±Ñ•È¡	½½±•…¸¤¤(€€€€€€€É•ÑÕÉ¸l(€€€€€€€€€ìµ•ÑÉ¥-•äè€Ñ½Ñ…°œ°µ•ÑÉ¥Y…±Õ”èÑ¡¥Ì¹Ñ½Ñ…°ô°(€€€€€€€€€ìµ•ÑÉ¥-•äè€É•Ù¥•Ý¥¹œœ°µ•ÑÉ¥Y…±Õ”èÑ¡¥Ì¹½¹ÑÉ…ÑÌ¹±•¹Ñ ô°(€€€€€€€€€ìµ•ÑÉ¥-•äè€ÕÍÑ½µ•ÉÌœ°µ•ÑÉ¥Y…±Õ”èÕÍÑ½µ•É%‘Ì¹Í¥é”ô°(€€€€€€€€€ìµ•ÑÉ¥-•äè€…µ½Õ¹Ðœ°µ•ÑÉ¥Y…±Õ”èÑ¡¥Ì¹™½Éµ…Ñ5½¹•ä¡Ñ¡¥Ì¹ÍÕµ	ä¡Ñ¡¥Ì¹½¹ÑÉ…ÑÌ°¥Ñ•´€ôø¥Ñ•´¹Í¥¹µ½Õ¹Ðñð¥Ñ•´¹Í¥¹}…µ½Õ¹Ð¤¤ô(€€€€€€€t(€€€€€ô(€€€€€¥˜€¡Ñ¡¥Ì¹µ½‘”€ôôô€Ñ•µÁ±…Ñ”œ¤ì(€€€€€€€É•ÑÕÉ¸l(€€€€€€€€€ìµ•ÑÉ¥-•äè€Ñ½Ñ…°œ°µ•ÑÉ¥Y…±Õ”èÑ¡¥Ì¹Ñ½Ñ…°ô°(€€€€€€€€€ìµ•ÑÉ¥-•äè€•¹…‰±•œ°µ•ÑÉ¥Y…±Õ”èÑ¡¥Ì¹Ñ•µÁ±…Ñ•Ì¹™¥±Ñ•È¡¥Ñ•´€ôøÑ¡¥Ì¹¥Í¹…‰±•¡¥Ñ•´¹ÍÑ…ÑÕÌ¤¤¹±•¹Ñ ô°(€€€€€€€€€ìµ•ÑÉ¥-•äè€‘¥Í…‰±•œ°µ•ÑÉ¥Y…±Õ”èÑ¡¥Ì¹Ñ•µÁ±…Ñ•Ì¹™¥±Ñ•È¡¥Ñ•´€ôø€…Ñ¡¥Ì¹¥Í¹…‰±•¡¥Ñ•´¹ÍÑ…ÑÕÌ¤¤¹±•¹Ñ ô°(€€€€€€€€€ìµ•ÑÉ¥-•äè€Ý¥Ñ¡¥±”œ°µ•ÑÉ¥Y…±Õ”èÑ¡¥Ì¹Ñ•µÁ±…Ñ•Ì¹™¥±Ñ•È¡¥Ñ•´€ôø¥Ñ•´¹™¥±•UÉ°ñð¥Ñ•´¹™¥±•}ÕÉ°¤¹±•¹Ñ ô(€€€€€€€t(€€€€€ô(€€€€€¥˜€¡Ñ¡¥Ì¹µ½‘”€ôôô€™•”œ¤ì(€€€€€€€É•ÑÕÉ¸l(€€€€€€€€€ìµ•ÑÉ¥-•äè€Ñ½Ñ…°œ°µ•ÑÉ¥Y…±Õ”èÑ¡¥Ì¹Ñ½Ñ…°ô°(€€€€€€€€€ìµ•ÑÉ¥-•äè€Á•¹‘¥¹œœ°µ•ÑÉ¥Y…±Õ”èÑ¡¥Ì¹™••Ì¹™¥±Ñ•È¡¥Ñ•´€ôøÑ¡¥Ì¹¥Í••A•¹‘¥¹œ¡¥Ñ•´¤¤¹±•¹Ñ ô°(€€€€€€€€€ìµ•ÑÉ¥-•äè€½¹™¥Éµ•œ°µ•ÑÉ¥Y…±Õ”èÑ¡¥Ì¹™••Ì¹™¥±Ñ•È¡¥Ñ•´€ôøÑ¡¥Ì¹¥Í••½¹™¥Éµ•¡¥Ñ•´¤¤¹±•¹Ñ ô°(€€€€€€€€€ìµ•ÑÉ¥-•äè€¥¹Ù½¥•œ°µ•ÑÉ¥Y…±Õ”èÑ¡¥Ì¹™••Ì¹™¥±Ñ•È¡¥Ñ•´€ôøÑ¡¥Ì¹¥Í••%¹Ù½¥•¡¥Ñ•´¤¤¹±•¹Ñ ô(€€€€€€€t(€€€€€ô(€€€€€¥˜€¡Ñ¡¥Ì¹µ½‘”€ôôô€…ÑÑ…¡µ•¹Ðœ¤ì(€€€€€€€½¹ÍÐ½¹ÑÉ…Ñ%‘Ì€ô¹•ÜM•Ð¡Ñ¡¥Ì¹…ÑÑ…¡µ•¹ÑÌ¹µ…À¡¥Ñ•´€ôø¥Ñ•´¹½¹ÑÉ…Ñ%ñð¥Ñ•´¹½¹ÑÉ…Ñ}¥¤¹™¥±Ñ•È¡	½½±•…¸¤¤(€€€€€€€É•ÑÕÉ¸l(€€€€€€€€€ìµ•ÑÉ¥-•äè€Ñ½Ñ…°œ°µ•ÑÉ¥Y…±Õ”èÑ¡¥Ì¹Ñ½Ñ…°ô°(€€€€€€€€€ìµ•ÑÉ¥-•äè€½¹ÑÉ…ÑÌœ°µ•ÑÉ¥Y…±Õ”è½¹ÑÉ…Ñ%‘Ì¹Í¥é”ô°(€€€€€€€€€ìµ•ÑÉ¥-•äè€ÑåÁ•œ°µ•ÑÉ¥Y…±Õ”èÑ¡¥Ì¹…ÑÑ…¡µ•¹ÑÌ¹™¥±Ñ•È¡¥Ñ•´€ôø¥Ñ•´¹™¥±•QåÁ”ñð¥Ñ•´¹™¥±•}ÑåÁ”¤¹±•¹Ñ ô°(€€€€€€€€€ìµ•ÑÉ¥-•äè€™¥±•Ìœ°µ•ÑÉ¥Y…±Õ”èÑ¡¥Ì¹…ÑÑ…¡µ•¹ÑÌ¹™¥±Ñ•È¡¥Ñ•´€ôø¥Ñ•´¹™¥±•UÉ°ñð¥Ñ•´¹™¥±•}ÕÉ°¤¹±•¹Ñ ô(€€€€€€€t(€€€€€ô(€€€€€É•ÑÕÉ¸mt(€€€ô°(€€€…¹I•…‘ÁÁÉ½Ù…° ¤ì(€€€€€É•ÑÕÉ¸Ñ¡¥Ì¸‘…ÕÑ ¹¡…ÍA•Éµ¤ ½¹ÑÉ…Ðé…ÁÁÉ½Ù…°é±¥ÍÐœ¤(€€€ô°(€€€…¹I•…‘•” ¤ì(€€€€€É•ÑÕÉ¸Ñ¡¥Ì¸‘…ÕÑ ¹¡…ÍA•Éµ¤ ½¹ÑÉ…Ðé™•”é±¥ÍÐœ¤(€€€ô°(€€€…¹I•…‘ÑÑ…¡µ•¹Ð ¤ì(€€€€€É•ÑÕÉ¸Ñ¡¥Ì¸‘…ÕÑ ¹¡…ÍA•Éµ¤ ½¹ÑÉ…Ðé…ÑÑ…¡µ•¹Ðé±¥ÍÐœ¤(€€€ô°(€€€…¹I•…‘MÑ…ÑÕÌ ¤ì(€€€€€É•ÑÕÉ¸Ñ¡¥Ì¸‘…ÕÑ ¹¡…ÍA•Éµ¤ ½¹ÑÉ…ÐéÍÑ…ÑÕÌé±¥ÍÐœ¤(€€€ô(€ô°(€Ý…Ñ èì(€€€€œ‘É½ÕÑ”¹ÅÕ•Éäœèì(€€€€€¥µµ•‘¥…Ñ”èÑÉÕ”°(€€€€€¡…¹‘±•È¡ÅÕ•Éä¤ì(€€€€€€€Ñ¡¥Ì¹µ½‘”€ôÑ¡¥Ì¹¹½Éµ…±¥é•5½‘”¡ÅÕ•Éä¹µ½‘Õ±”¤(€€€€€€€Ñ¡¥Ì¹…ÁÁ±åÕÍÑ½µ•ÉI½ÕÑ•EÕ•Éä ¤(€€€€€€€Ñ¡¥Ì¹±½…‘A…” ¤(€€€€€ô(€€€ô(€ô)ô(ð½ÍÉ¥ÁÐø((ñÍÑå±”Í½Á•±…¹œô‰ÍÍÌˆÍÉŒôˆ¸½½¹ÑÉ…ÐµÁ…”¹ÍÍÌˆøð½ÍÑå±”ø((ñÍÑå±”±…¹œô‰ÍÍÌˆø)¥µÁ½ÉÐ€ˆ¸¸½‰ÕÍ¥¹•ÍÌ½‰ÕÍ¥¹•ÍÌµ‘¥…±½œ¹ÍÍÌˆì(ð½ÍÑå±”ø(