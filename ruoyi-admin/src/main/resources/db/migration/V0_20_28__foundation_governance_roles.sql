insert into sys_role
(role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,status,del_flag,create_by,create_time)
select 'Foundation产品负责人','foundation_product_owner',40,'5',1,1,'0','0','flyway-v0.20.28',sysdate()
where not exists(select 1 from sys_role where role_key='foundation_product_owner');

insert into sys_role
(role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,status,del_flag,create_by,create_time)
select 'Foundation安全评审人','foundation_security_reviewer',41,'5',1,1,'0','0','flyway-v0.20.28',sysdate()
where not exists(select 1 from sys_role where role_key='foundation_security_reviewer');

insert into sys_role
(role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,status,del_flag,create_by,create_time)
select 'Foundation架构DBA评审人','foundation_arch_dba_reviewer',42,'5',1,1,'0','0','flyway-v0.20.28',sysdate()
where not exists(select 1 from sys_role where role_key='foundation_arch_dba_reviewer');

insert into sys_role
(role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,status,del_flag,create_by,create_time)
select 'Foundation QA验收人','foundation_qa_acceptor',43,'5',1,1,'0','0','flyway-v0.20.28',sysdate()
where not exists(select 1 from sys_role where role_key='foundation_qa_acceptor');

insert into sys_role
(role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,status,del_flag,create_by,create_time)
select 'Foundation独立准入评审人','foundation_independent_reviewer',44,'5',1,1,'0','0','flyway-v0.20.28',sysdate()
where not exists(select 1 from sys_role where role_key='foundation_independent_reviewer');

insert into sys_role_menu(role_id,menu_id)
select r.role_id,m.menu_id
from sys_role r
join sys_menu m on m.component='todo/config/index'
  or (r.role_key='foundation_product_owner' and m.perms in ('todo:decision:view','todo:decision:edit','todo:admission:view','todo:admission:edit'))
  or (r.role_key='foundation_security_reviewer' and m.perms in ('todo:admission:view','todo:admission:edit'))
  or (r.role_key='foundation_arch_dba_reviewer' and m.perms in ('todo:admission:view','todo:admission:edit','todo:admission:export'))
  or (r.role_key='foundation_qa_acceptor' and m.perms in ('todo:admission:view','todo:admission:edit'))
  or (r.role_key='foundation_independent_reviewer' and m.perms in ('todo:admission:view','todo:admission:edit'))
where r.role_key in ('foundation_product_owner','foundation_security_reviewer','foundation_arch_dba_reviewer',
  'foundation_qa_acceptor','foundation_independent_reviewer')
  and r.create_by='flyway-v0.20.28'
  and (m.component='todo/config/index' or m.perms in ('todo:decision:view','todo:decision:edit',
    'todo:admission:view','todo:admission:edit','todo:admission:export'))
  and not exists(select 1 from sys_role_menu existing where existing.role_id=r.role_id and existing.menu_id=m.menu_id);
