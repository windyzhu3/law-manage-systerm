-- 客户/合同模块字典补齐脚本
-- 可重复执行：补齐当前页面和后端状态机依赖的字典类型与字典数据。

insert into sys_dict_type(dict_name, dict_type, status, create_by, create_time, remark)
select item.dict_name, item.dict_type, '0', 'admin', sysdate(), item.remark
from (
  select '是否标记' dict_name, 'law_yes_no_flag' dict_type, '客户/联系人是否标记' remark union all
  select '合同审批动作', 'law_contract_approval_action', '合同审批动作' union all
  select '合同状态记录动作', 'law_contract_status_action', '合同状态记录动作'
) item
where not exists (select 1 from sys_dict_type where dict_type = item.dict_type);

insert into sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select item.sort, item.label, item.value, item.type, '', item.cls, item.def, '0', 'admin', sysdate(), item.label
from (
  select 1 sort, '是' label, '1' value, 'law_yes_no_flag' type, 'success' cls, 'N' def union all
  select 2, '否', '0', 'law_yes_no_flag', 'info', 'Y' union all
  select 1, '通过', 'pass', 'law_contract_approval_action', 'success', 'Y' union all
  select 2, '驳回', 'reject', 'law_contract_approval_action', 'danger', 'N' union all
  select 3, '退回修改', 'back', 'law_contract_approval_action', 'warning', 'N' union all
  select 1, '创建合同', 'create', 'law_contract_status_action', 'primary', 'Y' union all
  select 2, '提交审批', 'submit', 'law_contract_status_action', 'warning', 'N' union all
  select 3, '审批处理', 'approval', 'law_contract_status_action', 'success', 'N' union all
  select 4, '合同签署', 'sign', 'law_contract_status_action', 'success', 'N' union all
  select 5, '合同归档', 'archive', 'law_contract_status_action', 'info', 'N' union all
  select 6, '合同作废', 'void', 'law_contract_status_action', 'danger', 'N' union all
  select 7, '合同终止', 'terminate', 'law_contract_status_action', 'danger', 'N' union all
  select 8, '新增收费计划', 'fee_create', 'law_contract_status_action', 'primary', 'N' union all
  select 9, '调整收费计划', 'fee_update', 'law_contract_status_action', 'warning', 'N' union all
  select 10, '删除收费计划', 'fee_delete', 'law_contract_status_action', 'danger', 'N' union all
  select 11, '确认收款', 'fee_confirm', 'law_contract_status_action', 'success', 'N' union all
  select 12, '驳回收款', 'fee_reject', 'law_contract_status_action', 'danger', 'N' union all
  select 13, '更新开票状态', 'fee_invoice', 'law_contract_status_action', 'success', 'N' union all
  select 14, '新增附件', 'attachment_add', 'law_contract_status_action', 'primary', 'N' union all
  select 15, '删除附件', 'attachment_delete', 'law_contract_status_action', 'danger', 'N' union all
  select 16, '客户合并迁移', 'customer_merge', 'law_contract_status_action', 'warning', 'N'
) item
where not exists (select 1 from sys_dict_data where dict_type = item.type and dict_value = item.value);
