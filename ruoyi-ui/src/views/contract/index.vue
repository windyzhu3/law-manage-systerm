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
              <el-button v-if="canInvoiceFee(row)" v-hasPermi="['contract:f÷}|¶‰žËkºwµçI•™ÍmÉ•™9…µ•t¹±•…ÉY…±¥‘…Ñ”¡ÁÉ½ÁÌ¤(€€€€€€€ô(€€€€€ô¤(€€€ô°(€€€¹½Éµ…±¥é•5…ÁI½Ü¡É½Ü°™¥•±‘Ì¤ì(€€€€€½¹ÍÐÉ•ÍÕ±Ð€ôì€¸¸¹É½Üô(€€€€€™¥•±‘Ì¹™½É…  ¡m…µ•°°Í¹…­•t¤€ôøì(€€€€€€€¥˜€¡É•ÍÕ±Ñm…µ•±t€ôôôÕ¹‘•™¥¹•€˜˜É•ÍÕ±ÑmÍ¹…­•t€„ôôÕ¹‘•™¥¹•¤ì(€€€€€€€€€É•ÍÕ±Ñm…µ•±t€ôÉ•ÍÕ±ÑmÍ¹…­•t(€€€€€€€ô(€€€€€ô¤(€€€€€É•ÑÕÉ¸É•ÍÕ±Ð(€€€ô°(€€€Í•±•ÑÕÍÑ½µ•É½É½¹ÑÉ…Ð¡ÕÍÑ½µ•É%¤ì(€€€€€½¹ÍÐ¥Ñ•´€ôÑ¡¥Ì¹ÕÍÑ½µ•É=ÁÑ¥½¹Ì¹™¥¹¡ÕÍÑ½µ•È€ôøMÑÉ¥¹œ¡ÕÍÑ½µ•È¹ÕÍÑ½µ•É%¤€ôôôMÑÉ¥¹œ¡ÕÍÑ½µ•É%¤¤(€€€€€¥˜€¡¥Ñ•´¤Ñ¡¥Ì¹½¹ÑÉ…Ñ½É´¹ÕÍÑ½µ•É9…µ”€ô¥Ñ•´¹ÕÍÑ½µ•É9…µ”(€€€€€Ñ¡¥Ì¹±•…É½ÉµY…±¥‘…Ñ” ½¹ÑÉ…Ñ½É´œ°lÕÍÑ½µ•É%œ°€ÕÍÑ½µ•É9…µ”t¤(€€€ô°(€€€Í•±•Ñ½¹ÑÉ…Ñ½É•”¡½¹ÑÉ…Ñ%¤ì(€€€€€½¹ÍÐ¥Ñ•´€ôÑ¡¥Ì¹½¹ÑÉ…Ñ=ÁÑ¥½¹Ì¹™¥¹¡½¹ÑÉ…Ð€ôøMÑÉ¥¹œ¡½¹ÑÉ…Ð¹½¹ÑÉ…Ñ%¤€ôôôMÑÉ¥¹œ¡½¹ÑÉ…Ñ%¤¤(€€€€€¥˜€¡¥Ñ•´¤Ñ¡¥Ì¹™••½É´¹½¹ÑÉ…Ñ9…µ”€ô¥Ñ•´¹½¹ÑÉ…Ñ9…µ”(€€€€€Ñ¡¥Ì¹±•…É½ÉµY…±¥‘…Ñ” ™••½ÉµI•˜œ°l½¹ÑÉ…Ñ%t¤(€€€ô°(€€€Í•±•Ñ½¹ÑÉ…Ñ½ÉÑÑ…¡µ•¹Ð¡½¹ÑÉ…Ñ%¤ì(€€€€€½¹ÍÐ¥Ñ•´€ôÑ¡¥Ì¹½¹ÑÉ…Ñ=ÁÑ¥½¹Ì¹™¥¹¡½¹ÑÉ…Ð€ôøMÑÉ¥¹œ¡½¹ÑÉ…Ð¹½¹ÑÉ…Ñ%¤€ôôôMÑÉ¥¹œ¡½¹ÑÉ…Ñ%¤¤(€€€€€¥˜€¡¥Ñ•´¤Ñ¡¥Ì¹…ÑÑ…¡µ•¹Ñ½É´¹½¹ÑÉ…Ñ9…µ”€ô¥Ñ•´¹½¹ÑÉ…Ñ9…µ”(€€€€€Ñ¡¥Ì¹±•…É½ÉµY…±¥‘…Ñ” …ÑÑ…¡µ•¹Ñ½ÉµI•˜œ°l½¹ÑÉ…Ñ%t¤(€€€ô°(€€€½Á•¹½¹ÑÉ…Ð¡É½Ü¤ì(€€€€€Ñ¡¥Ì¹•¹ÍÕÉ•ÕÍÑ½µ•É=ÁÑ¥½¸¡É½Ü¤(€€€€€Ñ¡¥Ì¹½¹ÑÉ…Ñ½É´€ôÉ½Ü€üì(€€€€€€€€¸¸¹Ñ¡¥Ì¹¹½Éµ…±¥é•5…ÁI½Ü¡É½Ü°l(€€€€€€€€€l½¹ÑÉ…Ñ%œ°€½¹ÑÉ…Ñ}¥t°(€€€€€€€€€l½¹ÑÉ…Ñ9…µ”œ°€½¹ÑÉ…Ñ}¹…µ”t°(€€€€€€€€€lÕÍÑ½µ•É%œ°€ÕÍÑ½µ•É}¥t°(€€€€€€€€€lÕÍÑ½µ•É9…µ”œ°€ÕÍÑ½µ•É}¹…µ”t°(€€€€€€€€€l…Í•QåÁ”œ°€…Í•}ÑåÁ”t°(€€€€€€€€€lÍ¥¹µ½Õ¹Ðœ°€Í¥¹}…µ½Õ¹Ðt°(€€€€€€€€€l™••QåÁ”œ°€™••}ÑåÁ”t°(€€€€€€€€€lÍ¥¹5•Ñ¡½œ°€Í¥¹}µ•Ñ¡½t°(€€€€€€€€€lÉ¥Í­1•Ù•°œ°€É¥Í­}±•Ù•°t(€€€€€€€t¤°(€€€€€€€Í¥¹MÑ…ÑÕÌèÕ¹‘•™¥¹•(€€€€€ô€èì(€€€€€€€€¸¸¹Ñ¡¥Ì¹½¹ÑÉ…Ñ½É´°(€€€€€€€…Í•QåÁ”èÑ¡¥Ì¹‘¥Ñ•™…Õ±Ð ±…Ý}½¹ÑÉ…Ñ}…Í•}ÑåÁ”œ¤°(€€€€€€€™••QåÁ”èÑ¡¥Ì¹‘¥Ñ•™…Õ±Ð ±…Ý}½¹ÑÉ…Ñ}™••}ÑåÁ”œ¤°(€€€€€€€Í¥¹5•Ñ¡½èÑ¡¥Ì¹‘¥Ñ•™…Õ±Ð ±…Ý}½¹ÑÉ…Ñ}Í¥¹}µ•Ñ¡½œ¤°(€€€€€€€…Õ‘¥ÑMÑ…ÑÕÌèÑ¡¥Ì¹‘¥Ñ•™…Õ±Ð ±…Ý}½¹ÑÉ…Ñ}…Õ‘¥Ñ}ÍÑ…ÑÕÌœ¤°(€€€€€€€½¹ÑÉ…ÑMÑ…ÑÕÌèÑ¡¥Ì¹‘¥Ñ•™…Õ±Ð ±…Ý}½¹ÑÉ…Ñ}ÍÑ…ÑÕÌœ¤°(€€€€€€€É¥Í­1•Ù•°èÑ¡¥Ì¹‘¥Ñ•™…Õ±Ð ±…Ý}½¹ÑÉ…Ñ}É¥Í­}±•Ù•°œ¤°(€€€€€€€Í¥¹µ½Õ¹Ðè€À(€€€€€ô(€€€€€¥˜€ …É½Ü€˜˜€…Ñ¡¥Ì¹½¹ÑÉ…Ñ½É´¹ÕÍÑ½µ•É%¤Ñ¡¥Ì¹Í•…É¡ÕÍÑ½µ•É=ÁÑ¥½¹Ì œœ¤(€€€€€Ñ¡¥Ì¹½¹ÑÉ…Ñ=Á•¸€ôÑÉÕ”(€€€€€Ñ¡¥Ì¹±•…É½ÉµY…±¥‘…Ñ” ½¹ÑÉ…Ñ½É´œ¤(€€€ô°(€€€½Á•¹•Ñ…¥°¡É½Ü¤ì(€€€€€½¹ÍÐ½¹ÑÉ…Ñ%€ôÉ½Ü¹½¹ÑÉ…Ñ%(€€€€€Ñ¡¥Ì¹‘•Ñ…¥±=Á•¸€ôÑÉÕ”(€€€€€Ñ¡¥Ì¹±½…‘•Ñ…¥°¡½¹ÑÉ…Ñ%°É½Ü¤(€€€ô°(€€€±½…‘•Ñ…¥°¡½¹ÑÉ…Ñ%°Í••€ôíô¤ì(€€€€€Ñ¡¥Ì¹‘•Ñ…¥±½¹ÑÉ…Ð€ôì€¸¸¹Í••ô(€€€€€Ñ¡¥Ì¹‘•Ñ…¥±••Ì€ômt(€€€€€Ñ¡¥Ì¹‘•Ñ…¥±ÑÑ…¡µ•¹ÑÌ€ômt(€€€€€Ñ¡¥Ì¹‘•Ñ…¥±ÁÁÉ½Ù…±Ì€ômt(€€€€€Ñ¡¥Ì¹‘•Ñ…¥±MÑ…ÑÕÍ•Ì€ômt(€€€€€½¹ÍÐ™••I•ÅÕ•ÍÐ€ôÑ¡¥Ì¹…¹I•…‘•”€ü±¥ÍÑ•”¡ì½¹ÑÉ…Ñ%°Á…•9Õ´è€Ä°Á…•M¥é”è€Ôô¤€èAÉ½µ¥Í”¹É•Í½±Ù”¡ìÉ½ÝÌèmtô¤(€€€€€½¹ÍÐ…ÑÑ…¡µ•¹ÑI•ÅÕ•ÍÐ€ôÑ¡¥Ì¹…¹I•…‘ÑÑ…¡µ•¹Ð€ü±¥ÍÑÑÑ…¡µ•¹Ð¡ì½¹ÑÉ…Ñ%°Á…•9Õ´è€Ä°Á…•M¥é”è€Ôô¤€èAÉ½µ¥Í”¹É•Í½±Ù”¡ìÉ½ÝÌèmtô¤(€€€€€½¹ÍÐ…ÁÁÉ½Ù…±I•ÅÕ•ÍÐ€ôÑ¡¥Ì¹…¹I•…‘ÁÁÉ½Ù…°€ü±¥ÍÑÁÁÉ½Ù…°¡ì½¹ÑÉ…Ñ%°Á…•9Õ´è€Ä°Á…•M¥é”è€Ôô¤€èAÉ½µ¥Í”¹É•Í½±Ù”¡ìÉ½ÝÌèmtô¤(€€€€€½¹ÍÐÍÑ…ÑÕÍI•ÅÕ•ÍÐ€ôÑ¡¥Ì¹…¹I•…‘MÑ…ÑÕÌ€ü±¥ÍÑMÑ…ÑÕÌ¡ì½¹ÑÉ…Ñ%°Á…•9Õ´è€Ä°Á…•M¥é”è€Ôô¤€èAÉ½µ¥Í”¹É•Í½±Ù”¡ìÉ½ÝÌèmtô¤(€€€€€AÉ½µ¥Í”¹…±°¡l(€€€€€€€•Ñ½¹ÑÉ…Ð¡½¹ÑÉ…Ñ%¤°(€€€€€€€™••I•ÅÕ•ÍÐ°(€€€€€€€…ÑÑ…¡µ•¹ÑI•ÅÕ•ÍÐ°(€€€€€€€…ÁÁÉ½Ù…±I•ÅÕ•ÍÐ°(€€€€€€€ÍÑ…ÑÕÍI•ÅÕ•ÍÐ(€€€€€t¤¹Ñ¡•¸ ¡m‘•Ñ…¥°°™••Ì°…ÑÑ…¡µ•¹ÑÌ°…ÁÁÉ½Ù…±Ì°ÍÑ…ÑÕÍ•Ít¤€ôøì(€€€€€€€Ñ¡¥Ì¹‘•Ñ…¥±½¹ÑÉ…Ð€ô‘•Ñ…¥°¹‘…Ñ„ñðíô(€€€€€€€Ñ¡¥Ì¹‘•Ñ…¥±••Ì€ô™••Ì¹É½ÝÌñðmt(€€€€€€€Ñ¡¥Ì¹‘•Ñ…¥±ÑÑ…¡µ•¹ÑÌ€ô…ÑÑ…¡µ•¹ÑÌ¹É½ÝÌñðmt(€€€€€€€Ñ¡¥Ì¹‘•Ñ…¥±ÁÁÉ½Ù…±Ì€ô…ÁÁÉ½Ù…±Ì¹É½ÝÌñðmt(€€€€€€€Ñ¡¥Ì¹‘•Ñ…¥±MÑ…ÑÕÍ•Ì€ôÍÑ…ÑÕÍ•Ì¹É½ÝÌñðmt(€€€€€ô¤(€€€ô°(€€€É•™É•Í¡•Ñ…¥±%™=Á•¸¡É½Ü¤ì(€€€€€½¹ÍÐ½¹ÑÉ…Ñ%€ôÉ½Ü€˜˜€¡É½Ü¹½¹ÑÉ…Ñ%ñðÉ½Ü¹½¹ÑÉ…Ñ}¥¤(€€€€€¥˜€¡Ñ¡¥Ì¹‘•Ñ…¥±=Á•¸€˜˜½¹ÑÉ…Ñ%¤ì(€€€€€€€Ñ¡¥Ì¹±½…‘•Ñ…¥°¡½¹ÑÉ…Ñ%°É½Ü¤(€€€€€ô(€€€ô°(€€€Ù¥•Ý5…ÑÑ•È¡É½Ü¤ì(€€€€€Ñ¡¥Ì¸‘É½ÕÑ•È¹ÁÕÍ ¡ìÁ…Ñ è€œ½µ…ÑÑ•È½±¥ÍÐœ°ÅÕ•Éäèìµ½‘Õ±”è€±¥ÍÐœ°½¹ÑÉ…Ñ%èÉ½Ü¹½¹ÑÉ…Ñ%ñðÉ½Ü¹½¹ÑÉ…Ñ}¥ôô¤(€€€ô°(€€€¡…¹‘±••Ñ…¥±Ñ¥½¸¡…Ñ¥½¸°É½Ü¤ì(€€€€€…Ñ¥½¸¡É½Ü¤(€€€ô°(€€€Í…Ù•½¹ÑÉ…Ð ¤ì(€€€€€Ñ¡¥Ì¸‘É•™Ì¹É•Í½ÕÉ•¥…±½Ì¹Ù…±¥‘…Ñ” ½¹ÑÉ…Ðœ°Ù…±¥€ôøì(€€€€€€€¥˜€ …Ù…±¥¤É•ÑÕÉ¸(€€€€€€€€ì¡Ñ¡¥Ì¹½¹ÑÉ…Ñ½É´¹½¹ÑÉ…Ñ%€üÕÁ‘…Ñ•½¹ÑÉ…Ð€è…‘‘½¹ÑÉ…Ð¤¡Ñ¡¥Ì¹½¹ÑÉ…Ñ½É´¤¹Ñ¡•¸  ¤€ôøì(€€€€€€€€€Ñ¡¥Ì¸‘µ½‘…°¹µÍMÕ•ÍÌ Ÿ’þw–¶cš"C–*|œ¤(€€€€€€€€€Ñ¡¥Ì¹½¹ÑÉ…Ñ=Á•¸€ô™…±Í”(€€€€€€€€€Ñ¡¥Ì¹±½…‘A…” ¤(€€€€€€€€€Ñ¡¥Ì¹É•™É•Í¡•Ñ…¥±%™=Á•¸¡ì½¹ÑÉ…Ñ%èÑ¡¥Ì¹½¹ÑÉ…Ñ½É´¹½¹ÑÉ…Ñ%ô¤(€€€€€€€ô¤(€€€€€ô¤(€€€ô°(€€€É•µ½Ù•½¹ÑÉ…Ð¡É½Ü¤ìÑ¡¥Ì¸‘µ½‘…°¹½¹™¥É´ Ÿž†»¢º“–"ƒ¦f“¢¾—–B#–B3–B_¾ò|œ¤¹Ñ¡•¸  ¤€ôø‘•±½¹ÑÉ…Ð¡É½Ü¹½¹ÑÉ…Ñ%¤¤¹Ñ¡•¸  ¤€ôøìÑ¡¥Ì¸‘µ½‘…°¹µÍMÕ•ÍÌ Ÿ–"ƒ¦f“š"C–*|œ¤ìÑ¡¥Ì¹±½…‘A…” ¤ô¤¹…Ñ   ¤€ôøíô¤ô°(€€€ÍÕ‰µ¥Ñ=¹”¡É½Ü¤ì(€€€€€É•ÑÕÉ¸ÍÕ‰µ¥Ñ½¹ÑÉ…Ð¡É½Ü¹½¹ÑÉ…Ñ%¤¹Ñ¡•¸  ¤€ôøì(€€€€€€€Ñ¡¥Ì¸‘µ½‘…°¹µÍMÕ•ÍÌ Ÿ–ÞËš>C’ê“–º‡š&äœ¤(€€€€€€€Ñ¡¥Ì¹±½…‘A…” ¤(€€€€€€€Ñ¡¥Ì¹É•™É•Í¡•Ñ…¥±%™=Á•¸¡É½Ü¤(€€€€€ô¤(€€€ô°(€€€Í¥¹Ñ¥½¹Q•áÐ¡É½Ü¤ì(€€€€€É•ÑÕÉ¸Ñ¡¥Ì¹Í…µ•Y…±Õ”¡Ñ¡¥Ì¹Í¥¹MÑ…ÑÕÍ=˜¡É½Ü¤°Ñ¡¥Ì¹½¹ÑÉ…ÑMÑ…Ñ•Ì¹Í¥¹A…ÉÑ¥…°¤€ü€Ÿ¢†—¦öCž¶ûžöÈœ€è€Ÿž¶ûžöÈœ(€€€ô°(€€€¥¹Ù½¥•Ñ¥½¹Q•áÐ¡É½Ü¤ì(€€€€€É•ÑÕÉ¸Ñ¡¥Ì¹¥Í••%¹Ù½¥•A…ÉÑ¥…°¡É½Ü¤€ü€Ÿ¢†—¦öC–òž– œ€è€Ÿ–òž– œ(€€€ô°(€€€½Á•¹ÁÁÉ½Ù…°¡É½Ü¤ì(€€€€€½¹ÍÐ…Ñ¥½¸€ôÑ¡¥Ì¹‘¥Ñ•™…Õ±Ð ±…Ý}½¹ÑÉ…Ñ}…ÁÁÉ½Ù…±}…Ñ¥½¸œ¤ñð€¡Ñ¡¥Ì¹…ÁÁÉ½Ù…±Ñ¥½¹=ÁÑ¥½¹ÍlÁt€˜˜Ñ¡¥Ì¹…ÁÁÉ½Ù…±Ñ¥½¹=ÁÑ¥½¹ÍlÁt¹Ù…±Õ”¤ñð€Á…ÍÌœ(€€€€€Ñ¡¥Ì¹…ÁÁÉ½Ù…±½É´€ôì½¹ÑÉ…Ñ%èÉ½Ü¹½¹ÑÉ…Ñ%ñðÉ½Ü¹½¹ÑÉ…Ñ}¥°…Ñ¥½¸°½Á¥¹¥½¸è€œœô(€€€€€Ñ¡¥Ì¹…ÁÁÉ½Ù…±=Á•¸€ôÑÉÕ”(€€€€€Ñ¡¥Ì¸‘¹•áÑQ¥¬  ¤€ôøÑ¡¥Ì¸‘É•™Ì¹±¥™•å±•¥…±½Ì€˜˜Ñ¡¥Ì¸‘É•™Ì¹±¥™•å±•¥…±½Ì¹±•…ÉÁÁÉ½Ù…° ¤¤(€€€ô°(€€€Í…Ù•ÁÁÉ½Ù…° ¤ì(€€€€€Ñ¡¥Ì¸‘É•™Ì¹±¥™•å±•¥…±½Ì¹Ù…±¥‘…Ñ•ÁÁÉ½Ù…°¡Ù…±¥€ôøì(€€€€€€€¥˜€ …Ù…±¥¤É•ÑÕÉ¸(€€€€€€€…ÁÁÉ½Ù…±½¹ÑÉ…Ð¡Ñ¡¥Ì¹…ÁÁÉ½Ù…±½É´¤¹Ñ¡•¸  ¤€ôøìÑ¡¥Ì¸‘µ½‘…°¹µÍMÕ•ÍÌ Ÿ–º‡š&ç–º3š"@œ¤ìÑ¡¥Ì¹…ÁÁÉ½Ù…±=Á•¸€ô™…±Í”ìÑ¡¥Ì¹±½…‘A…” ¤ìÑ¡¥Ì¹É•™É•Í¡•Ñ…¥±%™=Á•¸¡ì½¹ÑÉ…Ñ%èÑ¡¥Ì¹…ÁÁÉ½Ù…±½É´¹½¹ÑÉ…Ñ%ô¤ô¤(€€€€€ô¤(€€€ô°(€€€Í¥¹=¹”¡É½Ü¤ì(€€€€€½¹ÍÐÁ…ÉÑ¥…°€ôÑ¡¥Ì¹Í…µ•Y…±Õ”¡Ñ¡¥Ì¹Í¥¹MÑ…ÑÕÍ=˜¡É½Ü¤°Ñ¡¥Ì¹½¹ÑÉ…ÑMÑ…Ñ•Ì¹Í¥¹A…ÉÑ¥…°¤(€€€€€Ñ¡¥Ì¹Í¥¹½É´€ôì(€€€€€€€½¹ÑÉ…Ñ%èÉ½Ü¹½¹ÑÉ…Ñ%ñðÉ½Ü¹½¹ÑÉ…Ñ}¥°(€€€€€€€Í¥¹MÑ…ÑÕÌèÁ…ÉÑ¥…°€üÑ¡¥Ì¹½¹ÑÉ…ÑMÑ…Ñ•Ì¹Í¥¹M¥¹•€èÑ¡¥Ì¹½¹ÑÉ…ÑMÑ…Ñ•Ì¹Í¥¹A…ÉÑ¥…°°(€€€€€€€Á…ÉÑ¥…°°(€€€€€€€É½Ü(€€€€€ô(€€€€€Ñ¡¥Ì¹Í¥¹=Á•¸€ôÑÉÕ”(€€€ô°(€€€Í…Ù•M¥¸ ¤ì(€€€€€¥˜€ …Ñ¡¥Ì¹Í¥¹½É´¹Í¥¹MÑ…ÑÕÌ¤ì(€€€€€€€Ñ¡¥Ì¸‘µ½‘…°¹µÍ]…É¹¥¹œ Ÿ¢¾ß¦'š.§ž¶ûžöË–*£’öpœ¤(€€€€€€€É•ÑÕÉ¸(€€€€€ô(€€€€€Í¥¹½¹ÑÉ…Ð¡ì½¹ÑÉ…Ñ%èÑ¡¥Ì¹Í¥¹½É´¹½¹ÑÉ…Ñ%°Í¥¹MÑ…ÑÕÌèÑ¡¥Ì¹Í¥¹½É´¹Í¥¹MÑ…ÑÕÌô¤¹Ñ¡•¸  ¤€ôøì(€€€€€€€Ñ¡¥Ì¸‘µ½‘…°¹µÍMÕ•ÍÌ Ÿž¶ûžöËš"C–*|œ¤(€€€€€€€Ñ¡¥Ì¹Í¥¹=Á•¸€ô™…±Í”(€€€€€€€Ñ¡¥Ì¹±½…‘A…” ¤(€€€€€€€Ñ¡¥Ì¹É•™É•Í¡•Ñ…¥±%™=Á•¸¡Ñ¡¥Ì¹Í¥¹½É´¹É½Ü¤(€€€€€ô¤¹…Ñ   ¤€ôøíô¤(€€€ô°(€€€…É¡¥Ù•=¹”¡É½Ü¤ìÑ¡¥Ì¸‘ÁÉ½µÁÐ Ÿ¢¾ß¢úO–—–öKš†¢¾Óšb8œ°€Ÿ–B#–B3–öKš†Œœ°Ñ¡¥Ì¹µ•ÍÍ…•	½á=ÁÑ¥½¹Ì¡ì¥¹ÁÕÑY…±Õ”è€Ÿ–B#–B3–öKš†Œœ°¥¹ÁÕÑA…ÑÑ•É¸è€½qL¬¼°¥¹ÁÕÑÉÉ½É5•ÍÍ…”è€Ÿ–öKš†¢¾Óšb;–þ–†¬œô¤¤¹Ñ¡•¸ ¡ìÙ…±Õ”ô¤€ôø…É¡¥Ù•½¹ÑÉ…Ð¡ì½¹ÑÉ…Ñ%èÉ½Ü¹½¹ÑÉ…Ñ%°É•…Í½¸èÙ…±Õ”ô¤¤¹Ñ¡•¸  ¤€ôøìÑ¡¥Ì¸‘µ½‘…°¹µÍMÕ•ÍÌ Ÿ–öKš†š"C–*|œ¤ìÑ¡¥Ì¹±½…‘A…” ¤ìÑ¡¥Ì¹É•™É•Í¡•Ñ…¥±%™=Á•¸¡É½Ü¤ô¤¹…Ñ   ¤€ôøíô¤ô°(€€€Ù½¥‘=¹”¡É½Ü¤ìÑ¡¥Ì¸‘ÁÉ½µÁÐ Ÿ¢¾ß¢úO–—’ös–ê–:–n€œ°€Ÿ–B#–B3’ös–ê|œ°Ñ¡¥Ì¹µ•ÍÍ…•	½á=ÁÑ¥½¹Ì¡ì¥¹ÁÕÑY…±Õ”è€Ÿ–B#–B3’ös–ê|œ°¥¹ÁÕÑA…ÑÑ•É¸è€½qL¬¼°¥¹ÁÕÑÉÉ½É5•ÍÍ…”è€Ÿ’ös–ê–:–nƒ–þ–†¬œô¤¤¹Ñ¡•¸ ¡ìÙ…±Õ”ô¤€ôøÙ½¥‘½¹ÑÉ…Ð¡ì½¹ÑÉ…Ñ%èÉ½Ü¹½¹ÑÉ…Ñ%°É•…Í½¸èÙ…±Õ”ô¤¤¹Ñ¡•¸  ¤€ôøìÑ¡¥Ì¸‘µ½‘…°¹µÍMÕ•ÍÌ Ÿ’ös–êš"C–*|œ¤ìÑ¡¥Ì¹±½…‘A…” ¤ìÑ¡¥Ì¹É•™É•Í¡•Ñ…¥±%™=Á•¸¡É½Ü¤ô¤¹…Ñ   ¤€ôøíô¤ô°(€€€Ñ•Éµ¥¹…Ñ•=¹”¡É½Ü¤ìÑ¡¥Ì¸‘ÁÉ½µÁÐ Ÿ¢¾ß¢úO–—žî#š¶‹–:–n€œ°€Ÿ–B#–B3žî#š¶ˆœ°Ñ¡¥Ì¹µ•ÍÍ…•	½á=ÁÑ¥½¹Ì¡ì¥¹ÁÕÑA…ÑÑ•É¸è€½qL¬¼°¥¹ÁÕÑÉÉ½É5•ÍÍ…”è€Ÿžî#š¶‹–:–nƒ–þ–†¬œô¤¤¹Ñ¡•¸ ¡ìÙ…±Õ”ô¤€ôøÑ•Éµ¥¹…Ñ•½¹ÑÉ…Ð¡ì½¹ÑÉ…Ñ%èÉ½Ü¹½¹ÑÉ…Ñ%°É•…Í½¸èÙ…±Õ”ô¤¤¹Ñ¡•¸  ¤€ôøìÑ¡¥Ì¸‘µ½‘…°¹µÍMÕ•ÍÌ Ÿžî#š¶‹š"C–*|œ¤ìÑ¡¥Ì¹±½…‘A…” ¤ìÑ¡¥Ì¹É•™É•Í¡•Ñ…¥±%™=Á•¸¡É½Ü¤ô¤¹…Ñ   ¤€ôøíô¤ô°(€€€½Á•¹%µÁ½ÉÐ ¤ìÑ¡¥Ì¸‘É•™Ì¹¥µÁ½ÉÑI•˜¹½Á•¸ ¤ô°(€€€¡…¹‘±•áÁ½ÉÐ ¤ìÑ¡¥Ì¹‘½Ý¹±½… ½¹ÑÉ…Ð½•áÁ½ÉÐœ°ì€¸¸¹Ñ¡¥Ì¹ÅÕ•Éäô°½¹ÑÉ…Ñ|‘í…Ñ”¹¹½Ü ¥ô¹á±Íá€¤ô°(€€€½Á•¹Q•µÁ±…Ñ”¡É½Ü¤ì(€€€€€Ñ¡¥Ì¹Ñ•µÁ±…Ñ•½É´€ôÉ½Ü€üÑ¡¥Ì¹¹½Éµ…±¥é•5…ÁI½Ü¡É½Ü°l(€€€€€€€lÑ•µÁ±…Ñ•%œ°€Ñ•µÁ±…Ñ•}¥t°(€€€€€€€lÑ•µÁ±…Ñ•9…µ”œ°€Ñ•µÁ±…Ñ•}¹…µ”t°(€€€€€€€l…Í•QåÁ”œ°€…Í•}ÑåÁ”t°(€€€€€€€l™¥±•9…µ”œ°€™¥±•}¹…µ”t°(€€€€€€€l™¥±•UÉ°œ°€™¥±•}ÕÉ°t°(€€€€€€€lÙ•ÉÍ¥½¹9¼œ°€Ù•ÉÍ¥½¹}¹¼t(€€€€€t¤€èìÍÑ…ÑÕÌèÑ¡¥Ì¹‘¥Ñ•™…Õ±Ð ÍåÍ}¹½Éµ…±}‘¥Í…‰±”œ¤°Ù•ÉÍ¥½¹9¼è€ØÄœ°…Í•QåÁ”èÑ¡¥Ì¹‘¥Ñ•™…Õ±Ð ±…Ý}½¹ÑÉ…Ñ}…Í•}ÑåÁ”œ¤ô(€€€€€Ñ¡¥Ì¹Ñ•µÁ±…Ñ•=Á•¸€ôÑÉÕ”(€€€€€Ñ¡¥Ì¹±•…É½ÉµY…±¥‘…Ñ” Ñ•µÁ±…Ñ•½ÉµI•˜œ¤(€€€ô°(€€€Íå¹¥±•5•Ñ„¡™½É´°Ù…±Õ”°¹…µ•-•ä€ô€™¥±•9…µ”œ°ÑåÁ•-•ä€ô€™¥±•QåÁ”œ¤ì(€€€€€¥˜€ …Ù…±Õ”¤É•ÑÕÉ¸(€€€€€Ñ¡¥Ì¸‘Í•Ð¡™½É´°¹…µ•-•ä°Ñ¡¥Ì¹™¥±•9…µ•É½µUÉ°¡Ù…±Õ”¤¤(€€€€€¥˜€¡ÑåÁ•-•ä¤Ñ¡¥Ì¸‘Í•Ð¡™½É´°ÑåÁ•-•ä°Ñ¡¥Ì¹™¥±•áÑÉ½µUÉ°¡Ù…±Õ”¤¤(€€€ô°(€€€Íå¹Q•µÁ±…Ñ•5•Ñ„¡Ù…±Õ”¤ì(€€€€€Ñ¡¥Ì¹Íå¹¥±•5•Ñ„¡Ñ¡¥Ì¹Ñ•µÁ±…Ñ•½É´°Ù…±Õ”°€™¥±•9…µ”œ°¹Õ±°¤(€€€€€Ñ¡¥Ì¹±•…É½ÉµY…±¥‘…Ñ” Ñ•µÁ±…Ñ•½ÉµI•˜œ°l™¥±•UÉ°t¤(€€€ô°(€€€Í…Ù•Q•µÁ±…Ñ” ¤ì(€€€€€Ñ¡¥Ì¸‘É•™Ì¹É•Í½ÕÉ•¥…±½Ì¹Ù…±¥‘…Ñ” Ñ•µÁ±…Ñ”œ°Ù…±¥€ôøì(€€€€€€€¥˜€ …Ù…±¥¤É•ÑÕÉ¸(€€€€€€€Ñ¡¥Ì¹Íå¹Q•µÁ±…Ñ•5•Ñ„¡Ñ¡¥Ì¹Ñ•µÁ±…Ñ•½É´¹™¥±•UÉ°¤(€€€€€€€€ì¡Ñ¡¥Ì¹Ñ•µÁ±…Ñ•½É´¹Ñ•µÁ±…Ñ•%€üÕÁ‘…Ñ•Q•µÁ±…Ñ”€è…‘‘Q•µÁ±…Ñ”¤¡Ñ¡¥Ì¹Ñ•µÁ±…Ñ•½É´¤¹Ñ¡•¸  ¤€ôøìÑ¡¥Ì¸‘µ½‘…°¹µÍMÕ•ÍÌ Ÿ’þw–¶cš"C–*|œ¤ìÑ¡¥Ì¹Ñ•µÁ±…Ñ•=Á•¸€ô™…±Í”ìÑ¡¥Ì¹±½…‘A…” ¤ô¤(€€€€€ô¤(€€€ô°(€€€É•µ½Ù•Q•µÁ±…Ñ”¡É½Ü¤ìÑ¡¥Ì¸‘µ½‘…°¹½¹™¥É´ Ÿž†»¢º“–"ƒ¦f“¢¾—–B#–B3š¢‡švÿ–B_¾ò–B¿žR£’â·žjš¢‡švÿ¦r¢š–#–sžR£Žœ¤¹Ñ¡•¸  ¤€ôø‘•±Q•µÁ±…Ñ”¡É½Ü¹Ñ•µÁ±…Ñ•%¤¤¹Ñ¡•¸  ¤€ôøìÑ¡¥Ì¸‘µ½‘…°¹µÍMÕ•ÍÌ Ÿ–"ƒ¦f“š"C–*|œ¤ìÑ¡¥Ì¹±½…‘A…” ¤ô¤¹…Ñ   ¤€ôøíô¤ô°(€€€½Á•¹•”¡É½Ü¤ì(€€€€€Ñ¡¥Ì¹•¹ÍÕÉ•½¹ÑÉ…Ñ=ÁÑ¥½¸¡É½Ü¤(€€€€€½¹ÍÐ¥Í••I½Ü€ôÉ½Ü€˜˜É½Ü¹Á±…¹}¥(€€€€€Ñ¡¥Ì¹™••½É´€ô¥Í••I½Ü(€€€€€€€€üìÁ±…¹%èÉ½Ü¹Á±…¹}¥°½¹ÑÉ…Ñ%èÉ½Ü¹½¹ÑÉ…Ñ}¥°Á•É¥½‘9¼èÉ½Ü¹Á•É¥½‘}¹¼°É••¥Ù…‰±•µ½Õ¹ÐèÉ½Ü¹É••¥Ù…‰±•}…µ½Õ¹Ð°Á±…¹I••¥Ù•…Ñ”èÉ½Ü¹Á±…¹}É••¥Ù•}‘…Ñ”°É••¥Ù•‘µ½Õ¹ÐèÉ½Ü¹É••¥Ù•‘}…µ½Õ¹Ð°½¹™¥ÉµMÑ…ÑÕÌèÉ½Ü¹½¹™¥Éµ}ÍÑ…ÑÕÌ°¥¹Ù½¥•MÑ…ÑÕÌèÉ½Ü¹¥¹Ù½¥•}ÍÑ…ÑÕÌô(€€€€€€€€èì½¹ÑÉ…Ñ%èÉ½Ü€˜˜É½Ü¹½¹ÑÉ…Ñ%°½¹ÑÉ…Ñ9…µ”èÉ½Ü€˜˜É½Ü¹½¹ÑÉ…Ñ9…µ”°Á•É¥½‘9¼è€Ä°É••¥Ù…‰±•µ½Õ¹Ðè€À°É••¥Ù•‘µ½Õ¹Ðè€À°½¹™¥ÉµMÑ…ÑÕÌèÑ¡¥Ì¹‘¥Ñ•™…Õ±Ð ±…Ý}½¹ÑÉ…Ñ}É••¥Ù•}ÍÑ…ÑÕÌœ¤°¥¹Ù½¥•MÑ…ÑÕÌèÑ¡¥Ì¹‘¥Ñ•™…Õ±Ð ±…Ý}½¹ÑÉ…Ñ}¥¹Ù½¥•}ÍÑ…ÑÕÌœ¤ô(€€€€€¥˜€ …É½Ü¤Ñ¡¥Ì¹Í•…É¡½¹ÑÉ…Ñ=ÁÑ¥½¹Ì œœ¤(€€€€€Ñ¡¥Ì¹™••=Á•¸€ôÑÉÕ”(€€€€€Ñ¡¥Ì¹±•…É½ÉµY…±¥‘…Ñ” ™••½ÉµI•˜œ¤(€€€ô°(€€€Í…Ù••” ¤ì(€€€€€Ñ¡¥Ì¸‘É•™Ì¹É•Í½ÕÉ•¥…±½Ì¹Ù…±¥‘…Ñ” ™•”œ°Ù…±¥€ôøì(€€€€€€€¥˜€ …Ù…±¥¤É•ÑÕÉ¸(€€€€€€€€ì¡Ñ¡¥Ì¹™••½É´¹Á±…¹%€üÕÁ‘…Ñ••”€è…‘‘•”¤¡Ñ¡¥Ì¹™••½É´¤¹Ñ¡•¸  ¤€ôøìÑ¡¥Ì¸‘µ½‘…°¹µÍMÕ•ÍÌ Ÿ’þw–¶cš"C–*|œ¤ìÑ¡¥Ì¹™••=Á•¸€ô™…±Í”ìÑ¡¥Ì¹±½…‘A…” ¤ìÑ¡¥Ì¹É•™É•Í¡•Ñ…¥±%™=Á•¸¡ì½¹ÑÉ…Ñ%èÑ¡¥Ì¹™••½É´¹½¹ÑÉ…Ñ%ô¤ô¤(€€€€€ô¤(€€€ô°(€€€É•µ½Ù••”¡É½Ü¤ìÑ¡¥Ì¸‘µ½‘…°¹½¹™¥É´ Ÿž†»¢º“–"ƒ¦f“¢¾—šRÛ¢Òç¢º‡–"K–B_¾ò–ÞËž†»¢º“š"[–ÞË–òž–£žj¢º‡–"K’â7–>¿–"ƒ¦f“Žœ¤¹Ñ¡•¸  ¤€ôø‘•±•”¡É½Ü¹Á±…¹}¥¤¤¹Ñ¡•¸  ¤€ôøìÑ¡¥Ì¸‘µ½‘…°¹µÍMÕ•ÍÌ Ÿ–"ƒ¦f“š"C–*|œ¤ìÑ¡¥Ì¹±½…‘A…” ¤ìÑ¡¥Ì¹É•™É•Í¡•Ñ…¥±%™=Á•¸¡É½Ü¤ô¤¹…Ñ   ¤€ôøíô¤ô°(€€€½¹™¥Éµ••=¹”¡É½Ü¤ìÑ¡¥Ì¸‘ÁÉ½µÁÐ Ÿ¢¾ß¢úO–—–º{šRÛ¦G¦Štœ°€Ÿž†»¢º“šRÛš²øœ°Ñ¡¥Ì¹µ•ÍÍ…•	½á=ÁÑ¥½¹Ì¡ì¥¹ÁÕÑY…±Õ”èMÑÉ¥¹œ¡É½Ü¹É••¥Ù…‰±•}…µ½Õ¹Ðñð€À¤°¥¹ÁÕÑA…ÑÑ•É¸è€½x ü„À¬¡p¸À¬¤ü¥q¬¡p¹q‘ìÄ°Éô¤ü¼°¥¹ÁÕÑÉÉ½É5•ÍÍ…”è€Ÿ¢¾ß¢úO–—–’Ÿ’ê8Ãžj¦G¦Štœô¤¤¹Ñ¡•¸ ¡ìÙ…±Õ”ô¤€ôø½¹™¥Éµ•”¡ìÁ±…¹%èÉ½Ü¹Á±…¹}¥°É••¥Ù•‘µ½Õ¹ÐèÙ…±Õ”ô¤¤¹Ñ¡•¸  ¤€ôøìÑ¡¥Ì¸‘µ½‘…°¹µÍMÕ•ÍÌ Ÿž†»¢º“š"C–*|œ¤ìÑ¡¥Ì¹±½…‘A…” ¤ìÑ¡¥Ì¹É•™É•Í¡•Ñ…¥±%™=Á•¸¡É½Ü¤ô¤¹…Ñ   ¤€ôøíô¤ô°(€€€É•©•Ñ••=¹”¡É½Ü¤ìÑ¡¥Ì¸‘ÁÉ½µÁÐ Ÿ¢¾ß¢úO–—¦¦Ï–n{–:–n€œ°€Ÿ¦¦Ï–n{šRÛš²øœ°Ñ¡¥Ì¹µ•ÍÍ…•	½á=ÁÑ¥½¹Ì¡ì¥¹ÁÕÑA…ÑÑ•É¸è€½qL¬¼°¥¹ÁÕÑÉÉ½É5•ÍÍ…”è€Ÿ¦¦Ï–n{–:–nƒ–þ–†¬œô¤¤¹Ñ¡•¸ ¡ìÙ…±Õ”ô¤€ôøÉ•©•Ñ•”¡ìÁ±…¹%èÉ½Ü¹Á±…¹}¥°É•…Í½¸èÙ…±Õ”ô¤¤¹Ñ¡•¸  ¤€ôøìÑ¡¥Ì¸‘µ½‘…°¹µÍMÕ•ÍÌ Ÿ–ÞË¦¦Ï–nxœ¤ìÑ¡¥Ì¹±½…‘A…” ¤ìÑ¡¥Ì¹É•™É•Í¡•Ñ…¥±%™=Á•¸¡É½Ü¤ô¤¹…Ñ   ¤€ôøíô¤ô°(€€€¥¹Ù½¥•••=¹”¡É½Ü¤ì(€€€€€½¹ÍÐÁ…ÉÑ¥…°€ôÑ¡¥Ì¹¥Í••%¹Ù½¥•A…ÉÑ¥…°¡É½Ü¤(€€€€€Ñ¡¥Ì¹¥¹Ù½¥•½É´€ôì(€€€€€€€Á±…¹%èÉ½Ü¹Á±…¹}¥ñðÉ½Ü¹Á±…¹%°(€€€€€€€¥¹Ù½¥•MÑ…ÑÕÌèÁ…ÉÑ¥…°€üÑ¡¥Ì¹½¹ÑÉ…ÑMÑ…Ñ•Ì¹¥¹Ù½¥•%ÍÍÕ•€èÑ¡¥Ì¹½¹ÑÉ…ÑMÑ…Ñ•Ì¹¥¹Ù½¥•A…ÉÑ¥…°°(€€€€€€€Á…ÉÑ¥…°°(€€€€€€€É½Ü(€€€€€ô(€€€€€Ñ¡¥Ì¹¥¹Ù½¥•=Á•¸€ôÑÉÕ”(€€€ô°(€€€Í…Ù•%¹Ù½¥” ¤ì(€€€€€¥˜€ …Ñ¡¥Ì¹¥¹Ù½¥•½É´¹¥¹Ù½¥•MÑ…ÑÕÌ¤ì(€€€€€€€Ñ¡¥Ì¸‘µ½‘…°¹µÍ]…É¹¥¹œ Ÿ¢¾ß¦'š.§–òž–£–*£’öpœ¤(€€€€€€€É•ÑÕÉ¸(€€€€€ô(€€€€€¥¹Ù½¥••”¡ìÁ±…¹%èÑ¡¥Ì¹¥¹Ù½¥•½É´¹Á±…¹%°¥¹Ù½¥•MÑ…ÑÕÌèÑ¡¥Ì¹¥¹Ù½¥•½É´¹¥¹Ù½¥•MÑ…ÑÕÌô¤¹Ñ¡•¸  ¤€ôøì(€€€€€€€Ñ¡¥Ì¸‘µ½‘…°¹µÍMÕ•ÍÌ Ÿ–òž–£ž*Ûš–ÞËšnÓšZÀœ¤(€€€€€€€Ñ¡¥Ì¹¥¹Ù½¥•=Á•¸€ô™…±Í”(€€€€€€€Ñ¡¥Ì¹±½…‘A…” ¤(€€€€€€€Ñ¡¥Ì¹É•™É•Í¡•Ñ…¥±%™=Á•¸¡Ñ¡¥Ì¹¥¹Ù½¥•½É´¹É½Ü¤(€€€€€ô¤¹…Ñ   ¤€ôøíô¤(€€€ô°(€€€½Á•¹ÑÑ…¡µ•¹Ð¡É½Ü¤ì(€€€€€Ñ¡¥Ì¹•¹ÍÕÉ•½¹ÑÉ…Ñ=ÁÑ¥½¸¡É½Ü¤(€€€€€½¹ÍÐ¥ÍÑÑ…¡µ•¹ÑI½Ü€ôÉ½Ü€˜˜É½Ü¹…ÑÑ…¡µ•¹Ñ}¥(€€€€€Ñ¡¥Ì¹…ÑÑ…¡µ•¹Ñ½É´€ô¥ÍÑÑ…¡µ•¹ÑI½Ü(€€€€€€€€üì…ÑÑ…¡µ•¹Ñ%èÉ½Ü¹…ÑÑ…¡µ•¹Ñ}¥°½¹ÑÉ…Ñ%èÉ½Ü¹½¹ÑÉ…Ñ}¥°™¥±•9…µ”èÉ½Ü¹™¥±•}¹…µ”°™¥±•UÉ°èÉ½Ü¹™¥±•}ÕÉ°°™¥±•QåÁ”èÉ½Ü¹™¥±•}ÑåÁ”°™¥±•M¥é”èÉ½Ü¹™¥±•}Í¥é”ô(€€€€€€€€èì½¹ÑÉ…Ñ%èÉ½Ü€˜˜É½Ü¹½¹ÑÉ…Ñ%°½¹ÑÉ…Ñ9…µ”èÉ½Ü€˜˜É½Ü¹½¹ÑÉ…Ñ9…µ”ô(€€€€€¥˜€ …É½Ü¤Ñ¡¥Ì¹Í•…É¡½¹ÑÉ…Ñ=ÁÑ¥½¹Ì œœ¤(€€€€€Ñ¡¥Ì¹…ÑÑ…¡µ•¹Ñ=Á•¸€ôÑÉÕ”(€€€ô°(€€€Íå¹ÑÑ…¡µ•¹Ñ5•Ñ„¡Ù…±Õ”¤ì(€€€€€Ñ¡¥Ì¹Íå¹¥±•5•Ñ„¡Ñ¡¥Ì¹…ÑÑ…¡µ•¹Ñ½É´°Ù…±Õ”¤(€€€€€Ñ¡¥Ì¹±•…É½ÉµY…±¥‘…Ñ” …ÑÑ…¡µ•¹Ñ½ÉµI•˜œ°l™¥±•UÉ°œ°€™¥±•9…µ”t¤(€€€ô°(€€€Í…Ù•ÑÑ…¡µ•¹Ð ¤ì(€€€€€Ñ¡¥Ì¸‘É•™Ì¹É•Í½ÕÉ•¥…±½Ì¹Ù…±¥‘…Ñ” …ÑÑ…¡µ•¹Ðœ°Ù…±¥€ôøì(€€€€€€€¥˜€ …Ù…±¥¤É•ÑÕÉ¸(€€€€€€€Ñ¡¥Ì¹Íå¹ÑÑ…¡µ•¹Ñ5•Ñ„¡Ñ¡¥Ì¹…ÑÑ…¡µ•¹Ñ½É´¹™¥±•UÉ°¤(€€€€€€€…‘‘ÑÑ…¡µ•¹Ð¡Ñ¡¥Ì¹…ÑÑ…¡µ•¹Ñ½É´¤¹Ñ¡•¸  ¤€ôøìÑ¡¥Ì¸‘µ½‘…°¹µÍMÕ•ÍÌ Ÿ’þw–¶cš"C–*|œ¤ìÑ¡¥Ì¹…ÑÑ…¡µ•¹Ñ=Á•¸€ô™…±Í”ìÑ¡¥Ì¹±½…‘A…” ¤ìÑ¡¥Ì¹É•™É•Í¡•Ñ…¥±%™=Á•¸¡ì½¹ÑÉ…Ñ%èÑ¡¥Ì¹…ÑÑ…¡µ•¹Ñ½É´¹½¹ÑÉ…Ñ%ô¤ô¤(€€€€€ô¤(€€€ô°(€€€É•µ½Ù•ÑÑ…¡µ•¹Ð¡É½Ü¤ì(€€€€€Ñ¡¥Ì¸‘µ½‘…°¹½¹™¥É´ Ÿž†»¢º“–"ƒ¦f“¢¾—–B#–B3¦f’îÛ–B_¾ò|œ¤¹Ñ¡•¸  ¤€ôø‘•±ÑÑ…¡µ•¹Ð¡É½Ü¹…ÑÑ…¡µ•¹Ñ}¥¤¤¹Ñ¡•¸  ¤€ôøì(€€€€€€€Ñ¡¥Ì¸‘µ½‘…°¹µÍMÕ•ÍÌ Ÿ–"ƒ¦f“š"C–*|œ¤(€€€€€€€Ñ¡¥Ì¹±½…‘A…” ¤(€€€€€€€Ñ¡¥Ì¹É•™É•Í¡•Ñ…¥±%™=Á•¸¡É½Ü¤(€€€€€ô¤¹…Ñ   ¤€ôøíô¤(€€€ô°(€€€½Á•¹IÕ±”¡É½Ü¤ì(€€€€€Ñ¡¥Ì¹ÉÕ±•½É´€ôì€¸¸¹É½Ü°ÍÑ…ÑÕÌèÉ½Ü¹ÍÑ…ÑÕÌñðÑ¡¥Ì¹‘¥Ñ•™…Õ±Ð ÍåÍ}¹½Éµ…±}‘¥Í…‰±”œ¤ô(€€€€€Ñ¡¥Ì¹ÉÕ±•=Á•¸€ôÑÉÕ”(€€€€€Ñ¡¥Ì¹±•…É½ÉµY…±¥‘…Ñ” ÉÕ±•½ÉµI•˜œ¤(€€€ô°(€€€Í…Ù•IÕ±” ¤ì(€€€€€Ñ¡¥Ì¸‘É•™Ì¹É•Í½ÕÉ•¥…±½Ì¹Ù…±¥‘…Ñ” ÉÕ±”œ°Ù…±¥€ôøì(€€€€€€€¥˜€ …Ù…±¥¤É•ÑÕÉ¸(€€€€€€€ÕÁ‘…Ñ•IÕ±”¡Ñ¡¥Ì¹ÉÕ±•½É´¤¹Ñ¡•¸  ¤€ôøìÑ¡¥Ì¸‘µ½‘…°¹µÍMÕ•ÍÌ Ÿ’þw–¶cš"C–*|œ¤ìÑ¡¥Ì¹ÉÕ±•=Á•¸€ô™…±Í”ìÑ¡¥Ì¹±½…‘A…” ¤ô¤(€€€€€ô¤(€€€ô(€ô)ô(ð½ÍÉ¥ÁÐø((ñÍÑå±”Í½Á•±…¹œô‰ÍÍÌˆø)¥µÁ½ÉÐ€ˆ¸¸½‰ÕÍ¥¹•ÍÌ½‰ÕÍ¥¹•ÍÌ¹ÍÍÌˆì((¹½¹ÑÉ…ÐµÁ…”ì(€€´µ‰¥èµ™¥±Ñ•Èµ¥¹ÁÕÐµÝ¥‘Ñ è€ÈÈÁÁàì(€€´µ‰¥èµ™¥±Ñ•ÈµÍ•±•ÐµÝ¥‘Ñ è€ÄÈáÁàì)ô((¹½¹ÑÉ…ÐµÁ…”€¹Í•ÑÑ¥¹œµÉ¥…ÉÑ¥±”¤ì(€‰…­É½Õ¹è€ŒÈÔØÍ•ˆì)ô((¹…ÁÁÉ½Ù…°µ…Ñ¥½¸µÉ½ÕÀì(€‘¥ÍÁ±…äè™±•àì(€Ý¥‘Ñ è€ÄÀÀ”ì((€€èéØµ‘••À€¹•°µÉ…‘¥¼µ‰ÕÑÑ½¸ì(€€€™±•àè€Äì(€ô((€€èéØµ‘••À€¹•°µÉ…‘¥¼µ‰ÕÑÑ½¹}}¥¹¹•Èì(€€€Ý¥‘Ñ è€ÄÀÀ”ì(€ô)ô(ð½ÍÑå±”ø((ñÍÑå±”±…¹œô‰ÍÍÌˆø)¥µÁ½ÉÐ€ˆ¸¸½‰ÕÍ¥¹•ÍÌ½‰ÕÍ¥¹•ÍÌµ‘¥…±½œ¹ÍÍÌˆì(ð½ÍÑå±”ø