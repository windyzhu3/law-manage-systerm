<template><div>
    <el-drawer :visible="nodeOpen" @close="$emit('update:nodeOpen', false)" size="560px" custom-class="matter-side-drawer" append-to-body>
      <div slot="title" class="drawer-title" :class="'biz-size-' + appSize"><span>CASE NODE</span><strong>{{ nodeForm.nodeId ? '编辑节点' : '新增节点' }}</strong></div>
      <el-form ref="node" :model="nodeForm" :rules="nodeRules" label-width="100px" class="drawer-form">
        <el-form-item label="所属案件" prop="caseId"><el-select v-model="nodeForm.caseId" :size="controlSize" filterable remote reserve-keyword placeholder="搜索案件" :remote-method="searchMatterOptions" :loading="matterSelectLoading"><el-option v-for="item in matterOptions" :key="item.case_id" :label="item.case_name" :value="item.case_id" /></el-select></el-form-item>
        <el-form-item label="节点名称" prop="nodeName"><el-input v-model="nodeForm.nodeName" :size="controlSize" /></el-form-item>
        <el-form-item label="节点类型" prop="nodeType"><el-select v-model="nodeForm.nodeType" :size="controlSize" @change="handleNodeTypeChange"><el-option v-for="item in dictOptions.law_case_node_type" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="节点状态" prop="nodeStatus"><el-select v-model="nodeForm.nodeStatus" :size="controlSize"><el-option v-for="item in dictOptions.law_case_node_status" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="计划日期" prop="planDate"><el-date-picker v-model="nodeForm.planDate" :size="controlSize" value-format="yyyy-MM-dd" /></el-form-item>
        <el-form-item label="实际日期"><el-date-picker v-model="nodeForm.actualDate" :size="controlSize" value-format="yyyy-MM-dd" /></el-form-item>
        <el-form-item label="法院/地点"><el-input v-model="nodeForm.courtPlace" :size="controlSize" /></el-form-item>
        <el-form-item label="法庭/庭号"><el-input v-model="nodeForm.courtRoom" :size="controlSize" /></el-form-item>
        <el-form-item label="材料清单">
          <matter-material-list v-model="nodeForm.materials" :options="dictOptions.law_case_material_status" :control-size="controlSize" :default-status="dictDefault('law_case_material_status') || 'pending'" show-upload @input="markNodeMaterialsCustom" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="nodeForm.remark" :size="controlSize" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <div class="drawer-footer"><el-button :size="controlSize" @click="$emit('update:nodeOpen', false)">取消</el-button><el-button :size="controlSize" type="primary" @click="saveNode">保存</el-button></div>
    </el-drawer>

    <el-drawer :visible="archiveOpen" @close="$emit('update:archiveOpen', false)" size="560px" custom-class="matter-side-drawer" append-to-body>
      <div slot="title" class="drawer-title" :class="'biz-size-' + appSize"><span>CASE ARCHIVE</span><strong>结案归档</strong></div>
      <el-form ref="archive" :model="archiveForm" :rules="archiveRules" label-width="110px" class="drawer-form">
        <el-form-item label="结案结果" prop="closeResult"><el-radio-group v-model="archiveForm.closeResult"><el-radio v-for="item in dictOptions.law_case_close_result" :key="item.value" :label="item.value">{{ item.label }}</el-radio></el-radio-group></el-form-item>
        <el-form-item label="结案日期" prop="closeDate"><el-date-picker v-model="archiveForm.closeDate" :size="controlSize" value-format="yyyy-MM-dd" /></el-form-item>
        <el-form-item label="实际回款"><el-input-number v-model="archiveForm.actualReceivedAmount" :size="controlSize" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="费用结清"><el-radio-group v-model="archiveForm.feeClearStatus"><el-radio v-for="item in dictOptions.law_case_fee_clear_status" :key="item.value" :label="item.value">{{ item.label }}</el-radio></el-radio-group></el-form-item>
        <el-form-item label="客户满意度"><el-rate v-model="archiveForm.satisfaction" /></el-form-item>
        <el-form-item label="办案总结" prop="summary"><el-input v-model="archiveForm.summary" :size="controlSize" type="textarea" :rows="5" /></el-form-item>
        <el-form-item label="归档资料">
          <matter-material-list v-model="archiveForm.materials" :options="dictOptions.law_case_material_status" :control-size="controlSize" :default-status="dictDefault('law_case_material_status') || 'pending'" show-upload />
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
      <div class="drawer-footer"><el-button :size="controlSize" @click="$emit('update:archiveOpen', false)">取消</el-button><el-button v-if="archiveForm.caseStatus === 'processing'" v-hasPermi="['matter:archive:apply']" :size="controlSize" type="primary" @click="submitArchive">提交结案</el-button><el-button v-if="archiveForm.caseStatus === 'closing'" v-hasPermi="['matter:archive:confirm']" :size="controlSize" type="warning" @click="confirmCloseSubmit">确认结案</el-button><el-button v-if="archiveForm.caseStatus === 'closed'" v-hasPermi="['matter:archive:confirm']" :size="controlSize" type="success" @click="confirmArchiveSubmit">确认归档</el-button></div>
    </el-drawer>
</div></template>
<script>
export default {name:'MatterWorkflowDrawers',components:{MatterMaterialList:()=>import('./MatterMaterialList')},props:{nodeOpen:Boolean,archiveOpen:Boolean,nodeForm:Object,nodeRules:Object,archiveForm:Object,archiveRules:Object,matterOptions:Array,matterSelectLoading:Boolean,dictOptions:Object,controlSize:String,appSize:String,searchMatterOptions:Function,saveNode:Function,submitArchive:Function,confirmCloseSubmit:Function,confirmArchiveSubmit:Function,dictDefault:Function,dictLabel:Function,archiveMaterialStats:Object,archiveConfirmEnabled:Boolean},methods:{validate(type,cb){const f=this.$refs[type];if(f)f.validate(cb)},clear(type){const f=this.$refs[type];if(f)f.clearValidate()}}}
</script>

