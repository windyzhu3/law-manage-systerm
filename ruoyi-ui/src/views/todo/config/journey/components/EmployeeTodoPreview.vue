<template>
  <section class="employee-preview" aria-labelledby="employee-preview-title">
    <header>
      <span>员工视角预览</span>
      <small>随配置实时更新</small>
    </header>
    <div class="employee-preview__card">
      <div class="employee-preview__topline">
        <span class="employee-preview__priority">{{ value.assigneeSummary || '负责人待配置' }}</span>
        <span class="employee-preview__due">{{ dueText }}</span>
      </div>
      <h3 id="employee-preview-title">{{ value.title || '待办事项标题' }}</h3>
      <p>{{ value.description || value.instruction || '员工将在这里看到办理说明、截止时间和完成要求。' }}</p>

      <dl v-if="fields.length" class="employee-preview__fields">
        <div v-for="(field, index) in fields.slice(0, 4)" :key="field.code || index">
          <dt>
            {{ field.label || field.name || '业务信息' }}
            <em v-if="field.conditional">条件必填</em>
            <em v-else-if="field.required">必填</em>
          </dt>
          <dd>{{ displayValue(field.value) }}</dd>
        </div>
      </dl>
      <div v-else class="employee-preview__placeholder">
        <span>业务对象信息</span>
        <i>将在选择事件与展示字段后出现</i>
      </div>

      <div v-if="materials.length" class="employee-preview__requirements">
        <strong>需上传材料</strong>
        <ul>
          <li v-for="(item, index) in materials.slice(0, 4)" :key="item.code || index">
            {{ item.label || item.name || item.code }}
          </li>
        </ul>
      </div>
      <div class="employee-preview__requirements">
        <strong>完成要求</strong>
        <ul v-if="instructions.length">
          <li v-for="(item, index) in instructions.slice(0, 4)" :key="index">{{ item }}</li>
        </ul>
        <span v-else>完成标准配置后，员工会在此看到必填项和材料要求。</span>
      </div>
      <el-button type="primary" size="small" disabled>开始办理</el-button>
    </div>
  </section>
</template>

<script>
export default {
  name: 'EmployeeTodoPreview',
  props: {
    value: { type: Object, default: () => ({}) }
  },
  computed: {
    fields() {
      return Array.isArray(this.value.fields) ? this.value.fields : []
    },
    materials() {
      return Array.isArray(this.value.materials) ? this.value.materials : []
    },
    instructions() {
      const configured = Array.isArray(this.value.requirements)
        ? this.value.requirements.map(item => item.label || item.title || item)
        : []
      const completion = Array.isArray(this.value.completionInstructions)
        ? this.value.completionInstructions
        : []
      return [...configured, ...completion]
    },
    dueText() {
      return this.value.dueSummary || this.value.dueText || '截止时间待配置'
    }
  },
  methods: {
    displayValue(value) {
      if (value === null || value === undefined || value === '') return '待业务数据填充'
      if (Array.isArray(value)) return value.join('、')
      return String(value)
    }
  }
}
</script>

<style scoped lang="scss">
.employee-preview {
  padding: 20px;
  background: #FFFFFF;
  border: 1px solid #D9E1EA;
  border-radius: 8px;

  > header {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    margin-bottom: 14px;
    color: #0B2A55;

    span {
      font-size: 16px;
      font-weight: 700;
      line-height: 24px;
    }

    small {
      font-size: 12px;
      color: #7B8898;
    }
  }
}

.employee-preview__card {
  padding: 18px;
  background: #F8FAFC;
  border: 1px solid #E2E8F0;
  border-radius: 8px;

  h3 {
    margin: 12px 0 6px;
    font-size: 17px;
    line-height: 26px;
    color: #0B2A55;
  }

  p {
    margin: 0 0 16px;
    font-size: 13px;
    line-height: 21px;
    color: #586779;
  }

  .el-button {
    width: 100%;
    margin-top: 16px;
  }
}

.employee-preview__topline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
}

.employee-preview__priority {
  padding: 2px 8px;
  color: #7A5210;
  background: #FFF2D5;
  border-radius: 10px;
}

.employee-preview__due {
  color: #65758A;
}

.employee-preview__fields {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin: 0 0 16px;

  div {
    min-width: 0;
    padding: 9px 10px;
    background: #FFFFFF;
    border: 1px solid #E2E8F0;
    border-radius: 8px;
  }

  dt,
  dd {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  dt {
    font-size: 11px;
    line-height: 18px;
    color: #8996A7;

    em {
      padding: 1px 4px;
      margin-left: 4px;
      font-size: 10px;
      font-style: normal;
      color: #7A5210;
      background: #FFF2D5;
      border-radius: 6px;
    }
  }

  dd {
    margin: 1px 0 0;
    font-size: 13px;
    line-height: 20px;
    color: #27384D;
  }
}

.employee-preview__placeholder,
.employee-preview__requirements {
  display: flex;
  flex-direction: column;
  padding: 12px;
  font-size: 12px;
  line-height: 20px;
  color: #65758A;
  background: #FFFFFF;
  border: 1px dashed #C8D2DE;
  border-radius: 8px;

  span,
  i {
    font-style: normal;
  }
}

.employee-preview__requirements {
  margin-top: 12px;
  border-style: solid;

  strong {
    margin-bottom: 4px;
    color: #27384D;
  }

  ul {
    padding-left: 18px;
    margin: 0;
  }
}
</style>
