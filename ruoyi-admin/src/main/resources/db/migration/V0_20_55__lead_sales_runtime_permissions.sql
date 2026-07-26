-- Grant the existing sales role only the phase-one lead runtime surface.
-- Identity creation and binding remain explicit operational responsibilities.
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

insert into sys_role_menu(role_id,menu_id)
select @sales_role_id,candidate.menu_id
from (
  select @lead_root_menu_id menu_id
  union
  select menu_id
  from sys_menu
  where parent_id=@lead_root_menu_id and path in ('mine','retry') and status='0'
  union
  select menu_id
  from sys_menu
  where status='0' and perms in (
    'lead:dashboard:view',
    'lead:mine:list',
    'lead:mine:query',
    'lead:tag:confirm',
    'lead:first-contact:handle',
    'lead:retry:list',
    'lead:retry:handle',
    'lead:call-record:add',
    'lead:call-record:view',
    'todo:list',
    'todo:query',
    'todo:complete',
    'todo:chain:query',
    'file:object:upload',
    'file:object:relate',
    'file:object:read'
  )
) candidate
where @sales_role_id is not null
  and candidate.menu_id is not null
  and not exists(
    select 1
    from sys_role_menu existing
    where existing.role_id=@sales_role_id
      and existing.menu_id=candidate.menu_id
  );
