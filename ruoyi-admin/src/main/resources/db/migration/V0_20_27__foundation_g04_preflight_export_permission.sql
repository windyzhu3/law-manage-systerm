set @definition_menu=(select menu_id from sys_menu where component='todo/config/index' order by menu_id limit 1);
set @fallback_menu=(select menu_id from sys_menu where perms='todo:list' order by menu_id limit 1);
set @permission_parent=coalesce(@definition_menu,@fallback_menu);
set @permission_code='todo:admission:export';
insert into sys_menu(menu_name,parent_id,order_num,path,component,`query`,route_name,is_frame,is_cache,
  menu_type,visible,status,perms,icon,create_by,create_time)
select 'Historical migration evidence export',@permission_parent,48,'#','',null,null,1,0,'F','0','0',
  @permission_code,'#','admin',sysdate()
where @permission_parent is not null
  and not exists(select 1 from sys_menu where perms=@permission_code);
