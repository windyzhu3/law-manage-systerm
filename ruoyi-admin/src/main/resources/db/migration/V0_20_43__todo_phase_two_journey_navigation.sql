-- Add the phase-two journey as a hidden compatibility sibling without changing
-- the existing /todo-engine/todo-template or resource-center navigation rows.
set @todo_engine_directory_id=(
  select parent_id
  from sys_menu
  where component='todo/config/template/index'
  order by menu_id
  limit 1
);

insert into sys_menu(
  menu_name,parent_id,order_num,path,component,`query`,route_name,is_frame,is_cache,
  menu_type,visible,status,perms,icon,create_by,create_time
)
select '模板配置旅程',@todo_engine_directory_id,2,'todo-template-journey',
       'todo/config/journey/index',null,'TodoTemplateJourney',1,0,
       'C','1','0','todo:template:list','list','admin',sysdate()
where @todo_engine_directory_id is not null
  and not exists(
    select 1 from sys_menu existing
    where existing.component='todo/config/journey/index'
  );
