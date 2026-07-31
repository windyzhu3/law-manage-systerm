<template>
  <section class="journey-step dod-step">
    <header class="journey-step__header">
      <span class="journey-step__eyebrow">第四步 · 完成标准</span>
      <h2>员工做到什么，才算完成这张待办？</h2>
      <p>先采用推荐业务配方，再按实际办理要求选择需要填写的信息、上传的材料和条件要求。</p>
    </header>

    <div ref="recipeCard" tabindex="-1">
    <dod-recipe-picker
      :recipes="rankedRecipes"
      :selected-code="config.recipeCode || ''"
      :readonly="readonly"
      @select="applyRecipe"
    />
    </div>

    <div class="dod-step__sections">
      <section class="dod-config-card">
        <header><i class="el-icon-edit-outline" /><div><h3>员工需要填写</h3><p>完成待办前必须补充的业务信息。</p></div></header>
        <el-select
          ref="requiredFields"
          v-model="requiredFields"
          multiple
          filterable
          :disabled="readonly"
          placeholder="选择必填业务信息"
          @change="commitGoverned"
        >
          <el-option v-for="field in activeFields" :key="field.code" :label="field.name" :value="field.code">
            <span>{{ field.name }}</span><small>{{ field.description || field.type }}</small>
          </el-option>
        </el-select>
        <div class="dod-config-card__summary">
          <span v-for="code in requiredFields" :key="code"><i class="el-icon-circle-check" />{{ fieldName(code) }}</span>
        </div>
      </section>

      <section class="dod-config-card">
        <header><i class="el-icon-paperclip" /><div><h3>员工需要上传</h3><p>完成待办时必须提交的材料或凭证。</p></div></header>
        <el-select
          ref="requiredAttachments"
          v-model="requiredAttachments"
          multiple
          filterable
          :disabled="readonly"
          placeholder="选择必传材料"
          @change="commitGoverned"
        >
          <el-option v-for="material in activeMaterials" :key="material.code" :label="material.name" :value="material.code" />
        </el-select>
        <div class="dod-config-card__summary">
          <span v-for="code in requiredAttachments" :key="code"><i class="el-icon-document" />{{ materialName(code) }}</span>
        </div>
      </section>
    </div>

    <el-collapse v-model="activeAdvanced" class="dod-advanced">
      <el-collapse-item name="advanced">
        <template slot="title"><i class="el-icon-setting" />高级设置（可选）· 条件、说明与系统校验</template>
    <section ref="conditionalRules" class="dod-condition-card" tabindex="-1">
      <header>
        <div><h3>条件要求</h3><p>仅在特定业务结果下，额外要求员工填写信息。</p></div>
        <el-button v-if="!readonly" size="small" icon="el-icon-plus" @click="addCondition">添加条件要求</el-button>
      </header>
      <div v-for="(rule, index) in conditionalRules" :key="index" class="dod-condition-row">
        <span>当</span>
        <el-select v-model="rule.when.field" :disabled="readonly" filterable placeholder="选择判断字段" @change="commitConditions">
          <el-option v-for="field in activeFields" :key="field.code" :label="field.name" :value="field.code" />
        </el-select>
        <span>等于</span>
        <el-input v-model.trim="rule.when.equals" :disabled="readonly" placeholder="填写业务值" @change="commitConditions" />
        <span>时，必须填写</span>
        <el-select v-model="rule.field" :disabled="readonly" filterable placeholder="选择必填信息" @change="commitConditions">
          <el-option v-for="field in activeFields" :key="field.code" :label="field.name" :value="field.code" />
        </el-select>
        <el-button v-if="!readonly" type="text" class="is-danger" @click="removeCondition(index)">删除</el-button>
      </div>
      <el-empty v-if="!conditionalRules.length" description="没有附加条件要求" :image-size="58" />
    </section>

    <section class="dod-instructions">
      <h3>办理说明</h3>
      <p>这些提示会直接展示给员工，帮助其一次完成办理。</p>
      <el-select
        ref="employeeInstructions"
        v-model="employeeInstructions"
        multiple
        filterable
        allow-create
        default-first-option
        :disabled="readonly"
        placeholder="输入一句办理提示后回车添加"
        @change="commitInstructions"
      />
    </section>

        <div class="dod-validator-settings">
        <p>以下能力由管理员维护。业务人员通常只需使用推荐配方，无需理解技术编码与参数。</p>
        <el-select
          ref="validatorRefs"
          v-model="validatorRefs"
          multiple
          filterable
          :disabled="readonly"
          placeholder="选择可用的系统校验能力"
          @change="commitAdvanced"
        >
          <el-option v-for="validator in selectableValidators" :key="validator.code" :label="validator.name || validator.code" :value="validator.code">
            <span>{{ validator.name || '自动校验' }}</span>
            <small>{{ validator.description || validator.code }} · {{ validator.code }}</small>
          </el-option>
        </el-select>
        <div v-for="validator in selectedValidators" :key="validator.code" class="dod-validator">
          <strong>{{ validator.name || validator.code }}</strong>
          <span>{{ validator.description || '系统将在员工提交时自动检查。' }}</span>
          <small>技术编码：{{ validator.code }}<template v-if="validator.parameterSchema"> · 参数结构由管理员治理</template></small>
        </div>
        </div>
      </el-collapse-item>
    </el-collapse>
  </section>
</template>

<script>
import DodRecipePicker from '../components/DodRecipePicker'
import {
  rankDodRecipes,
  normalizeDodConfig,
  hydrateDodConditions,
  createDodCondition,
  materializeDodRecipe,
  updateGovernedDod
} from '../journey-step-model'

const clone = value => JSON.parse(JSON.stringify(value == null ? [] : value))

export default {
  name: 'DodStep',
  components: { DodRecipePicker },
  props: {
    value: { type: Object, default: () => ({}) },
    resources: { type: Object, default: () => ({}) },
    businessType: { type: String, default: '' },
    businessStage: { type: String, default: '' },
    event: { type: Object, default: () => ({}) },
    readonly: Boolean
  },
  data() {
    return {
      requiredFields: [],
      requiredAttachments: [],
      conditionalRules: [],
      validatorRefs: [],
      employeeInstructions: [],
      activeAdvanced: []
    }
  },
  computed: {
    config() { return this.value.config || {} },
    activeFields() { return (this.resources.fields || []).filter(item => item.status !== 'DISABLED') },
    activeMaterials() { return (this.resources.materials || []).filter(item => item.status !== 'DISABLED') },
    selectableValidators() {
      return (this.resources.validators || []).filter(item => item.selectable !== false && item.status !== 'DISABLED')
    },
    selectedValidators() {
      return this.validatorRefs.map(code => this.selectableValidators.find(item => item.code === code) || { code })
    },
    rankedRecipes() {
      return rankDodRecipes(this.resources.recipes || [], {
        businessType: this.businessType,
        businessAction: this.event.eventType,
        templateStage: this.businessStage
      })
    }
  },
  watch: {
    value: {
      immediate: true,
      deep: true,
      handler() { this.hydrate() }
    }
  },
  methods: {
    hydrate() {
      const canonical = normalizeDodConfig(this.config)
      this.requiredFields = clone(canonical.requiredFields || [])
      this.requiredAttachments = clone(canonical.materials || []).map(material => material.type || material.code).filter(Boolean)
      this.conditionalRules = hydrateDodConditions(canonical)
      this.validatorRefs = clone(this.config.validatorRefs || [])
      this.employeeInstructions = clone(this.config.employeeInstructions || [])
    },
    applyRecipe(recipe) {
      if (this.readonly) return
      this.emitPatch(materializeDodRecipe(recipe, this.value))
    },
    commitGoverned() {
      this.emitPatch(updateGovernedDod(this.value, {
        requiredFields: this.requiredFields,
        requiredAttachments: this.requiredAttachments,
        conditionalRules: this.conditionalRules
      }))
    },
    commitConditions() { this.commitGoverned() },
    addCondition() {
      const first = this.activeFields[0]
      this.conditionalRules.push(createDodCondition(first ? first.code : ''))
      this.commitConditions()
    },
    removeCondition(index) {
      this.conditionalRules.splice(index, 1)
      this.commitConditions()
    },
    commitInstructions() {
      this.emitPatch({ config: { ...normalizeDodConfig(this.config), employeeInstructions: clone(this.employeeInstructions) } })
    },
    commitAdvanced() {
      this.emitPatch({ config: { ...normalizeDodConfig(this.config), validatorRefs: clone(this.validatorRefs) } })
    },
    emitPatch(patch) {
      this.$emit('patch', { dod: patch })
    },
    fieldName(code) {
      const field = this.activeFields.find(item => item.code === code)
      return field ? field.name : code
    },
    materialName(code) {
      const material = this.activeMaterials.find(item => item.code === code)
      return material ? material.name : code
    },
    dodFocusTarget(fieldPath, resourceKey) {
      const requested = String(fieldPath || '')
      if (['recipeCard', 'requiredFields', 'requiredAttachments', 'conditionalRules',
        'employeeInstructions', 'validatorRefs'].includes(requested)) return requested
      const key = String(resourceKey || '').toUpperCase()
      const path = requested.toLowerCase()
      if (key === 'DOD_RECIPE' || path.includes('recipe')) return 'recipeCard'
      if (key === 'MATERIAL' || path.includes('material') || path.includes('attachment')) return 'requiredAttachments'
      if (key === 'VALIDATOR' || path.includes('validator')) return 'validatorRefs'
      if (path.includes('conditional') || path.includes('condition')) return 'conditionalRules'
      if (path.includes('instruction')) return 'employeeInstructions'
      if (path.includes('requiredfield')) return 'requiredFields'
      return 'recipeCard'
    },
    focusField(fieldPath, resourceKey) {
      const target = this.dodFocusTarget(fieldPath, resourceKey)
      if (['conditionalRules', 'employeeInstructions', 'validatorRefs'].includes(target)) {
        this.activeAdvanced = ['advanced']
      }
      this.$nextTick(() => {
        const reference = this.$refs[target]
        const element = reference && (reference.$el || reference)
        if (element && element.scrollIntoView) element.scrollIntoView({ behavior: 'smooth', block: 'center' })
        if (reference && reference.focus) reference.focus()
        else if (element && element.focus) element.focus()
      })
    }
  }
}
</script>

<style scoped lang="scss">
.journey-step__header {
  margin-bottom: 22px;

  h2 { margin: 5px 0 8px; font-size: 22px; line-height: 32px; color: #0B2A55; }
  p { margin: 0; font-size: 14px; line-height: 23px; color: #65758A; }
}

.journey-step__eyebrow { font-size: 12px; color: #C89A3D; }

.dod-step__sections {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-top: 16px;
}

.dod-config-card,
.dod-condition-card,
.dod-instructions {
  padding: 16px;
  background: #FFFFFF;
  border: 1px solid #D9E1EA;
  border-radius: 8px;

  > header {
    display: flex;
    gap: 10px;
    align-items: flex-start;
    margin-bottom: 12px;

    > i { margin-top: 2px; font-size: 21px; color: #C89A3D; }
  }

  h3 { margin: 0; font-size: 15px; color: #0B2A55; }
  p { margin: 3px 0 0; font-size: 12px; line-height: 19px; color: #65758A; }
  .el-select { width: 100%; }
}

.dod-config-card__summary {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-height: 25px;
  margin-top: 10px;

  span {
    padding: 4px 7px;
    font-size: 12px;
    color: #34465B;
    background: #F0F4F8;
    border-radius: 8px;
  }

  i { margin-right: 4px; color: #C89A3D; }
}

.dod-condition-card { margin-top: 14px; }
.dod-condition-card > header { justify-content: space-between; }

.dod-condition-row {
  display: grid;
  grid-template-columns: auto minmax(130px, 1fr) auto minmax(120px, .8fr) auto minmax(130px, 1fr) auto;
  gap: 8px;
  align-items: center;
  padding: 10px;
  margin-top: 8px;
  font-size: 13px;
  background: #F7F9FC;
  border-radius: 8px;

  .is-danger { color: #C43D3D; }
}

.dod-instructions { margin-top: 14px; }
.dod-instructions .el-select { width: 100%; margin-top: 10px; }

.dod-advanced {
  margin-top: 14px;

  ::v-deep .el-collapse-item__header {
    padding: 0 14px;
    color: #0B2A55;
    background: #F7F9FC;
  }

  ::v-deep .el-collapse-item__content { padding: 14px; }
  .el-icon-setting { margin-right: 7px; color: #C89A3D; }
  .el-select { width: 100%; }
  p { margin: 0 0 10px; color: #65758A; }
}

.dod-validator-settings {
  padding: 16px;
  background: #FFFFFF;
  border: 1px solid #D9E1EA;
  border-radius: 8px;
}

.dod-validator {
  display: flex;
  flex-direction: column;
  padding: 10px;
  margin-top: 8px;
  background: #F7F9FC;
  border-radius: 8px;

  strong { color: #0B2A55; }
  span { margin: 3px 0; color: #53667C; }
  small { color: #8A98A8; }
}

@media (max-width: 960px) {
  .dod-step__sections { grid-template-columns: 1fr; }
  .dod-condition-row { grid-template-columns: 1fr; }
}
</style>
