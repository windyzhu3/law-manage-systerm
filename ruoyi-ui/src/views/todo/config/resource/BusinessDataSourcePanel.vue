<template>
  <section v-loading="loading" class="data-source-panel">
    <header class="data-source-panel__header">
      <div>
        <h2>模拟数据源状态</h2>
        <p>只读检查业务对象下拉、Payload 读取和安全样例是否可用于模板模拟，不会读取或修改实际业务数据。</p>
      </div>
      <el-button size="small" icon="el-icon-refresh" @click="load">刷新状态</el-button>
    </header>
    <div class="data-source-grid">
      <article v-for="item in rows" :key="item.businessType" class="data-source-item">
        <div class="data-source-item__heading">
          <span class="data-source-item__icon"><i class="el-icon-connection" /></span>
          <div><strong>{{ businessLabel(item.businessType) }}</strong><small>{{ item.businessType }}</small></div>
          <el-tag :type="statusType(item.status)" size="small">{{ statusLabel(item.status) }}</el-tag>
        </div>
        <ul>
          <li :class="{ ready: item.directoryAvailable }"><i :class="item.directoryAvailable ? 'el-icon-circle-check' : 'el-icon-circle-close'" />业务对象可选择</li>
          <li :class="{ ready: item.payloadAvailable }"><i :class="item.payloadAvailable ? 'el-icon-circle-check' : 'el-icon-circle-close'" />业务字段可读取</li>
          <li :class="{ ready: item.sampleAvailable }"><i :class="item.sampleAvailable ? 'el-icon-circle-check' : 'el-icon-circle-close'" />安全样例可使用</li>
        </ul>
        <p>{{ message(item) }}</p>
      </article>
    </div>
    <el-empty v-if="!loading && !rows.length" description="暂无数据源状态" :image-size="72" />
  </section>
</template>

<script>
import { listConfigurationDataSources } from '@/api/todo-resources'

export default {
  name: 'BusinessDataSourcePanel',
  data() { return { loading: false, rows: [] } },
  created() { this.load() },
  methods: {
    async load() {
      this.loading = true
      try {
        const response = await listConfigurationDataSources()
        this.rows = response.data || []
      } catch (error) {
        this.rows = []
        this.$modal.msgError((error && (error.msg || error.message)) || '加载模拟数据源状态失败')
      } finally {
        this.loading = false
      }
    },
    businessLabel(type) {
      return ({ LEAD: '线索', CUSTOMER: '客户', CONTRACT: '合同', CASE: '案件', MATTER: '事项' })[type] || type
    },
    statusLabel(status) {
      return ({ READY: '已就绪', PARTIAL: '部分就绪', CONFLICT: '配置冲突' })[status] || status
    },
    statusType(status) {
      return ({ READY: 'success', PARTIAL: 'warning', CONFLICT: 'danger' })[status] || 'info'
    },
    message(item) {
      if (item.status === 'READY') return '该业务类型可直接用于只读模拟。'
      if (!item.directoryAvailable) return '业务对象目录尚未接入，模拟时无法选择测试对象。'
      if (!item.payloadAvailable) return 'Payload 适配器尚未接入，无法形成完整模拟输入。'
      if (!item.sampleAvailable) return '安全样例尚未准备，可使用有权限的真实业务对象模拟。'
      return '检测到重复适配，请由技术管理员处理。'
    }
  }
}
</script>

<style scoped lang="scss">
.data-source-panel__header {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 18px;

  h2 {
    margin: 0;
    font-size: 18px;
    color: #0B2A55;
  }

  p {
    margin: 5px 0 0;
    font-size: 13px;
    line-height: 21px;
    color: #65758A;
  }
}

.data-source-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 12px;
}

.data-source-item {
  padding: 16px;
  background: #F8FAFC;
  border: 1px solid #D9E1EA;
  border-radius: 8px;

  ul {
    display: grid;
    gap: 7px;
    padding: 12px 0;
    margin: 12px 0;
    list-style: none;
    border-top: 1px solid #E3E9F0;
    border-bottom: 1px solid #E3E9F0;
  }

  li {
    font-size: 13px;
    color: #9F2F2F;

    &.ready {
      color: #256B4A;
    }

    i {
      margin-right: 6px;
    }
  }

  > p {
    margin: 0;
    font-size: 12px;
    line-height: 19px;
    color: #65758A;
  }
}

.data-source-item__heading {
  display: grid;
  grid-template-columns: 36px 1fr auto;
  gap: 10px;
  align-items: center;

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
    color: #8492A2;
  }
}

.data-source-item__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  color: #0B2A55;
  background: #EAF0F7;
  border-radius: 8px;
}

@media (max-width: 640px) {
  .data-source-panel__header {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
