<template>
  <section class="business-resource" v-loading="loading">
    <div class="business-resource__toolbar">
      <div><strong>业务字段与材料</strong><p>统一维护完成条件和模拟测试可选择的业务语义，避免用户输入字段编码。</p></div>
      <el-select v-model="businessType" placeholder="业务类型" size="small" @change="loadAll"><el-option v-for="item in businessTypes" :key="item" :label="item" :value="item" /></el-select>
    </div>
    <el-row :gutter="16">
      <el-col :span="12">
        <el-card shadow="never"><div slot="header"><strong>可选字段</strong><span class="count">{{ fields.length }}</span></div>
          <el-table :data="fields" max-height="420"><el-table-column prop="name" label="字段" min-width="130"><template #default="{ row }">{{ row.name }}<p class="cell-note">{{ row.code }}</p></template></el-table-column><el-table-column prop="type" label="类型" width="90" /><el-table-column label="必填" width="70" align="center"><template #default="{ row }">{{ row.required ? '是' : '否' }}</template></el-table-column></el-table>
          <el-empty v-if="!fields.length" description="暂无字段，请先维护事件 Payload Schema" :image-size="54" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never"><div slot="header"><strong>材料类型</strong><span class="count">{{ materials.length }}</span></div>
          <el-table :data="materials" max-height="420"><el-table-column prop="name" label="材料" min-width="130"><template #default="{ row }">{{ row.name }}<p class="cell-note">{{ row.code }}</p></template></el-table-column><el-table-column prop="description" label="说明" min-width="150" show-overflow-tooltip /><el-table-column prop="status" label="状态" width="80" /></el-table>
          <el-empty v-if="!materials.length" description="暂无材料类型" :image-size="54" />
        </el-card>
      </el-col>
    </el-row>
    <el-card shadow="never" class="recipe-card"><div slot="header"><strong>推荐完成条件配方</strong><span class="count">{{ recipes.length }}</span></div>
      <div v-if="recipes.length" class="recipe-grid"><article v-for="item in recipes" :key="item.code"><strong>{{ item.name }}</strong><p>{{ item.description }}</p><div><el-tag v-for="field in item.requiredFields || []" :key="field" size="mini">字段 · {{ field }}</el-tag><el-tag v-for="material in item.requiredAttachments || []" :key="material" size="mini" type="success">材料 · {{ material }}</el-tag></div></article></div>
      <el-empty v-else description="当前业务类型暂无推荐配方" :image-size="54" />
    </el-card>
  </section>
</template>

<script>
import { listFieldResources, listMaterialResources, listDodRecipeResources } from '@/api/todo-resources'
export default {
  name: 'BusinessResourcePanel',
  data() { return { loading: false, businessType: 'LEAD', businessTypes: ['LEAD', 'CUSTOMER', 'CONTRACT', 'CASE', 'MATTER'], fields: [], materials: [], recipes: [] } },
  created() { this.loadAll() },
  methods: {
    async loadAll() { this.loading = true; try { const params = { businessType: this.businessType }; const [fields, materials, recipes] = await Promise.all([listFieldResources(params), listMaterialResources(params), listDodRecipeResources(params)]); this.fields = fields.data || []; this.materials = materials.data || []; this.recipes = recipes.data || [] } catch (error) { this.fields = []; this.materials = []; this.recipes = []; this.$modal.msgError('加载业务资源失败') } finally { this.loading = false } }
  }
}
</script>

<style scoped lang="scss">
.business-resource__toolbar { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px; }.business-resource__toolbar p,.cell-note { margin: 5px 0 0; color: #64748b; font-size: 12px; }.count { margin-left: 8px; color: #94a3b8; }.recipe-card { margin-top: 16px; }.recipe-grid { display: grid; grid-template-columns: repeat(auto-fit,minmax(240px,1fr)); gap: 12px; }.recipe-grid article { padding: 14px; border: 1px solid #e2e8f0; border-radius: 8px; background: #f8fafc; }.recipe-grid p { min-height: 36px; color: #64748b; font-size: 13px; }.recipe-grid .el-tag { margin: 2px 4px 2px 0; }
</style>
