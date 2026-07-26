-- Materialize the PRD intake role. Production user assignment remains an
-- explicit administrator responsibility; this migration never creates users.
insert into sys_role(
  role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,
  status,del_flag,create_by,create_time,remark
)
select convert(0xE7BABFE7B4A2E4BFA1E681AFE59198 using utf8mb4),
  'lead_information_officer',34,'1',1,1,'0','0','migration',sysdate(),
  'v0.2 lead intake tag-confirmation role'
where not exists(
  select 1 from sys_role
  where role_key='lead_information_officer' and del_flag='0'
);

set @information_role_id=(
  select case when count(*)=1 then min(role_id) else null end
  from sys_role
  where role_key='lead_information_officer' and status='0' and del_flag='0'
);
set @sales_role_id=(
  select case when count(*)=1 then min(role_id) else null end
  from sys_role
  where role_key='sales' and status='0' and del_flag='0'
);
set @lead_root_menu_id=(
  select menu_id
  from sys_menu
  where parent_id=0 and path='lead' and status='0'
  order by menu_id
  limit 1
);
set @tag_confirm_menu_id=(
  select menu_id
  from sys_menu
  where status='0' and perms='lead:tag:confirm'
  order by menu_id
  limit 1
);

-- Tag confirmation belongs to intake, not to an already-assigned salesperson.
delete from sys_role_menu
where role_id=@sales_role_id
  and menu_id=@tag_confirm_menu_id;

insert into sys_role_menu(role_id,menu_id)
select @information_role_id,candidate.menu_id
from (
  select @lead_root_menu_id menu_id
  union
  select menu_id
  from sys_menu
  where parent_id=@lead_root_menu_id and path='all' and status='0'
  union
  select menu_id
  from sys_menu
  where status='0' and perms in(
    'lead:dashboard:view',
    'lead:all:list',
    'lead:query',
    'lead:tag:confirm'
  )
) candidate
where @information_role_id is not null
  and candidate.menu_id is not null
  and not exists(
    select 1 from sys_role_menu existing
    where existing.role_id=@information_role_id
      and existing.menu_id=candidate.menu_id
  );
