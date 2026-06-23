<template>
  <div class="table-card">
    <slot name="header" />
    <div v-if="$slots.filters" class="filter-toolbar biz-table-filterbar" :class="{ 'is-filter-collapsed': !showSearch }">
      <div v-show="showSearch" class="biz-table-filter-content">
        <slot name="filters" />
      </div>
      <right-toolbar v-if="toolbar" :showSearch.sync="innerShowSearch" @queryTable="$emit('query')" />
    </div>
    <div class="table-wrap">
      <slot />
    </div>
    <pagination
      v-if="pagination"
      v-show="total > 0"
      :total="total"
      :page.sync="innerPage"
      :limit.sync="innerLimit"
      @pagination="$emit('pagination')"
    />
  </div>
</template>

<script>
export default {
  name: 'BizTableCard',
  props: {
    showSearch: { type: Boolean, default: true },
    toolbar: { type: Boolean, default: true },
    pagination: { type: Boolean, default: true },
    total: { type: Number, default: 0 },
    page: { type: Number, default: 1 },
    limit: { type: Number, default: 10 }
  },
  computed: {
    innerShowSearch: {
      get() { return this.showSearch },
      set(value) { this.$emit('update:showSearch', value) }
    },
    innerPage: {
      get() { return this.page },
      set(value) { this.$emit('update:page', value) }
    },
    innerLimit: {
      get() { return this.limit },
      set(value) { this.$emit('update:limit', value) }
    }
  }
}
</script>

<style lang="scss">
.biz-table-filterbar {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  flex-wrap: nowrap;
  overflow-x: auto;
  overflow-y: hidden;
  margin-bottom: 12px;
  padding-bottom: 4px;
  min-height: 34px;
  scrollbar-width: thin;
  scrollbar-color: #d7e1f3 transparent;
}

.biz-table-filterbar::-webkit-scrollbar {
  height: 6px;
}

.biz-table-filterbar::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: #d7e1f3;
}

.biz-table-filterbar::-webkit-scrollbar-track {
  background: transparent;
}

.biz-table-filterbar.is-filter-collapsed {
  justify-content: flex-end;
  overflow-x: visible;
}

.biz-table-filter-content {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1 1 auto;
  min-width: 0;
  flex-wrap: nowrap;
  overflow-y: hidden;

  .biz-filter-main,
  .biz-filter-actions {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: nowrap;
    flex: none;
    min-width: 0;
  }

  .biz-filter-main {
    flex: 1 1 auto;
    overflow-x: auto;
    overflow-y: hidden;
    padding-bottom: 1px;
    scrollbar-width: none;
  }

  .biz-filter-main::-webkit-scrollbar {
    display: none;
  }

  .biz-filter-actions {
    margin-left: auto;
  }

  .el-input {
    width: var(--biz-filter-input-width, 200px);
    flex: 0 0 var(--biz-filter-input-width, 200px);
  }

  .el-select {
    width: var(--biz-filter-select-width, 128px);
    flex: 0 0 var(--biz-filter-select-width, 128px);

    .el-input {
      width: 100%;
      flex-basis: auto;
    }
  }

  .el-input__inner {
    height: 34px;
    line-height: 34px;
    font-size: var(--biz-font-small, 12px);
  }

  .el-button + .el-button {
    margin-left: 0;
  }
}

.biz-table-filterbar .top-right-btn {
  flex: 0 0 auto;
  margin-left: 4px;
}

@media (max-width: 760px) {
  .biz-table-filterbar,
  .biz-table-filter-content,
  .biz-table-filter-content .biz-filter-main,
  .biz-table-filter-content .biz-filter-actions {
    flex-wrap: wrap;
    width: 100%;
  }

  .biz-table-filter-content .el-input,
  .biz-table-filter-content .el-select {
    width: 100%;
    flex-basis: 100%;
  }

  .biz-table-filterbar .top-right-btn {
    margin-left: auto;
  }
}
</style>
