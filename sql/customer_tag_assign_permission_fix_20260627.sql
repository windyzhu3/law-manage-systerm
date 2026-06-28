-- Fix missing customer tag assignment button permission.
-- This script is idempotent and can be re-run safely.

set @customer_list_id = (
    select menu_id
    from sys_menu
    where perms = 'customer:list' and menu_type = 'C'
    limit 1
);

insert into sys_menu(
    menu_name, parent_id, order_num, path, component, query, route_name,
    is_frame, is_cache, menu_type, visible, status, perms, icon,
    create_by, create_time, remark
)
select
    '客户标签分配', @customer_list_id, 7, '#', '', '', '',
    1, 0, 'F', '0', '0', 'customer:tag:assign', '#',
    'system', sysdate(), '客户列表标签分配按钮'
where @customer_list_id is not null
  and not exists (
      select 1 from sys_menu where perms = 'customer:tag:assign'
  );

set @customer_tag_assign_id = (
    select menu_id
    from sys_menu
    where perms = 'customer:tag:assign'
    limit 1
);

-- Roles that can maintain customer tags should also be able to assign tags from the customer list.
insert into sys_role_menu(role_id, menu_id)
select distinct rm.role_id, @customer_tag_assign_id
from sys_role_menu rm
inner join sys_menu m on m.menu_id = rm.menu_id
where @customer_tag_assign_id is not null
  and m.perms in ('customer:tag:list', 'customer:tag:query')
  and not exists (
      select 1
      from sys_role_menu exists_rm
      where exists_rm.role_id = rm.role_id
        and exists_rm.menu_id = @customer_tag_assign_id
  );
