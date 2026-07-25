-- Make file retirement relation-scoped, replay-safe, and physically asynchronous
-- after the durable metadata boundary.
alter table file_relation_action
  drop check chk_file_relation_action_type;
alter table file_relation_action
  add constraint chk_file_relation_action_type
  check (action_type in ('RELATE','REVOKE','RETIRE_RELATION','RETIRE_OBJECT'));

alter table file_storage_cleanup
  add unique key uk_file_cleanup_action_target
  (actor_id,action_id,file_object_id,target_type,target_key);

alter table file_lifecycle_audit
  add unique key uk_file_lifecycle_action_event
  (actor_id,action_id,file_object_id,relation_id,event_type);

start transaction;

set @todo_menu=(select menu_id from sys_menu where perms='todo:list' order by menu_id limit 1);
insert into sys_menu(
  menu_name,parent_id,order_num,path,component,`query`,route_name,is_frame,is_cache,
  menu_type,visible,status,perms,icon,create_by,create_time)
select 'File object retirement',@todo_menu,34,'#','',null,null,1,0,'F','0','0',
  'file:object:retire','#','migration',sysdate()
where @todo_menu is not null
  and not exists(select 1 from sys_menu where perms='file:object:retire');

insert into sys_role_menu(role_id,menu_id)
select distinct current_grant.role_id,retire_menu.menu_id
from sys_role_menu current_grant
join sys_menu relate_menu
  on relate_menu.menu_id=current_grant.menu_id
 and relate_menu.perms='file:object:relate'
join sys_menu retire_menu on retire_menu.perms='file:object:retire'
where not exists(
  select 1 from sys_role_menu existing
  where existing.role_id=current_grant.role_id
    and existing.menu_id=retire_menu.menu_id
);

insert into sys_menu(
  menu_name,parent_id,order_num,path,component,`query`,route_name,is_frame,is_cache,
  menu_type,visible,status,perms,icon,create_by,create_time)
select 'E2E backend identity',@todo_menu,99,'#','',null,null,1,0,'F','0','0',
  'foundation:e2e:identity','#','migration',sysdate()
where @todo_menu is not null
  and not exists(select 1 from sys_menu where perms='foundation:e2e:identity');

commit;
