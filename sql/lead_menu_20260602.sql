-- 线索管理菜单骨架
-- 适用于已有数据库。重复执行不会重复插入菜单。

-- 将业务模块放在系统管理之前。
update sys_menu set order_num = 2 where parent_id = 0 and path = 'system';
update sys_menu set order_num = 3 where parent_id = 0 and path = 'monitor';
update sys_menu set order_num = 4 where parent_id = 0 and path = 'tool';
update sys_menu set order_num = 99 where parent_id = 0 and path = 'http://ruoyi.vip';

insert into sys_menu (
  menu_name, parent_id, order_num, path, component, query, route_name,
  is_frame, is_cache, menu_type, visible, status, perms, icon,
  create_by, create_time, update_by, update_time, remark
)
select
  '线索管理', 0, 1, 'lead', null, '', '',
  1, 0, 'M', '0', '0', '', 'user',
  'admin', sysdate(), '', null, '律所线索管理目录'
where not exists (select 1 from sys_menu where parent_id = 0 and path = 'lead');

set @lead_menu_id = (select menu_id from sys_menu where parent_id = 0 and path = 'lead' limit 1);

insert into sys_menu (
  menu_name, parent_id, order_num, path, component, query, route_name,
  is_frame, is_cache, menu_type, visible, status, perms, icon,
  create_by, create_time, update_by, update_time, remark
)
select '线索工作台', @lead_menu_id, 1, 'dashboard', 'lead/index', '{"module":"dashboard"}', 'LeadDashboard', 1, 0, 'C', '0', '0', 'lead:dashboard:view', 'dashboard', 'admin', sysdate(), '', null, '线索工作台'
where not exists (select 1 from sys_menu where parent_id = @lead_menu_id and path = 'dashboard');

insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select '全部线索', @lead_menu_id, 2, 'all', 'lead/index', '{"module":"all"}', 'LeadAll', 1, 0, 'C', '0', '0', 'lead:all:list', 'list', 'admin', sysdate(), '', null, '全部线索'
where not exists (select 1 from sys_menu where parent_id = @lead_menu_id and path = 'all');

insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select '我的线索', @lead_menu_id, 3, 'mine', 'lead/index', '{"module":"mine"}', 'LeadMine', 1, 0, 'C', '0', '0', 'lead:mine:list', 'user', 'admin', sysdate(), '', null, '我的线索'
where not exists (select 1 from sys_menu where parent_id = @lead_menu_id and path = 'mine');

insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select '线索公海', @lead_menu_id, 4, 'pool', 'lead/index', '{"module":"pool"}', 'LeadPool', 1, 0, 'C', '0', '0', 'lead:pool:list', 'peoples', 'admin', sysdate(), '', null, '线索公海'
where not exists (select 1 from sys_menu where parent_id = @lead_menu_id and path = 'pool');

insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select '跟进任务', @lead_menu_id, 5, 'followup', 'lead/index', '{"module":"followup"}', 'LeadFollowup', 1, 0, 'C', '0', '0', 'lead:followup:list', 'time', 'admin', sysdate(), '', null, '跟进任务'
where not exists (select 1 from sys_menu where parent_id = @lead_menu_id and path = 'followup');

insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select '线索回收站', @lead_menu_id, 6, 'recycle', 'lead/index', '{"module":"recycle"}', 'LeadRecycle', 1, 0, 'C', '0', '0', 'lead:recycle:list', 'delete', 'admin', sysdate(), '', null, '线索回收站'
where not exists (select 1 from sys_menu where parent_id = @lead_menu_id and path = 'recycle');

insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select '线索设置', @lead_menu_id, 7, 'settings', 'lead/index', '{"module":"settings"}', 'LeadSettings', 1, 0, 'C', '0', '0', 'lead:settings:list', 'edit', 'admin', sysdate(), '', null, '线索设置'
where not exists (select 1 from sys_menu where parent_id = @lead_menu_id and path = 'settings');

-- 按钮权限。重复执行不会重复插入；可在角色授权中按需勾选。
insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select item.menu_name, @lead_menu_id, item.order_num, '#', '', '', '', 1, 0, 'F', '0', '0', item.perms, '#', 'admin', sysdate(), '', null, item.menu_name
from (
  select '线索查询' menu_name, 101 order_num, 'lead:query' perms union all
  select '我的线索查询', 101, 'lead:mine:query' union all
  select '我的线索跟进', 102, 'lead:mine:followup' union all
  select '我的线索入公海', 103, 'lead:mine:pool:move' union all
  select '我的线索转化', 104, 'lead:mine:convert' union all
  select '公海线索查询', 101, 'lead:pool:query' union all
  select '回收站线索查询', 101, 'lead:recycle:query' union all
  select '线索配置选项', 101, 'lead:setting:options' union all
  select '线索新增', 102, 'lead:add' union all
  select '线索编辑', 103, 'lead:edit' union all
  select '线索删除', 104, 'lead:remove' union all
  select '线索分配', 105, 'lead:assign' union all
  select '线索入公海', 106, 'lead:pool:move' union all
  select '公海领取', 107, 'lead:pool:claim' union all
  select '线索转化', 108, 'lead:convert' union all
  select '新增跟进', 109, 'lead:followup:add' union all
  select '编辑跟进', 110, 'lead:followup:edit' union all
  select '删除跟进', 111, 'lead:followup:remove' union all
  select '回收站恢复', 112, 'lead:recycle:restore' union all
  select '彻底删除线索', 113, 'lead:recycle:purge' union all
  select '线索配置新增', 114, 'lead:settings:add' union all
  select '线索配置编辑', 115, 'lead:settings:edit' union all
  select '线索配置删除', 116, 'lead:settings:remove'
) item
where not exists (select 1 from sys_menu where perms = item.perms);

-- 校正按钮权限挂载点：按钮应挂在对应页面菜单下，而不是线索管理目录下。
set @lead_dashboard_id = (select menu_id from sys_menu where parent_id = @lead_menu_id and path = 'dashboard' limit 1);
set @lead_all_id = (select menu_id from sys_menu where parent_id = @lead_menu_id and path = 'all' limit 1);
set @lead_mine_id = (select menu_id from sys_menu where parent_id = @lead_menu_id and path = 'mine' limit 1);
set @lead_pool_id = (select menu_id from sys_menu where parent_id = @lead_menu_id and path = 'pool' limit 1);
set @lead_followup_id = (select menu_id from sys_menu where parent_id = @lead_menu_id and path = 'followup' limit 1);
set @lead_recycle_id = (select menu_id from sys_menu where parent_id = @lead_menu_id and path = 'recycle' limit 1);
set @lead_settings_id = (select menu_id from sys_menu where parent_id = @lead_menu_id and path = 'settings' limit 1);

update sys_menu set parent_id = @lead_all_id, order_num = 101 where perms = 'lead:query';
update sys_menu set parent_id = @lead_mine_id, order_num = 101 where perms = 'lead:mine:query';
update sys_menu set parent_id = @lead_mine_id, order_num = 102 where perms = 'lead:mine:followup';
update sys_menu set parent_id = @lead_mine_id, order_num = 103 where perms = 'lead:mine:pool:move';
update sys_menu set parent_id = @lead_mine_id, order_num = 104 where perms = 'lead:mine:convert';
update sys_menu set parent_id = @lead_pool_id, order_num = 101 where perms = 'lead:pool:query';
update sys_menu set parent_id = @lead_recycle_id, order_num = 101 where perms = 'lead:recycle:query';
update sys_menu set parent_id = @lead_dashboard_id, order_num = 101 where perms = 'lead:setting:options';
update sys_menu set parent_id = @lead_all_id, order_num = 102 where perms = 'lead:add';
update sys_menu set parent_id = @lead_all_id, order_num = 103 where perms = 'lead:edit';
update sys_menu set parent_id = @lead_all_id, order_num = 104 where perms = 'lead:remove';
update sys_menu set parent_id = @lead_all_id, order_num = 105 where perms = 'lead:assign';
update sys_menu set parent_id = @lead_all_id, order_num = 106 where perms = 'lead:pool:move';
update sys_menu set parent_id = @lead_pool_id, order_num = 101 where perms = 'lead:pool:claim';
update sys_menu set parent_id = @lead_all_id, order_num = 107 where perms = 'lead:convert';
update sys_menu set parent_id = @lead_followup_id, order_num = 101 where perms = 'lead:followup:add';
update sys_menu set parent_id = @lead_followup_id, order_num = 102 where perms = 'lead:followup:edit';
update sys_menu set parent_id = @lead_followup_id, order_num = 103 where perms = 'lead:followup:remove';
update sys_menu set parent_id = @lead_recycle_id, order_num = 101 where perms = 'lead:recycle:restore';
update sys_menu set parent_id = @lead_recycle_id, order_num = 102 where perms = 'lead:recycle:purge';
update sys_menu set parent_id = @lead_settings_id, order_num = 101 where perms = 'lead:settings:add';
update sys_menu set parent_id = @lead_settings_id, order_num = 102 where perms = 'lead:settings:edit';
update sys_menu set parent_id = @lead_settings_id, order_num = 103 where perms = 'lead:settings:remove';

set @sales_role_id = (select role_id from sys_role where role_key='sales' and del_flag='0' limit 1);
insert into sys_role_menu(role_id, menu_id)
select @sales_role_id, m.menu_id
from sys_menu m
where @sales_role_id is not null
  and m.perms in (
    'lead:dashboard:view','lead:mine:list','lead:pool:list','lead:followup:list','lead:recycle:list',
    'lead:mine:query','lead:mine:followup','lead:mine:pool:move','lead:mine:convert',
    'lead:pool:query','lead:recycle:query','lead:setting:options',
    'lead:pool:claim','lead:followup:add','lead:followup:edit','lead:followup:remove',
    'lead:recycle:restore','lead:recycle:purge'
  )
  and not exists(select 1 from sys_role_menu rm where rm.role_id=@sales_role_id and rm.menu_id=m.menu_id);

-- 线索模块字典。前端展示字段通过字典管理维护，避免在页面内写死枚举文本。
insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
select item.dict_name, item.dict_type, '0', 'admin', sysdate(), item.remark
from (
  select '线索状态' dict_name, 'law_lead_status' dict_type, '线索状态字典' remark union all
  select '线索优先级', 'law_lead_priority', '线索优先级字典' union all
  select '线索跟进方式', 'law_lead_follow_type', '线索跟进方式字典' union all
  select '线索公海状态', 'law_lead_pool_status', '线索公海状态字典' union all
  select '线索配置类型', 'law_lead_setting_type', '线索配置类型字典'
) item
where not exists (select 1 from sys_dict_type where dict_type = item.dict_type);

insert into sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select item.dict_sort, item.dict_label, item.dict_value, item.dict_type, '', item.list_class, item.is_default, '0', 'admin', sysdate(), item.remark
from (
  select 1 dict_sort, '待分配' dict_label, '0' dict_value, 'law_lead_status' dict_type, 'info' list_class, 'Y' is_default, '线索待分配' remark union all
  select 2, '待跟进', '1', 'law_lead_status', 'warning', 'N', '线索待跟进' union all
  select 3, '跟进中', '2', 'law_lead_status', 'primary', 'N', '线索跟进中' union all
  select 4, '已转化', '3', 'law_lead_status', 'success', 'N', '线索已转化' union all
  select 5, '无效', '4', 'law_lead_status', 'info', 'N', '线索无效' union all
  select 6, '已关闭', '5', 'law_lead_status', 'info', 'N', '线索已关闭' union all
  select 1, '高优先级', '1', 'law_lead_priority', 'danger', 'N', '高优先级线索' union all
  select 2, '中优先级', '2', 'law_lead_priority', 'warning', 'Y', '中优先级线索' union all
  select 3, '低优先级', '3', 'law_lead_priority', 'success', 'N', '低优先级线索' union all
  select 1, '电话', 'phone', 'law_lead_follow_type', 'primary', 'Y', '电话跟进' union all
  select 2, '微信', 'wechat', 'law_lead_follow_type', 'success', 'N', '微信跟进' union all
  select 3, '面谈', 'meeting', 'law_lead_follow_type', 'warning', 'N', '面谈跟进' union all
  select 4, '邮件', 'email', 'law_lead_follow_type', 'info', 'N', '邮件跟进' union all
  select 1, '已有归属', '0', 'law_lead_pool_status', 'primary', 'Y', '已有负责人' union all
  select 2, '公海线索', '1', 'law_lead_pool_status', 'warning', 'N', '公海线索' union all
  select 1, '线索来源', 'source', 'law_lead_setting_type', 'primary', 'Y', '来源配置' union all
  select 2, '线索标签', 'tag', 'law_lead_setting_type', 'success', 'N', '标签配置' union all
  select 3, '无效原因', 'invalid_reason', 'law_lead_setting_type', 'info', 'N', '无效原因配置'
) item
where not exists (select 1 from sys_dict_data where dict_type = item.dict_type and dict_value = item.dict_value);
