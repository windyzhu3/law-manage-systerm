-- Grant the existing partner-manager role only the lead supervisor runtime surface.
-- Identity creation and binding remain explicit operational responsibilities.
set @supervisor_role_id=(
  select case when count(*)=1 then min(role_id) else null end
  from sys_role
  where role_key='law_partner_manager' and status='0' and del_flag='0'
);

set @lead_root_menu_id=(
  select menu_id
  from sys_menu
  where parent_id=0 and path='lead' and status='0'
  order by menu_id
  limit 1
);

insert into sys_role_menu(role_id,menu_id)
select @supervisor_role_id,candidate.menu_id
from (
  select @lead_root_menu_id menu_id
  union
  select menu_id
  from sys_menu
  where parent_id=@lead_root_menu_id
    and path in ('invalid-review','dead-pool')
    and status='0'
  union
  select menu_id
  from sys_menu
  where status='0' and perms in (
    'lead:invalid-review:list',
    'lead:invalid-review:handle',
    'lead:dead-pool:list',
    'lead:dead-pool:restore',
    'todo:list',
    'todo:query',
    'todo:chain:query',
    'file:object:read'
  )
) candidate
where @supervisor_role_id is not null
  and candidate.menu_id is not null
  and not exists(
    select 1
    from sys_role_menu existing
    where existing.role_id=@supervisor_role_id
      and existing.menu_id=candidate.menu_id
  );
