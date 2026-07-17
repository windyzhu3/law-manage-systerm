-- Definition management capabilities are intentionally separate. This migration creates no
-- sys_role_menu grants: ordinary todo handlers therefore receive no publish capability.
set @definition_menu=(select menu_id from sys_menu where component='todo/config/index' order by menu_id limit 1);
set @fallback_menu=(select menu_id from sys_menu where perms='todo:list' order by menu_id limit 1);
set @permission_parent=coalesce(@definition_menu,@fallback_menu);

insert into sys_menu(menu_name,parent_id,order_num,path,component,`query`,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
select x.name,@permission_parent,x.ord,'#','',null,null,1,0,'F','0','0',x.perm,'#','admin',sysdate() from (
 select 'Definition view' name,40 ord,'todo:definition:view' perm union all
 select 'Definition preflight',41,'todo:definition:preflight' union all
 select 'Definition simulation',42,'todo:definition:simulate' union all
 select 'Definition semantic diff',43,'todo:definition:diff' union all
 select 'Decision catalog view',44,'todo:decision:view'
) x
where @permission_parent is not null
  and not exists(select 1 from sys_menu existing where existing.perms=x.perm);

-- Existing todo:definition:edit and todo:definition:publish permissions remain independent.
-- In particular, this migration never grants todo:definition:publish to any business role.
