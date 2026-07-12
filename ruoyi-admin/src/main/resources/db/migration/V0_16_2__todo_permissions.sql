insert into sys_menu(menu_name,parent_id,order_num,path,component,query_param,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
select '待办中心',0,4,'todo','todo/index','',null,1,0,'C','0','0','todo:list','clipboard','admin',sysdate()
where not exists(select 1 from sys_menu where perms='todo:list');
set @todo_menu=(select menu_id from sys_menu where perms='todo:list' order by menu_id limit 1);
insert into sys_menu(menu_name,parent_id,order_num,path,component,query_param,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
select x.name,@todo_menu,x.ord,'#','',null,null,1,0,'F','0','0',x.perm,'#','admin',sysdate() from (
select '待办详情' name,1 ord,'todo:query' perm union all select '领取',2,'todo:claim' union all select '开始',3,'todo:start' union all select '提交',4,'todo:submit' union all select '完成',5,'todo:complete' union all select '退回',6,'todo:return' union all select '转派',7,'todo:transfer' union all select '取消',8,'todo:cancel' union all select '模板管理',9,'todo:template:manage' union all select '日历管理',10,'todo:calendar:manage') x
where not exists(select 1 from sys_menu m where m.perms=x.perm);
