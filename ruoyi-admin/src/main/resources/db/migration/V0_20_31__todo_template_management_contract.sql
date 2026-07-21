alter table todo_template
  add column version int not null default 0 after status,
  add key idx_todo_template_configuration_list(status,business_type,update_time,template_id);

insert into sys_menu(menu_name,parent_id,order_num,path,component,`query`,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
select x.menu_name,page.menu_id,x.order_num,'#','',null,null,1,0,'F','0','0',x.perms,'#','admin',sysdate()
from (
  select '导入模板' menu_name,13 order_num,'todo:template:import' perms union all
  select '启停模板',14,'todo:template:toggle'
) x
join sys_menu page on page.component='todo/config/template/index'
where not exists(select 1 from sys_menu existing where existing.perms=x.perms);
