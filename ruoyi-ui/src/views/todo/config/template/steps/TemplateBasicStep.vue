<template>
  <el-form ref="form" :model="model" :rules="rules" label-width="110px">
    <el-row :gutter="16">
      <el-col :span="12"><el-form-item label="模板编码" prop="templateCode"><el-input v-model.trim="model.templateCode" :disabled="readonly || persisted" maxlength="64" /></el-form-item></el-col>
      <el-col :span="12"><el-form-item label="模板名称" prop="templateName"><el-input v-model.trim="model.templateName" :disabled="readonly" maxlength="128" /></el-form-item></el-col>
    </el-row>
    <el-row :gutter="16">
      <el-col :span="8"><el-form-item label="业务类型" prop="businessType"><el-select v-model="model.businessType" :disabled="readonly" class="full"><el-option v-for="item in dict.type.law_todo_business_type" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
      <el-col :span="8"><el-form-item label="业务阶段" prop="businessStage"><el-select v-model="model.businessStage" :disabled="readonly" class="full"><el-option v-for="item in dict.type.law_todo_business_stage" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
      <el-col :span="8"><el-form-item label="模板类型" prop="templateType"><el-select v-model="model.templateType" :disabled="readonly" class="full"><el-option v-for="item in dict.type.law_todo_template_type" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
    </el-row>
    <el-row :gutter="16">
      <el-col :span="8"><el-form-item label="默认优先级" prop="priority"><el-select v-model="model.priority" :disabled="readonly" class="full"><el-option v-for="item in dict.type.law_todo_priority" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
      <el-col :span="16"><el-form-item label="变更摘要" prop="changeSummary"><el-input v-model.trim="model.changeSummary" :disabled="readonly" maxlength="256" /></el-form-item></el-col>
    </el-row>
    <el-form-item label="影响范围"><el-input v-model.trim="model.impactScope" :disabled="readonly" type="textarea" :rows="2" maxlength="512" /></el-form-item>
    <el-form-item label="说明"><el-input v-model.trim="model.description" :disabled="readonly" type="textarea" :rows="3" maxlength="1000" /></el-form-item>
  </el-form>
</template>

<script>
export default {
  name: 'TemplateBasicStep',
  dicts: ['law_todo_business_stage', 'law_todo_business_type', 'law_todo_template_type', 'law_todo_priority'],
  props: { value: { type: Object, required: true }, readonly: Boolean, persisted: Boolean },
  data() { return { syncing: false, model: { ...this.value }, rules: { templateCode: [{ required: true, message: '请输入模板编码', trigger: 'blur' }], templateName: [{ required: true, message: '请输入模板名称', trigger: 'blur' }], businessType: [{ required: true, message: '请选择业务类型', trigger: 'change' }], businessStage: [{ required: true, message: '请选择业务阶段', trigger: 'change' }], templateType: [{ required: true, message: '请选择模板类型', trigger: 'change' }], priority: [{ required: true, message: '请选择优先级', trigger: 'change' }] } } },
  watch: { value: { deep: true, handler(value) { if (JSON.stringify(value) === JSON.stringify(this.model)) return; this.syncing = true; this.model = { ...value }; this.$nextTick(() => { this.syncing = false }) } }, model: { deep: true, handler(value) { if (this.syncing) return; this.$emit('input', { ...value }); this.$emit('dirty-change') } } },
  methods: { validate() { return new Promise(resolve => this.$refs.form.validate(valid => resolve(valid))) } }
}
</script>
<style scoped>.full{width:100%}</style>
