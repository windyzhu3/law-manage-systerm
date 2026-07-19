set @foundation_product_owner_existing_role_id=(select role_id from sys_role where role_key='foundation_product_owner' order by role_id limit 1);
insert into sys_role
(role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,status,del_flag,create_by,create_time)
select 'Foundation产品负责人','foundation_product_owner',40,'5',1,1,'0','0','flyway-v0.20.28',sysdate()
where not exists(select 1 from sys_role where role_key='foundation_product_owner');
set @foundation_product_owner_new_role_id=if(@foundation_product_owner_existing_role_id is null,(select role_id from sys_role where role_key='foundation_product_owner' order by role_id limit 1),null);

set @foundation_security_reviewer_existing_role_id=(select role_id from sys_role where role_key='foundation_security_reviewer' order by role_id limit 1);
insert into sys_role
(role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,status,del_flag,create_by,create_time)
select 'Foundation安全评审人','foundation_security_reviewer',41,'5',1,1,'0','0','flyway-v0.20.28',sysdate()
where not exists(select 1 from sys_role where role_key='foundation_security_reviewer');
set @foundation_security_reviewer_new_role_id=if(@foundation_security_reviewer_existing_role_id is null,(select role_id from sys_role where role_key='foundation_security_reviewer' order by role_id limit 1),null);

set @foundation_arch_dba_reviewer_existing_role_id=(select role_id from sys_role where role_key='foundation_arch_dba_reviewer' order by role_id limit 1);
insert into sys_role
(role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,status,del_flag,create_by,create_time)
select 'Foundation架构DBA评审人','foundation_arch_dba_reviewer',42,'5',1,1,'0','0','flyway-v0.20.28',sysdate()
where not exists(select 1 from sys_role where role_key='foundation_arch_dba_reviewer');
set @foundation_arch_dba_reviewer_new_role_id=if(@foundation_arch_dba_reviewer_existing_role_id is null,(select role_id from sys_role where role_key='foundation_arch_dba_reviewer' order by role_id limit 1),null);

set @foundation_qa_acceptor_existing_role_id=(select role_id from sys_role where role_key='foundation_qa_acceptor' order by role_id limit 1);
insert into sys_role
(role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,status,del_flag,create_by,create_time)
select 'Foundation QA验收人','foundation_qa_acceptor',43,'5',1,1,'0','0','flyway-v0.20.28',sysdate()
where not exists(select 1 from sys_role where role_key='foundation_qa_acceptor');
set @foundation_qa_acceptor_new_role_id=if(@foundation_qa_acceptor_existing_role_id is null,(select role_id from sys_role where role_key='foundation_qa_acceptor' order by role_id limit 1),null);

set @foundation_independent_reviewer_existing_role_id=(select role_id from sys_role where role_key='foundation_independent_reviewer' order by role_id limit 1);
insert into sys_role
(role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,status,del_flag,create_by,create_time)
select 'Foundation独立准入评审人','foundation_independent_reviewer',44,'5',1,1,'0','0','flyway-v0.20.28',sysdate()
where not exists(select 1 from sys_role where role_key='foundation_independent_reviewer');
set @foundation_independent_reviewer_new_role_id=if(@foundation_independent_reviewer_existing_role_id is null,(select role_id from sys_role where role_key='foundation_independent_reviewer' order by role_id limit 1),null);

insert into sys_role_menu(role_id,menu_id)
select created.role_id,m.menu_id
from (
  select @foundation_product_owner_new_role_id role_id,'foundation_product_owner' role_key union all
  select @foundation_security_reviewer_new_role_id,'foundation_security_reviewer' union all
  select @foundation_arch_dba_reviewer_new_role_id,'foundation_arch_dba_reviewer' union all
  select @foundation_qa_acceptor_new_role_id,'foundation_qa_acceptor' union all
  select @foundation_independent_reviewer_new_role_id,'foundation_independent_reviewer'
) created
join sys_menu m on m.component='todo/config/index'
  or (created.role_key='foundation_product_owner' and m.perms in ('todo:decision:view','todo:decision:edit','todo:admission:view','todo:admission:edit'))
  or (created.role_key='foundation_security_reviewer' and m.perms in ('todo:admission:view','todo:admission:edit'))
  or (created.role_key='foundation_arch_dba_reviewer' and m.perms in ('todo:admission:view','todo:admission:edit','todo:admission:export'))
  or (created.role_key='foundation_qa_acceptor' and m.perms in ('todo:admission:view','todo:admission:edit'))
  or (created.role_key='foundation_independent_reviewer' and m.perms in ('todo:admission:view','todo:admission:edit'))
where created.role_id is not null
  and (m.component='todo/config/index' or m.perms in ('todo:decision:view','todo:decision:edit',
    'todo:admission:view','todo:admission:edit','todo:admission:export'))
  and not exists(select 1 from sys_role_menu existing where existing.role_id=created.role_id and existing.menu_id=m.menu_id);
