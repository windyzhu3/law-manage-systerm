<template>
  <div class="biz-page finance-page" :class="'biz-size-' + appSize">
    <biz-page-header eyebrow="FINANCE CENTER" :title="pageTitle" :description="pageDescription">
      <el-button v-if="mode === 'overview'" :size="controlSize" plain icon="el-icon-tickets" @click="switchMode('receivable')">æŸ¥çœ‹åº”æ”¶</el-button>
      <el-button v-if="mode === 'overview'" :size="controlSize" plain icon="el-icon-wallet" @click="switchMode('payment')">å¾…ç¡®è®¤å›æ¬¾</el-button>
      <el-button v-if="mode === 'overview'" :size="controlSize" type="primary" icon="el-icon-document-checked" @click="switchMode('invoice')">å¼€ç¥¨ç®¡ç†</el-button>
    </biz-page-header>
    <finance-action-dialogs
      ref="actionDialogs"
      :payment-visible.sync="paymentOpen"
      :reject-visible.sync="rejectOpen"
      :invoice-visible.sync="invoiceOpen"
      :expense-visible.sync="expenseOpen"
      :payment-form="paymentForm"
      :reject-form="rejectForm"
      :invoice-form="invoiceForm"
      :expense-form="expenseForm"
      :payment-rules="paymentRules"
      :reject-rules="rejectRules"
      :invoice-rules="invoiceRules"
      :expense-rules="expenseRules"
      :options="dict.type"
      :control-size="controlSize"
      :dialog-class="dialogClass"
      :format-money="formatMoney"
      @submit-payment="submitPayment"
      @submit-reject="submitReject"
      @submit-invoice="submitInvoice"
      @submit-expense="submitExpense"
      @sync-expense-file="syncExpenseFile"
    />


    <biz-hero :eyebrow="heroMeta.eyebrow" :title="heroMeta.title" :description="heroMeta.description" />
    <biz-metrics :metrics="metrics" :config="metricConfig" />

    <template v-if="mode === 'overview'">
      <finance-flow-overview :cards="financeFlowCards" :money-formatter="formatMoney" />

      <section class="finance-charts-grid">
        <article class="finance-card finance-chart-card">
          <div class="finance-card-title">
            <div>
              <h3>å›æ¬¾è¶‹åŠ¿ï¼ˆä¸‡å…ƒï¼‰</h3>
              <p>è¿‘ 6 ä¸ªæœˆç¡®è®¤å›æ¬¾é‡‘é¢ä¸å›æ¬¾ç¬”æ•°</p>
            </div>
            <el-button :size="controlSize" type="text" @click="switchMode('payment')">æ›´å¤š</el-button>
          </div>
          <div v-if="receiveTrendRows.length" class="combo-chart">
            <svg viewBox="0 0 520 210" preserveAspectRatio="none">
              <polyline class="trend-grid" points="0,175 520,175" />
              <polyline class="trend-grid" points="0,120 520,120" />
              <polyline class="trend-grid" points="0,65 520,65" />
              <rect v-for="bar in receiveBarList" :key="bar.key" class="chart-hover-bar" :x="bar.x" :y="bar.y" :width="bar.width" :height="bar.height" rx="4">
                <title>{{ bar.title }}</title>
              </rect>
              <polyline class="trend-line green" :points="receiveCountPoints" />
              <circle v-for="point in receiveCountPointList" :key="point.key" class="chart-hover-point" :cx="point.x" :cy="point.y" r="4">
                <title>{{ point.title }}</title>
              </circle>
            </svg>
            <div class="chart-labels"><span v-for="item in receiveTrendRows" :key="item.itemName">{{ item.itemName }}</span></div>
            <div class="chart-legend"><span><i class="blue" />å›æ¬¾é‡‘é¢</span><span><i class="green" />å›æ¬¾ç¬”æ•°</span></div>
          </div>
          <el-empty v-else :image-size="72" description="æš‚æ— å›æ¬¾è¶‹åŠ¿æ•°æ®" />
        </article>

        <article class="finance-card finance-chart-card">
          <div class="finance-card-title">
            <div>
              <h3>åº”æ”¶è´¦é¾„åˆ†å¸ƒ</h3>
              <p>æœªæ”¶æ¬¾é¡¹æŒ‰è´¦é¾„åŒºé—´åˆ†å¸ƒ</p>
            </div>
          </div>
          <div class="donut-wrap">
            <i class="donut" :style="agingDonutStyle"><b>{{ formatShortMoney(agingAmountTotal) }}</b><span>åº”æ”¶æ€»é¢</span></i>
            <ul>
              <li v-for="item in agingStats" :key="item.itemName">
                <em :style="{ background: item.color }" />
                <span>{{ item.label }}</span>
                <strong>{{ formatMoney(item.amountValue) }}</strong>
                <small>{{ item.percent }}</small>
              </li>
            </ul>
          </div>
        </article>

        <article class="finance-card finance-chart-card">
          <div class="finance-card-title">
            <div>
              <h3>å¼€ç¥¨ / è´¹ç”¨è¶‹åŠ¿ï¼ˆä¸‡å…ƒï¼‰</h3>
              <p>å·²å¼€ç¥¨é‡‘é¢ä¸æ¡ˆä»¶è´¹ç”¨æ”¯å‡ºè¶‹åŠ¿</p>
            </div>
            <el-button :size="controlSize" type="text" @click="switchMode('invoice')">æ›´å¤š</el-button>
          </div>
          <div v-if="invoiceExpenseRows.length" class="combo-chart">
            <svg viewBox="0 0 520 210" preserveAspectRatio="none">
              <polyline class="trend-grid" points="0,175 520,175" />
              <polyline class="trend-grid" points="0,120 520,120" />
              <polyline class="trend-grid" points="0,65 520,65" />
              <rect v-for="bar in invoiceBarList" :key="bar.key" class="chart-hover-bar" :x="bar.x" :y="bar.y" :width="bar.width" :height="bar.height" rx="4">
                <title>{{ bar.title }}</title>
              </rect>
              <polyline class="trend-line green" :points="expenseLinePoints" />
              <circle v-for="point in expensePointList" :key="point.key" class="chart-hover-point" :cx="point.x" :cy="point.y" r="4">
                <title>{{ point.title }}</title>
              </circle>
            </svg>
            <div class="chart-labels"><span v-for="item in invoiceExpenseRows" :key="item.itemName">{{ item.itemName }}</span></div>
            <div class="chart-legend"><span><i class="blue" />å¼€ç¥¨é‡‘é¢</span><span><i class="green" />è´¹ç”¨æ”¯å‡º</span></div>
          </div>
          <el-empty v-else :image-size="72" description="æš‚æ— å¼€ç¥¨/è´¹ç”¨è¶‹åŠ¿æ•°æ®" />
        </article>
      </section>

      <finance-overview-tables
        :size="controlSize"
        :pending-payments="pendingPayments"
        :overdue-receivables="overdueReceivables"
        :invoice-activities="invoiceActivities"
        :summary-rows="financeSummaryRows"
        :invoice-status-options="dict.type.law_contract_invoice_status"
        :compact-money="formatCompactMoney"
        :short-date="shortDate"
        :summary-value="formatSummaryValue"
        :summary-year-value="formatSummaryYearValue"
        @navigate="switchMode"
      />
      <section v-if="false" class="finance-overview-tables">
        <biz-table-card :toolbar="false" :pagination="false">
          <template slot="header">
            <div class="section-heading">
              <h3>å¾…ç¡®è®¤å›æ¬¾åˆ—è¡¨</h3>
              <el-button :size="controlSize" type="text" @click="switchMode('payment')">æ›´å¤š</el-button>
            </div>
          </template>
          <el-table :data="pendingPayments" :size="controlSize" class="overview-mini-table">
            <el-table-column label="å®¢æˆ·åç§°" min-width="118" show-overflow-tooltip>
              <template slot-scope="{ row }"><span class="biz-link">{{ row.customerName || '-' }}</span></template>
            </el-table-column>
            <el-table-column label="é‡‘é¢" width="82" align="right"><template slot-scope="{ row }">{{ formatCompactMoney(row.receivableAmount) }}</template></el-table-column>
            <el-table-column label="åˆ°è´¦æ—¥" width="76"><template slot-scope="{ row }">{{ shortDate(row.planReceiveDate) }}</template></el-table-column>
          </el-table>
        </biz-table-card>

        <biz-table-card :toolbar="false" :pagination="false">
          <template slot="header">
            <div class="section-heading">
              <h3>é€¾æœŸåº”æ”¶å®¢æˆ·</h3>
              <el-button :size="controlSize" type="text" @click="switchMode('receivable', { overdueOnly: '1' })">æ›´å¤š</el-button>
            </div>
          </template>
          <el-table :data="overdueReceivables" :size="controlSize" class="overview-mini-table">
            <el-table-column label="å®¢æˆ·åç§°" min-width="118" show-overflow-tooltip>
              <template slot-scope="{ row }"><span class="biz-link">{{ row.customerName || '-' }}</span></template>
            </el-table-column>
            <el-table-column label="å¤©æ•°" width="62" align="center"><template slot-scope="{ row }">{{ row.agingDays || 0 }}å¤©</template></el-table-column>
            <el-table-column label="é‡‘é¢" width="82" align="right"><template slot-scope="{ row }">{{ formatCompactMoney(row.pendingAmount) }}</template></el-table-column>
          </el-table>
        </biz-table-card>

        <biz-table-card :toolbar="false" :pagination="false">
          <template slot="header">
            <div class="section-heading">
              <h3>è¿‘æœŸå‘ç¥¨åŠ¨æ€</h3>
              <el-button :size="controlSize" type="text" @click="switchMode('invoice')">æ›´å¤š</el-button>
            </div>
          </template>
          <el-table :data="invoiceActivities" :size="controlSize" class="overview-mini-table">
            <el-table-column label="å®¢æˆ·åç§°" prop="customerName" min-width="122" show-overflow-tooltip />
            <el-table-column label="é‡‘é¢" width="82" align="right"><template slot-scope="{ row }">{{ formatCompactMoney(row.amount) }}</template></el-table-column>
            <el-table-column label="çŠ¶æ€" width="68" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_contract_invoice_status" :value="row.invoiceStatus" /></template></el-table-column>
          </el-table>
        </biz-table-card>

        <biz-table-card :toolbar="false" :pagination="false">
          <template slot="header">
            <div class="section-heading">
              <h3>è´¹ç”¨ / å›æ¬¾æ¦‚è§ˆè¡¨</h3>
              <el-button :size="controlSize" type="text" @click="switchMode('report')">æŸ¥çœ‹æŠ¥è¡¨</el-button>
            </div>
          </template>
          <el-table :data="financeSummaryRows" :size="controlSize" class="overview-mini-table">
            <el-table-column label="é¡¹ç›®" prop="itemName" min-width="76" />
            <el-table-column label="æœ¬æœˆ" width="76" align="right"><template slot-scope="{ row }">{{ formatSummaryValue(row) }}</template></el-table-column>
            <el-table-column label="æœ¬å¹´" width="82" align="right"><template slot-scope="{ row }">{{ formatSummaryYearValue(row) }}</template></el-table-column>
          </el-table>
        </biz-table-card>
      </section>
    </template>

    <template v-else-if="false && mode === 'overview'">
      <section class="finance-overview-grid">
        <article class="finance-card trend-card">
          <div class="finance-card-title">
            <div>
              <h3>å›æ¬¾è¶‹åŠ¿</h3>
              <p>è¿‘ 6 ä¸ªæœˆç¡®è®¤å›æ¬¾èµ°åŠ¿</p>
            </div>
            <strong>{{ formatMoney(totalTrendAmount) }}</strong>
          </div>
          <svg class="trend-chart" viewBox="0 0 520 180" preserveAspectRatio="none">
            <polyline class="trend-grid" points="0,150 520,150" />
            <polyline class="trend-grid" points="0,105 520,105" />
            <polyline class="trend-grid" points="0,60 520,60" />
            <polyline class="trend-line" :points="trendPoints" />
            <circle v-for="(point, index) in trendPointList" :key="index" :cx="point.x" :cy="point.y" r="4" />
          </svg>
          <div class="trend-labels">
            <span v-for="item in trend" :key="item.itemName">{{ item.itemName }}</span>
          </div>
        </article>

        <article class="finance-card aging-card">
          <div class="finance-card-title">
            <div>
              <h3>è´¦é¾„åˆ†å¸ƒ</h3>
              <p>æœªæ”¶æ¬¾è®¡åˆ’æŒ‰é€¾æœŸå¤©æ•°åˆ†å¸ƒ</p>
            </div>
          </div>
          <div class="donut-wrap">
            <i class="donut" :style="agingDonutStyle"><b>{{ agingTotal }}</b><span>ç¬”åº”æ”¶</span></i>
            <ul>
              <li v-for="item in agingStats" :key="item.itemName">
                <em :style="{ background: item.color }" />
                <span>{{ item.label }}</span>
                <strong>{{ item.itemValue || 0 }}</strong>
              </li>
            </ul>
          </div>
        </article>

        <article class="finance-card link-card">
          <div class="finance-card-title">
            <div>
              <h3>æ¨¡å—è”åŠ¨æ¦‚è§ˆ</h3>
              <p>åˆåŒã€æ¡ˆä»¶ã€è´¹ç”¨çš„è´¢åŠ¡å…³ç³»</p>
            </div>
          </div>
          <div class="link-items">
            <div v-for="item in linkCards" :key="item.key">
              <i :class="item.icon" />
              <span>{{ item.title }}</span>
              <strong>{{ item.value }}</strong>
              <small>{{ item.desc }}</small>
            </div>
          </div>
        </article>

        <article class="finance-card funnel-card">
          <div class="finance-card-title">
            <div>
              <h3>çº¿ç´¢è½¬åŒ–æ¼æ–—</h3>
              <p>ä»çº¿ç´¢é¢„è®¡é‡‘é¢åˆ°å®¢æˆ·ç­¾çº¦é‡‘é¢</p>
            </div>
            <strong>{{ leadConversionRate }}%</strong>
          </div>
          <div class="funnel-list">
            <div v-for="item in leadFunnelCards" :key="item.key">
              <span>{{ item.title }}</span>
              <i><em :style="{ width: item.width }" /></i>
              <strong>{{ item.value }}</strong>
              <small>{{ item.desc }}</small>
            </div>
          </div>
        </article>

        <article class="finance-card reminder-card">
          <div class="finance-card-title">
            <div>
              <h3>è´¢åŠ¡æé†’</h3>
              <p>ä¼˜å…ˆå¤„ç†å½±å“ç°é‡‘æµçš„äº‹é¡¹</p>
            </div>
          </div>
          <div class="reminder-list">
            <button v-for="item in reminderCards" :key="item.key" @click="switchMode(item.mode, item.query)">
              <i :class="item.icon" />
              <span><b>{{ item.title }}</b><small>{{ item.desc }}</small></span>
              <strong>{{ item.value }}</strong>
              <em class="el-icon-arrow-right" />
            </button>
          </div>
        </article>
      </section>

      <section class="finance-tables-grid">
        <biz-table-card :toolbar="false" :pagination="false">
          <template slot="header">
            <div class="section-heading">
              <h3>å¾…ç¡®è®¤å›æ¬¾</h3>
              <el-button :size="controlSize" type="text" @click="switchMode('payment')">æŸ¥çœ‹å…¨éƒ¨</el-button>
            </div>
          </template>
          <el-table :data="pendingPayments" :size="controlSize">
            <el-table-column label="å®¢æˆ·/åˆåŒ" min-width="190">
              <template slot-scope="{ row }">
                <span class="biz-link">{{ row.customerName || '-' }}</span>
                <small class="sub-text">{{ row.contractNo || '-' }}</small>
              </template>
            </el-table-column>
            <el-table-column labe×ÏxîÚ$z{-®éÜj×F†—2æf–ÆTæÖTg&öÕW&Â‡fÇVR¢F†—2æW‡Vç6Tf÷&Òçf÷V6†W%7FGW2ÒfÇVRòwWÆöFVBr¢vÖ—76–ærp¢ÒÀ¢6ä–çfö–6R‡&÷r’°¢&WGW&â&÷ræ6öæf—&Õ7FGW2ÓÓÒsrbb&÷ræ–çfö–6U7FGW2ÓÒsp¢ÒÀ¢6ä6öÆÆV7B‡&÷r’°¢&WGW&â&÷ræ6öæf—&Õ7FGW2ÓÓÒsrÇÂ‡&÷ræ6öæf—&Õ7FGW2ÓÓÒsrbbçVÖ&W"‡&÷rçVæF–ætÖ÷VçBÇÂ’â¢ÒÀ¢F6†&ö&EfÇVR†¶W’’°¢6öç7BÖÒF†—2æ¶W–VB‡F†—2æF6†&ö&Bæ6&G2ÇÂµÒ¢&WGW&âçVÖ&W"†Ö¶¶W•ÒbbÖ¶¶W•ÒæÖWG&–5fÇVRÇÂ¢ÒÀ¢Æ—7E7VÒ†vWGFW"’°¢&WGW&â‡F†—2æÆ—7BÇÂµÒ’ç&VGV6R‚‡7VÒÂ&÷r’Óâ7VÒ²çVÖ&W"†vWGFW"‡&÷r’ÇÂ’Â¢ÒÀ¢Æ—7D6÷VçB‡&VF–6FR’°¢&WGW&â‡F†—2æÆ—7BÇÂµÒ’æf–ÇFW"‡&÷rÓâ&VF–6FR‡&÷r’’æÆVæwF€¢ÒÀ¢6ö×ÆWFTÖöçF†Ç•&÷w2‡&÷w2ÂFVfVÇG2’°¢6öç7BÖÒ‡&÷w2ÇÂµÒ’ç&VGV6R‚‡F&vWBÂ—FVÒ’Óâ°¢F&vWE¶—FVÒæ—FVÔæÖUÒÒ—FVĞ¢&WGW&âF&vW@¢ÒÂ·Ò¢&WGW&âF†—2æÆ7E6—„ÖöçF‡2‚’æÖ†ÖöçF‚Óâ‡²—FVÔæÖS¢ÖöçF‚ÂââæFVfVÇG2Ââââ†Ö¶ÖöçF…ÒÇÂ·Ò’Ò’¢ÒÀ¢Æ7E6—„ÖöçF‡2‚’°¢6öç7Bæ÷rÒæWrFFR‚¢6öç7BÖöçF‡2ÒµĞ¢f÷"†ÆWB’ÒS²’ãÒ²’ÒÒ’°¢6öç7BFFRÒæWrFFR†æ÷rævWDgVÆÅ–V"‚’Âæ÷rævWDÖöçF‚‚’Ò’Â¢ÖöçF‡2çW6‚†FFRævWDgVÆÅ–V"‚’²rÒr²7G&–ær†FFRævWDÖöçF‚‚’²’çE7F'Bƒ"Âsr’¢Ğ¢&WGW&âÖöçF‡0¢ÒÀ¢'V–ÆD&'2‡&÷w2ÂfÇVT¶W’’°¢6öç7BÆ—7BÒ&÷w2bb&÷w2æÆVæwF‚ò&÷w2¢µĞ¢6öç7BÖ‚ÒÖF‚æÖ‚‚ââæÆ—7BæÖ†—FVÒÓâçVÖ&W"†—FVÕ·fÇVT¶W•ÒÇÂ’’Â¢6öç7BvÒÆ—7BæÆVæwF‚òS#òÆ—7BæÆVæwF‚¢S# ¢6öç7Bv–GF‚ÒÖF‚æÖ‚ƒ‚ÂÖF‚æÖ–âƒC"Âv¢ã3B’¢&WGW&âÆ—7BæÖ‚†—FVÒÂ–æFW‚’Óâ°¢6öç7BfÇVRÒçVÖ&W"†—FVÕ·fÇVT¶W•ÒÇÂ¢6öç7B†V–v‡BÒÖF‚æÖ‚ƒbÂfÇVRòÖ‚¢3¢&WGW&â²¶W“¢—FVÒæ—FVÔæÖR²rÒr²–æFW‚Âƒ¢ÖF‚ç&÷VæB†–æFW‚¢v²vò"Òv–GF‚ò"’Â“¢ÖF‚ç&÷VæBƒsRÒ†V–v‡B’Âv–GF‚Â†V–v‡BÂF—FÆS¢G¶—FVÒæ—FVÔæÖWŞûÉ¢G·F†—2æf÷&ÖDÖöæW’‡fÇVR—ÖĞ¢Ò¢ÒÀ¢'V–ÆDÆ–æUö–çG2‡&÷w2ÂfÇVT¶W’’°¢6öç7BÆ—7BÒ&÷w2bb&÷w2æÆVæwF‚ò&÷w2¢µĞ¢6öç7BÖ‚ÒÖF‚æÖ‚‚ââæÆ—7BæÖ†—FVÒÓâçVÖ&W"†—FVÕ·fÇVT¶W•ÒÇÂ’’Â¢6öç7BvÒÆ—7BæÆVæwF‚ÃÒòS#¢S#ò†Æ—7BæÆVæwF‚Ò¢&WGW&âÆ—7BæÖ‚†—FVÒÂ–æFW‚’Óâ°¢6öç7BfÇVRÒçVÖ&W"†—FVÕ·fÇVT¶W•ÒÇÂ¢&WGW&â²¶W“¢—FVÒæ—FVÔæÖR²rÒr²–æFW‚Âƒ¢ÖF‚ç&÷VæB†–æFW‚¢v’Â“¢ÖF‚ç&÷VæBƒsRÒfÇVRòÖ‚¢3’ÂF—FÆS¢G¶—FVÒæ—FVÔæÖWŞûÉ¢G·fÇVRçFôÆö6ÆU7G&–ær‚—ÖĞ¢Ò¢ÒÀ¢'V–ÆEG&VæEö–çG2‡&÷w2Âv–GF‚’°¢6öç7BÆ—7BÒ&÷w2bb&÷w2æÆVæwF‚ò&÷w2¢·²—FVÔæÖS¢rÒrÂ—FVÕfÇVS¢ÕĞ¢6öç7BÖ‚ÒÖF‚æÖ‚‚ââæÆ—7BæÖ†—FVÒÓâçVÖ&W"†—FVÒæ—FVÕfÇVRÇÂ’’Â¢6öç7BvÒÆ—7BæÆVæwF‚ÓÓÒòv–GF‚¢v–GF‚ò†Æ—7BæÆVæwF‚Ò¢&WGW&âÆ—7BæÖ‚†—FVÒÂ–æFW‚’Óâ‡²ƒ¢ÖF‚ç&÷VæB†–æFW‚¢v’Â“¢ÖF‚ç&÷VæBƒSÒçVÖ&W"†—FVÒæ—FVÕfÇVRÇÂ’òÖ‚¢’Ò’¢ÒÀ¢&%v–GF‚‡fÇVRÂ&÷w2’°¢6öç7BÖ‚ÒÖF‚æÖ‚‚âââ‡&÷w2ÇÂµÒ’æÖ†—FVÒÓâçVÖ&W"†—FVÒæ—FVÕfÇVRÇÂ’’Â¢&WGW&âÖF‚æÖ‚ƒ‚ÂçVÖ&W"‡fÇVRÇÂ’òÖ‚¢’²rRp¢ÒÀ¢6öçfW'6–öä&%v–GF‚†—FVÒ’°¢&WGW&âÖF‚æÖ‚ƒ‚ÂçVÖ&W"†—FVÒæ6öçfW'6–öå&FRÇÂ’’²rRp¢ÒÀ¢f–VÆB‡&÷rÂ6ÖVÄ¶W’Â6æ¶T¶W’’°¢–b‚&÷r’&WGW&âVæFVf–æV@¢&WGW&â&÷u¶6ÖVÄ¶W•ÒÓÒVæFVf–æVBbb&÷u¶6ÖVÄ¶W•ÒÓÒçVÆÂò&÷u¶6ÖVÄ¶W•Ò¢&÷u·6æ¶T¶W•Ğ¢ÒÀ¢çVÒ†—FVÒ’°¢&WGW&â—FVÒòçVÖ&W"†—FVÒæÖWG&–5fÇVRÇÂ’¢ ¢ÒÀ¢¶W–VB‡&÷w2ÂfÇVT¶W’’°¢&WGW&â‡&÷w2ÇÂµÒ’ç&VGV6R‚‡F&vWBÂ—FVÒ’Óâ°¢F&vWE¶—FVÒæÖWG&–4¶W•ÒÒfÇVT¶W’ò—FVÕ·fÇVT¶W•Ò¢—FVĞ¢&WGW&âF&vW@¢ÒÂ·Ò¢ÒÀ¢f÷&ÖEÆ–äÖöæW’‡fÇVR’°¢&WGW&âçVÖ&W"‡fÇVRÇÂ’çFôÆö6ÆU7G&–ær‚¢ÒÀ¢f÷&ÖE6†÷'DÖöæW’‡fÇVR’°¢6öç7BÖ÷VçBÒçVÖ&W"‡fÇVRÇÂ¢–b„ÖF‚æ'2†Ö÷VçB’ãÒ’&WGW&â†Ö÷VçBò’çFôÆö6ÆU7G&–ær‡VæFVf–æVBÂ²Ö†–×VÔg&7F–öäF–v—G3¢Ò’²~Kˆrp¢&WGW&âÖ÷VçBçFôÆö6ÆU7G&–ær‚¢ÒÀ¢f÷&ÖD6ö×7DÖöæW’‡fÇVR’°¢6öç7BÖ÷VçBÒçVÖ&W"‡fÇVRÇÂ¢–b„ÖF‚æ'2†Ö÷VçB’ãÒ’&WGW&â†Ö÷VçBò’çFôÆö6ÆU7G&–ær‡VæFVf–æVBÂ²Ö†–×VÔg&7F–öäF–v—G3¢Ò’²~Kˆrp¢&WGW&âÖ÷VçBçFôÆö6ÆU7G&–ær‚¢ÒÀ¢6†÷'DFFR‡fÇVR’°¢&WGW&âfÇVRò7G&–ær‡fÇVR’ç6Æ–6RƒRÂ’¢rÒp¢ÒÀ¢f÷&ÖE7VÖÖ'•fÇVR‡&÷r’°¢&WGW&â&÷ræÖWG&–4¶W’ÓÓÒw&V6V—fT6÷VçBròçVÖ&W"‡&÷ræÖöçF…fÇVRÇÂ’çFôÆö6ÆU7G&–ær‚’¢F†—2æf÷&ÖD6ö×7DÖöæW’‡&÷ræÖöçF…fÇVR¢ÒÀ¢f÷&ÖE7VÖÖ'•–V%fÇVR‡&÷r’°¢&WGW&â&÷ræÖWG&–4¶W’ÓÓÒw&V6V—fT6÷VçBròçVÖ&W"‡&÷rç–V%fÇVRÇÂ’çFôÆö6ÆU7G&–ær‚’¢F†—2æf÷&ÖD6ö×7DÖöæW’‡&÷rç–V%fÇVR¢ÒÀ¢f÷&ÖDÖöæW’‡fÇVR’°¢–b‡fÇVRÓÒçVÆÂÇÂfÇVRÓÓÒrr’&WGW&â|*Rp¢&WGW&â|*Rr²çVÖ&W"‡fÇVRÇÂ’çFôÆö6ÆU7G&–ær‚¢Ğ¢Ğ§Ğ£Â÷67&—Cà £Ç7G–ÆR66÷VBÆæsÒ'6772#à¢æf–ææ6R×vR°¢ÒÖ&—¢Öf–ÇFW"Ö–çWB×v–GFƒ¢##ƒ°¢ÒÖ&—¢Öf–ÇFW"×6VÆV7B×v–GFƒ¢3'ƒ° ¢£§bÖFVWæ&—¢Öf–ÇFW"ÖÖ–âæVÂÖFFRÖVF—F÷"ÒÖFFW&ævR°¢v–GFƒ¢#Sƒ°¢fÆWƒ¢#Sƒ°¢Ğ§Ğ ¢æf–ææ6RÖ÷fW'f–WrÖw&–B°¢F—7Æ“¢w&–C°¢w&–B×FV×ÆFRÖ6öÇVÖç3¢Ö–æÖ‚ƒÂãCVg"’Ö–æÖ‚ƒ#ƒ‚ÂãsVg"’Ö–æÖ‚ƒ#ƒ‚ÂãsVg"“°¢v¢gƒ°¢Ö&v–â×F÷¢gƒ°§Ğ ¢æf–ææ6RÖ6&B°¢&÷&FW#¢‚6öÆ–B6S†VFcc°¢&÷&FW"×&F—W3¢'ƒ°¢&6¶w&÷VæC¢6ffc°¢&÷‚×6†F÷s¢w‚g‚&v&ƒ3bÂs2Â3RÂãCR“°¢FF–æs¢gƒ°§Ğ ¢çG&VæBÖ6&B°¢w&–B×&÷s¢7â#°§Ğ ¢æf–ææ6RÖ6&B×F—FÆRÀ¢ç6V7F–öâÖ†VF–ær°¢F—7Æ“¢fÆWƒ°¢Æ–vâÖ—FV×3¢fÆW‚×7F'C°¢§W7F–g’Ö6öçFVçC¢76RÖ&WGvVVã°¢Ö&v–âÖ&÷GFöÓ¢'ƒ° ¢ƒ2°¢Ö&v–ã¢°¢6öÆ÷#¢3cs&°¢föçB×6—¦S¢f"‚ÒÖ&—¢ÖföçBÖ6&B“°¢Ğ ¢°¢Ö&v–ã¢G‚°¢6öÆ÷#¢3“F6#ƒ°¢föçB×6—¦S¢f"‚ÒÖ&—¢ÖföçBÖÖ–æ’“°¢Ğ ¢7G&öær°¢6öÆ÷#¢3#Sc6V#°¢föçB×6—¦S¢f"‚ÒÖ&—¢ÖföçB×6V7F–öâ“°¢Ğ§Ğ ¢çG&VæBÖ6†'B°¢v–GFƒ¢S°¢†V–v‡C¢ƒƒ° ¢çG&VæBÖw&–B°¢f–ÆÃ¢æöæS°¢7G&ö¶S¢6VVc&cs°¢7G&ö¶R×v–GFƒ¢°¢Ğ ¢çG&VæBÖÆ–æR°¢f–ÆÃ¢æöæS°¢7G&ö¶S¢3#Sc6V#°¢7G&ö¶R×v–GFƒ¢C°¢7G&ö¶RÖÆ–æV6¢&÷VæC°¢7G&ö¶RÖÆ–æV¦ö–ã¢&÷VæC°¢Ğ ¢6—&6ÆR°¢f–ÆÃ¢6ffc°¢7G&ö¶S¢3#Sc6V#°¢7G&ö¶R×v–GFƒ¢3°¢Ğ§Ğ ¢çG&VæBÖÆ&VÇ2°¢F—7Æ“¢fÆWƒ°¢§W7F–g’Ö6öçFVçC¢76RÖ&WGvVVã°¢6öÆ÷#¢3“F6#ƒ°¢föçB×6—¦S¢f"‚ÒÖ&—¢ÖföçBÖÖ–æ’“°§Ğ ¢æFöçWB×w&°¢F—7Æ“¢fÆWƒ°¢Æ–vâÖ—FV×3¢6VçFW#°¢v¢'ƒ°§Ğ ¢æFöçWB°¢÷6—F–öã¢&VÆF—fS°¢F—7Æ“¢fÆWƒ°¢fÆW‚ÖF—&V7F–öã¢6öÇVÖã°¢Æ–vâÖ—FV×3¢6VçFW#°¢§W7F–g’Ö6öçFVçC¢6VçFW#°¢v–GFƒ¢3‡ƒ°¢†V–v‡C¢3‡ƒ°¢&÷&FW"×&F—W3¢SS°¢fÆWƒ¢3‡ƒ° ¢c£¦gFW"°¢6öçFVçC¢rs°¢÷6—F–öã¢'6öÇWFS°¢–ç6WC¢#gƒ°¢&÷&FW"×&F—W3¢SS°¢&6¶w&÷VæC¢6ffc°¢Ğ ¢"À¢7â°¢÷6—F–öã¢&VÆF—fS°¢¢Ö–æFWƒ¢°¢Ğ ¢"°¢6öÆ÷#¢3cs&°¢föçB×6—¦S¢f"‚ÒÖ&—¢ÖföçBÖÖWG&–2“°¢Ğ ¢7â°¢6öÆ÷#¢3cCsC†#°¢föçB×6—¦S¢f"‚ÒÖ&—¢ÖföçBÖÖ–æ’“°¢Ğ§Ğ ¢æFöçWB×w&VÂ°¢fÆWƒ¢°¢FF–æs¢°¢Ö&v–ã¢°¢Æ—7B×7G–ÆS¢æöæS° ¢Æ’°¢F—7Æ“¢w&–C°¢w&–B×FV×ÆFRÖ6öÇVÖç3¢‚g"WFó°¢Æ–vâÖ—FV×3¢6VçFW#°¢v¢‡ƒ°¢Ö&v–ã¢‡‚°¢6öÆ÷#¢3cCsC†#°¢föçB×6—¦S¢f"‚ÒÖ&—¢ÖföçB×6ÖÆÂ“°¢Ğ ¢VÒ°¢v–GFƒ¢‡ƒ°¢†V–v‡C¢‡ƒ°¢&÷&FW"×&F—W3¢““—ƒ°¢Ğ ¢7G&öær°¢6öÆ÷#¢3cs&°¢Ğ§Ğ ¢æÆ–æ²Ö—FV×2°¢F—7Æ“¢w&–C°¢v¢ƒ° ¢F—b°¢F—7Æ“¢w&–C°¢w&–B×FV×ÆFRÖ6öÇVÖç3¢3g‚g"WFó°¢Æ–vâÖ—FV×3¢6VçFW#°¢v¢ƒ°¢FF–æs¢ƒ°¢&÷&FW#¢‚6öÆ–B6VFc&cs°¢&÷&FW"×&F—W3¢ƒ°¢&6¶w&÷VæC¢6c†f&fc°¢Ğ ¢’°¢F—7Æ“¢fÆWƒ°¢Æ–vâÖ—FV×3¢6VçFW#°¢§W7F–g’Ö6öçFVçC¢6VçFW#°¢v–GFƒ¢3gƒ°¢†V–v‡C¢3gƒ°¢&÷&FW"×&F—W3¢SS°¢6öÆ÷#¢3#Sc6V#°¢&6¶w&÷VæC¢6Vc&fc°¢Ğ ¢7â°¢6öÆ÷#¢333CSS°¢föçB×6—¦S¢f"‚ÒÖ&—¢ÖföçB×6ÖÆÂ“°¢Ğ ¢7G&öær°¢6öÆ÷#¢3cs&°¢föçB×6—¦S¢f"‚ÒÖ&—¢ÖföçB×6V7F–öâ“°¢Ğ ¢6ÖÆÂ°¢w&–BÖ6öÇVÖã¢"òC°¢6öÆ÷#¢3“F6#ƒ°¢Ğ§Ğ ¢ægVææVÂÖÆ—7B°¢F—7Æ“¢w&–C°¢v¢ƒ° ¢F—b°¢F—7Æ“¢w&–C°¢w&–B×FV×ÆFRÖ6öÇVÖç3¢ƒg‚g"WFó°¢Æ–vâÖ—FV×3¢6VçFW#°¢v¢ƒ°¢Ğ ¢7â°¢6öÆ÷#¢333CSS°¢föçB×6—¦S¢f"‚ÒÖ&—¢ÖföçB×6ÖÆÂ“°¢Ğ ¢’°¢†V–v‡C¢‡ƒ°¢&÷&FW"×&F—W3¢““—ƒ°¢&6¶w&÷VæC¢6VVc&fc°¢÷fW&fÆ÷s¢†–FFVã°¢Ğ ¢VÒ°¢F—7Æ“¢&Æö6³°¢†V–v‡C¢S°¢&÷&FW"×&F—W3¢–æ†W&—C°¢&6¶w&÷VæC¢Æ–æV"Öw&F–VçBƒ“FVrÂ3#Sc6V"Â33†&Fc‚“°¢Ğ ¢7G&öær°¢6öÆ÷#¢3cs&°¢föçB×6—¦S¢f"‚ÒÖ&—¢ÖföçBÖ6&B“°¢Ğ ¢6ÖÆÂ°¢w&–BÖ6öÇVÖã¢"òC°¢6öÆ÷#¢3“F6#ƒ°¢föçB×6—¦S¢f"‚ÒÖ&—¢ÖföçBÖÖ–æ’“°¢Ğ§Ğ ¢ç&VÖ–æFW"ÖÆ—7B°¢F—7Æ“¢w&–C°¢v¢ƒ° ¢'WGFöâ°¢F—7Æ“¢w&–C°¢w&–B×FV×ÆFRÖ6öÇVÖç3¢C‚g"WFòGƒ°¢Æ–vâÖ—FV×3¢6VçFW#°¢v¢ƒ°¢v–GFƒ¢S°¢FF–æs¢'ƒ°¢&÷&FW#¢‚6öÆ–B6VFc&cs°¢&÷&FW"×&F—W3¢ƒ°¢&6¶w&÷VæC¢6ffc°¢FW‡BÖÆ–vã¢ÆVgC°¢7W'6÷#¢ö–çFW#°¢Ğ ¢“¦f—'7BÖ6†–ÆB°¢F—7Æ“¢fÆWƒ°¢Æ–vâÖ—FV×3¢6VçFW#°¢§W7F–g’Ö6öçFVçC¢6VçFW#°¢v–GFƒ¢Cƒ°¢†V–v‡C¢Cƒ°¢&÷&FW"×&F—W3¢SS°¢6öÆ÷#¢3#Sc6V#°¢&6¶w&÷VæC¢6VVcFfc°¢föçB×6—¦S¢‡ƒ°¢Ğ ¢"À¢6ÖÆÂ°¢F—7Æ“¢&Æö6³°¢Ğ ¢"°¢6öÆ÷#¢3cs&°¢föçB×6—¦S¢f"‚ÒÖ&—¢ÖföçB×6ÖÆÂ“°¢Ğ ¢6ÖÆÂ°¢Ö&v–â×F÷¢7ƒ°¢6öÆ÷#¢3“F6#ƒ°¢Ğ ¢7G&öær°¢6öÆ÷#¢6VcCCCC°¢föçB×6—¦S¢f"‚ÒÖ&—¢ÖföçB×6V7F–öâ“°¢Ğ§Ğ ¢æf–ææ6R×F&ÆW2Öw&–BÀ¢ç&W÷'BÖw&–B°¢F—7Æ“¢w&–C°¢w&–B×FV×ÆFRÖ6öÇVÖç3¢&WVBƒ"ÂÖ–æÖ‚ƒÂg"’“°¢v¢gƒ°¢Ö&v–â×F÷¢gƒ°§Ğ ¢æf–ææ6RÖfÆ÷rÖ6&B°¢Ö&v–â×F÷¢gƒ°§Ğ ¢æf–ææ6RÖfÆ÷r°¢F—7Æ“¢w&–C°¢w&–B×FV×ÆFRÖ6öÇVÖç3¢&WVBƒbÂÖ–æÖ‚ƒÂg"’“°¢v¢'ƒ°§Ğ ¢æf–ææ6RÖfÆ÷rÖ—FVÒ°¢÷6—F–öã¢&VÆF—fS°¢F—7Æ“¢w&–C°¢w&–B×FV×ÆFRÖ6öÇVÖç3¢C'‚g#°¢Æ–vâÖ—FV×3¢6VçFW#°¢v¢ƒ°¢Ö–âÖ†V–v‡C¢sƒ°¢FF–æs¢‚‡ƒ°¢&÷&FW"×&F—W3¢'ƒ°¢&6¶w&÷VæC¢Æ–æV"Öw&F–VçBƒƒFVrÂ6c†f&fbRÂ6fffffbR“° ¢âF—b°¢Ö–â×v–GFƒ¢°¢Ğ ¢"À¢7G&öær°¢F—7Æ“¢&Æö6³°¢Ğ ¢"°¢6öÆ÷#¢3cCsC†#°¢föçB×6—¦S¢f"‚ÒÖ&—¢ÖföçB×6ÖÆÂ“°¢Ğ ¢7G&öær°¢Ö&v–â×F÷¢Gƒ°¢6öÆ÷#¢3cs&°¢föçB×6—¦S¢f"‚ÒÖ&—¢ÖföçBÖ6&B“°¢v†—FR×76S¢æ÷w&°¢Ğ ¢âVÒ°¢÷6—F–öã¢'6öÇWFS°¢&–v‡C¢Ógƒ°¢F÷¢SS°¢¢Ö–æFWƒ¢°¢Ö–â×v–GFƒ¢3‡ƒ°¢G&ç6f÷&Ó¢G&ç6ÆFU’‚ÓSR“°¢6öÆ÷#¢3cCsC†#°¢föçB×6—¦S¢f"‚ÒÖ&—¢ÖföçBÖÖ–æ’“°¢föçB×7G–ÆS¢æ÷&ÖÃ°¢FW‡BÖÆ–vã¢6VçFW#° ¢c£¦gFW"°¢6öçFVçC¢rs°¢F—7Æ“¢&Æö6³°¢v–GFƒ¢#Gƒ°¢†V–v‡C¢ƒ°¢Ö&v–ã¢7‚WFò°¢&6¶w&÷VæC¢66&CVS°¢Ğ¢Ğ§Ğ ¢æfÆ÷rÖ–6öâ°¢F—7Æ“¢fÆWƒ°¢Æ–vâÖ—FV×3¢6VçFW#°¢§W7F–g’Ö6öçFVçC¢6VçFW#°¢v–GFƒ¢C'ƒ°¢†V–v‡C¢C'ƒ°¢&÷&FW"×&F—W3¢SS°¢6öÆ÷#¢3#Sc6V#°¢&6¶w&÷VæC¢6Vc&fc°¢föçB×6—¦S¢‡ƒ° ¢bæw&VVâ²6öÆ÷#¢3f3F²&6¶w&÷VæC¢6Vfc²Ğ¢bçW'ÆR²6öÆ÷#¢3v36VC²&6¶w&÷VæC¢6c6S†fc²Ğ¢bæ÷&ævR²6öÆ÷#¢6c“s3c²&6¶w&÷VæC¢6ffc6Ss²Ğ¢bæ7–â²6öÆ÷#¢3ƒ“##²&6¶w&÷VæC¢6Sff&fc²Ğ§Ğ ¢æf–ææ6RÖ6†'G2Öw&–B°¢F—7Æ“¢w&–C°¢w&–B×FV×ÆFRÖ6öÇVÖç3¢&WVBƒ2ÂÖ–æÖ‚ƒÂg"’“°¢v¢gƒ°¢Ö&v–â×F÷¢gƒ°§Ğ ¢æf–ææ6RÖ6†'BÖ6&B°¢Ö–âÖ†V–v‡C¢#sƒ°§Ğ ¢æ6öÖ&òÖ6†'B°¢7fr°¢v–GFƒ¢S°¢†V–v‡C¢#ƒ°¢Ğ ¢çG&VæBÖÆ–æR°¢f–ÆÃ¢æöæS°¢7G&ö¶R×v–GFƒ¢3°¢7G&ö¶RÖÆ–æV6¢&÷VæC°¢7G&ö¶RÖÆ–æV¦ö–ã¢&÷VæC°¢Ğ ¢&V7B°¢f–ÆÃ¢W&Â‚6f–ææ6T&$w&F–VçB“°¢f–ÆÃ¢3#Sc6V#°¢Ğ ¢6—&6ÆR°¢f–ÆÃ¢6ffc°¢7G&ö¶S¢3#&3SVS°¢7G&ö¶R×v–GFƒ¢3°¢Ğ ¢æ6†'BÖ†÷fW"Ö&"À¢æ6†'BÖ†÷fW"×ö–çB°¢7W'6÷#¢ö–çFW#°¢G&ç6—F–öã¢÷6—G’ã‡2V6RÂf–ÇFW"ã‡2V6S° ¢c¦†÷fW"°¢÷6—G“¢ãƒ#°¢f–ÇFW#¢G&÷×6†F÷rƒG‚w‚&v&ƒ3rÂ“’Â#3RÂã#"’“°¢Ğ¢Ğ§Ğ ¢çG&VæBÖÆ–æRæw&VVâ°¢7G&ö¶S¢3#&3SVS°§Ğ ¢æ6†'BÖÆ&VÇ2À¢æ6†'BÖÆVvVæB°¢F—7Æ“¢fÆWƒ°¢Æ–vâÖ—FV×3¢6VçFW#°¢§W7F–g’Ö6öçFVçC¢76RÖ&WGvVVã°¢v¢‡ƒ°¢6öÆ÷#¢3cCsC†#°¢föçB×6—¦S¢f"‚ÒÖ&—¢ÖföçBÖÖ–æ’“°§Ğ ¢æ6†'BÖÆVvVæB°¢§W7F–g’Ö6öçFVçC¢fÆW‚×7F'C°¢Ö&v–â×F÷¢‡ƒ° ¢7â°¢F—7Æ“¢–æÆ–æRÖfÆWƒ°¢Æ–vâÖ—FV×3¢6VçFW#°¢v¢gƒ°¢Ğ ¢’°¢v–GFƒ¢‡ƒ°¢†V–v‡C¢‡ƒ°¢&÷&FW"×&F—W3¢SS° ¢bæ&ÇVR²&6¶w&÷VæC¢3#Sc6V#²Ğ¢bæw&VVâ²&6¶w&÷VæC¢3#&3SVS²Ğ¢Ğ§Ğ ¢æf–ææ6RÖ÷fW'f–Wr×F&ÆW2°¢F—7Æ“¢w&–C°¢w&–B×FV×ÆFRÖ6öÇVÖç3¢&WVBƒBÂÖ–æÖ‚ƒÂg"’“°¢v¢'ƒ°¢Ö&v–â×F÷¢gƒ° ¢£§bÖFVWæ&—¢×F&ÆRÖ6&B°¢Ö–â×v–GFƒ¢°¢Ğ§Ğ ¢æ÷fW'f–WrÖÖ–æ’×F&ÆR°¢£§bÖFVWæVÂ×F&ÆUõö6VÆÂ°¢FF–æs¢g‚°¢föçB×6—¦S¢f"‚ÒÖ&—¢ÖföçBÖÖ–æ’“°¢Ğ ¢£§bÖFVWF‚æVÂ×F&ÆUõö6VÆÂ°¢&6¶w&÷VæC¢6c†ff3°¢Ğ ¢£§bÖFVWæ6VÆÂ°¢FF–ærÖÆVgC¢gƒ°¢FF–ær×&–v‡C¢gƒ°¢Ğ ¢£§bÖFVWæVÂ×F&ÆUõö&öG’×w&W"°¢÷fW&fÆ÷r×ƒ¢†–FFVã°¢Ğ§Ğ ¢ç&W÷'BÖÖ–â°¢w&–BÖ6öÇVÖã¢òÓ°§Ğ ¢æf–ææ6R×&W÷'B×FööÆ&"°¢F—7Æ“¢fÆWƒ°¢Æ–vâÖ—FV×3¢6VçFW#°¢§W7F–g’Ö6öçFVçC¢76RÖ&WGvVVã°¢v¢gƒ°¢Ö&v–â×F÷¢gƒ° ¢ƒ2°¢Ö&v–ã¢gƒ°¢6öÆ÷#¢3c#Cs°¢föçB×6—¦S¢f"‚ÒÖ&—¢ÖföçBÖÆr“°¢Ğ ¢°¢Ö&v–ã¢°¢6öÆ÷#¢3cCsC†#°¢föçB×6—¦S¢f"‚ÒÖ&—¢ÖföçB×6Ò“°¢Ğ§Ğ ¢ç&W÷'B×FööÆ&"Ö7F–öç2°¢F—7Æ“¢fÆWƒ°¢Æ–vâÖ—FV×3¢6VçFW#°¢v¢ƒ°¢fÆW‚×w&¢w&°¢§W7F–g’Ö6öçFVçC¢fÆW‚ÖVæC°§Ğ ¢ç&W÷'B×7FB°¢F—7Æ“¢w&–C°¢v¢‡ƒ° ¢7â°¢6öÆ÷#¢3cCsC†#°¢föçB×6—¦S¢f"‚ÒÖ&—¢ÖföçB×6ÖÆÂ“°¢Ğ ¢7G&öær°¢6öÆ÷#¢3cs&°¢föçB×6—¦S¢f"‚ÒÖ&—¢ÖföçBÖÖWG&–2“°¢Ğ ¢6ÖÆÂ°¢6öÆ÷#¢3“F6#ƒ°¢Ğ§Ğ ¢æ&"ÖÆ—7B°¢F—7Æ“¢w&–C°¢v¢ƒ° ¢F—b°¢F—7Æ“¢w&–C°¢w&–B×FV×ÆFRÖ6öÇVÖç3¢“g‚g"#ƒ°¢Æ–vâÖ—FV×3¢6VçFW#°¢v¢ƒ°¢föçB×6—¦S¢f"‚ÒÖ&—¢ÖföçB×6ÖÆÂ“°¢Ğ ¢’°¢†V–v‡C¢‡ƒ°¢&÷&FW"×&F—W3¢““—ƒ°¢&6¶w&÷VæC¢6VFc&cs°¢÷fW&fÆ÷s¢†–FFVã°¢Ğ ¢VÒ°¢F—7Æ“¢&Æö6³°¢†V–v‡C¢S°¢&÷&FW"×&F—W3¢–æ†W&—C°¢&6¶w&÷VæC¢Æ–æV"Öw&F–VçBƒ“FVrÂ3#Sc6V"Â3f#fCB“°¢Ğ ¢7G&öær°¢FW‡BÖÆ–vã¢&–v‡C°¢6öÆ÷#¢3cs&°¢Ğ§Ğ ¢ç6÷W&6RÖ6öçfW'6–öâÖÆ—7B°¢F—7Æ“¢w&–C°¢v¢'ƒ° ¢âF—b°¢F—7Æ“¢w&–C°¢w&–B×FV×ÆFRÖ6öÇVÖç3¢S‚Ö–æÖ‚ƒc‚Âg"’s'‚ƒƒ°¢Æ–vâÖ—FV×3¢6VçFW#°¢v¢'ƒ°¢Ğ ¢7â°¢6öÆ÷#¢333CSS°¢föçB×6—¦S¢f"‚ÒÖ&—¢ÖföçB×6ÖÆÂ“°¢÷fW&fÆ÷s¢†–FFVã°¢FW‡BÖ÷fW&fÆ÷s¢VÆÆ—6—3°¢v†—FR×76S¢æ÷w&°¢Ğ ¢’°¢†V–v‡C¢—ƒ°¢&÷&FW"×&F—W3¢““—ƒ°¢&6¶w&÷VæC¢6VVc&fc°¢÷fW&fÆ÷s¢†–FFVã°¢Ğ ¢VÒ°¢F—7Æ“¢&Æö6³°¢†V–v‡C¢S°¢&÷&FW"×&F—W3¢–æ†W&—C°¢&6¶w&÷VæC¢Æ–æV"Öw&F–VçBƒ“FVrÂ3v36VBÂ3#&C6VR“°¢Ğ ¢7G&öær°¢6öÆ÷#¢3#Sc6V#°¢föçB×6—¦S¢f"‚ÒÖ&—¢ÖföçBÖ6&B“°¢Ğ ¢6ÖÆÂ°¢6öÆ÷#¢3cCsC†#°¢föçB×6—¦S¢f"‚ÒÖ&—¢ÖföçBÖÖ–æ’“°¢FW‡BÖÆ–vã¢&–v‡C°¢Ğ§Ğ ¢æ÷fW&GVR°¢6öÆ÷#¢6VcCCCC°¢föçB×vV–v‡C¢s°§Ğ ¤ÖVF–†Ö‚×v–GFƒ¢#ƒ‚’°¢æf–ææ6RÖ÷fW'f–WrÖw&–BÀ¢æf–ææ6RÖ6†'G2Öw&–BÀ¢æf–ææ6RÖ÷fW'f–Wr×F&ÆW2À¢æf–ææ6R×F&ÆW2Öw&–BÀ¢ç&W÷'BÖw&–B°¢w&–B×FV×ÆFRÖ6öÇVÖç3¢g#°¢Ğ ¢æf–ææ6RÖfÆ÷r°¢w&–B×FV×ÆFRÖ6öÇVÖç3¢&WVBƒ"ÂÖ–æÖ‚ƒÂg"’“°¢Ğ ¢æf–ææ6RÖfÆ÷rÖ—FVÒâVÒ°¢F—7Æ“¢æöæS°¢Ğ ¢æf–ææ6R×&W÷'B×FööÆ&"°¢Æ–vâÖ—FV×3¢fÆW‚×7F'C°¢fÆW‚ÖF—&V7F–öã¢6öÇVÖã°¢Ğ §Ğ£Â÷7G–ÆSà