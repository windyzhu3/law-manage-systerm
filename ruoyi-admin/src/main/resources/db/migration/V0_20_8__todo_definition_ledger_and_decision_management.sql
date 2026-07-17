alter table todo_definition_action
  add column action_status varchar(16) not null default 'APPLIED' after action_type,
  add column request_fingerprint varchar(64) null after action_status,
  add column operator_dept_id bigint null after operator_name,
  add key idx_todo_definition_action_fingerprint(request_fingerprint,action_status);

alter table todo_definition_action
  add constraint chk_todo_definition_governed_action
    check (action_type not in ('ROLLBACK_DRAFT','CREATE_DECISION','UPDATE_DECISION')
      or (request_fingerprint is not null and action_status in ('CLAIMED','APPLIED')));

alter table todo_decision
  add column resolution varchar(2000) null after conclusion,
  add column version int not null default 0 after update_time;

set @definition_menu=(select menu_id from sys_menu where component='todo/config/index' order by menu_id limit 1);
set @fallback_menu=(select menu_id from sys_menu where perms='todo:list' order by menu_id limit 1);
set @permission_parent=coalesce(@definition_menu,@fallback_menu);

insert into sys_menu(menu_name,parent_id,order_num,path,component,`query`,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
select 'Decision edit',@permission_parent,45,'#','',null,null,1,0,'F','0','0','todo:decision:edit','#','admin',sysdate()
where @permission_parent is not null
  and not exists(select 1 from sys_menu existing where existing.perms='todo:decision:edit');

-- No role grant is created here. Decision editing remains independently assignable.
