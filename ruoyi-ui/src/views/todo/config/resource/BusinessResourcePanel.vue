<template>
  <section v-loading="loading" class="business-resource">
    <header class="business-resource__toolbar">
      <div>
        <h2>业务资源</h2>
        <p>统一维护触发条件、完成条件和模拟测试使用的业务语义，配置人员无需记忆技术编码。</p>
      </div>
      <el-select v-model="businessType" placeholder="业务类型" size="small" @change="loadAll">
        <el-option v-for="item in businessTypes" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
    </header>

    <section class="resource-section">
      <header>
        <div><h3>业务字段 <span>{{ fields.length }}</span></h3><p>字段来自已启用事件 Schema，也可补充统一中文名称和选项。</p></div>
        <el-button v-hasPermi="['todo:resource:add']" size="small" type="primary" plain icon="el-icon-plus" @click="openCreate('FIELD')">新增字段</el-button>
      </header>
      <el-table :data="fields" stripe>
        <el-table-column prop="name" label="字段名称" min-width="150">
          <template #default="{ row }"><strong>{{ row.name }}</strong><small class="cell-note">{{ row.code }}</small></template>
        </el-table-column>
        <el-table-column label="业务类型" width="110"><template #default="{ row }">{{ fieldTypeLabel(row.type) }}</template></el-table-column>
        <el-table-column label="使用范围" min-width="190">
          <template #default="{ row }">
            <el-tag v-for="event in row.sourceEvents || []" :key="event" size="mini" class="tag-gap">{{ event }}</el-tag>
            <span v-if="!(row.sourceEvents || []).length" class="cell-note">统一业务字段</span>
          </template>
        </el-table-column>
        <el-table-column label="属性" width="150">
          <template #default="{ row }">
            <el-tag v-if="row.required" size="mini" type="warning">必填</el-tag>
            <el-tag v-if="row.sensitive" size="mini" type="danger" class="tag-gap">敏感</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="center">
          <template #default="{ row }">
            <el-button v-if="editable(row)" v-hasPermi="['todo:resource:edit']" type="text" @click="openEdit('FIELD', row)">编辑</el-button>
            <span v-else class="cell-note">事件字段</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!fields.length" description="暂无字段，请先维护事件 Payload Schema" :image-size="54" />
    </section>

    <section class="resource-section">
      <header>
        <div><h3>材料类型 <span>{{ materials.length }}</span></h3><p>用于定义员工完成待办时需要提交的合同、回单、审批件等材料。</p></div>
        <el-button v-hasPermi="['todo:resource:add']" size="small" type="primary" plain icon="el-icon-plus" @click="openCreate('MATERIAL')">新增材料</el-button>
      </header>
      <el-table :data="materials" stripe>
        <el-table-column prop="name" label="材料名称" min-width="170">
          <template #default="{ row }"><strong>{{ row.name }}</strong><small class="cell-note">{{ row.code }}</small></template>
        </el-table-column>
        <el-table-column prop="description" label="员工使用说明" min-width="260" show-overflow-tooltip />
        <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ row.status === 'ACTIVE' ? '可使用' : '已停用' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="90" align="center"><template #default="{ row }"><el-button v-if="editable(row)" v-hasPermi="['todo:resource:edit']" type="text" @click="openEdit('MATERIAL', row)">编辑</el-button></template></el-table-column>
      </el-table>
      <el-empty v-if="!materials.length" description="暂无材料类型" :image-size="54" />
    </section>

    <section class="resource-section">
      <header>
        <div><h3>完成条件配方 <span>{{ recipes.length }}</span></h3><p>将常用字段、材料和校验组合为可复用方案，减少模板逐项配置。</p></div>
        <el-button v-hasPermi="['todo:resource:add']" size="small" type="primary" plain icon="el-icon-plus" @click="openCreate('DOD_RECIPE')">新增配方</el-button>
      </header>
      <div v-if="recipes.length" class="recipe-grid">
        <article v-for="item in recipes" :key="item.code">
          <div><strong>{{ item.name }}</strong><small>{{ item.code }}</small><el-button v-if="editable(item)" v-hasPermi="['todo:resource:edit']" type="text" @click="openEdit('DOD_RECIPE', item)">编辑</el-button></div>
          <p>{{ item.description || '用于快速建立员工可理解的完成标准。' }}</p>
          <div class="recipe-grid__tags">
            <el-tag v-for="field in item.requiredFields || []" :key="`field-${field}`" size="mini">字段 · {{ resourceName(fields, field) }}</el-tag>
            <el-tag v-for="material in item.requiredAttachments || []" :key="`material-${material}`" size="mini" type="success">材料 · {{ resourceName(materials, material) }}</el-tag>
            <el-tag v-for="validator in item.validatorRefs || []" :key="`validator-${validator}`" size="mini" type="warning">校验 · {{ resourceName(validators, validator) }}</el-tag>
          </div>
        </article>
      </div>
      <el-empty v-else description="当前业务类型暂无推荐配方" :image-size="54" />
    </section>

    <resource-item-drawer
      :visible.sync="drawer.open"
      :resource-type="drawer.type"
      :item="drawer.item"
      :business-type="businessType"
      :field-options="fields"
      :material-options="materials"
      :validator-options="validators"
      @saved="saved"
    />
  </section>
</template>

<script>
import ResourceItemDrawer from './ResourceItemDrawer'
import {
  listFieldResources,
  listMaterialResources,
  listDodRecipeResources,
  listValidatorResources
} from '@/api/todo-resources'

export default {
  name: 'BusinessResourcePanel',
  components: { ResourceItemDrawer },
  data() {
    return {
      loading: false,
      businessType: 'LEAD',
      businessTypes: [
        { value: 'LEAD', label: '线索' },
        { value: 'CUSTOMER', label: '客户' },
        { value: 'CONTRACT', label: '合同' },
        { value: 'CASE', label: '案件' },
        { value: 'MATTER', label: '事项' }
      ],
      fields: [],
      materials: [],
      recipes: [],
      validators: [],
      drawer: { open: false, type: 'FIELD', item: null }
    }
  },
  created() { this.loadAll() },
  methods: {
    async load() { return this.loadAll() },
    async loadAll() {
      this.loading = true
      try {
        const params = { businessType: this.businessType }
        const [fields, materials, recipes, validators] = await Promise.all([
          listFieldResources(params),
          listMaterialResources(params),
          listDodRecipeResources(params),
          listValidatorResources(params)
        ])
        this.fields = fields.data || []
        this.materials = materials.data || []
        this.recipes = recipes.data || []
        this.validators = validators.data || []
      } catch (error) {
        this.fields = []
        this.materials = []
        this.recipes = []
        this.validators = []
        this.$modal.msgError((error && (error.msg || error.message)) || '加载业务资源失败')
      } finally {
        this.loading = false
      }
    },
    openCreate(type) { this.drawer = { open: true, type, item: null } },
    openEdit(type, item) { this.drawer = { open: true, type, item } },
    editable(item) { return item && item.source === 'GOVERNED' && Boolean(item.resourceItemId) },
    saved() { this.loadAll() },
    fieldTypeLabel(type) {
      return ({ string: '文本', integer: '整数', number: '数字', boolean: '是/否', object: '对象', array: '列表' })[type] || type
    },
    resourceName(rows, code) {
      const item = (rows || []).find(row => row.code === code)
      return item ? item.name : code
    }
  }
}
</script>

<style scoped lang="scss">
.business-resource__toolbar,
.resource-section > header {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
}

.business-resource__toolbar {
  margin-bottom: 18px;

  h2 {
    margin: 0;
    font-size: 18px;
    color: #0B2A55;
  }

  p {
    margin: 5px 0 0;
    font-size: 13px;
    line-height: 20px;
    color: #65758A;
  }
}

.resource-section {
  padding: 18px 0;
  border-top: 1px solid #D9E1EA;

  > header {
    margin-bottom: 12px;

    h3 {
      margin: 0;
      font-size: 15px;
      color: #0B2A55;

      span {
        margin-left: 6px;
        font-size: 12px;
        color: #7B8898;
      }
    }

    p {
      margin: 4px 0 0;
      font-size: 12px;
      color: #65758A;
    }
  }
}

.cell-note {
  display: block;
  margin-top: 3px;
  font-size: 11px;
  color: #7B8898;
}

.tag-gap {
  margin: 2px 3px;
}

.recipe-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 12px;

  article {
    padding: 15px;
    background: #F8FAFC;
    border: 1px solid #D9E1EA;
    border-radius: 8px;
  }

  strong,
  small {
    display: block;
  }

  strong {
    color: #0B2A55;
  }

  small {
    margin-top: 2px;
    font-size: 11px;
    color: #7B8898;
  }

  p {
    min-height: 38px;
    margin: 10px 0;
    font-size: 13px;
    line-height: 19px;
    color: #65758A;
  }
}

.recipe-grid__tags .el-tag {
  margin: 2px 4px 2px 0;
}

@media (max-width: 640px) {
  .business-resource__toolbar,
  .resource-section > header {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
