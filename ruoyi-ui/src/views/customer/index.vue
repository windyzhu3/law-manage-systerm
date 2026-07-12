<template>
  <div class="biz-page customer-page" :class="'biz-size-' + appSize">
    <biz-page-header eyebrow="CUSTOMER CENTER" :title="pageTitle" :description="pageDescription">
      <el-button v-if="mode === 'list'" v-hasPermi="['customer:import']" :size="controlSize" plain icon="el-icon-upload2" @click="openImport">å¯¼å…¥</el-button>
      <el-button v-if="mode === 'list'" v-hasPermi="['customer:export']" :size="controlSize" plain icon="el-icon-download" @click="handleExport">å¯¼å‡º</el-button>
      <el-button v-if="mode === 'list'" v-hasPermi="['customer:add']" :size="controlSize" type="primary" icon="el-icon-plus" @click="openCustomer()">æ–°å¢žå®¢æˆ·</el-button>
      <el-button v-if="mode === 'contact'" v-hasPermi="['customer:contact:add']" :size="controlSize" type="primary" icon="el-icon-plus" @click="openContact()">æ–°å¢žè”ç³»äºº</el-button>
      <el-button v-if="mode === 'followup'" v-hasPermi="['customer:followup:add']" :size="controlSize" type="primary" icon="el-icon-plus" @click="openFollowup()">æ–°å¢žè·Ÿè¿›</el-button>
      <el-button v-if="mode === 'tag'" v-hasPermi="['customer:tag:add']" :size="controlSize" type="primary" icon="el-icon-plus" @click="openTag()">æ–°å¢žæ ‡ç­¾</el-button>
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
          <el-input v-model="query.customerName" :size="controlSize" prefix-icon="el-icon-search" placeholder="æœç´¢å®¢æˆ·åç§°ã€å…¬å¸åç§°ã€æ‰‹æœºå·" clearable @clear="search" @keyup.enter.native="search" />
          <el-select v-model="query.customerType" :size="controlSize" placeholder="å®¢æˆ·ç±»åž‹ï¼šå…¨éƒ¨" clearable @change="search">
            <el-option v-for="item in dict.type.law_customer_type" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="query.industry" :size="controlSize" placeholder="æ‰€å±žè¡Œä¸šï¼šå…¨éƒ¨" clearable @change="search">
            <el-option v-for="item in dict.type.law_customer_industry" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="query.tagId" :size="controlSize" placeholder="æ ‡ç­¾ï¼šå…¨éƒ¨" clearable @change="search">
            <el-option v-for="item in tagOptions" :key="tagValue(item)" :label="tagLabel(item)" :value="tagValue(item)" />
          </el-select>
        </div>
        <div class="biz-filter-actions">
          <el-popover v-model="advancedOpen" placement="bottom-end" width="360" trigger="click" popper-class="business-advanced-popover">
            <div class="advanced-filter-panel">
              <div class="advanced-title">
                <strong>é«˜çº§ç­›é€‰</strong>
                <span>ç»„åˆå®¢æˆ·ç­‰çº§ä¸Žè´Ÿè´£äººå¿«é€Ÿå®šä½</span>
              </div>
              <el-form label-position="top">
                <el-form-item label="å®¢æˆ·ç­‰çº§">
                  <el-select v-model="query.customerLevel" :size="controlSize" placeholder="å…¨éƒ¨ç­‰çº§" clearable>
                    <el-option v-for="item in dict.type.law_customer_level" :key="item.value" :label="item.label" :value="item.value" />
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
      <el-table v-loading="loading" :data="list" :size="controlSize">
          <el-table-column type="selection" width="42" align="center" />
          <el-table-column label="å®¢æˆ·ç¼–å·" prop="customerNo" min-width="140" align="center" />
          <el-table-column label="å®¢æˆ·ä¿¡æ¯" min-width="170">
            <template slot-scope="{ row }">
              <a class="biz-link" @click="openDetail(row)">{{ row.customerName }}</a>
              <span class="sub-text">{{ row.companyName || maskMobile(row.mobile) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="å®¢æˆ·ç±»åž‹" width="96" align="center">
            <template slot-scope="{ row }"><dict-tag :options="dict.type.law_customer_type" :value="row.customerType" /></template>
          </el-table-column>
          <el-table-column label="è¡Œä¸š" width="108" align="center">
            <template slot-scope="{ row }">{{ dictLabel('law_customer_industry', row.industry) }}</template>
          </el-table-column>
          <el-table-column label="ç­‰çº§" width="96" align="center">
            <template slot-scope="{ row }"><dict-tag :options="dict.type.law_customer_level" :value="row.customerLevel" /></template>
          </el-table-column>
          <el-table-column label="çŠ¶æ€" width="88" align="center">
            <template slot-scope="{ row }"><dict-tag :options="dict.type.law_customer_status" :value="customerStatusOf(row)" /></template>
          </el-table-column>
          <el-table-column label="æ ‡ç­¾" min-width="150">
            <template slot-scope="{ row }">
              <span v-if="customerTags(row).length" class="tag-pills">
                <i v-for="item in customerTags(row)" :key="item.name" :style="{ borderColor: item.color, color: item.color }">{{ item.name }}</i>
              </span>
              <span v-else class="sub-text">æœªè®¾ç½®</span>
            </template>
          </el-table-column>
          <el-table-column label="æœ€è¿‘è·Ÿè¿›" prop="lastFollowTime" min-width="132" align="center" />
          <el-table-column label="è´Ÿè´£äºº" width="104" align="center">
            <template slot-scope="{ row }"><span class="owner-cell"><i>{{ avatar(row.ownerName) }}</i>{{ row.ownerName || '-' }}</span></template>
          </el-table-column>
          <el-table-column label="å…³è”åˆåŒ" width="90" align="center">
            <template slot-scope="{ row }">
              <span class="contract-count">{{ customerContractCountOf(row) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="æ“ä½œ" width="268" align="center" class-name="small-padding fixed-width biz-operation-column" fixed="right">
            <template slot-scope="{ row }">
              <span class="action-buttons">
                <el-button v-hasPermi="['customer:query']" :size="controlSize" type="text" icon="el-icon-view" @click="openDetail(row)">è¯¦æƒ…</el-button>
                <el-button v-hasPermi="['customer:edit']" :size="controlSize" type="text" icon="el-icon-edit" :disabled="!canOperateCustomer(row)" @click="openCustomer(row)">ç¼–è¾‘</el-button>
                <el-button v-hasPermi="['customer:tag:assign']" :size="controlSize" type="text" icon="el-icon-price-tag" :disabled="!canOperateCustomer(row)" @click="openCustomerTags(row)">æ ‡ç­¾</el-button>
                <el-button v-if="canCreateContract" v-hasPermi="['contract:add']" :size="controlSize" type="text" icon="el-icon-document-add" :disabled="!canOperateCustomer(row)" @click="newContract(row)">æ–°å»ºåˆåŒ</el-button>
                <el-button v-hasPermi="['customer:remove']" :size="controlSize" type="text" icon="el-icon-delete" class="danger-text" :disabled="!canRemoveCustomer(row)" :title="customerRemoveTip(row)" @click="removeCustomer(row)">åˆ é™¤</el-button>
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
          <el-input v-model="query.contactName" :size="controlSize" prefix-icon="el-icon-search" placeholder="æœç´¢è”ç³»äºº" clearable @clear="search" @keyup.enter.native="search" />
          <el-input v-model="query.customerName" :size="controlSize" placeholder="å®¢æˆ·åç§°" clearable @clear="search" @keyup.enter.native="search" />
          <el-input v-model="query.mobile" :size="controlSize" placeholder="æ‰‹æœºå·" clearable @clear="search" @keyup.enter.native="search" />
        </div>
        <div class="biz-filter-actions">
          <el-button :size="controlSize" plain icon="el-icon-refresh" @click="reset">é‡ç½®</el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="contacts" :size="controlSize">
          <el-table-column label="è”ç³»äºº" prop="contact_name" min-width="120" />
          <el-table-column label="æ‰€å±žå®¢æˆ·" prop="customerName" min-width="150" />
          <el-table-column label="èŒåŠ¡" prop="position_name" width="110" align="center" />
          <el-table-column label="æ‰‹æœºå·" width="130" align="center"><template slot-scope="{ row }">{{ maskMobile(row.mobile) }}</template></el-table-column>
          <el-table-column label="å…³ç³»" width="110" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_contact_relation" :value="row.relation_type" /></template></el-table-column>
          <el-table-column label="å…³é”®è”ç³»äºº" width="110" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_yes_no_flag" :value="row.key_contact" /></template></el-table-column>
          <el-table-column label="æ“ä½œ" width="120" align="center" class-name="small-padding fixed-width">
            <template slot-scope="{ row }">
              <el-button v-hasPermi="['customer:contact:edit']" :size="controlSize" type="text" icon="el-icon-edit" @click="openContact(row)">ç¼–è¾‘</el-button>
              <el-button v-hasPermi="['customer:contact:remove']" :size="controlSize" type="text" icon="el-icon-delete" class="danger-text" @click="removeContact(row)">åˆ é™¤</el-button>
            </template>
          </el-table-column>
      </el-table>
    </biz-table-card>

    <div v-else-if="mode === 'followup'" class="table-card timeline-page">
      <div class="section-title"><h3>å®¢æˆ·è·Ÿè¿›</h3><p>æ²‰æ·€æ¯ä¸€æ¬¡æ²Ÿé€šå†…å®¹å’Œä¸‹æ¬¡è·Ÿè¿›è®¡åˆ’</p></div>
      <div v-loading="loading" class="biz-timeline-list follow-list">
        <article v-for="item in followups" :key="item.followup_id">
          <i :class="'timeline-icon type-' + item.follow_type"><svg-icon :icon-class="followIcon(item.follow_type)" /></i>
          <div>
            <h4>{{ item.customerName || '-' }} <span>{{ dictLabel('law_customer_follow_type', item.follow_type) }}</span></h4>
            <p>{{ item.content || 'æš‚æ— è·Ÿè¿›å†…å®¹' }}</p>
            <small>{{ item.followUserName || '-' }} Â· {{ item.create_time || '-' }}<em v-if="item.next_follow_time">ä¸‹æ¬¡è·Ÿè¿›ï¼š{{ item.next_follow_time }}</em></small>
          </div>
          <el-button v-hasPermi="['customer:followup:remove']" :size="controlSize" type="text" class="danger-text" @click="removeFollowup(item)">åˆ é™¤</el-button>
        </article>
        <el-empty v-if="!followups.length" description="æš‚æ— è·Ÿè¿›è®°å½•" />
      </div>
      <pagination v-show="total > 0" :total="total" :page.sync="query.pageNum" :limit.sync="query.pageSize" @pagination="loadFollowups" />
    </div>

    <div v-else-if="mode === 'tag'" class="setting-grid">
      <article v-for="item in tags" :key="item.tag_id">
        <i :style="{ background: item.tag_color || '#3b82f6' }" />
        <div><h4>{{ item.tag_name }}</h4><p>æŽ’åº {{ item.order_num || 0 }}</p></div>
        <span :class="{ disabled: !sameValue(item.status, dictValue('sys_normal_disable', '0')) }">{{ dictLabel('sys_normal_disable', item.status) }}</span>
        <footer>
          <el-button v-hasPermi="['customer:tag:edit']" :size="controlSize" type="text" @click="openTag(item)">ç¼–è¾‘</el-button>
          <el-button v-hasPermi="['customer:tag:remove']" :size="controlSize" type="text" class="danger-text" @click="removeTag(item)">åˆ é™¤</el-button>
        </footer>
      </article>
      <el-empty v-if="!tags.length" description="æš‚æ— å®¢æˆ·æ ‡ç­¾" />
    </div>

    <biz-table-card v-else-if="mode === 'merge'" :show-search.sync="showSearch" :pagination="false" @query="loadMerge">
      <template #header>
        <div class="section-title"><h3>åŽ»é‡åˆå¹¶</h3><p>æŒ‰å®¢æˆ·åç§°ã€æ‰‹æœºå·ã€ä¼ä¸šåç§°è¾…åŠ©è¯†åˆ«é‡å¤å®¢æˆ·</p></div>
      </template>
      <template #filters>
        <div class="biz-filter-main">
          <el-input v-model="mergeQuery.customerName" :size="controlSize" prefix-icon="el-icon-search" placeholder="å®¢æˆ·åç§°" clearable @clear="loadMerge" @keyup.enter.native="loadMerge" />
          <el-input v-model="mergeQuery.mobile" :size="controlSize" placeholder="æ‰‹æœºå·" clearable @clear="loadMerge" @keyup.enter.native="loadMerge" />
          <el-input v-model="mergeQuery.creditCode" :size="controlSize" placeholder="ä¿¡ç”¨ä»£ç " clearable @clear="loadMerge" @keyup.enter.native="loadMerge" />
        </div>
        <div class="biz-filter-actions">
          <el-button :size="controlSize" plain icon="el-icon-refresh" @click="resetMerge">é‡ç½®</el-button>
        </div>
      </template>
      <el-table :data="mergeList" :size="controlSize">
          <el-table-column label="å®¢æˆ·ç¼–å·" prop="customerNo" min-width="140" />
          <el-table-column label="å®¢æˆ·åç§°" prop="customerName" min-width="150" />
          <el-table-column label="æ‰‹æœºå·" width="130"><template slot-scope="{ row }">{{ maskMobile(row.mobile) }}</template></el-table-column>
          <el-table-column label="ä¿¡ç”¨ä»£ç " prop="creditCode" min-width="160" show-overflow-tooltip />
          <el-table-column label="æ“ä½œ" width="120" align="center"><template slot-scope="{ row }"><el-button v-hasPermi="['customer:merge:merge']" :size="controlSize" type="teÛ^ü¶‰žËkºwµçx(€€€€€€€€€€€€ñ•°µ½ÁÑ¥½¸Øµ™½Èô‰¥Ñ•´¥¸ÕÍÑ½µ•É=ÁÑ¥½¹Ìˆ€é­•äô‰¥Ñ•´¹ÕÍÑ½µ•É%ˆ€é±…‰•°ô‰¥Ñ•´¹ÕÍÑ½µ•É9…µ”ˆ€éÙ…±Õ”ô‰¥Ñ•´¹ÕÍÑ½µ•É%ˆ€é‘¥Í…‰±•ôˆ……¹=Á•É…Ñ•ÕÍÑ½µ•È¡¥Ñ•´¤ˆø(€€€€€€€€€€€€€€ñÍÁ…¸ùíì¥Ñ•´¹ÕÍÑ½µ•É9…µ”õôð½ÍÁ…¸ø(€€€€€€€€€€€€€€ñÍÁ…¸±…ÍÌô‰Í•±•ÐµÍÕˆˆùíì¥Ñ•´¹µ½‰¥±”ñð¥Ñ•´¹½µÁ…¹å9…µ”ñð¥Ñ•´¹ÕÍÑ½µ•É9¼õôð½ÍÁ…¸ø(€€€€€€€€€€€€ð½•°µ½ÁÑ¥½¸ø(€€€€€€€€€€ð½•°µÍ•±•Ðø(€€€€€€€€ð½•°µ™½É´µ¥Ñ•´ø(€€€€€€€€ñ•°µ™½É´µ¥Ñ•´±…‰•°ô‹šZç–ò<ˆÁÉ½Àô‰™½±±½ÝQåÁ”ˆøñ•°µÍ•±•ÐØµµ½‘•°ô‰™½±±½Ý½É´¹™½±±½ÝQåÁ”ˆ€éÍ¥é”ô‰½¹ÑÉ½±M¥é”ˆøñ•°µ½ÁÑ¥½¸Øµ™½Èô‰¥Ñ•´¥¸‘¥Ð¹ÑåÁ”¹±…Ý}ÕÍÑ½µ•É}™½±±½Ý}ÑåÁ”ˆ€é­•äô‰¥Ñ•´¹Ù…±Õ”ˆ€é±…‰•°ô‰¥Ñ•´¹±…‰•°ˆ€éÙ…±Õ”ô‰¥Ñ•´¹Ù…±Õ”ˆ€¼øð½•°µÍ•±•Ðøð½•°µ™½É´µ¥Ñ•´ø(€€€€€€€€ñ•°µ™½É´µ¥Ñ•´±…‰•°ô‹––ºäˆÁÉ½Àô‰½¹Ñ•¹Ðˆøñ•°µ¥¹ÁÕÐØµµ½‘•°ô‰™½±±½Ý½É´¹½¹Ñ•¹Ðˆ€éÍ¥é”ô‰½¹ÑÉ½±M¥é”ˆÑåÁ”ô‰Ñ•áÑ…É•„ˆ€éÉ½ÝÌôˆÐˆ€¼øð½•°µ™½É´µ¥Ñ•´ø(€€€€€€€€ñ•°µ™½É´µ¥Ñ•´±…‰•°ô‹’â/š²‡¢Þ¢þlˆøñ•°µ‘…Ñ”µÁ¥­•ÈØµµ½‘•°ô‰™½±±½Ý½É´¹¹•áÑ½±±½ÝQ¥µ”ˆ€éÍ¥é”ô‰½¹ÑÉ½±M¥é”ˆÙ…±Õ”µ™½Éµ…Ðô‰åååäµ54µ‘! éµ´éÍÌˆÑåÁ”ô‰‘…Ñ•Ñ¥µ”ˆ€¼øð½•°µ™½É´µ¥Ñ•´ø(€€€€€€ð½•°µ™½É´ø(€€€€€€ñ‘¥ØÍ±½Ðô‰™½½Ñ•Èˆøñ•°µ‰ÕÑÑ½¸€éÍ¥é”ô‰½¹ÑÉ½±M¥é”ˆ±¥¬ô‰™½±±½Ý=Á•¸€ô™…±Í”ˆû–>[šÚ ð½•°µ‰ÕÑÑ½¸øñ•°µ‰ÕÑÑ½¸€éÍ¥é”ô‰½¹ÑÉ½±M¥é”ˆÑåÁ”ô‰ÁÉ¥µ…Éäˆ±¥¬ô‰Í…Ù•½±±½ÝÕÀˆûž†»–ºhð½•°µ‰ÕÑÑ½¸øð½‘¥Øø(€€€€ð½•°µ‘¥…±½œø((€€€€ñ•°µ‘¥…±½œ€éÑ¥Ñ±”ô‰Ñ…½É´¹Ñ…%€ü€Ÿžò[¢úGš‚ž¶øœ€è€ŸšZÃ–Š{š‚ž¶øœˆ€éÙ¥Í¥‰±”¹Íå¹Œô‰Ñ…=Á•¸ˆÝ¥‘Ñ ôˆÐØÁÁàˆ€éÕÍÑ½´µ±…ÍÌô‰‘¥…±½±…ÍÌˆ…ÁÁ•¹µÑ¼µ‰½‘äø(€€€€€€ñ•°µ™½É´É•˜ô‰Ñ…½ÉµI•˜ˆ€éµ½‘•°ô‰Ñ…½É´ˆ€éÉÕ±•Ìô‰Ñ…IÕ±•Ìˆ±…‰•°µÝ¥‘Ñ ôˆäÁÁàˆø(€€€€€€€€ñ•°µ™½É´µ¥Ñ•´±…‰•°ô‹š‚ž¶û–B4ˆÁÉ½Àô‰Ñ…9…µ”ˆøñ•°µ¥¹ÁÕÐØµµ½‘•°ô‰Ñ…½É´¹Ñ…9…µ”ˆ€éÍ¥é”ô‰½¹ÑÉ½±M¥é”ˆ€¼øð½•°µ™½É´µ¥Ñ•´ø(€€€€€€€€ñ•°µ™½É´µ¥Ñ•´±…‰•°ô‹¦Šs¢&Èˆøñ•°µ½±½ÈµÁ¥­•ÈØµµ½‘•°ô‰Ñ…½É´¹Ñ…½±½Èˆ€éÍ¥é”ô‰½¹ÑÉ½±M¥é”ˆ€¼øð½•°µ™½É´µ¥Ñ•´ø(€€€€€€€€ñ•°µ™½É´µ¥Ñ•´±…‰•°ô‹š:K–ê<ˆøñ•°µ¥¹ÁÕÐµ¹Õµ‰•ÈØµµ½‘•°ô‰Ñ…½É´¹½É‘•É9Õ´ˆ€éÍ¥é”ô‰½¹ÑÉ½±M¥é”ˆ€éµ¥¸ôˆÀˆ€¼øð½•°µ™½É´µ¥Ñ•´ø(€€€€€€€€ñ•°µ™½É´µ¥Ñ•´±…‰•°ô‹ž*Ûšˆøñ•°µÉ…‘¥¼µÉ½ÕÀØµµ½‘•°ô‰Ñ…½É´¹ÍÑ…ÑÕÌˆøñ•°µÉ…‘¥¼Øµ™½Èô‰¥Ñ•´¥¸‘¥Ð¹ÑåÁ”¹ÍåÍ}¹½Éµ…±}‘¥Í…‰±”ˆ€é­•äô‰¥Ñ•´¹Ù…±Õ”ˆ€é±…‰•°ô‰¥Ñ•´¹Ù…±Õ”ˆùíì¥Ñ•´¹±…‰•°õôð½•°µÉ…‘¥¼øð½•°µÉ…‘¥¼µÉ½ÕÀøð½•°µ™½É´µ¥Ñ•´ø(€€€€€€ð½•°µ™½É´ø(€€€€€€ñ‘¥ØÍ±½Ðô‰™½½Ñ•Èˆøñ•°µ‰ÕÑÑ½¸€éÍ¥é”ô‰½¹ÑÉ½±M¥é”ˆ±¥¬ô‰Ñ…=Á•¸€ô™…±Í”ˆû–>[šÚ ð½•°µ‰ÕÑÑ½¸øñ•°µ‰ÕÑÑ½¸€éÍ¥é”ô‰½¹ÑÉ½±M¥é”ˆÑåÁ”ô‰ÁÉ¥µ…Éäˆ±¥¬ô‰Í…Ù•Q…œˆûž†»–ºhð½•°µ‰ÕÑÑ½¸øð½‘¥Øø(€€€€ð½•°µ‘¥…±½œø((€€€€ñ•°µ‘¥…±½œÑ¥Ñ±”ô‹–º‹š"ßš‚ž¶øˆ€éÙ¥Í¥‰±”¹Íå¹Œô‰ÕÍÑ½µ•ÉQ…=Á•¸ˆÝ¥‘Ñ ôˆÔÈÁÁàˆ€éÕÍÑ½´µ±…ÍÌô‰‘¥…±½±…ÍÌˆ…ÁÁ•¹µÑ¼µ‰½‘äø(€€€€€€ñ•°µ™½É´±…‰•°µÝ¥‘Ñ ôˆäÁÁàˆø(€€€€€€€€ñ•°µ™½É´µ¥Ñ•´±…‰•°ô‹–º‹š"Üˆø(€€€€€€€€€€ñ•°µ¥¹ÁÕÐ€éÙ…±Õ”ô‰ÕÍÑ½µ•ÉQ…½É´¹ÕÍÑ½µ•É9…µ”ˆ€éÍ¥é”ô‰½¹ÑÉ½±M¥é”ˆ‘¥Í…‰±•€¼ø(€€€€€€€€ð½•°µ™½É´µ¥Ñ•´ø(€€€€€€€€ñ•°µ™½É´µ¥Ñ•´±…‰•°ô‹š‚ž¶øˆø(€€€€€€€€€€ñ•°µ¡•­‰½àµÉ½ÕÀØµµ½‘•°ô‰ÕÍÑ½µ•ÉQ…½É´¹Ñ…%‘Ìˆø(€€€€€€€€€€€€ñ•°µ¡•­‰½àØµ™½Èô‰¥Ñ•´¥¸ÕÍÑ½µ•ÉQ…=ÁÑ¥½¹Ìˆ€é­•äô‰¥Ñ•´¹Ñ…}¥ˆ€é±…‰•°ô‰¥Ñ•´¹Ñ…}¥ˆø(€€€€€€€€€€€€€€ñÍÁ…¸±…ÍÌô‰Ñ…œµ‘½Ðˆ€éÍÑå±”ô‰ì‰…­É½Õ¹è¥Ñ•´¹Ñ…}½±½Èñð€œŒÍˆàÉ˜Øœôˆ€¼ùíì¥Ñ•´¹Ñ…}¹…µ”õô(€€€€€€€€€€€€ð½•°µ¡•­‰½àø(€€€€€€€€€€ð½•°µ¡•­‰½àµÉ½ÕÀø(€€€€€€€€€€ñ•°µ•µÁÑäØµ¥˜ôˆ…ÕÍÑ½µ•ÉQ…=ÁÑ¥½¹Ì¹±•¹Ñ ˆ‘•ÍÉ¥ÁÑ¥½¸ô‹šjš^ƒ–B¿žR£š‚ž¶øˆ€é¥µ…”µÍ¥é”ôˆÜÈˆ€¼ø(€€€€€€€€ð½•°µ™½É´µ¥Ñ•´ø(€€€€€€ð½•°µ™½É´ø(€€€€€€ñ‘¥ØÍ±½Ðô‰™½½Ñ•Èˆøñ•°µ‰ÕÑÑ½¸€éÍ¥é”ô‰½¹ÑÉ½±M¥é”ˆ±¥¬ô‰ÕÍÑ½µ•ÉQ…=Á•¸€ô™…±Í”ˆû–>[šÚ ð½•°µ‰ÕÑÑ½¸øñ•°µ‰ÕÑÑ½¸€éÍ¥é”ô‰½¹ÑÉ½±M¥é”ˆÑåÁ”ô‰ÁÉ¥µ…Éäˆ±¥¬ô‰Í…Ù•ÕÍÑ½µ•ÉQ…Ìˆûž†»–ºhð½•°µ‰ÕÑÑ½¸øð½‘¥Øø(€€€€ð½•°µ‘¥…±½œø((€€€€ñ•°µ‘¥…±½œÑ¥Ñ±”ô‹–º‹š"ß–B#–æØˆ€éÙ¥Í¥‰±”¹Íå¹Œô‰µ•É•=Á•¸ˆÝ¥‘Ñ ôˆÔØÁÁàˆ€éÕÍÑ½´µ±…ÍÌô‰‘¥…±½±…ÍÌˆ…ÁÁ•¹µÑ¼µ‰½‘äø(€€€€€€ñ•°µ…±•ÉÐÑ¥Ñ±”ô‹–B#–æÛ–B;¾ò3–ú–B#–æÛ–º‹š"ßžj¢SžÎï’êëŽ¢Þ¢þoŽš‚ž¶û–J3–B#–B3–Â¢þžžï–"Ã’âï–º‹š"ß¾ò3–:–º‹š"ß’òkš‚¢ºÃ’âë–ÞË–B#–æÛŽˆÑåÁ”ô‰Ý…É¹¥¹œˆ€é±½Í…‰±”ô‰™…±Í”ˆÍ¡½Üµ¥½¸€¼ø(€€€€€€ñ•°µ™½É´É•˜ô‰µ•É•½ÉµI•˜ˆ€éµ½‘•°ô‰µ•É•½É´ˆ€éÉÕ±•Ìô‰µ•É•IÕ±•Ìˆ±…‰•°µÝ¥‘Ñ ôˆÄÄÁÁàˆ±…ÍÌô‰µ•É”µ™½É´ˆø(€€€€€€€€ñ•°µ™½É´µ¥Ñ•´±…‰•°ô‹–ú–B#–æÛ–º‹š"ÜˆÁÉ½Àô‰µ•É•‘ÕÍÑ½µ•É%ˆø(€€€€€€€€€€ñ•°µ¥¹ÁÕÐ€éÙ…±Õ”ô‰µ•É•½É´¹µ•É•‘ÕÍÑ½µ•É9…µ”ˆ€éÍ¥é”ô‰½¹ÑÉ½±M¥é”ˆ‘¥Í…‰±•€¼ø(€€€€€€€€ð½•°µ™½É´µ¥Ñ•´ø(€€€€€€€€ñ•°µ™½É´µ¥Ñ•´±…‰•°ô‹’âï–º‹š"ÜˆÁÉ½Àô‰µ…¥¹ÕÍÑ½µ•É%ˆø(€€€€€€€€€€ñ•°µÍ•±•ÐØµµ½‘•°ô‰µ•É•½É´¹µ…¥¹ÕÍÑ½µ•É%ˆ€éÍ¥é”ô‰½¹ÑÉ½±M¥é”ˆ™¥±Ñ•É…‰±”É•µ½Ñ”É•Í•ÉÙ”µ­•åÝ½ÉÁ±…•¡½±‘•Èô‹šBsžÒ‹–æÛ¦'š.§’âï–º‹š"Üˆ€éÉ•µ½Ñ”µµ•Ñ¡½ô‰Í•…É¡ÕÍÑ½µ•É=ÁÑ¥½¹Ìˆ€é±½…‘¥¹œô‰ÕÍÑ½µ•ÉM•±•Ñ1½…‘¥¹œˆ¡…¹”ô‰Í•±•Ñ5…¥¹ÕÍÑ½µ•Èˆø(€€€€€€€€€€€€ñ•°µ½ÁÑ¥½¸Øµ™½Èô‰¥Ñ•´¥¸ÕÍÑ½µ•É=ÁÑ¥½¹Ìˆ€é­•äô‰¥Ñ•´¹ÕÍÑ½µ•É%ˆ€é±…‰•°ô‰¥Ñ•´¹ÕÍÑ½µ•É9…µ”ˆ€éÙ…±Õ”ô‰¥Ñ•´¹ÕÍÑ½µ•É%ˆ€é‘¥Í…‰±•ô‰¥Ñ•´¹ÕÍÑ½µ•É%€ôôôµ•É•½É´¹µ•É•‘ÕÍÑ½µ•É%ñð€……¹=Á•É…Ñ•ÕÍÑ½µ•È¡¥Ñ•´¤ˆø(€€€€€€€€€€€€€€ñÍÁ…¸ùíì¥Ñ•´¹ÕÍÑ½µ•É9…µ”õôð½ÍÁ…¸ø(€€€€€€€€€€€€€€ñÍÁ…¸±…ÍÌô‰Í•±•ÐµÍÕˆˆùíì¥Ñ•´¹µ½‰¥±”ñð¥Ñ•´¹½µÁ…¹å9…µ”ñð¥Ñ•´¹ÕÍÑ½µ•É9¼õôð½ÍÁ…¸ø(€€€€€€€€€€€€ð½•°µ½ÁÑ¥½¸ø(€€€€€€€€€€ð½•°µÍ•±•Ðø(€€€€€€€€ð½•°µ™½É´µ¥Ñ•´ø(€€€€€€€€ñ•°µ™½É´µ¥Ñ•´±…‰•°ô‹–B#–æÛ¢¾Óšb8ˆø(€€€€€€€€€€ñ•°µ¥¹ÁÕÐØµµ½‘•°ô‰µ•É•½É´¹½¹Ñ•¹Ðˆ€éÍ¥é”ô‰½¹ÑÉ½±M¥é”ˆÑåÁ”ô‰Ñ•áÑ…É•„ˆ€éÉ½ÝÌôˆÌˆ€¼ø(€€€€€€€€ð½•°µ™½É´µ¥Ñ•´ø(€€€€€€ð½•°µ™½É´ø(€€€€€€ñ‘¥ØÍ±½Ðô‰™½½Ñ•Èˆøñ•°µ‰ÕÑÑ½¸€éÍ¥é”ô‰½¹ÑÉ½±M¥é”ˆ±¥¬ô‰µ•É•=Á•¸€ô™…±Í”ˆû–>[šÚ ð½•°µ‰ÕÑÑ½¸øñ•°µ‰ÕÑÑ½¸€éÍ¥é”ô‰½¹ÑÉ½±M¥é”ˆÑåÁ”ô‰ÁÉ¥µ…Éäˆ±¥¬ô‰Í…Ù•5•É”ˆûž†»¢º“–B#–æØð½•°µ‰ÕÑÑ½¸øð½‘¥Øø(€€€€ð½•°µ‘¥…±½œø(€€ð½‘¥Øø(ð½Ñ•µÁ±…Ñ”ø((ñÍÉ¥ÁÐø)¥µÁ½ÉÐá•±%µÁ½ÉÑ¥…±½œ™É½´€ ½½µÁ½¹•¹ÑÌ½á•±%µÁ½ÉÑ¥…±½œœ)¥µÁ½ÉÐ	¥é!•É¼™É½´€ ½Ù¥•ÝÌ½‰ÕÍ¥¹•ÍÌ½½µÁ½¹•¹ÑÌ½	¥é!•É¼œ)¥µÁ½ÉÐ	¥é5•ÑÉ¥Ì™É½´€ ½Ù¥•ÝÌ½‰ÕÍ¥¹•ÍÌ½½µÁ½¹•¹ÑÌ½	¥é5•ÑÉ¥Ìœ)¥µÁ½ÉÐ	¥éA…•!•…‘•È™É½´€ ½Ù¥•ÝÌ½‰ÕÍ¥¹•ÍÌ½½µÁ½¹•¹ÑÌ½	¥éA…•!•…‘•Èœ)¥µÁ½ÉÐ	¥éQ…‰±•…É™É½´€ ½Ù¥•ÝÌ½‰ÕÍ¥¹•ÍÌ½½µÁ½¹•¹ÑÌ½	¥éQ…‰±•…Éœ)¥µÁ½ÉÐ‰ÕÍ¥¹•ÍÍU¤™É½´€ ½Ù¥•ÝÌ½‰ÕÍ¥¹•ÍÌ½µ¥á¥¹Ì½‰ÕÍ¥¹•ÍÍU¤œ)¥µÁ½ÉÐÕÍÑ½µ•É1¥™•å±”™É½´€ ½Ù¥•ÝÌ½‰ÕÍ¥¹•ÍÌ½µ¥á¥¹Ì½ÕÍÑ½µ•É1¥™•å±”œ)¥µÁ½ÉÐÕÍÑ½µ•ÉA…•Ñ¥½¹Ì™É½´€œ¸½ÕÍÑ½µ•ÈµÁ…”µ…Ñ¥½¹Ìœ)¥µÁ½ÉÐÕÍÑ½µ•É•Ñ…¥±É…Ý•È™É½´€œ¸½½µÁ½¹•¹ÑÌ½ÕÍÑ½µ•É•Ñ…¥±É…Ý•Èœ)¥µÁ½ÉÐì±¥ÍÑÕÍÑ½µ•É=Ý¹•È°±¥ÍÑQ…œô™É½´€ ½…Á¤½ÕÍÑ½µ•Èœ)¥µÁ½ÉÐì±¥ÍÑM•ÑÑ¥¹œô™É½´€ ½…Á¤½±•…œ()•áÁ½ÉÐ‘•™…Õ±Ðì(€¹…µ”è€ÕÍÑ½µ•Èœ°(€µ¥á¥¹Ìèm‰ÕÍ¥¹•ÍÍU¤°ÕÍÑ½µ•É1¥™•å±”°ÕÍÑ½µ•ÉA…•Ñ¥½¹Ít°(€½µÁ½¹•¹ÑÌèìá•±%µÁ½ÉÑ¥…±½œ°	¥é!•É¼°	¥é5•ÑÉ¥Ì°	¥éA…•!•…‘•È°	¥éQ…‰±•…É°ÕÍÑ½µ•É•Ñ…¥±É…Ý•Èô°(€‘¥ÑÌèl±…Ý}ÕÍÑ½µ•É}ÑåÁ”œ°€±…Ý}å•Í}¹½}™±…œœ°€±…Ý}ÕÍÑ½µ•É}±•Ù•°œ°€±…Ý}ÕÍÑ½µ•É}¥¹‘ÕÍÑÉäœ°€±…Ý}½¹Ñ…Ñ}É•±…Ñ¥½¸œ°€±…Ý}ÕÍÑ½µ•É}™½±±½Ý}ÑåÁ”œ°€±…Ý}ÕÍÑ½µ•É}ÍÑ…ÑÕÌœ°€ÍåÍ}¹½Éµ…±}‘¥Í…‰±”t°(€‘…Ñ„ ¤ì(€€€½¹ÍÐ½µÁ…¹åI•ÅÕ¥É•€ô€¡ÉÕ±”°Ù…±Õ”°…±±‰…¬¤€ôøì(€€€€€¥˜€¡Ñ¡¥Ì¹™½É´¹ÕÍÑ½µ•ÉQåÁ”€ôôô€•¹Ñ•ÉÁÉ¥Í”œ€˜˜€…Ù…±Õ”¤ì(€€€€€€€…±±‰…¬¡¹•ÜÉÉ½È Ÿ’ò’âk–º‹š"ß¢¾ß¢úO–—–³–>ã–B7žžÀœ¤¤(€€€€€ô•±Í”ì(€€€€€€€…±±‰…¬ ¤(€€€€€ô(€€€ô(€€€É•ÑÕÉ¸ì(€€€€€µ½‘”è€±¥ÍÐœ°(€€€€€…Ù…¥±…‰±•5½‘•Ìèl±¥ÍÐœ°€½¹Ñ…Ðœ°€™½±±½ÝÕÀœ°€Ñ…œœ°€µ•É”t°(€€€€€Í¡½ÝM•…É èÑÉÕ”°(€€€€€±½…‘¥¹œè™…±Í”°(€€€€€Ñ½Ñ…°è€À°(€€€€€±¥ÍÐèmt°(€€€€€½¹Ñ…ÑÌèmt°(€€€€€™½±±½ÝÕÁÌèmt°(€€€€€Ñ…Ìèmt°(€€€€€µ•ÑÉ¥Ìèmt°(€€€€€½¹Ñ…Ñ5•ÑÉ¥Ìèmt°(€€€€€µ•É•5•ÑÉ¥Ìèmt°(€€€€€ÅÕ•ÉäèìÁ…•9Õ´è€Ä°Á…•M¥é”è€ÄÀ°ÕÍÑ½µ•É9…µ”è€œœ°ÕÍÑ½µ•ÉQåÁ”è€œœ°¥¹‘ÕÍÑÉäè€œœ°Ñ…%è€œœ°½Ý¹•É%è€œœ°ÕÍÑ½µ•É1•Ù•°è€œœô°(€€€€€½Ý¹•É=ÁÑ¥½¹Ìèmt°(€€€€€±•…‘M½ÕÉ•=ÁÑ¥½¹Ìèmt°(€€€€€Ñ…=ÁÑ¥½¹Ìèmt°(€€€€€…‘Ù…¹•‘=Á•¸è™…±Í”°(€€€€€™½É´èíô°(€€€€€½¹Ñ…Ñ½É´èíô°(€€€€€™½±±½Ý½É´èíô°(€€€€€Ñ…½É´èíô°(€€€€€ÕÍÑ½µ•ÉQ…½É´èíô°(€€€€€ÕÍÑ½µ•ÉQ…=ÁÑ¥½¹Ìèmt°(€€€€€µ•É•EÕ•Éäèíô°(€€€€€µ•É•½É´èíô°(€€€€€µ•É•1¥ÍÐèmt°(€€€€€ÕÍÑ½µ•É=ÁÑ¥½¹Ìèmt°(€€€€€ÕÍÑ½µ•ÉM•±•Ñ1½…‘¥¹œè™…±Í”°(€€€€€‰ÕÍ¥¹•ÍÍA…•5•Ñ„èì(€€€€€€€‘•™…Õ±ÑQ¥Ñ±”è€Ÿ–º‹š"ß’â·–þœ°(€€€€€€€Ñ¥Ñ±•Ìèì±¥ÍÐè€Ÿ–º‹š"ß–"_¢† œ°½¹Ñ…Ðè€Ÿ¢SžÎï’êëžº‡žBœ°™½±±½ÝÕÀè€Ÿ–º‹š"ß¢Þ¢þlœ°Ñ…œè€Ÿ–º‹š"ßš‚ž¶øœ°µ•É”è€Ÿ–:ï¦7–B#–æØœô°(€€€€€€€‘•ÍÉ¥ÁÑ¥½¹Ìèì(€€€€€€€€€±¥ÍÐè€Ÿžî’âžîÓš*“–º‹š"ßš†š†#Ž¢SžÎï’êëŽ¢Þ¢þo’â;–B#–B3¢ö³–2Xœ°(€€€€€€€€€½¹Ñ…Ðè€Ÿžº‡žB–º‹š"ß’â/žj–Ï¦R»¢SžÎï’êë–J3šÊ¦k–ÏžÎìœ°(€€€€€€€€€™½±±½ÝÕÀè€Ÿ¢ºÃ–öW–º‹š"ßšÊ¦k––ºç’â;’â/š²‡¢Þ¢þo¢º‡–"Hœ°(€€€€€€€€€Ñ…œè€ŸžîÓš*“–º‹š"ß–"žú“š‚ž¶û¾ò3žR£’ê;–B;žî·žî?¢B”œ°(€€€€€€€€€µ•É”è€Ÿ¢¾–"¯¦7–’7–º‹š"ß–æÛ¢þžžï¢SžÎï’êëŽ¢Þ¢þoŽš‚ž¶û–J3–B#–B3–Ï¢Pœ(€€€€€€€ô(€€€€€ô°(€€€€€‘•Ñ…¥±=Á•¸è™…±Í”°(€€€€€‘•Ñ…¥±ÕÍÑ½µ•É%è¹Õ±°°(€€€€€‘•Ñ…¥±ÕÍÑ½µ•Èèíô°(€€€€€‘•Ñ…¥±½¹Ñ…ÑÌèmt°(€€€€€‘•Ñ…¥±½±±½ÝÕÁÌèmt°(€€€€€‘•Ñ…¥±½¹ÑÉ…ÑÌèmt°(€€€€€‘•Ñ…¥±5•É•1½Ìèmt°(€€€€€ÕÍÑ½µ•É=Á•¸è™…±Í”°(€€€€€½¹Ñ…Ñ=Á•¸è™…±Í”°(€€€€€™½±±½Ý=Á•¸è™…±Í”°(€€€€€Ñ…=Á•¸è™…±Í”°(€€€€€ÕÍÑ½µ•ÉQ…=Á•¸è™…±Í”°(€€€€€µ•É•=Á•¸è™…±Í”°(€€€€€ÕÍÑ½µ•ÉIÕ±•Ìèì(€€€€€€€ÕÍÑ½µ•É9…µ”èmìÉ•ÅÕ¥É•èÑÉÕ”°µ•ÍÍ…”è€Ÿ¢¾ß¢úO–—–º‹š"ß–B7žžÀœ°ÑÉ¥•Èè€‰±ÕÈœõt°(€€€€€€€ÕÍÑ½µ•ÉQåÁ”èmìÉ•ÅÕ¥É•èÑÉÕ”°µ•ÍÍ…”è€Ÿ¢¾ß¦'š.§–º‹š"ßžÆï–z,œ°ÑÉ¥•Èè€¡…¹”œõt°(€€€€€€€µ½‰¥±”èmìÉ•ÅÕ¥É•èÑÉÕ”°µ•ÍÍ…”è€Ÿ¢¾ß¢úO–—š&/šrë–>Üœ°ÑÉ¥•Èè€‰±ÕÈœõt°(€€€€€€€Í½ÕÉ•½‘”èmìÉ•ÅÕ¥É•èÑÉÕ”°µ•ÍÍ…”è€Ÿ¢¾ß¦'š.§–º‹š"ßšv—šê@œ°ÑÉ¥•Èè€¡…¹”œõt°(€€€€€€€ÕÍÑ½µ•É1•Ù•°èmìÉ•ÅÕ¥É•èÑÉÕ”°µ•ÍÍ…”è€Ÿ¢¾ß¦'š.§–º‹š"ßž¶'žêœœ°ÑÉ¥•Èè€¡…¹”œõt°(€€€€€€€µ…¥¹•µ…¹èmìÉ•ÅÕ¥É•èÑÉÕ”°µ•ÍÍ…”è€Ÿ¢¾ß¢úO–—’âï¢š¦ršÆœ°ÑÉ¥•Èè€‰±ÕÈœõt°(€€€€€€€½Ý¹•É%èmìÉ•ÅÕ¥É•èÑÉÕ”°µ•ÍÍ…”è€Ÿ¢¾ß¦'š.§¢Ò¢Ò’êèœ°ÑÉ¥•Èè€¡…¹”œõt°(€€€€€€€½µÁ…¹å9…µ”èmìÙ…±¥‘…Ñ½Èè½µÁ…¹åI•ÅÕ¥É•°ÑÉ¥•Èè€‰±ÕÈœõt(€€€€€ô°(€€€€€½¹Ñ…ÑIÕ±•Ìèì(€€€€€€€ÕÍÑ½µ•É%èmìÉ•ÅÕ¥É•èÑÉÕ”°µ•ÍÍ…”è€Ÿ¢¾ß¦'š.§š&–Æ{–º‹š"Üœ°ÑÉ¥•Èè€¡…¹”œõt°(€€€€€€€½¹Ñ…Ñ9…µ”èmìÉ•ÅÕ¥É•èÑÉÕ”°µ•ÍÍ…”è€Ÿ¢¾ß¢úO–—¢SžÎï’êèœ°ÑÉ¥•Èè€‰±ÕÈœõt°(€€€€€€€µ½‰¥±”èmìÉ•ÅÕ¥É•èÑÉÕ”°µ•ÍÍ…”è€Ÿ¢¾ß¢úO–—š&/šrë–>Üœ°ÑÉ¥•Èè€‰±ÕÈœõt°(€€€€€€€É•±…Ñ¥½¹QåÁ”èmìÉ•ÅÕ¥É•èÑÉÕ”°µ•ÍÍ…”è€Ÿ¢¾ß¦'š.§–ÏžÎìœ°ÑÉ¥•Èè€¡…¹”œõt(€€€€€ô°(€€€€€™½±±½ÝIÕ±•Ìèì(€€€€€€€ÕÍÑ½µ•É%èmìÉ•ÅÕ¥É•èÑÉÕ”°µ•ÍÍ…”è€Ÿ¢¾ß¦'š.§š&–Æ{–º‹š"Üœ°ÑÉ¥•Èè€¡…¹”œõt°(€€€€€€€™½±±½ÝQåÁ”èmìÉ•ÅÕ¥É•èÑÉÕ”°µ•ÍÍ…”è€Ÿ¢¾ß¦'š.§¢Þ¢þošZç–ò<œ°ÑÉ¥•Èè€¡…¹”œõt°(€€€€€€€½¹Ñ•¹ÐèmìÉ•ÅÕ¥É•èÑÉÕ”°µ•ÍÍ…”è€Ÿ¢¾ß¢úO–—¢Þ¢þo––ºäœ°ÑÉ¥•Èè€‰±ÕÈœõt(€€€€€ô°(€€€€€Ñ…IÕ±•Ìèì(€€€€€€€Ñ…9…µ”èmìÉ•ÅÕ¥É•èÑÉÕ”°µ•ÍÍ…”è€Ÿ¢¾ß¢úO–—š‚ž¶û–B4œ°ÑÉ¥•Èè€‰±ÕÈœõt(€€€€€ô°(€€€€€µ•É•IÕ±•Ìèì(€€€€€€€µ•É•‘ÕÍÑ½µ•É%èmìÉ•ÅÕ¥É•èÑÉÕ”°µ•ÍÍ…”è€Ÿ¢¾ß¦'š.§–ú–B#–æÛ–º‹š"Üœ°ÑÉ¥•Èè€¡…¹”œõt°(€€€€€€€µ…¥¹ÕÍÑ½µ•É%èmìÉ•ÅÕ¥É•èÑÉÕ”°µ•ÍÍ…”è€Ÿ¢¾ß¦'š.§’âï–º‹š"Üœ°ÑÉ¥•Èè€¡…¹”œõt(€€€€€ô°(€€€€€µ•ÑÉ¥½¹™¥œèl(€€€€€€€ì­•äè€Ñ½Ñ…°œ°±…‰•°è€Ÿ–º‹š"ßšïšVÀœ°¡¥¹Ðè€Ÿ–£¦£šr'šV#–º‹š"Üœ°¥½¸è€Á•½Á±•Ìœ°½±½Èè€‰±Õ”œô°(€€€€€€€ì­•äè€µ½¹Ñ¡9•Üœ°±…‰•°è€Ÿšr³šr#šZÃ–Šxœ°¡¥¹Ðè€ŸšZÃšÊ'šÞ–º‹š"Üœ°¥½¸è€‘…Ñ”œ°½±½Èè€å…¸œô°(€€€€€€€ì­•äè€¡¥¡Y…±Õ”œ°±…‰•°è€Ÿ¦®c’îß–ó–º‹š"Üœ°¡¥¹Ðè€Ÿ¦7ž
çžî?¢B—–º‹š"Üœ°¥½¸è€ÍÑ…Èœ°½±½Èè€Ù¥½±•Ðœô°(€€€€€€€ì­•äè€…Ñ¥Ù”œ°±…‰•°è€ŸšÒï¢Þ–º‹š"Üœ°¡¥¹Ðè€Ÿ–ÞËšr'¢Þ¢þo¢ºÃ–öTœ°¥½¸è€Ñ¥µ”œ°½±½Èè€É••¸œô(€€€€€t°(€€€€€½¹Ñ…Ñ5•ÑÉ¥½¹™¥œèl(€€€€€€€ì­•äè€Ñ½Ñ…°œ°±…‰•°è€Ÿ¢SžÎï’êëšïšVÀœ°¡¥¹Ðè€Ÿž²›–B#–öO–&7ž¶o¦$œ°¥½¸è€Á•½Á±•Ìœ°½±½Èè€‰±Õ”œô°(€€€€€€€ì­•äè€­•å½¹Ñ…ÑÌœ°±…‰•°è€Ÿ–Ï¦R»¢SžÎï’êèœ°¡¥¹Ðè€Ÿ–öO–&7¦†×–Ï¦R»¢SžÎï’êèœ°¥½¸è€ÍÑ…Èœ°½±½Èè€Ù¥½±•Ðœô°(€€€€€€€ì­•äè€ÕÍÑ½µ•ÉÌœ°±…‰•°è€Ÿ–Ï¢S–º‹š"Üœ°¡¥¹Ðè€Ÿ–öO–&7¦†×¢šžn[–º‹š"Üœ°¥½¸è€ÑÉ•”œ°½±½Èè€å…¸œô°(€€€€€€€ì­•äè€Ý¥Ñ¡5½‰¥±”œ°±…‰•°è€ŸžVg–¶cš&/šrë–>Üœ°¡¥¹Ðè€Ÿ–öO–&7¦†×šr'š&/šrë–>Üœ°¥½¸è€Á¡½¹”œ°½±½Èè€É••¸œô(€€€€€t°(€€€€€µ•É•5•ÑÉ¥½¹™¥œèl(€€€€€€€ì­•äè€…¹‘¥‘…Ñ•Ìœ°±…‰•°è€Ÿ–g¦'–º‹š"Üœ°¡¥¹Ðè€Ÿ–öO–&7¢¾–"¯žîOšzpœ°¥½¸è€Á•½Á±•Ìœ°½±½Èè€‰±Õ”œô°(€€€€€€€ì­•äè€Ý¥Ñ¡5½‰¥±”œ°±…‰•°è€Ÿš&/šrë–>ßžêÿžÒˆœ°¡¥¹Ðè€Ÿ–>¿š2'š&/šrë–>ßš¾S–¾äœ°¥½¸è€Á¡½¹”œ°½±½Èè€å…¸œô°(€€€€€€€ì­•äè€Ý¥Ñ¡½µÁ…¹äœ°±…‰•°è€Ÿ’ò’âk–º‹š"Üœ°¡¥¹Ðè€Ÿ–B¯–³–>ã–B7žžÀœ°¥½¸è€‘½Õµ•¹Ñ…Ñ¥½¸œ°½±½Èè€Ù¥½±•Ðœô°(€€€€€€€ì­•äè€Ý¥Ñ¡É•‘¥Ñ½‘”œ°±…‰•°è€Ÿ’þ‡žR£’îž‚œ°¡¥¹Ðè€Ÿ–>¿š2'’þ‡žR£’îž‚š¾S–¾äœ°¥½¸è€•‘Õ…Ñ¥½¸œ°½±½Èè€É••¸œô(€€€€€t(€€€ô(€ô°(€½µÁÕÑ•èì(€€€¡•É½5•Ñ„ ¤ì(€€€€€½¹ÍÐµ•Ñ„€ôì(€€€€€€€±¥ÍÐèì(€€€€€€€€€•å•‰É½Üè€Ÿ–º‹š"ß¢Ö’êŸšÊ'šÞ œ°(€€€€€€€€€Ñ¥Ñ±”è€Ÿžî’âžîÓš*“–º‹š"ßš†š†#Ž¢SžÎï’êë’â;’âk–*‡¢ö³–2Xœ°(€€€€€€€€€‘•ÍÉ¥ÁÑ¥½¸è€ŸžêÿžÒ‹¢ö³–2[–B;–ö‹š"C–º‹š"ß¢Ö’êŸ¾ò3š2žî·¢ºÃ–öWšÊ¦kŽ–B#–B3’â;šr7–*‡ž*ÛšŽœ(€€€€€€€ô°(€€€€€€€½¹Ñ…Ðèì(€€€€€€€€€•å•‰É½Üè€=9QP5959Pœ°(€€€€€€€€€Ñ¥Ñ±”è€Ÿžî’âžº‡žB–º‹š"ß¢SžÎï’êë’â;–Ï¦R»šÊ¦k–ÏžÎìœ°(€€€€€€€€€‘•ÍÉ¥ÁÑ¥½¸è€ŸšÊ'šÞ–º‹š"ß¢SžÎï’êëŽ–ÏžÎïžÆï–z/’â;–Ï¦R»¢SžÎï’êëš‚¢¾¾ò3šZç’úÿ–B;žî·¢Þ¢þo–J3–B#–B3–6?–B3Žœ(€€€€€€€ô°(€€€€€€€µ•É”èì(€€€€€€€€€•å•‰É½Üè€UA1%Q%=8œ°(€€€€€€€€€Ñ¥Ñ±”è€Ÿ¢¾–"¯¦7–’7–º‹š"ß–æÛ–B#–æÛ’âk–*‡¢Ö’êœœ°(€€€€€€€€€‘•ÍÉ¥ÁÑ¥½¸è€Ÿš2'–º‹š"ß–B7žžÃŽš&/šrë–>ß–J3’þ‡žR£’îž‚¢ú–*§¢¾–"¯¦7–’7¢ºÃ–öW¾ò3–B#–æÛ¢SžÎï’êëŽ¢Þ¢þoŽš‚ž¶û’â;–B#–B3–Ï¢SŽœ(€€€€€€€ô(€€€€€ô(€€€€€É•ÑÕÉ¸µ•Ñ…mÑ¡¥Ì¹µ½‘•tñðµ•Ñ„¹±¥ÍÐ(€€€ô°(€€€µ½‘•5•ÑÉ¥Ì ¤ì(€€€€€¥˜€¡Ñ¡¥Ì¹µ½‘”€ôôô€½¹Ñ…Ðœ¤É•ÑÕÉ¸Ñ¡¥Ì¹½¹Ñ…Ñ5•ÑÉ¥Ì(€€€€€¥˜€¡Ñ¡¥Ì¹µ½‘”€ôôô€µ•É”œ¤É•ÑÕÉ¸Ñ¡¥Ì¹µ•É•5•ÑÉ¥Ì(€€€€€É•ÑÕÉ¸Ñ¡¥Ì¹µ•ÑÉ¥Ì(€€€ô°(€€€µ½‘•5•ÑÉ¥½¹™¥œ ¤ì(€€€€€¥˜€¡Ñ¡¥Ì¹µ½‘”€ôôô€½¹Ñ…Ðœ¤É•ÑÕÉ¸Ñ¡¥Ì¹½¹Ñ…Ñ5•ÑÉ¥½¹™¥œ(€€€€€¥˜€¡Ñ¡¥Ì¹µ½‘”€ôôô€µ•É”œ¤É•ÑÕÉ¸Ñ¡¥Ì¹µ•É•5•ÑÉ¥½¹™¥œ(€€€€€É•ÑÕÉ¸Ñ¡¥Ì¹µ•ÑÉ¥½¹™¥œ(€€€ô°(€€€…¹I•…‘½¹ÑÉ…Ð ¤ì(€€€€€É•ÑÕÉ¸Ñ¡¥Ì¸‘…ÕÑ ¹¡…ÍA•Éµ¥=È¡l½¹ÑÉ…Ðé±¥ÍÐœ°€½¹ÑÉ…ÐéÅÕ•Éät¤(€€€ô°(€€€…¹I•…‘½¹Ñ…Ð ¤ì(€€€€€É•ÑÕÉ¸Ñ¡¥Ì¸‘…ÕÑ ¹¡…ÍA•Éµ¤ ÕÍÑ½µ•Èé½¹Ñ…Ðé±¥ÍÐœ¤(€€€ô°(€€€…¹I•…‘½±±½ÝÕÀ ¤ì(€€€€€É•ÑÕÉ¸Ñ¡¥Ì¸‘…ÕÑ ¹¡…ÍA•Éµ¤ ÕÍÑ½µ•Èé™½±±½ÝÕÀé±¥ÍÐœ¤(€€€ô°(€€€…¹I•…‘Q…œ ¤ì(€€€€€É•ÑÕÉ¸Ñ¡¥Ì¸‘…ÕÑ ¹¡…ÍA•Éµ¥=È¡lÕÍÑ½µ•ÈéÑ…œé±¥ÍÐœ°€ÕÍÑ½µ•ÈéÑ…œéÅÕ•Éäœ°€ÕÍÑ½µ•ÈéÑ…œé…ÍÍ¥¸t¤(€€€ô°(€€€…¹É•…Ñ•½¹ÑÉ…Ð ¤ì(€€€€€É•ÑÕÉ¸Ñ¡¥Ì¸‘…ÕÑ ¹¡…ÍA•Éµ¤ ½¹ÑÉ…Ðé…‘œ¤€˜˜Ñ¡¥Ì¹…¹I•…‘½¹ÑÉ…Ð(€€€ô°(€€€…¹I•…‘5•É•1½œ ¤ì(€€€€€É•ÑÕÉ¸Ñ¡¥Ì¸‘…ÕÑ ¹¡…ÍA•Éµ¤ ÕÍÑ½µ•Èéµ•É”é±¥ÍÐœ¤(€€€ô(€ô°(€Ý…Ñ èì(€€€€œ‘É½ÕÑ”¹ÅÕ•Éä¹µ½‘Õ±”œèì(€€€€€¥µµ•‘¥…Ñ”èÑÉÕ”°(€€€€€¡…¹‘±•È¡Ù…±Õ”¤ì(€€€€€€€Ñ¡¥Ì¹µ½‘”€ôÑ¡¥Ì¹¹½Éµ…±¥é•5½‘”¡Ù…±Õ”¤(€€€€€€€Ñ¡¥Ì¹±½…‘A…” ¤(€€€€€ô(€€€ô(€ô°(€É•…Ñ• ¤ì(€€€€¼¼ƒ¢Ò¢Ò’êë’â/š.'¾òk’î–¾çšr'šZÃ–Šx¿žò[¢úD¿–"¦7šv¦fCžjžR£š"ß–*ƒ¢ö÷¾ò#–>¢±•…½¥¹‘•à¹ÙÕ—¾ò$(€€€¥˜€¡Ñ¡¥Ì¸‘…ÕÑ ¹¡…ÍA•Éµ¥=È¡lÕÍÑ½µ•Èé…‘œ°€ÕÍÑ½µ•Èé•‘¥Ðœ°€ÕÍÑ½µ•Èé…ÍÍ¥¸t¤¤ì(€€€€€±¥ÍÑÕÍÑ½µ•É=Ý¹•È ¤¹Ñ¡•¸¡É•Ì€ôøìÑ¡¥Ì¹½Ý¹•É=ÁÑ¥½¹Ì€ôÉ•Ì¹‘…Ñ„ñðmtô¤(€€€ô(€€€±¥ÍÑM•ÑÑ¥¹œ¡ìÍ•ÑÑ¥¹QåÁ”è€Í½ÕÉ”œô¤¹Ñ¡•¸¡É•Ì€ôøì(€€€€€Ñ¡¥Ì¹±•…‘M½ÕÉ•=ÁÑ¥½¹Ì€ô€¡É•Ì¹‘…Ñ„ñðmt¤¹™¥±Ñ•È¡¥Ñ•´€ôø¥Ñ•´¹ÍÑ…ÑÕÌ€ôôô€œÀœ¤(€€€ô¤(€€€€¼¼ƒ–B¿žR£’â·žjš‚ž¶û’â/š.$(€€€¥˜€¡Ñ¡¥Ì¹…¹I•…‘Q…œ¤ì(€€€€€±¥ÍÑQ…œ¡ìÍÑ…ÑÕÌèÑ¡¥Ì¹‘¥ÑY…±Õ” ÍåÍ}¹½Éµ…±}‘¥Í…‰±”œ°€œÀœ¤ô¤¹Ñ¡•¸¡É•Ì€ôøì(€€€€€€€Ñ¡¥Ì¹Ñ…=ÁÑ¥½¹Ì€ôÉ•Ì¹‘…Ñ„ñðmt(€€€€€ô¤(€€€ô(€ô)ô(ð½ÍÉ¥ÁÐø((ñÍÑå±”Í½Á•±…¹œô‰ÍÍÌˆø)¥µÁ½ÉÐ€ˆ¸¸½‰ÕÍ¥¹•ÍÌ½‰ÕÍ¥¹•ÍÌ¹ÍÍÌˆì((¹ÕÍÑ½µ•ÈµÁ…”ì(€€´µ‰¥èµ™¥±Ñ•Èµ¥¹ÁÕÐµÝ¥‘Ñ è€ÈÄÁÁàì(€€´µ‰¥èµ™¥±Ñ•ÈµÍ•±•ÐµÝ¥‘Ñ è€ÄÈáÁàì)ô((¹ÑåÁ”µÝ•¡…Ñí‰…­É½Õ¹èŒÄÁˆäàÅô¹ÑåÁ”µµ••Ñ¥¹í‰…­É½Õ¹è˜Ôå”Á‰ô¹ÑåÁ”µ•µ…¥±í‰…­É½Õ¹èŒáˆÕ˜Ùô(¹Ñ…œµÁ¥±±Íí‘¥ÍÁ±…äé™±•àí™±•àµÝÉ…ÀéÝÉ…Àí…ÀèÕÁáô¹Ñ…œµÁ¥±±Ì¥íÁ…‘‘¥¹œèÉÁà€ÝÁàí‰½É‘•ÈèÅÁàÍ½±¥í‰½É‘•ÈµÉ…‘¥ÕÌèÄÁÁàí‰…­É½Õ¹è™™˜í™½¹ÐµÍ¥é”éÙ…È ´µ‰¥èµ™½¹Ðµµ¥¹¤¤í™½¹ÐµÍÑå±”é¹½Éµ…°í±¥¹”µ¡•¥¡ÐèÄ¸Ñô(¹½¹ÑÉ…Ðµ½Õ¹Ñí‘¥ÍÁ±…äé¥¹±¥¹”µ™±•àí…±¥¸µ¥Ñ•µÌé•¹Ñ•Èí©ÕÍÑ¥™äµ½¹Ñ•¹Ðé•¹Ñ•Èíµ¥¸µÝ¥‘Ñ èÈáÁàí¡•¥¡ÐèÈÉÁàíÁ…‘‘¥¹œèÀ€áÁàí‰½É‘•ÈµÉ…‘¥ÕÌèääåÁàí½±½ÈèŒÈÔØÍ•ˆí‰…­É½Õ¹è••˜Ñ™˜í™½¹ÐµÝ•¥¡ÐèØÀÀí™½¹ÐµÍ¥é”éÙ…È ´µ‰¥èµ™½¹ÐµÍµ…±°¥ô(ð½ÍÑå±”ø((ñÍÑå±”±…¹œô‰ÍÍÌˆø)¥µÁ½ÉÐ€ˆ¸¸½‰ÕÍ¥¹•ÍÌ½‰ÕÍ¥¹•ÍÌµ‘¥…±½œ¹ÍÍÌˆì(ð½ÍÑå±”ø(