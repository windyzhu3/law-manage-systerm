-- Test-only, idempotent bootstrap for one disposable Todo configuration E2E run.
-- All TODO_CONFIG_E2E_* placeholders are replaced by CI before execution.
set names utf8mb4 collate utf8mb4_unicode_ci;
set @run_marker='TODO_CONFIG_E2E_RUN_MARKER';
set @test_remark=concat('TEST_ONLY|TODO_CONFIG_E2E|',@run_marker);
set @sla_code='TODO_CONFIG_E2E_SLA_CODE';
set @dod_code='TODO_CONFIG_E2E_DOD_CODE';
set @lead_no='TODO_CONFIG_E2E_LEAD_NO';

delimiter //
drop procedure if exists todo_config_e2e_bootstrap_guard//
create procedure todo_config_e2e_bootstrap_guard()
begin
  if database() <> 'TODO_CONFIG_E2E_DATABASE' or database() not regexp '_e2e$' then
    signal sqlstate '45000' set message_text='Unsafe Todo configuration E2E bootstrap database';
  end if;
  if exists(select 1 from sys_role where role_key='todo_config_admin' and del_flag='0' and remark not like 'TEST_ONLY|TODO_CONFIG_E2E|%') then
    signal sqlstate '45000' set message_text='todo_config_admin role collides with a non-test role';
  end if;
  if exists(select 1 from sys_user where user_name='todo_config_admin' and del_flag='0' and remark not like 'TEST_ONLY|TODO_CONFIG_E2E|%') then
    signal sqlstate '45000' set message_text='todo_config_admin user collides with a non-test user';
  end if;
  if exists(select 1 from todo_sla_rule where rule_code=@sla_code and create_by<>@run_marker) then
    signal sqlstate '45000' set message_text='E2E SLA code collides with another resource';
  end if;
  if exists(select 1 from todo_dod_rule where rule_code=@dod_code and create_by<>@run_marker) then
    signal sqlstate '45000' set message_text='E2E DoD code collides with another resource';
  end if;
  if exists(select 1 from biz_lead where lead_no=@lead_no and (create_by<>@run_marker or remark<>@test_remark)) then
    signal sqlstate '45000' set message_text='E2E lead number collides with another resource';
  end if;
end//
delimiter ;

call todo_config_e2e_bootstrap_guard();

insert into sys_role(role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,status,del_flag,create_by,create_time,remark)
select 'Todo E2E admin','todo_config_admin',90,'5',1,1,'0','0',@run_marker,sysdate(),@test_remark
where not exists(select 1 from sys_role where role_key='todo_config_admin' and del_flag='0');
update sys_role set status='0',create_by=@run_marker,remark=@test_remark
where role_key='todo_config_admin' and del_flag='0' and remark like 'TEST_ONLY|TODO_CONFIG_E2E|%';
set @todo_config_role_id=(select role_id from sys_role where role_key='todo_config_admin' and del_flag='0' and create_by=@run_marker and remark=@test_remark order by role_id limit 1);

insert into sys_user(dept_id,user_name,nick_name,user_type,email,phonenumber,sex,avatar,password,status,del_flag,create_by,create_time,remark)
select 103,'todo_config_admin','Todo E2E admin','99','','','0','','TODO_CONFIG_E2E_PASSWORD_HASH','0','0',@run_marker,sysdate(),@test_remark
where not exists(select 1 from sys_user where user_name='todo_config_admin' and del_flag='0');
update sys_user set password='TODO_CONFIG_E2E_PASSWORD_HASH',status='0',user_type='99',create_by=@run_marker,remark=@test_remark
where user_name='todo_config_admin' and del_flag='0' and remark like 'TEST_ONLY|TODO_CONFIG_E2E|%';
set @todo_config_user_id=(select user_id from sys_user where user_name='todo_config_admin' and del_flag='0' and create_by=@run_marker and remark=@test_remark order by user_id limit 1);

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
select @sla_code,concat('E2E first contact 30 minutes · ',@run_marker),'RESPONSE',30,'MINUTE','DEFAULT','TODO_CREATED',80,100,150,'0',0,@run_marker,sysdate()
where not exists(select 1 from todo_sla_rule where rule_code=@sla_code and create_by=@run_marker);

insert into todo_dod_rule(rule_code,rule_name,rule_type,required_fields_json,required_attachments_json,
  conditional_rules_json,validator_refs_json,error_messages_json,status,version,create_by,create_time)
select @dod_code,concat('E2E first contact completion · ',@run_marker),'TASK',json_array('contactResult'),json_array(),json_array(),json_array(),json_object(),'0',0,@run_marker,sysdate()
where not exists(select 1 from todo_dod_rule where rule_code=@dod_code and create_by=@run_marker);

insert into biz_lead(lead_no,lead_name,contact_name,status,pool_status,priority,owner_id,dept_id,del_flag,create_by,create_time,update_time,remark)
select @lead_no,concat('Todo configuration E2E lead · ',@run_marker),'E2E contact','1','0','2',@todo_config_user_id,103,'0',@run_marker,sysdate(),sysdate(),@test_remark
where not exists(select 1 from biz_lead where lead_no=@lead_no and create_by=@run_marker and remark=@test_remark);

-- Disable captcha only in this guarded disposable E2E database.
update sys_config set config_value='false' where config_key='sys.account.captchaEnabled';

call todo_config_e2e_bootstrap_guard();
drop procedure todo_config_e2e_bootstrap_guard;
