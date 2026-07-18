<template><div><el-button v-hasPermi="['todo:calendar:manage']" type="primary" size="mini" @click="$refs.dialog.show({})">新增日历</el-button><el-table :data="rows"><el-table-column prop="calendar_code" label="编码"/><el-table-column prop="calendar_name" label="名称"/><el-table-column prop="work_days" label="工作日"/><el-table-column prop="work_start" label="开始"/><el-table-column prop="work_end" label="结束"/><el-table-column label="操作"><template slot-scope="scope"><el-button v-hasPermi="['todo:calendar:manage']" type="text" @click="$refs.dialog.show(scope.row)">编辑</el-button></template></el-table-column></el-table><work-calendar-dialog ref="dialog" :on-save="save"/></div></template>
<script>
import WorkCalendarDialog from './WorkCalendarDialog'
import { saveWorkCalendar } from '@/api/todo-definition'
export default { components: { WorkCalendarDialog }, props: { rows: Array, onRefresh: Function }, methods: { async save(data) { await saveWorkCalendar(data); await this.onRefresh() } } }
</script>
