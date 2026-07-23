<template>
  <section class="recipe-picker" aria-label="推荐完成标准">
    <header>
      <div>
        <h3>推荐完成标准</h3>
        <p>系统已按当前业务、事件和办理阶段排序，选择后仍可继续调整。</p>
      </div>
      <el-tag size="small" type="warning">{{ recipes.length }} 套可用配方</el-tag>
    </header>
    <div v-if="recipes.length" class="recipe-picker__grid">
      <button
        v-for="(recipe, index) in recipes"
        :key="recipe.code"
        type="button"
        class="recipe-card"
        :class="{ 'is-selected': recipe.code === selectedCode }"
        :disabled="readonly"
        @click="$emit('select', recipe)"
      >
        <span class="recipe-card__rank">{{ index === 0 ? '最匹配' : `推荐 ${index + 1}` }}</span>
        <i :class="recipe.code === selectedCode ? 'el-icon-circle-check' : 'el-icon-document-checked'" />
        <strong>{{ recipe.name || '完成标准配方' }}</strong>
        <p>{{ recipe.description || '按此业务场景收集员工办理结果和材料。' }}</p>
        <small>{{ summary(recipe) }}</small>
      </button>
    </div>
    <el-empty v-else description="当前业务暂无推荐配方，可在下方直接选择完成要求" :image-size="66" />
  </section>
</template>

<script>
export default {
  name: 'DodRecipePicker',
  props: {
    recipes: { type: Array, default: () => [] },
    selectedCode: { type: String, default: '' },
    readonly: Boolean
  },
  methods: {
    summary(recipe) {
      const fields = (recipe.requiredFields || []).length
      const materials = (recipe.requiredAttachments || []).length
      const rules = (recipe.conditionalRules || []).length
      return `${fields} 项必填信息 · ${materials} 类材料${rules ? ` · ${rules} 条条件要求` : ''}`
    }
  }
}
</script>

<style scoped lang="scss">
.recipe-picker {
  padding: 16px;
  background: #F7F9FC;
  border: 1px solid #D9E1EA;
  border-radius: 8px;

  > header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    margin-bottom: 13px;

    h3 {
      margin: 0;
      font-size: 16px;
      color: #0B2A55;
    }

    p {
      margin: 4px 0 0;
      font-size: 12px;
      color: #65758A;
    }
  }
}

.recipe-picker__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.recipe-card {
  position: relative;
  min-height: 130px;
  padding: 15px;
  color: #34465B;
  text-align: left;
  cursor: pointer;
  background: #FFFFFF;
  border: 1px solid #D9E1EA;
  border-radius: 8px;

  &:hover,
  &.is-selected {
    border-color: #C89A3D;
  }

  &.is-selected {
    background: #FFF9EC;
  }

  > i {
    display: block;
    margin: 17px 0 7px;
    font-size: 22px;
    color: #0B2A55;
  }

  strong,
  p,
  small {
    display: block;
  }

  strong {
    font-size: 14px;
    color: #0B2A55;
  }

  p {
    margin: 4px 0 6px;
    font-size: 12px;
    line-height: 19px;
    color: #65758A;
  }

  small {
    color: #7B8898;
  }
}

.recipe-card__rank {
  position: absolute;
  top: 12px;
  right: 12px;
  padding: 2px 7px;
  font-size: 11px;
  color: #7A5210;
  background: #FFF2D5;
  border-radius: 10px;
}

@media (max-width: 960px) {
  .recipe-picker__grid {
    grid-template-columns: 1fr;
  }
}
</style>
