<template>
  <div class="definition-builder">
    <el-form-item label="SLA 分钟" prop="sla.config.minutes"><el-input-number :value="model.minutes" :disabled="readonly" :min="1" @input="change('minutes', $event)" /></el-form-item>
    <el-form-item label="工作日历"><el-select :value="model.calendarCode" :disabled="readonly" filterable placeholder="选择工作日历" @input="change('calendarCode', $event)"><el-option v-for="calendar in calendars" :key="calendar.calendar_code || calendar.calendarCode" :label="calendar.calendar_name || calendar.calendarName || calendar.calendar_code" :value="calendar.calendar_code || calendar.calendarCode" /></el-select></el-form-item>
  </div>
</template>
<script>
import { listWorkCalendars } from '@/api/todo-definition'
export default { name: 'SlaRuleBuilder', props: { value: { type: Object, default: () => ({}) }, readonly: Boolean }, data() { return { calendars: [] } }, computed: { model() { return { minutes: 480, calendarCode: 'DEFAULT', ...this.value } } }, created() { listWorkCalendars().then(response => { this.calendars = response.data || [] }) }, methods: { change(key, value) { this.$emit('input', { ...this.model, [key]: value }) } } }
</script>
