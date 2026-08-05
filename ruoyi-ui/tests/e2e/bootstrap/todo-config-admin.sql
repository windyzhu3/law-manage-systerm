-- Test-only, idempotent bootstrap for one disposable Todo configuration E2E run.
-- All TODO_CONFIG_E2E_* placeholders are replaced by CI before execution.
set names utf8mb4 collate utf8mb4_unicode_ci;
set @run_marker='TODO_CONFIG_E2E_RUN_MARKER';
set @test_remark=concat('TEST_ONLY|TODO_CONFIG_E2E|',@run_marker);
set @sla_code='TODO_CONFIG_E2E_SLA_CODE';
set @dod_code='TODO_CONFIG_E2E_DOD_CODE';
set @lead_no='TODO_CONFIG_E2E_LEAD_NO';
set @captcha_restore_key=concat('todo.e2e.captcha.restore.',@run_marker);
set @repair_template_code=concat('E2E_TODO_CONFIG_',@run_marker,'_JOURNEY_REPAIR');
set @failed_template_code=concat('E2E_TODO_CONFIG_',@run_marker,'_JOURNEY_FAILED');
set @warning_template_code=concat('E2E_TODO_CONFIG_',@run_marker,'_JOURNEY_WARNING');
set @repair_event_type=upper(concat('E2E_SCHEMA_REPAIR_',@run_marker));
set @warning_decision_code=concat('E2E_ADVISORY_',@run_marker);
set @runtime_policy_code=concat('TODO_E2E_POLICY_',left(sha2(@run_marker,256),32));

delimiter //
drop procedure if exists todo_config_e2e_bootstrap_guard//
create procedure todo_config_e2e_bootstrap_guard()
begin
  if database() <> 'TODO_CONFIG_E2E_DATABASE' or database() not regexp '_e2e$' then
    signal sqlstate '45000' set message_text='Unsafe Todo configuration E2E bootstrap database';
  end if;
  if exists(select 1 from sys_role where role_key='todo_config_admin' and del_flag='0' and coalesce(remark,'') not like 'TEST_ONLY|TODO_CONFIG_E2E|%') then
    signal sqlstate '45000' set message_text='todo_config_admin role collides with a non-test role';
  end if;
  if exists(select 1 from sys_user where user_name='todo_config_admin' and del_flag='0' and coalesce(remark,'') not like 'TEST_ONLY|TODO_CONFIG_E2E|%') then
    signal sqlstate '45000' set message_text='todo_config_admin user collides with a non-test user';
  end if;
  if exists(select 1 from sys_role where role_key in ('todo_business_admin','todo_resource_admin','todo_publisher','todo_auditor')
      and del_flag='0' and coalesce(remark,'') not like 'TEST_ONLY|TODO_CONFIG_E2E|%') then
    signal sqlstate '45000' set message_text='Todo journey role collides with a non-test role';
  end if;
  if exists(select 1 from sys_user where user_name in ('todo_business_admin','todo_resource_admin','todo_publisher','todo_auditor')
      and del_flag='0' and coalesce(remark,'') not like 'TEST_ONLY|TODO_CONFIG_E2E|%') then
    signal sqlstate '45000' set message_text='Todo journey user collides with a non-test user';
  end if;
  if exists(select 1 from todo_sla_rule where rule_code=@sla_code and coalesce(create_by,'')<>coalesce(@run_marker,'')) then
    signal sqlstate '45000' set message_text='E2E SLA code collides with another resource';
  end if;
  if exists(select 1 from todo_dod_rule where rule_code=@dod_code and coalesce(create_by,'')<>coalesce(@run_marker,'')) then
    signal sqlstate '45000' set message_text='E2E DoD code collides with another resource';
  end if;
  if exists(select 1 from biz_lead where lead_no=@lead_no and (coalesce(create_by,'')<>coalesce(@run_marker,'') or coalesce(remark,'')<>coalesce(@test_remark,''))) then
    signal sqlstate '45000' set message_text='E2E lead number collides with another resource';
  end if;
  if exists(select 1 from sys_config where config_key=@captcha_restore_key and (coalesce(create_by,'')<>coalesce(@run_marker,'') or coalesce(remark,'')<>coalesce(@test_remark,''))) then
    signal sqlstate '45000' set message_text='E2E captcha restore key collides with another resource';
  end if;
  if exists(select 1 from todo_template where template_code in (@repair_template_code,@failed_template_code,@warning_template_code)
      and coalesce(create_by,'')<>'todo_config_admin') then
    signal sqlstate '45000' set message_text='Todo journey fixture collides with another template';
  end if;
  if exists(select 1 from todo_event_catalog where event_type=@repair_event_type and coalesce(create_by,'')<>coalesce(@run_marker,'')) then
    signal sqlstate '45000' set message_text='Todo journey repair event collides with another resource';
  end if;
  if exists(select 1 from todo_decision where decision_code=@warning_decision_code and coalesce(create_by,'')<>coalesce(@run_marker,'')) then
    signal sqlstate '45000' set message_text='Todo journey advisory decision collides with another resource';
  end if;
  if exists(select 1 from biz_lead_assignment_policy where policy_code=@runtime_policy_code and coalesce(create_by,'')<>coalesce(@run_marker,'')) then
    signal sqlstate '45000' set message_text='Todo runtime assignment policy collides with another resource';
  end if;
end//
delimiter ;

call todo_config_e2e_bootstrap_guard();

insert into sys_role(role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,status,del_flag,create_by,create_time,remark)
select 'Todo E2E admin','todo_config_admin',90,'3',1,1,'0','0',@run_marker,sysdate(),@test_remark
where not exists(select 1 from sys_role where role_key='todo_config_admin' and del_flag='0');
update sys_role set status='0',data_scope='3',create_by=@run_marker,remark=@test_remark
where role_key='todo_config_admin' and del_flag='0' and remark like 'TEST_ONLY|TODO_CONFIG_E2E|%';
set @todo_config_role_id=(select role_id from sys_role where role_key='todo_config_admin' and del_flag='0' and create_by=@run_marker and remark=@test_remark order by role_id limit 1);

insert into sys_user(dept_id,user_name,nick_name,user_type,email,phonenumber,sex,avatar,password,pwd_update_date,status,del_flag,create_by,create_time,remark)
select 103,'todo_config_admin','Todo E2E admin','99','','','0','','TODO_CONFIG_E2E_PASSWORD_HASH',sysdate(),'0','0',@run_marker,sysdate(),@test_remark
where not exists(select 1 from sys_user where user_name='todo_config_admin' and del_flag='0');
update sys_user set password='TODO_CONFIG_E2E_PASSWORD_HASH',pwd_update_date=sysdate(),status='0',user_type='99',create_by=@run_marker,remark=@test_remark
where user_name='todo_config_admin' and del_flag='0' and remark like 'TEST_ONLY|TODO_CONFIG_E2E|%';
set @todo_config_user_id=(select user_id from sys_user where user_name='todo_config_admin' and del_flag='0' and create_by=@run_marker and remark=@test_remark order by user_id limit 1);

insert ignore into sys_user_role(user_id,role_id) values(@todo_config_user_id,@todo_config_role_id);

-- Named least-privilege identities used by deterministic journey boundary scenarios.
insert into sys_role(role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,status,del_flag,create_by,create_time,remark)
select x.role_name,x.role_key,x.role_sort,'5',1,1,'0','0',@run_marker,sysdate(),@test_remark
from (
  select 'Todo E2E business admin' role_name,'todo_business_admin' role_key,91 role_sort union all
  select 'Todo E2E resource admin','todo_resource_admin',92 union all
  select 'Todo E2E publisher','todo_publisher',93 union all
  select 'Todo E2E auditor','todo_auditor',94
) x where not exists(select 1 from sys_role existing where existing.role_key=x.role_key and existing.del_flag='0');
update sys_role set status='0',create_by=@run_marker,remark=@test_remark
where role_key in ('todo_business_admin','todo_resource_admin','todo_publisher','todo_auditor')
  and del_flag='0' and remark like 'TEST_ONLY|TODO_CONFIG_E2E|%';

insert into sys_user(dept_id,user_name,nick_name,user_type,email,phonenumber,sex,avatar,password,pwd_update_date,status,del_flag,create_by,create_time,remark)
select 103,x.user_name,x.nick_name,'99','','','0','','TODO_CONFIG_E2E_PASSWORD_HASH',sysdate(),'0','0',@run_marker,sysdate(),@test_remark
from (
  select 'todo_business_admin' user_name,'Todo E2E business admin' nick_name union all
  select 'todo_resource_admin','Todo E2E resource admin' union all
  select 'todo_publisher','Todo E2E publisher' union all
  select 'todo_auditor','Todo E2E auditor'
) x where not exists(select 1 from sys_user existing where existing.user_name=x.user_name and existing.del_flag='0');
update sys_user set password='TODO_CONFIG_E2E_PASSWORD_HASH',pwd_update_date=sysdate(),status='0',user_type='99',
  create_by=@run_marker,remark=@test_remark
where user_name in ('todo_business_admin','todo_resource_admin','todo_publisher','todo_auditor')
  and del_flag='0' and remark like 'TEST_ONLY|TODO_CONFIG_E2E|%';

insert ignore into sys_user_role(user_id,role_id)
select u.user_id,r.role_id from sys_user u join sys_role r on r.role_key=u.user_name
where u.user_name in ('todo_business_admin','todo_resource_admin','todo_publisher','todo_auditor')
  and u.create_by=@run_marker and u.remark=@test_remark and r.create_by=@run_marker and r.remark=@test_remark;

-- Isolate runtime leads in a disposable child department. The publisher is the
-- department leader so TD-002 supervisor routing resolves deterministically.
set @runtime_dept_code=concat('TODO_E2E_',@run_marker);
insert into sys_dept(parent_id,ancestors,dept_name,dept_code,order_num,leader,status,del_flag,create_by,create_time)
select 103,'0,100,101,103',concat('Todo E2E ',left(@run_marker,20)),@runtime_dept_code,99,
  'todo_publisher','0','0',@run_marker,sysdate()
where not exists(select 1 from sys_dept where dept_code=@runtime_dept_code);
set @runtime_dept_id=(select dept_id from sys_dept where dept_code=@runtime_dept_code and create_by=@run_marker limit 1);
update sys_user set dept_id=@runtime_dept_id
where user_name='todo_config_admin' and create_by=@run_marker and remark=@test_remark;

-- Runtime TD-003 and TD-004 schedules require an immutable assignment-policy
-- snapshot for the disposable sales department.
set @runtime_td003_version_id=(select v.version_id from todo_template t
  join todo_template_version v on v.template_id=t.template_id
  where t.template_code='TD-003' and v.status='PUBLISHED'
  order by v.version_no desc,v.version_id desc limit 1);
insert into biz_lead_assignment_policy(policy_code,policy_name,sales_dept_id,source_code,business_type,
  retry_rule_json,status,row_version,create_by,create_time)
select @runtime_policy_code,concat('Todo E2E runtime policy - ',left(@run_marker,20)),@runtime_dept_id,'*','LEAD',
  json_object(
    'templateVersionId',@runtime_td003_version_id,'ruleVersionId',@runtime_td003_version_id,
    'timezone','Asia/Shanghai','windows',json_array(
      json_object('windowCode','T0','windowOrder',0,'dayOffset',0,'startOffsetMinutes',0,'durationMinutes',120,'maxAttempts',3,'occurrenceNo',1),
      json_object('windowCode','T1_AM','windowOrder',1,'dayOffset',1,'startTime','09:00:00','endTime','11:00:00','maxAttempts',1,'occurrenceNo',1),
      json_object('windowCode','T1_NOON','windowOrder',2,'dayOffset',1,'startTime','12:00:00','endTime','14:00:00','maxAttempts',1,'occurrenceNo',1),
      json_object('windowCode','T1_PM','windowOrder',3,'dayOffset',1,'startTime','15:00:00','endTime','18:00:00','maxAttempts',1,'occurrenceNo',1),
      json_object('windowCode','T2_AM','windowOrder',4,'dayOffset',2,'startTime','09:00:00','endTime','11:00:00','maxAttempts',1,'occurrenceNo',1),
      json_object('windowCode','T2_NOON','windowOrder',5,'dayOffset',2,'startTime','12:00:00','endTime','14:00:00','maxAttempts',1,'occurrenceNo',1),
      json_object('windowCode','T2_PM','windowOrder',6,'dayOffset',2,'startTime','15:00:00','endTime','18:00:00','maxAttempts',1,'occurrenceNo',1)
    )
  ),'ACTIVE',0,@run_marker,sysdate()
where @runtime_dept_id is not null and @runtime_td003_version_id is not null
  and not exists(select 1 from biz_lead_assignment_policy where policy_code=@runtime_policy_code);
set @runtime_policy_id=(select policy_id from biz_lead_assignment_policy
  where policy_code=@runtime_policy_code and create_by=@run_marker limit 1);
insert ignore into biz_lead_assignment_policy_candidate(policy_id,user_id,sort_order,status,create_by,create_time)
values(@runtime_policy_id,@todo_config_user_id,0,'ACTIVE',@run_marker,sysdate());

-- Grant only the six configuration pages, their operation buttons, and their parent directory.
insert ignore into sys_role_menu(role_id,menu_id)
select @todo_config_role_id,m.menu_id from sys_menu m
where m.status='0' and (
  (m.menu_type='M' and m.path='todo-engine')
  or m.component in ('todo/index','todo/config/template/index','todo/config/journey/index','todo/config/trigger/index','todo/config/sla/index',
                     'todo/config/dod/index','todo/config/simulation/index','todo/config/release/index','todo/config/resource/index')
  or m.perms in ('lead:mine:query','lead:assign','lead:first-contact:handle',
                 'lead:invalid-review:handle','lead:retry:handle',
                 'todo:list','todo:query','todo:claim','todo:start','todo:submit','todo:complete',
                 'todo:definition:publish')
  or m.parent_id in (select page.menu_id from sys_menu page where page.component in (
       'todo/config/template/index','todo/config/journey/index','todo/config/trigger/index','todo/config/sla/index',
       'todo/config/dod/index','todo/config/simulation/index','todo/config/release/index','todo/config/resource/index'))
);

-- Business admin edits and simulates drafts, resource admin governs catalogues,
-- publisher executes the release gate, and auditor has immutable read/diff access.
insert ignore into sys_role_menu(role_id,menu_id)
select r.role_id,m.menu_id
from sys_role r join sys_menu m
where r.role_key in ('todo_business_admin','todo_resource_admin','todo_publisher','todo_auditor')
  and r.create_by=@run_marker and r.remark=@test_remark and m.status='0'
  and (
    (m.menu_type='M' and m.path='todo-engine')
    or (r.role_key='todo_business_admin' and (
      m.component in ('todo/config/template/index','todo/config/journey/index','todo/config/simulation/index')
      or m.perms in ('todo:template:list','todo:template:create','todo:template:edit','todo:template:copy',
                     'todo:simulation:list','todo:simulation:simulate')))
    or (r.role_key='todo_resource_admin' and (
      m.component='todo/config/resource/index'
      or m.perms in ('todo:resource:list','todo:resource:query','todo:resource:add','todo:resource:edit','todo:resource:status')))
    or (r.role_key='todo_publisher' and (
      m.component in ('todo/index','todo/config/template/index','todo/config/journey/index','todo/config/simulation/index','todo/config/release/index')
      or m.perms in ('todo:template:list','todo:simulation:list','todo:simulation:simulate',
                     'todo:release:list','todo:release:publish','todo:release:diff','todo:definition:diff','todo:definition:publish',
                     'todo:list','todo:query','todo:claim','todo:start','todo:submit','todo:complete','lead:invalid-review:handle')))
    or (r.role_key='todo_auditor' and (
      m.component in ('todo/config/template/index','todo/config/journey/index','todo/config/release/index')
      or m.perms in ('todo:template:list','todo:release:list','todo:release:diff','todo:definition:diff')))
  );

-- Contract markers below are intentionally explicit and are checked by the source gate.
-- todo:template:list todo:trigger:list todo:sla-rule:list todo:dod-rule:list
-- todo:simulation:simulate
-- todo:release:list

insert into todo_sla_rule(rule_code,rule_name,sla_type,duration_value,duration_unit,calendar_code,start_strategy,
  soft_remind_percent,hard_remind_percent,escalate_percent,status,version,create_by,create_time)
select @sla_code,concat('E2E first contact 30 minutes - ',@run_marker),'RESPONSE',30,'MINUTE','DEFAULT','TODO_CREATED',80,100,150,'0',0,@run_marker,sysdate()
where not exists(select 1 from todo_sla_rule where rule_code=@sla_code and create_by=@run_marker);

insert into todo_dod_rule(rule_code,rule_name,rule_type,required_fields_json,required_attachments_json,
  conditional_rules_json,validator_refs_json,error_messages_json,status,version,create_by,create_time)
select @dod_code,concat('E2E first contact completion - ',@run_marker),'TASK',json_array('contactResult'),json_array(),json_array(),json_array(),json_object(),'0',0,@run_marker,sysdate()
where not exists(select 1 from todo_dod_rule where rule_code=@dod_code and create_by=@run_marker);

insert into biz_lead(lead_no,lead_name,contact_name,status,pool_status,priority,owner_id,dept_id,del_flag,create_by,create_time,update_time,remark)
select @lead_no,concat('Todo configuration E2E lead - ',@run_marker),'E2E contact','1','0','2',@todo_config_user_id,103,'0',@run_marker,sysdate(),sysdate(),@test_remark
where not exists(select 1 from biz_lead where lead_no=@lead_no and create_by=@run_marker and remark=@test_remark);
update todo_sla_rule set rule_name=concat('E2E first contact 30 minutes - ',@run_marker)
where rule_code=@sla_code and create_by=@run_marker;
update todo_dod_rule set rule_name=concat('E2E first contact completion - ',@run_marker)
where rule_code=@dod_code and create_by=@run_marker;
update biz_lead set lead_name=concat('Todo configuration E2E lead - ',@run_marker)
where lead_no=@lead_no and create_by=@run_marker and remark=@test_remark;

-- Deterministic journey fixtures. Source definitions remain immutable; every fixture is a disposable draft.
set @source_version_id=(select v.version_id from todo_template t join todo_template_version v on v.template_id=t.template_id
  where t.template_code='LEAD_FIRST_CONTACT' and v.status='PUBLISHED'
  order by v.version_no desc,v.version_id desc limit 1);

insert into todo_event_catalog(event_type,event_name,description,payload_version,business_object_type,payload_schema_json,
  owner_field_paths_json,condition_field_paths_json,default_value_field_paths_json,producer,source_module,
  sample_payload_json,schema_status,version,status,create_by,create_time)
select @repair_event_type,concat('E2E schema repair - ',@run_marker),'Disposable incomplete event schema',1,'LEAD',
  json_object('type','object','properties',json_object()),json_array(),json_array(),json_array(),
  'todo-e2e','lead',json_object(),'INCOMPLETE',0,'ACTIVE',@run_marker,sysdate()
where not exists(select 1 from todo_event_catalog where event_type=@repair_event_type and payload_version=1);

insert into todo_decision(decision_code,title,description,blocking,status,create_by,create_time)
select @warning_decision_code,concat('E2E publication advisory - ',@run_marker),
  'Disposable non-blocking decision that requires an explicit release review','N','OPEN',@run_marker,sysdate()
where not exists(select 1 from todo_decision where decision_code=@warning_decision_code);
update todo_event_catalog
set event_name=concat('E2E schema repair - ',@run_marker)
where event_type=@repair_event_type and payload_version=1 and create_by=@run_marker;
update todo_decision
set title=concat('E2E publication advisory - ',@run_marker)
where decision_code=@warning_decision_code and create_by=@run_marker;

insert into todo_template(template_code,template_name,business_type,current_version,status,create_by,create_time)
select x.template_code,x.template_name,'LEAD',1,'0','todo_config_admin',sysdate()
from (
  select @repair_template_code template_code,concat('E2E journey repair - ',@run_marker) template_name union all
  select @failed_template_code,concat('E2E journey failed simulation - ',@run_marker) union all
  select @warning_template_code,concat('E2E journey warning review - ',@run_marker)
) x where not exists(select 1 from todo_template existing where existing.template_code=x.template_code);
update todo_template
set template_name=case template_code
  when @repair_template_code then concat('E2E journey repair - ',@run_marker)
  when @failed_template_code then concat('E2E journey failed simulation - ',@run_marker)
  else concat('E2E journey warning review - ',@run_marker)
end
where template_code in (@repair_template_code,@failed_template_code,@warning_template_code)
  and create_by='todo_config_admin';

insert into todo_template_version(template_id,version_no,status,source_version_id,owner_rule_json,dod_rule_json,
  sla_rule_json,next_rule_json,ui_schema_json,change_summary,impact_scope,rollback_source_version_id,
  definition_schema_version,definition_json,compiled_json,definition_hash,validation_report_json,
  published_by,published_time,update_by,update_time)
select target.template_id,1,'DRAFT',source.version_id,source.owner_rule_json,source.dod_rule_json,
  source.sla_rule_json,source.next_rule_json,source.ui_schema_json,'Deterministic real-backend E2E fixture',
  'Todo journey simulation and publish',source.version_id,source.definition_schema_version,
  case target.template_code
    when @repair_template_code then json_set(source.definition_json,'$.templateCode',target.template_code,
      '$.event.eventType',@repair_event_type,'$.event.payloadVersion',1)
    when @failed_template_code then json_set(source.definition_json,'$.templateCode',target.template_code,
      '$.event.condition',json_object('$expression',json_object('version',1,'root',
        json_object('field','ownerId','operator','EQ','value',-999999999))))
    else json_set(source.definition_json,'$.templateCode',target.template_code,
      '$.decisionRefs',json_array(@warning_decision_code))
  end,
  null,null,null,null,null,'todo_config_admin',sysdate()
from todo_template target join todo_template_version source on source.version_id=@source_version_id
where target.template_code in (@repair_template_code,@failed_template_code,@warning_template_code)
  and not exists(select 1 from todo_template_version existing where existing.template_id=target.template_id);

-- Disable captcha only in this guarded disposable E2E database.
insert into sys_config(config_name,config_key,config_value,config_type,create_by,create_time,remark)
select 'Todo E2E captcha restore',@captcha_restore_key,config_value,'N',@run_marker,sysdate(),@test_remark
from sys_config where config_key='sys.account.captchaEnabled'
  and not exists(select 1 from sys_config restore where restore.config_key=@captcha_restore_key);
update sys_config set config_value='false' where config_key='sys.account.captchaEnabled';

call todo_config_e2e_bootstrap_guard();
drop procedure todo_config_e2e_bootstrap_guard;
