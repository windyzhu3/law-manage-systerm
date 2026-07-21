-- Test-only, idempotent bootstrap for the real Todo configuration-centre E2E.
-- The password placeholder is replaced at runtime. This file is never loaded by Flyway.
set names utf8mb4 collate utf8mb4_unicode_ci;

insert into sys_role(role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,status,del_flag,create_by,create_time,remark)
select 'Todo config E2E admin','todo_config_admin',90,'5',1,1,'0','0','todo-config-e2e',sysdate(),'TEST_ONLY|Todo configuration real E2E'
where not exists(select 1 from sys_role where role_key='todo_config_admin' and del_flag='0');
set @todo_config_role_id=(select role_id from sys_role where role_key='todo_config_admin' and del_flag='0' order by role_id limit 1);

insert into sys_user(dept_id,user_name,nick_name,user_type,email,phonenumber,sex,avatar,password,status,del_flag,create_by,create_time,remark)
select 103,'todo_config_admin','Todo configuration E2E','99','','','0','','TODO_CONFIG_E2E_PASSWORD_HASH','0','0','todo-config-e2e',sysdate(),'TEST_ONLY|Todo configuration real E2E'
where not exists(select 1 from sys_user where user_name='todo_config_admin' and del_flag='0');
update sys_user set password='TODO_CONFIG_E2E_PASSWORD_HASH',status='0',user_type='99',remark='TEST_ONLY|Todo configuration real E2E'
where user_name='todo_config_admin' and del_flag='0';
set @todo_config_user_id=(select user_id from sys_user where user_name='todo_config_admin' and del_flag='0' order by user_id limit 1);

insert ignore into sys_user_role(user_id,role_id) values(@todo_config_user_id,@todo_config_role_id);

-- Grant only the six configuration pages, their operation buttons, and their parent directory.
insert ignore into sys_role_menu(role_id,menu_id)
select @todo_config_role_id,m.menu_id from sys_menu m
where m.status='0' and (
  (m.menu_type='M' and m.path='todo-engine')
  or m.component in ('todo/config/template/index','todo/config/trigger/index','todo/config/sla/index',
                     'todo/config/dod/index','todo/config/simulation/index','todo/config/release/index')
  or m.parent_id in (select page.menu_id from sys_menu page where page.component in (
       'todo/config/template/index','todo/config/trigger/index','todo/config/sla/index',
       'todo/config/dod/index','todo/config/simulation/index','todo/config/release/index'))
);

-- Contract markers below are intentionally explicit and are checked by the source gate.
-- todo:template:list todo:trigger:list todo:sla-rule:list todo:dod-rule:list
-- todo:simulation:simulate
-- todo:release:list

insert into todo_sla_rule(rule_code,rule_name,sla_type,duration_value,duration_unit,calendar_code,start_strategy,
  soft_remind_percent,hard_remind_percent,escalate_percent,status,version,create_by,create_time)
select 'E2E_SLA_FIRST_CONTACT_30M','E2E first contact 30 minutes','RESPONSE',30,'MINUTE','DEFAULT','TODO_CREATED',80,100,150,'0',0,'todo-config-e2e',sysdate()
where not exists(select 1 from todo_sla_rule where rule_code='E2E_SLA_FIRST_CONTACT_30M');

insert into todo_dod_rule(rule_code,rule_name,rule_type,required_fields_json,required_attachments_json,
  conditional_rules_json,validator_refs_json,error_messages_json,status,version,create_by,create_time)
select 'E2E_DOD_FIRST_CONTACT','E2E first contact completion','TASK',json_array('contactResult'),json_array(),json_array(),json_array(),json_object(),'0',0,'todo-config-e2e',sysdate()
where not exists(select 1 from todo_dod_rule where rule_code='E2E_DOD_FIRST_CONTACT');

insert into biz_lead(lead_no,lead_name,contact_name,status,pool_status,priority,owner_id,dept_id,del_flag,create_by,create_time,update_time,remark)
select 'E2E-TODO-CONFIG-LEAD','Todo configuration E2E lead','E2E contact','1','0','2',@todo_config_user_id,103,'0','todo-config-e2e',sysdate(),sysdate(),'TEST_ONLY'
where not exists(select 1 from biz_lead where lead_no='E2E-TODO-CONFIG-LEAD');
update biz_lead set owner_id=@todo_config_user_id,dept_id=103,del_flag='0'
where lead_no='E2E-TODO-CONFIG-LEAD';

-- Disable captcha only in this disposable E2E database.
update sys_config set config_value='false' where config_key='sys.account.captchaEnabled';
