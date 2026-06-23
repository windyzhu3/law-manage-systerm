-- 律所线索模块：业务表、演示数据、菜单升级。
-- 适用于 MySQL 8，重复执行不会重复创建对象或演示数据。

create table if not exists biz_lead (
  lead_id bigint not null auto_increment comment '线索ID',
  lead_no varchar(32) not null comment '线索编号',
  lead_name varchar(120) not null comment '线索名称',
  contact_name varchar(60) default '' comment '联系人',
  mobile varchar(30) default '' comment '手机号',
  wechat varchar(60) default '' comment '微信',
  company_name varchar(160) default '' comment '单位名称',
  source_code varchar(40) default '' comment '来源编码',
  status char(1) default '0' comment '状态：0待分配 1待跟进 2跟进中 3已转化 4无效 5关闭',
  pool_status char(1) default '0' comment '是否在公海：0否 1是',
  priority char(1) default '2' comment '优先级：1高 2中 3低',
  legal_demand varchar(500) default '' comment '法律需求',
  estimated_amount decimal(12,2) default 0 comment '预计金额',
  owner_id bigint default null comment '负责人ID',
  dept_id bigint default null comment '负责人部门ID',
  next_follow_time datetime default null comment '下次跟进时间',
  last_follow_time datetime default null comment '最后跟进时间',
  converted_time datetime default null comment '转化时间',
  invalid_reason varchar(255) default '' comment '无效原因',
  pool_reason varchar(255) default '' comment '进入公海原因',
  del_flag char(1) default '0' comment '删除标志：0正常 2删除',
  delete_time datetime default null comment '删除时间',
  create_by varchar(64) default '',
  create_time datetime default null,
  update_by varchar(64) default '',
  update_time datetime default null,
  remark varchar(500) default null,
  primary key (lead_id),
  unique key uk_biz_lead_no (lead_no),
  key idx_biz_lead_owner (owner_id),
  key idx_biz_lead_status (status, pool_status, del_flag),
  key idx_biz_lead_mobile (mobile)
) engine=innodb comment='律所线索';

create table if not exists biz_lead_followup (
  followup_id bigint not null auto_increment,
  lead_id bigint not null,
  follow_type varchar(30) default 'phone' comment '跟进方式',
  follow_result varchar(60) default '' comment '跟进结果',
  content varchar(1000) default '' comment '跟进内容',
  next_follow_time datetime default null,
  follow_user_id bigint default null,
  task_status char(1) default '1' comment '0待办 1完成',
  create_by varchar(64) default '',
  create_time datetime default null,
  update_by varchar(64) default '',
  update_time datetime default null,
  remark varchar(500) default null,
  primary key (followup_id),
  key idx_lead_followup_lead (lead_id),
  key idx_lead_followup_user (follow_user_id)
) engine=innodb comment='线索跟进记录';

create table if not exists biz_lead_assignment_log (
  log_id bigint not null auto_increment,
  lead_id bigint not null,
  from_owner_id bigint default null,
  to_owner_id bigint default null,
  action_type varchar(30) default '',
  reason varchar(255) default '',
  create_by varchar(64) default '',
  create_time datetime default null,
  primary key (log_id),
  key idx_lead_assignment_lead (lead_id)
) engine=innodb comment='线索分配轨迹';

create table if not exists biz_lead_setting (
  setting_id bigint not null auto_increment,
  setting_type varchar(30) not null comment 'source tag invalid_reason pool_rule',
  setting_code varchar(40) not null,
  setting_name varchar(80) not null,
  color varchar(20) default '',
  order_num int default 0,
  status char(1) default '0',
  create_by varchar(64) default '',
  create_time datetime default null,
  update_by varchar(64) default '',
  update_time datetime default null,
  remark varchar(500) default null,
  primary key (setting_id),
  unique key uk_lead_setting (setting_type, setting_code)
) engine=innodb comment='线索配置';

insert ignore into biz_lead_setting(setting_type, setting_code, setting_name, color, order_num, status, create_by, create_time) values
('source','online','线上咨询','#3b82f6',1,'0','admin',sysdate()),
('source','referral','客户转介绍','#8b5cf6',2,'0','admin',sysdate()),
('source','activity','市场活动','#06b6d4',3,'0','admin',sysdate()),
('source','walkin','到所咨询','#10b981',4,'0','admin',sysdate()),
('tag','important','重点客户','#ef4444',1,'0','admin',sysdate()),
('tag','enterprise','企业客户','#f59e0b',2,'0','admin',sysdate()),
('invalid_reason','unreachable','无法联系','#94a3b8',1,'0','admin',sysdate()),
('invalid_reason','duplicate','重复线索','#64748b',2,'0','admin',sysdate()),
('invalid_reason','rejected','明确拒绝','#475569',3,'0','admin',sysdate());

insert into biz_lead(lead_no, lead_name, contact_name, mobile, wechat, company_name, source_code, status, pool_status, priority, legal_demand, estimated_amount, owner_id, dept_id, next_follow_time, last_follow_time, create_by, create_time, update_time, remark)
select 'XS202606020001','某科技公司常年法律顾问','张先生','13800138001','zhang-keji','上海某科技有限公司','online','2','0','1','企业合规与合同审查',80000,1,103,date_add(sysdate(), interval 1 day),sysdate(),'admin',sysdate(),sysdate(),'重点跟进'
where not exists(select 1 from biz_lead where lead_no='XS202606020001');
insert into biz_lead(lead_no, lead_name, contact_name, mobile, company_name, source_code, status, pool_status, priority, legal_demand, estimated_amount, owner_id, dept_id, next_follow_time, create_by, create_time, update_time)
select 'XS202606020002','劳动争议咨询','李女士','13800138002','','referral','1','0','2','劳动仲裁咨询',12000,1,103,sysdate(),'admin',sysdate(),sysdate()
where not exists(select 1 from biz_lead where lead_no='XS202606020002');
insert into biz_lead(lead_no, lead_name, contact_name, mobile, company_name, source_code, status, pool_status, priority, legal_demand, estimated_amount, pool_reason, create_by, create_time, update_time)
select 'XS202606020003','房产继承纠纷','王先生','13800138003','','walkin','0','1','2','遗产继承与房产分割',30000,'新录入待领取','admin',sysdate(),sysdate()
where not exists(select 1 from biz_lead where lead_no='XS202606020003');
insert into biz_lead(lead_no, lead_name, contact_name, mobile, company_name, source_code, status, pool_status, priority, legal_demand, estimated_amount, owner_id, dept_id, converted_time, create_by, create_time, update_time)
select 'XS202606020004','知识产权专项服务','陈经理','13800138004','某文创公司','activity','3','0','1','商标与著作权保护',50000,1,103,sysdate(),'admin',sysdate(),sysdate()
where not exists(select 1 from biz_lead where lead_no='XS202606020004');
insert into biz_lead(lead_no, lead_name, contact_name, mobile, source_code, status, pool_status, priority, legal_demand, estimated_amount, owner_id, dept_id, next_follow_time, create_by, create_time, update_time)
select 'XS202606020005','交通事故赔偿咨询','赵先生','13800138005','online','1','0','3','交通事故赔偿',9000,1,103,date_sub(sysdate(), interval 1 day),'admin',sysdate(),sysdate()
where not exists(select 1 from biz_lead where lead_no='XS202606020005');

update sys_menu set component='lead/index' where parent_id=(select menu_id from (select menu_id from sys_menu where parent_id=0 and path='lead' limit 1) t);

set @lead_menu_id = (select menu_id from sys_menu where parent_id = 0 and path = 'lead' limit 1);

insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
select item.menu_name, @lead_menu_id, item.order_num, '#', '', '', '', 1, 0, 'F', '0', '0', item.perms, '#', 'admin', sysdate(), '', null, item.menu_name
from (
  select '线索查询' menu_name, 101 order_num, 'lead:query' perms union all
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
set @lead_pool_id = (select menu_id from sys_menu where parent_id = @lead_menu_id and path = 'pool' limit 1);
set @lead_followup_id = (select menu_id from sys_menu where parent_id = @lead_menu_id and path = 'followup' limit 1);
set @lead_recycle_id = (select menu_id from sys_menu where parent_id = @lead_menu_id and path = 'recycle' limit 1);
set @lead_settings_id = (select menu_id from sys_menu where parent_id = @lead_menu_id and path = 'settings' limit 1);

update sys_menu set parent_id = @lead_all_id, order_num = 101 where perms = 'lead:query';
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
