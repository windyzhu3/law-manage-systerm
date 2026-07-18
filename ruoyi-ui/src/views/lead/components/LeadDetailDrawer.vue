<template>
  <el-drawer :visible.sync="visible" size="920px" :custom-class="'lead-detail-drawer ' + sizeClass" append-to-body :with-header="false" @close="$emit('close')">
    <div v-if="lead" class="detail-shell">
      <main>
        <button class="drawer-close" @click="visible=false"><i class="el-icon-close" /></button>
        <div class="detail-title"><div class="initial">{{ initial }}</div><div><h2>{{ lead.contactName || lead.leadName }}</h2><p>{{ maskMobile(lead.mobile) }} <i /> {{ lead.wechat || '未填写微信' }}</p></div><el-tag size="mini" :type="status.type">{{ status.label }}</el-tag></div>
        <div class="summary-strip"><span>线索编号：<b>{{ lead.leadNo }}</b></span><span>来源：<b>{{ sourceName }}</b></span><span>优先级：<b>{{ priorityName || '-' }}</b></span><span>创建时间：<b>{{ lead.createTime || '-' }}</b></span></div>
        <div class="detail-actions"><el-button v-if="canEdit" v-hasPermi="['lead:edit']" type="primary" icon="el-icon-edit" @click="$emit('edit',lead)">编辑</el-button><el-button v-if="canAssign" v-hasPermi="['lead:assign']" icon="el-icon-user" @click="$emit('assign',lead)">分配</el-button><el-button v-if="canFollow" v-hasPermi="followPerms" icon="el-icon-plus" @click="$emit('follow',lead)">新增跟进</el-button></div>
        <section><h3>基础信息</h3><dl><dt>客户名称</dt><dd>{{ lead.contactName || '-' }}</dd><dt>线索名称</dt><dd>{{ lead.leadName || '-' }}</dd><dt>联系电话</dt><dd>{{ lead.mobile || '-' }}</dd><dt>微信号</dt><dd>{{ lead.wechat || '-' }}</dd><dt>单位名称</dt><dd>{{ lead.companyName || '-' }}</dd><dt>负责人</dt><dd>{{ lead.ownerName || '待领取' }}</dd></dl></section>
        <section><h3>法律需求</h3><dl><dt>需求描述</dt><dd class="wide">{{ lead.legalDemand || '暂未填写需求描述' }}</dd><dt>预计金额</dt><dd>{{ amount }}</dd><dt>下次跟进</dt><dd>{{ lead.nextFollowTime || '-' }}</dd><dt>备注</dt><dd class="wide">{{ lead.remark || '-' }}</dd></dl></section>
        <section><h3>分配信息</h3><dl><dt>当前负责人</dt><dd>{{ lead.ownerName || '待领取' }}</dd><dt>所属部门</dt><dd>{{ lead.deptName || '-' }}</dd><dt>公海状态</dt><dd>{{ poolStatusName || '-' }}</dd><dt>最后跟进</dt><dd>{{ lead.lastFollowTime || '-' }}</dd></dl></section>
        <business-todo-summary v-if="Number(lead.leadId) > 0" business-type="LEAD" :business-id="lead.leadId" :business-no="lead.leadNo" />
      </main>
      <aside>
        <div class="timeline-head"><div><h2>跟进记录</h2><p>共 {{ followups.length }} 条沟通记录</p></div><el-button type="text" icon="el-icon-refresh" @click="$emit('refresh')" /></div>
        <div v-if="followups.length" class="timeline">
          <article v-for="item in followups" :key="item.followupId">
            <i :class="'type-' + item.followType"><svg-icon :icon-class="followIcon(item.followType)" /></i>
            <div><h4>{{ followType(item.followType) }} <small>{{ item.followUserName || '-' }}</small></h4><p>{{ item.content || '暂无详细记录' }}</p><span>{{ item.createTime || '-' }}</span><em v-if="item.nextFollowTime">下次跟进：{{ item.nextFollowTime }}</em></div>
          </article>
        </div>
        <el-empty v-else description="暂无跟进记录" :image-size="64" />
        <el-button v-if="canFollow" v-hasPermi="followPerms" class="follow-button" type="primary" icon="el-icon-plus" @click="$emit('follow',lead)">新增跟进</el-button>
      </aside>
    </div>
  </el-drawer>
</template>

<script>
import BusinessTodoSummary from '@/views/todo/components/BusinessTodoSummary'
export default {
  name:'LeadDetailDrawer',
  components: { BusinessTodoSummary },
  props:{value:Boolean,lead:{type:Object,default:null},followups:{type:Array,default:()=>[]},sourceName:String,status:{type:Object,default:()=>({})},priorityName:String,poolStatusName:String,followTypeOptions:{type:Array,default:()=>[]},followPerms:{type:Array,default:()=>['lead:followup:add']},sizeClass:{type:String,default:'lead-size-medium'}},
  computed:{visible:{get(){return this.value},set(value){this.$emit('input',value)}},initial(){return(this.lead.contactName||this.lead.leadName||'线').slice(0,1)},amount(){return this.lead.estimatedAmount ? '¥ ' + Number(this.lead.estimatedAmount).toLocaleString() : '-'},terminal(){return ['3','4','5'].includes(this.lead.status)},inPool(){return this.lead.poolStatus === '1'},canEdit(){return !this.terminal},canAssign(){return !this.terminal},canFollow(){return !this.terminal && !this.inPool && !!this.lead.ownerId}},
  methods:{maskMobile(value){return value ? value.replace(/(\d{3})\d{4}(\d{4})/,'$1****$2') : '未填写电话'},followType(type){const item=this.followTypeOptions.find(option=>String(option.value)===String(type));return item?item.label:(type||'-')},followIcon(type){return({phone:'phone',wechat:'wechat',meeting:'peoples',email:'email'})[type]||'message'}}
}
</script>

<style lang="scss">
.lead-detail-drawer{max-width:96%;.el-drawer__body{overflow:auto;background:#f7f9fc}}.detail-shell{display:grid;grid-template-columns:1.35fr .9fr;min-height:100%}.detail-shell main{position:relative;padding:23px}.drawer-close{position:absolute;right:18px;top:18px;border:0;color:#64748b;background:transparent;cursor:pointer}.detail-title{display:flex;align-items:center;gap:13px;padding-right:30px;.initial{display:flex;align-items:center;justify-content:center;width:54px;height:54px;border-radius:50%;color:#fff;background:linear-gradient(135deg,#668cff,#8b5cf6);font-size:23px}h2{margin:0;color:#172b4d;font-size:21px}p{margin:7px 0 0;color:#64748b;font-size:12px}i{display:inline-block;margin:0 7px;width:3px;height:3px;border-radius:50%;background:#94a3b8}.el-tag{margin-left:auto}}.summary-strip{display:flex;flex-wrap:wrap;gap:0;margin-top:16px;padding:11px;border:1px solid #e5eaf2;border-radius:7px;background:#fff;color:#94a3b8;font-size:11px;span{padding:0 13px;border-right:1px solid #edf1f7}span:last-child{border:0}b{color:#475569;font-weight:500}}.detail-actions{display:flex;gap:8px;margin:15px 0}.detail-shell section{margin-top:13px;border:1px solid #e5eaf2;border-radius:8px;background:#fff;overflow:hidden;h3{margin:0;padding:12px 15px;border-bottom:1px solid #edf1f7;color:#334155;font-size:15px}dl{display:grid;grid-template-columns:78px 1fr 78px 1fr;gap:12px 8px;padding:15px;margin:0;font-size:12px}dt{color:#94a3b8}dd{margin:0;color:#475569;line-height:1.6}.wide{grid-column:span 3}}.detail-shell aside{position:relative;padding:21px 18px 72px;border-left:1px solid #e5eaf2;background:#fff}.timeline-head{display:flex;align-items:start;justify-content:space-between;border-bottom:1px solid #edf1f7;h2{margin:0;color:#243858;font-size:19px}p{margin:6px 0 14px;color:#94a3b8;font-size:11px}}.timeline{padding-left:16px;border-left:1px solid #d8e3f6}.timeline article{position:relative;display:flex;gap:10px;margin:18px 0 23px;i{position:absolute;left:-27px;top:1px;display:flex;align-items:center;justify-content:center;width:21px;height:21px;border-radius:50%;color:#fff;background:#2563eb;font-size:11px}.type-wechat{background:#10b981}.type-meeting{background:#f59e0b}.type-email{background:#8b5cf6}h4{margin:0;color:#334155;font-size:13px}small{margin-left:7px;color:#64748b;font-weight:400}p{margin:8px 0;color:#64748b;font-size:12px;line-height:1.7}span,em{display:block;margin-top:5px;color:#94a3b8;font-size:10px;font-style:normal}em{color:#6182b6}}.follow-button{position:absolute;right:18px;bottom:18px;left:18px;width:calc(100% - 36px)}
.lead-detail-drawer{--lead-font-base:13px;--lead-font-small:12px;--lead-font-mini:11px;--lead-font-title:21px;--lead-font-section:15px;--lead-font-timeline:19px}.lead-detail-drawer.lead-size-default{--lead-font-base:14px;--lead-font-small:13px;--lead-font-mini:12px;--lead-font-title:23px;--lead-font-section:16px;--lead-font-timeline:20px}.lead-detail-drawer.lead-size-small{--lead-font-base:12px;--lead-font-small:11px;--lead-font-mini:10px;--lead-font-title:20px;--lead-font-section:14px;--lead-font-timeline:18px}.lead-detail-drawer.lead-size-mini{--lead-font-base:11px;--lead-font-small:10px;--lead-font-mini:9px;--lead-font-title:19px;--lead-font-section:13px;--lead-font-timeline:17px}.detail-title h2{font-size:var(--lead-font-title)}.detail-title p,.detail-shell section dl,.timeline article p{font-size:var(--lead-font-small)}.summary-strip,.timeline-head p,.timeline article span,.timeline article em{font-size:var(--lead-font-mini)}.detail-shell section h3{font-size:var(--lead-font-section)}.timeline-head h2{font-size:var(--lead-font-timeline)}.timeline article h4{font-size:var(--lead-font-base)}
@media(max-width:760px){.lead-detail-drawer{width:96%!important}.detail-shell{display:block}.detail-shell aside{border-top:1px solid #e5eaf2;border-left:0}.detail-shell section dl{grid-template-columns:72px 1fr}.detail-shell section .wide{grid-column:span 1}.summary-strip span{margin:4px 0}}
</style>
