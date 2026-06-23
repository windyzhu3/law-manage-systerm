-- 案件中心菜单与角色授权补丁脚本，可重复执行
-- 适用场景：案件中心表和接口已初始化，但页面菜单未显示或角色未分配 matter:* 权限。

insert into sys_menu(menu_name,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
select '案件中心',0,8,'matter',null,'','Matter',1,0,'M','0','0','','documentation','admin',sysdate(),'案件中心'
where not exists(select 1 from sys_menu where parent_id=0 and path='matter');

set @matter_menu_id = (select menu_id from sys_menu where parent_id=0 and path='matter' limit 1);

update sys_menu
set menu_name='案件中心',
    order_num=8,
    path='matter',
    component=null,
    query='',
    route_name='Matter',
    is_frame=1,
    is_cache=0,
    menu_type='M',
    visible='0',
    status='0',
    perms='',
    icon='documentation',
    remark='案件中心',
    update_by='admin',
    update_time=sysdate()
where menu_id=@matter_menu_id;

insert into sys_menu(menu_name,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
select item.menu_name,@matter_menu_id,item.order_num,item.path,'matter/index',item.query,item.route_name,1,0,'C','0','0',item.perms,item.icon,'admin',sysdate(),item.menu_name
from (
 select '案件列表' menu_name,1 order_num,'list' path,'{"module":"list"}' query,'MatterList' route_name,'matter:list' perms,'list' icon union all
 select '我的案件',2,'mine','{"module":"mine"}','MatterMine','matter:list','user' union all
 select '进度记录',3,'progress','{"module":"progress"}','MatterProgress','matter:progress:list','time' union all
 select '关键节点',4,'node','{"module":"node"}','MatterNode','matter:node:list','date' union all
 select '费用管理',5,'expense','{"module":"expense"}','MatterExpense','matter:expense:list','money' union all
 select '文档资料',6,'document','{"module":"document"}','MatterDocument','matter:document:list','documentation' union all
 select '结案归档',7,'archive','{"module":"archive"}','MatterArchive','matter:archive:list','folder' union all
 select '状态记录',8,'status','{"module":"status"}','MatterStatus','matter:status:list','time'
) item
where @matter_menu_id is not null
  and not exists(select 1 from sys_menu where parent_id=@matter_menu_id and path=item.path);

update sys_menu m
join (
 select '案件列表' menu_name,1 order_num,'list' path,'{"module":"list"}' query,'MatterList' route_name,'matter:list' perms,'list' icon union all
 select '我的案件',2,'mine','{"module":"mine"}','MatterMine','matter:list','user' union all
 select '进度记录',3,'progress','{"module":"progress"}','MatterProgress','matter:progress:list','time' union all
 select '关键节点',4,'node','{"module":"node"}','MatterNode','matter:node:list','date' union all
 select '费用管理',5,'expense','{"module":"expense"}','MatterExpense','matter:expense:list','money' union all
 select '文档资料',6,'document','{"module":"document"}','MatterDocument','matter:document:list','documentation' union all
 select '结案归档',7,'archive','{"module":"archive"}','MatterArchive','matter:archive:list','folder' union all
 select '状态记录',8,'status','{"module":"status"}','MatterStatus','matter:status:list','time'
) item on item.path=m.path
set m.menu_name=item.menu_name,
    m.parent_id=@matter_menu_id,
    m.order_num=item.order_num,
    m.component='matter/index',
    m.query=item.query,
    m.route_name=item.route_name,
    m.is_frame=1,
    m.is_cache=0,
    m.menu_type='C',
    m.visible='0',
    m.status='0',
    m.perms=item.perms,
    m.icon=item.icon,
    m.remark=item.menu_name,
    m.update_by='admin',
    m.update_time=sysdate()
where @matter_menu_id is not null
  and m.parent_id=@matter_menu_id;

insert into sys_menu(menu_name,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
select item.menu_name, m.menu_id, item.order_num, '#', '', '', '', 1, 0, 'F', '0', '0', item.perms, '#', 'admin', sysdate(), item.menu_name
from sys_menu m
join (
 select '案件查询' menu_name,'matter:list' parent_perm,1 order_num,'matter:query' perms union all
 select '新增案件','matter:list',2,'matter:add' union all
 select '编辑案件','matter:list',3,'matter:edit' union all
 select '导入案件','matter:list',4,'matter:import' union all
 select '导出案件','matter:list',5,'matter:export' union all
 select '新增进度','matter:progress:list',1,'matter:progress:add' union all
 select '编辑进度','matter:progress:list',2,'matter:progress:edit' union all
 select '删除进度','matter:progress:list',3,'matter:progress:remove' union all
 select '新增节点','matter:node:list',1,'matter:node:add' union all
 select '编辑节点','matter:node:list',2,'matter:node:edit' union all
 select '删除节点','matter:node:list',3,'matter:node:remove' union all
 select '发送提醒','matter:node:list',4,'matter:node:remind' union all
 select '新增费用','matter:expense:list',1,'matter:expense:add' union all
 select '编辑费用','matter:expense:list',2,'matter:expense:edit' union all
 select '删除费用','matter:expense:list',3,'matter:expense:remove' union all
 select '新增文档','matter:document:list',1,'matter:document:add' union all
 select '删除文档','matter:document:list',2,'matter:document:remove' union all
 select '结案申请','matter:archive:list',1,'matter:archive:apply' union all
 select '确认归档','matter:archive:list',2,'matter:archive:confirm'
) item on m.perms=item.parent_perm
where @matter_menu_id is not null
  and m.parent_id=@matter_menu_id
  and not exists(select 1 from sys_menu where perms=item.perms);

update sys_menu btn
join (
 select '案件查询' menu_name,'matter:list' parent_perm,1 order_num,'matter:query' perms union all
 select '新增案件','matter:list',2,'matter:add' union all
 select '编辑案件','matter:list',3,'matter:edit' union all
 select '导入案件','matter:list',4,'matter:import' union all
 select '导出案件','matter:list',5,'matter:export' union all
 select '新增进度','matter:progress:list',1,'matter:progress:add' union all
 select '编辑进度','matter:progress:list',2,'matter:progress:edit' union all
 select '删除进度','matter:progress:list',3,'matter:progress:remove' union all
 select '新增节点','matter:node:list',1,'matter:node:add' union all
 select '编辑节点','matter:node:list',2,'matter:node:edit' union all
 select '删除节点','matter:node:list',3,'matter:node:remove' union all
 select '发送提醒','matter:node:list',4,'matter:node:remind' union all
 select '新增费用','matter:expense:list',1,'matter:expense:add' union all
 select '编辑费用','matter:expense:list',2,'matter:expense:edit' union all
 select '删除费用','matter:expense:list',3,'matter:expense:remove' union all
 select '新增文档','matter:document:list',1,'matter:document:add' union all
 select '删除文档','matter:document:list',2,'matter:document:remove' union all
 select '结案申请','matter:archive:list',1,'matter:archive:apply' union all
 select '确认归档','matter:archive:list',2,'matter:archive:confirm'
) item on item.perms=btn.perms
join sys_menu parent_menu on parent_menu.perms=item.parent_perm and parent_menu.parent_id=@matter_menu_id
set btn.menu_name=item.menu_name,
    btn.parent_id=parent_menu.menu_id,
    btn.order_num=item.order_num,
    btn.path='#',
    btn.component='',
    btn.query='',
    btn.route_name='',
    btn.is_frame=1,
    btn.is_cache=0,
    btn.menu_type='F',
    btn.visible='0',
    btn.status='0',
    btn.icon='#',
    btn.remark=item.menu_name,
    btn.update_by='admin',
    btn.update_time=sysdate()
where @matter_menu_id is not null;

set @case_manager_role_id=(select role_id from sys_role where role_key='case_manager' and del_flag='0' limit 1);
set @lawyer_role_id=(select role_id from sys_role where role_key='lawyer' and del_flag='0' limit 1);
set @intern_lawyer_role_id=(select role_id from sys_role where role_key='intern_lawyer' and del_flag='0' limit 1);
set @law_partner_manager_role_id=(select role_id from sys_role where role_key='law_partner_manager' and del_flag='0' limit 1);

insert into sys_role_menu(role_id,menu_id)
select @case_manager_role_id,m.menu_id
from sys_menu m
where @case_manager_role_id is not null
  and (m.menu_id=@matter_menu_id or m.parent_id=@matter_menu_id or m.perms like 'matter:%')
  and not exists(select 1 from sys_role_menu rm where rm.role_id=@case_manager_role_id and rm.menu_id=m.menu_id);

insert into sys_role_menu(role_id,menu_id)
select @law_partner_manager_role_id,m.menu_id
from sys_menu m
where @law_partner_manager_role_id is not null
  and (m.menu_id=@matter_menu_id or m.parent_id=@matter_menu_id or m.perms like 'matter:%')
  and not exists(select 1 from sys_role_menu rm where rm.role_id=@law_partner_manager_role_id and rm.menu_id=m.menu_id);

insert into sys_role_menu(role_id,menu_id)
select @lawyer_role_id,m.menu_id
from sys_menu m
where @lawyer_role_id is not null
  and (
    m.menu_id=@matter_menu_id
    or (m.parent_id=@matter_menu_id and m.path in ('list','mine','progress','node','expense','document','archive','status'))
    or m.perms in (
      'matter:list','matter:query',
      'matter:progress:list','matter:progress:add','matter:progress:edit','matter:progress:remove',
      'matter:node:list','matter:node:add','matter:node:edit','matter:node:remove','matter:node:remind',
      'matter:expense:list','matter:expense:add','matter:expense:edit','matter:expense:remove',
      'matter:document:list','matter:document:add','matter:document:remove',
      'matter:archive:list','matter:archive:apply','matter:archive:confirm',
      'matter:status:list'
    )
  )
  and not exists(select 1 from sys_role_menu rm where rm.role_id=@lawyer_role_id and rm.menu_id=m.menu_id);

insert into sys_role_menu(role_id,menu_id)
select @intern_lawyer_role_id,m.menu_id
from sys_menu m
where @intern_lawyer_role_id is not null
  and (
    m.menu_id=@matter_menu_id
    or (m.parent_id=@matter_menu_id and m.path in ('list','mine','progress','node','document','status'))
    or m.perms in (
      'matter:list','matter:query',
      'matter:progress:list','matter:progress:add',
      'matter:node:list',
      'matter:document:list',
      'matter:status:list'
    )
  )
  and not exists(select 1 from sys_role_menu rm where rm.role_id=@intern_lawyer_role_id and rm.menu_id=m.menu_id);
