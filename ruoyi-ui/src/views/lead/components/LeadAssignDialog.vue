<template>
  <el-dialog title="线索分配" :visible.sync="visible" width="760px" append-to-body :custom-class="'lead-assign-dialog ' + sizeClass" @close="$emit('close')">
    <div class="lead-summary">
      <div><span>线索编号</span><strong>{{ lead.leadNo || '-' }}</strong></div><div><span>客户名称</span><strong>{{ lead.contactName || lead.leadName || '-' }}</strong></div><div><span>来源</span><strong>{{ sourceName }}</strong></div><div><span>当前状态</span><strong>{{ statusName }}</strong></div>
    </div>
    <div class="assign-layout">
      <div>
        <h4>选择负责人 <em>*</em></h4>
        <el-select v-model="form.ownerId" filterable placeholder="请选择负责人">
          <el-option v-for="item in owners" :key="item.userId" :label="item.nickName + ' (' + item.userName + ')'" :value="item.userId" />
        </el-select>
        <h4>分配优先级</h4>
        <div class="priority-readonly"><i :class="'level-' + lead.priority" />{{ priorityName }}<small>优先级沿用当前线索设置，可在线索编辑中调整</small></div>
        <h4>分配原因</h4>
        <el-input v-model="form.reason" type="textarea" :rows="4" placeholder="请输入分配说明，便于后续跟进人员了解背景" maxlength="200" show-word-limit />
      </div>
      <aside>
        <h4>可分配人员</h4>
        <p>当前共 {{ owners.length }} 位可选负责人</p>
        <div v-for="item in owners.slice(0, 5)" :key="item.userId" :class="{ selected: form.ownerId === item.userId }" class="owner-card" @click="form.ownerId=item.userId">
          <span>{{ avatar(item) }}</span><div><strong>{{ item.nickName }}</strong><small>{{ item.dept ? item.dept.deptName : (item.deptName || '业务团队') }}</small></div><i class="el-icon-check" />
        </div>
      </aside>
    </div>
    <div slot="footer"><el-button @click="visible=false">取消</el-button><el-button type="primary" :disabled="!form.ownerId" @click="$emit('submit', form)">确认分配</el-button></div>
  </el-dialog>
</template>

<script>
export default {
  name: 'LeadAssignDialog',
  props: { value: Boolean, lead: { type:Object,default:()=>({}) }, owners:{type:Array,default:()=>[]}, sourceName:String, statusName:String, priorityName:String, sizeClass:{type:String,default:'lead-size-medium'} },
  data(){return{form:{leadId:null,ownerId:null,reason:''}}},
  computed:{ visible:{get(){return this.value},set(value){this.$emit('input',value)}} },
  watch:{ value(open){if(open)this.form={leadId:this.lead.leadId,ownerId:this.lead.ownerId,reason:''}} },
  methods:{avatar(item){return(item.nickName||item.userName||'人').slice(0,1)}}
}
</script>

<style lang="scss">
.lead-assign-dialog{border-radius:12px;overflow:hidden}.lead-assign-dialog .el-dialog__header{padding:20px 23px;border-bottom:1px solid #edf1f7}.lead-assign-dialog .el-dialog__body{padding:18px 23px}.lead-summary{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;padding:15px;border:1px solid #e8edf5;border-radius:9px;background:#fbfcff;span,strong{display:block}span{margin-bottom:7px;color:#94a3b8;font-size:11px}strong{color:#334155;font-size:13px}}.assign-layout{display:grid;grid-template-columns:1.25fr .85fr;gap:20px;margin-top:16px;h4{margin:12px 0 8px;color:#334155;font-size:13px}em{color:#ef4444}.el-select{width:100%}aside{padding:0 13px;border-left:1px solid #edf1f7}aside p{margin:-2px 0 10px;color:#94a3b8;font-size:11px}.priority-readonly{display:flex;align-items:center;gap:8px;padding:11px;border:1px solid #e5eaf2;border-radius:6px;color:#475569;font-size:13px;i{width:9px;height:9px;border-radius:50%}small{margin-left:auto;color:#94a3b8;font-size:10px}.level-1{background:#ef4444}.level-2{background:#f59e0b}.level-3{background:#10b981}}.owner-card{display:grid;grid-template-columns:34px 1fr 16px;gap:9px;align-items:center;margin-top:8px;padding:9px;border:1px solid #e8edf5;border-radius:7px;cursor:pointer;span{display:flex;align-items:center;justify-content:center;width:32px;height:32px;border-radius:50%;color:#2563eb;background:#eef4ff;font-weight:700}strong,small{display:block}strong{color:#334155;font-size:12px}small{margin-top:3px;color:#94a3b8;font-size:10px}.el-icon-check{display:none;color:#2563eb}}.owner-card.selected{border-color:#8eb5ff;background:#f4f8ff;.el-icon-check{display:block}}}
.lead-assign-dialog{--lead-font-base:13px;--lead-font-small:12px;--lead-font-mini:11px}.lead-assign-dialog.lead-size-default{--lead-font-base:14px;--lead-font-small:13px;--lead-font-mini:12px}.lead-assign-dialog.lead-size-small{--lead-font-base:12px;--lead-font-small:11px;--lead-font-mini:10px}.lead-assign-dialog.lead-size-mini{--lead-font-base:11px;--lead-font-small:10px;--lead-font-mini:9px}.lead-summary span,.assign-layout aside p{font-size:var(--lead-font-mini)}.lead-summary strong,.assign-layout h4,.priority-readonly{font-size:var(--lead-font-base)}.priority-readonly small,.owner-card small{font-size:var(--lead-font-mini)}.owner-card strong{font-size:var(--lead-font-small)}
@media(max-width:700px){.lead-assign-dialog{width:94%!important}.lead-summary{grid-template-columns:1fr 1fr}.assign-layout{grid-template-columns:1fr}.assign-layout aside{display:none}}
</style>
