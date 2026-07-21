-- Todo Engine configuration-center localization.
-- Keep the retired legacy route hidden and disabled even on upgraded databases.
update sys_menu
set visible='1',status='1'
where component='todo/config/index';

update sys_menu menu
join (
  select '待办模板' menu_name,'todo/config/template/index' component union all
  select '触发规则','todo/config/trigger/index' union all
  select 'SLA规则','todo/config/sla/index' union all
  select '完成条件','todo/config/dod/index' union all
  select '模拟测试','todo/config/simulation/index' union all
  select '发布记录','todo/config/release/index'
) localized on localized.component=menu.component
set menu.menu_name=localized.menu_name,
    menu.update_by='admin',
    menu.update_time=sysdate();

update sys_dict_type dictionary
join (
  select '业务阶段' dict_name,'law_todo_business_stage' dict_type union all
  select '业务类型','law_todo_business_type' union all
  select '模板类型','law_todo_template_type' union all
  select '发布状态','law_todo_publish_status' union all
  select '触发方式','law_todo_trigger_mode' union all
  select '条件操作符','law_todo_condition_operator' union all
  select '负责人规则类型','law_todo_owner_rule_type' union all
  select 'SLA类型','law_todo_sla_type' union all
  select 'SLA时间单位','law_todo_sla_unit' union all
  select 'SLA计时起点','law_todo_sla_start_strategy' union all
  select '超时策略','law_todo_timeout_strategy' union all
  select '完成条件类型','law_todo_dod_rule_type' union all
  select '规则状态','law_todo_rule_status' union all
  select '版本状态','law_todo_version_status'
) localized on localized.dict_type=dictionary.dict_type
set dictionary.dict_name=localized.dict_name,
    dictionary.remark='Todo Engine 配置中心',
    dictionary.update_by='admin',
    dictionary.update_time=sysdate();

update sys_dict_data dictionary
join (
  select '线索' dict_label,'LEAD' dict_value,'law_todo_business_stage' dict_type union all
  select '合同','CONTRACT','law_todo_business_stage' union all
  select '案件','CASE','law_todo_business_stage' union all
  select '事项','MATTER','law_todo_business_stage' union all
  select '归档','ARCHIVE','law_todo_business_stage' union all
  select '线索','LEAD','law_todo_business_type' union all
  select '客户','CUSTOMER','law_todo_business_type' union all
  select '合同','CONTRACT','law_todo_business_type' union all
  select '案件','CASE','law_todo_business_type' union all
  select '事项','MATTER','law_todo_business_type' union all
  select '标准模板','STANDARD','law_todo_template_type' union all
  select '自定义模板','CUSTOM','law_todo_template_type' union all
  select '草稿','DRAFT','law_todo_publish_status' union all
  select '已发布','PUBLISHED','law_todo_publish_status' union all
  select '已回滚','ROLLED_BACK','law_todo_publish_status' union all
  select '事件触发','EVENT','law_todo_trigger_mode' union all
  select '手动触发','MANUAL','law_todo_trigger_mode' union all
  select '定时触发','SCHEDULE','law_todo_trigger_mode' union all
  select '等于','EQ','law_todo_condition_operator' union all
  select '不等于','NE','law_todo_condition_operator' union all
  select '属于','IN','law_todo_condition_operator' union all
  select '不属于','NOT_IN','law_todo_condition_operator' union all
  select '存在','EXISTS','law_todo_condition_operator' union all
  select '不存在','NOT_EXISTS','law_todo_condition_operator' union all
  select '指定用户','USER','law_todo_owner_rule_type' union all
  select '指定角色','ROLE','law_todo_owner_rule_type' union all
  select '指定部门','DEPT','law_todo_owner_rule_type' union all
  select '指定岗位','POST','law_todo_owner_rule_type' union all
  select '事件载荷字段','PAYLOAD','law_todo_owner_rule_type' union all
  select '业务负责人','BUSINESS_OWNER','law_todo_owner_rule_type' union all
  select '直属上级','SUPERVISOR','law_todo_owner_rule_type' union all
  select '轮询分配','ROUND_ROBIN','law_todo_owner_rule_type' union all
  select '分配层级','ASSIGNMENT_LEVEL','law_todo_owner_rule_type' union all
  select '响应型','RESPONSE','law_todo_sla_type' union all
  select '分钟','MINUTE','law_todo_sla_unit' union all
  select '小时','HOUR','law_todo_sla_unit' union all
  select '天','DAY','law_todo_sla_unit' union all
  select '待办创建时','TODO_CREATED','law_todo_sla_start_strategy' union all
  select '提醒','REMIND','law_todo_timeout_strategy' union all
  select '升级','ESCALATE','law_todo_timeout_strategy' union all
  select '自动动作','AUTO_ACTION','law_todo_timeout_strategy' union all
  select '任务完成条件','TASK','law_todo_dod_rule_type' union all
  select '启用','0','law_todo_rule_status' union all
  select '停用','1','law_todo_rule_status' union all
  select '草稿','DRAFT','law_todo_version_status' union all
  select '已发布','PUBLISHED','law_todo_version_status' union all
  select '已回滚','ROLLED_BACK','law_todo_version_status' union all
  select '已退役','RETIRED','law_todo_version_status'
) localized on localized.dict_type=dictionary.dict_type and localized.dict_value=dictionary.dict_value
set dictionary.dict_label=localized.dict_label,
    dictionary.remark=localized.dict_label,
    dictionary.update_by='admin',
    dictionary.update_time=sysdate();
