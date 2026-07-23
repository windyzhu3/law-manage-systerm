<template>
  <config-detail-drawer
    ref="drawer"
    :visible="visible"
    :title="title"
    size="720px"
    :dirty="dirty"
    @update:visible="$emit('update:visible', $event)"
    @reset="reset"
  >
    <el-form ref="form" :model="form" :rules="rules" label-width="110px" class="resource-item-form">
      <el-alert
        :title="guidance"
        type="info"
        :closable="false"
        show-icon
      />
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="资源编码" prop="resourceCode">
            <el-input
              v-model.trim="form.resourceCode"
              :disabled="readonly || persisted"
              placeholder="如 CONTRACT_AMOUNT"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="中文名称" prop="resourceName">
            <el-input v-model.trim="form.resourceName" :disabled="readonly" placeholder="如 合同金额" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="业务类型" prop="businessType">
        <el-select v-model="form.businessType" :disabled="readonly || persisted" class="full-width">
          <el-option v-for="item in businessTypes" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="用途说明">
        <el-input v-model.trim="form.description" :disabled="readonly" placeholder="说明员工在什么业务场景会看到或使用该资源" />
      </el-form-item>

      <template v-if="resourceType === 'FIELD'">
        <el-divider content-position="left">字段规则</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="字段类型" prop="fieldType">
              <el-select v-model="form.fieldType" :disabled="readonly" class="full-width">
                <el-option label="文本" value="string" />
                <el-option label="整数" value="integer" />
                <el-option label="数字" value="number" />
                <el-option label="是/否" value="boolean" />
                <el-option label="对象" value="object" />
                <el-option label="列表" value="array" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="字段属性">
              <el-checkbox v-model="form.required" :disabled="readonly">业务必填</el-checkbox>
              <el-checkbox v-model="form.sensitive" :disabled="readonly">敏感字段</el-checkbox>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="可选业务值">
          <el-select
            v-model="form.options"
            :disabled="readonly"
            multiple
            filterable
            allow-create
            default-first-option
            class="full-width"
            placeholder="可选；输入后回车添加"
          />
        </el-form-item>
      </template>

      <template v-else-if="resourceType === 'DOD_RECIPE'">
        <el-divider content-position="left">完成条件配方</el-divider>
        <el-form-item label="适用动作">
          <el-select v-model="form.businessActions" :disabled="readonly" multiple filterable allow-create class="full-width" placeholder="选择或录入业务动作" />
        </el-form-item>
        <el-form-item label="适用阶段">
          <el-select v-model="form.templateStages" :disabled="readonly" multiple filterable allow-create class="full-width" placeholder="选择或录入模板阶段" />
        </el-form-item>
        <el-form-item label="必填字段">
          <el-select v-model="form.requiredFields" :disabled="readonly" multiple filterable class="full-width" placeholder="选择员工必须填写的字段">
            <el-option v-for="item in fieldOptions" :key="item.code" :label="item.name" :value="item.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="必交材料">
          <el-select v-model="form.requiredAttachments" :disabled="readonly" multiple filterable class="full-width" placeholder="选择员工必须提交的材料">
            <el-option v-for="item in materialOptions" :key="item.code" :label="item.name" :value="item.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务校验">
          <el-select v-model="form.validatorRefs" :disabled="readonly" multiple filterable class="full-width" placeholder="可选；仅展示可用校验器">
            <el-option v-for="item in selectableValidators" :key="item.code" :label="item.name" :value="item.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="员工提示">
          <el-select v-model="form.employeeInstructions" :disabled="readonly" multiple filterable allow-create class="full-width" placeholder="输入一句完成提示后回车添加" />
        </el-form-item>
        <el-form-item label="推荐优先级">
          <el-input-number v-model="form.recommendationPriority" :disabled="readonly" :min="0" controls-position="right" />
        </el-form-item>
      </template>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="状态">
            <el-switch v-model="form.status" :disabled="readonly" active-value="ACTIVE" inactive-value="DISABLED" active-text="启用" inactive-text="停用" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="显示顺序">
            <el-input-number v-model="form.sortOrder" :disabled="readonly" :min="0" controls-position="right" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <template #footer>
      <el-button @click="$refs.drawer.requestProgrammaticClose()">关闭</el-button>
      <el-button v-if="!readonly" type="primary" :loading="saving" @click="save">保存并返回</el-button>
    </template>
  </config-detail-drawer>
</template>

<script>
import ConfigDetailDrawer from '../shared/ConfigDetailDrawer'
import { createConfigurationResource, updateConfigurationResource } from '@/api/todo-resources'

const emptyForm = () => ({
  resourceItemId: null,
  resourceCode: '',
  resourceName: '',
  description: '',
  businessType: 'LEAD',
  status: 'ACTIVE',
  sortOrder: 0,
  version: 0,
  fieldType: 'string',
  required: false,
  sensitive: false,
  options: [],
  businessActions: [],
  templateStages: [],
  recommendationPriority: 0,
  requiredFields: [],
  requiredAttachments: [],
  validatorRefs: [],
  employeeInstructions: []
})

export default {
  name: 'ResourceItemDrawer',
  components: { ConfigDetailDrawer },
  props: {
    visible: Boolean,
    resourceType: { type: String, required: true },
    businessType: { type: String, default: 'LEAD' },
    item: { type: Object, default: null },
    readonly: Boolean,
    fieldOptions: { type: Array, default: () => [] },
    materialOptions: { type: Array, default: () => [] },
    validatorOptions: { type: Array, default: () => [] },
    focusField: { type: String, default: '' }
  },
  data() {
    return {
      form: emptyForm(),
      initialSnapshot: '',
      saving: false,
      businessTypes: [
        { value: 'LEAD', label: '线索' },
        { value: 'CUSTOMER', label: '客户' },
        { value: 'CONTRACT', label: '合同' },
        { value: 'CASE', label: '案件' },
        { value: 'MATTER', label: '事项' }
      ],
      rules: {
        resourceCode: [
          { required: true, message: '请输入资源编码', trigger: 'blur' },
          { pattern: /^[A-Z][A-Z0-9_.-]{1,63}$/, message: '编码以大写字母开头，可包含数字、点、下划线或连字符', trigger: 'blur' }
        ],
        resourceName: [{ required: true, message: '请输入中文名称', trigger: 'blur' }],
        businessType: [{ required: true, message: '请选择业务类型', trigger: 'change' }],
        fieldType: [{ required: true, message: '请选择字段类型', trigger: 'change' }]
      }
    }
  },
  computed: {
    persisted() { return Boolean(this.form.resourceItemId) },
    dirty() { return !this.readonly && Boolean(this.initialSnapshot) && this.snapshot() !== this.initialSnapshot },
    typeLabel() { return ({ FIELD: '业务字段', MATERIAL: '材料类型', DOD_RECIPE: '完成条件配方' })[this.resourceType] || '业务资源' },
    title() { return `${this.persisted ? '编辑' : '新增'}${this.typeLabel}` },
    guidance() {
      return ({
        FIELD: '字段用于触发条件、完成条件和模拟数据展示；普通用户只会看到中文名称。',
        MATERIAL: '材料类型用于约束员工完成待办时必须提交的业务资料。',
        DOD_RECIPE: '配方把常用字段、材料和业务校验组合成可复用的完成标准。'
      })[this.resourceType]
    },
    selectableValidators() { return this.validatorOptions.filter(item => item.selectable) }
  },
  watch: {
    visible(open) { if (open) this.hydrate() },
    item: { deep: true, handler() { if (this.visible) this.hydrate() } }
  },
  methods: {
    snapshot() { return JSON.stringify(this.form) },
    reset() { this.form = emptyForm(); this.initialSnapshot = '' },
    hydrate() {
      const source = this.item || {}
      this.form = {
        ...emptyForm(),
        ...source,
        resourceItemId: source.resourceItemId || source.resource_item_id || null,
        resourceCode: source.resourceCode || source.resource_code || source.code || '',
        resourceName: source.resourceName || source.resource_name || source.name || '',
        businessType: source.businessType || source.business_type || this.businessType || 'LEAD',
        sortOrder: Number(source.sortOrder !== undefined ? source.sortOrder : source.sort_order || 0),
        version: Number(source.version || 0),
        fieldType: source.fieldType || source.type || 'string',
        options: Array.isArray(source.options) ? source.options.slice() : [],
        businessActions: (source.businessActions || []).slice(),
        templateStages: (source.templateStages || []).slice(),
        requiredFields: (source.requiredFields || []).slice(),
        requiredAttachments: (source.requiredAttachments || []).slice(),
        validatorRefs: (source.validatorRefs || []).slice(),
        employeeInstructions: (source.employeeInstructions || []).slice()
      }
      this.initialSnapshot = this.snapshot()
      this.$nextTick(() => this.focus(this.focusField))
    },
    valueObject() {
      if (this.resourceType === 'FIELD') {
        return {
          type: this.form.fieldType,
          required: Boolean(this.form.required),
          sensitive: Boolean(this.form.sensitive),
          options: this.form.options || []
        }
      }
      if (this.resourceType === 'DOD_RECIPE') {
        return {
          businessActions: this.form.businessActions || [],
          templateStages: this.form.templateStages || [],
          recommendationPriority: Number(this.form.recommendationPriority || 0),
          requiredFields: this.form.requiredFields || [],
          requiredAttachments: this.form.requiredAttachments || [],
          validatorRefs: this.form.validatorRefs || [],
          conditionalRules: [],
          employeeInstructions: this.form.employeeInstructions || []
        }
      }
      return {}
    },
    payload() {
      return {
        resourceItemId: this.form.resourceItemId,
        resourceType: this.resourceType,
        resourceCode: this.form.resourceCode,
        resourceName: this.form.resourceName,
        description: this.form.description,
        businessType: this.form.businessType,
        valueJson: JSON.stringify(this.valueObject()),
        status: this.form.status,
        sortOrder: Number(this.form.sortOrder || 0),
        actionId: `resource-item-${Date.now()}-${Math.random().toString(16).slice(2)}`,
        expectedVersion: Number(this.form.version || 0)
      }
    },
    async save() {
      if (this.saving) return
      const valid = await new Promise(resolve => this.$refs.form.validate(ok => resolve(ok)))
      if (!valid) return
      this.saving = true
      try {
        const payload = this.payload()
        const response = this.persisted
          ? await updateConfigurationResource(this.form.resourceItemId, payload)
          : await createConfigurationResource(payload)
        this.$modal.msgSuccess(`${this.typeLabel}已保存`)
        this.$emit('saved', response.data)
        this.$refs.drawer.closeAfterSave()
      } catch (error) {
        this.$modal.msgError((error && (error.msg || error.message)) || `${this.typeLabel}保存失败`)
      } finally {
        this.saving = false
      }
    },
    focus(field) {
      if (!field || !this.$refs.form) return
      const target = this.$refs.form.$el.querySelector(`[for="${field}"]`)
      if (target && target.scrollIntoView) target.scrollIntoView({ behavior: 'smooth', block: 'center' })
    }
  }
}
</script>

<style scoped lang="scss">
.resource-item-form > .el-alert {
  margin-bottom: 18px;
}

.full-width {
  width: 100%;
}

@media (max-width: 640px) {
  .resource-item-form ::v-deep .el-col {
    width: 100%;
  }
}
</style>
