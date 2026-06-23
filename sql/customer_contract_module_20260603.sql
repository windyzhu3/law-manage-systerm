-- 客户中心与合同中心完整初始化脚本
-- 可重复执行：业务表使用 create if not exists，菜单/字典使用 not exists。

set @lead_customer_col_exists = (
  select count(1) from information_schema.columns
  where table_schema = database() and table_name = 'biz_lead' and column_name = 'customer_id'
);
set @lead_customer_col_sql = if(@lead_customer_col_exists = 0,
  'alter table biz_lead add column customer_id bigint default null comment ''转化客户ID''',
  'select 1'
);
prepare stmt from @lead_customer_col_sql;
execute stmt;
deallocate prepare stmt;

create table if not exists biz_customer (
  customer_id bigint not null auto_increment comment '客户ID',
  customer_no varchar(32) not null comment '客户编号',
  customer_name varchar(120) not null comment '客户名称',
  customer_type varchar(20) not null comment '客户类型',
  mobile varchar(30) not null comment '手机号',
  wechat varchar(64) default null comment '微信号',
  email varchar(100) default null comment '邮箱',
  company_name varchar(160) default null comment '公司名称',
  credit_code varchar(64) default null comment '统一社会信用代码',
  industry varchar(50) default null comment '所属行业',
  region varchar(120) default null comment '地区',
  source_code varchar(50) default null comment '客户来源',
  customer_level varchar(20) default '2' comment '客户等级',
  main_demand varchar(500) default null comment '主要需求',
  owner_id bigint default null comment '负责人',
  dept_id bigint default null comment '部门ID',
  first_contact_time datetime default null comment '首次联系时间',
  last_follow_time datetime default null comment '最近跟进时间',
  next_follow_time datetime default null comment '下次跟进时间',
  lead_id bigint default null comment '来源线索ID',
  status char(1) default '0' comment '客户状态',
  del_flag char(1) default '0' comment '删除标志',
  create_by varchar(64) default '',
  create_time datetime default null,
  update_by varchar(64) default '',
  update_time datetime default null,
  remark varchar(500) default null,
  primary key (customer_id),
  unique key uk_biz_customer_no (customer_no),
  key idx_customer_owner (owner_id),
  key idx_customer_mobile (mobile),
  key idx_customer_credit (credit_code),
  key idx_customer_lead (lead_id)
) comment='客户档案';

create table if not exists biz_customer_contact (
  contact_id bigint not null auto_increment,
  customer_id bigint not null,
  contact_name varchar(80) not null,
  position_name varchar(80) default null,
  mobile varchar(30) default null,
  wechat varchar(64) default null,
  email varchar(100) default null,
  relation_type varchar(30) default null,
  key_contact char(1) default '0',
  owner_id bigint default null,
  del_flag char(1) default '0',
  create_by varchar(64) default '',
  create_time datetime default null,
  update_by varchar(64) default '',
  update_time datetime default null,
  remark varchar(500) default null,
  primary key (contact_id),
  key idx_customer_contact_customer (customer_id)
) comment='客户联系人';

create table if not exists biz_customer_followup (
  followup_id bigint not null auto_increment,
  customer_id bigint not null,
  follow_type varchar(30) not null,
  follow_result varchar(200) default null,
  content varchar(1000) default null,
  next_follow_time datetime default null,
  follow_user_id bigint default null,
  task_status char(1) default '1',
  del_flag char(1) default '0',
  create_by varchar(64) default '',
  create_time datetime default null,
  update_by varchar(64) default '',
  update_time datetime default null,
  primary key (followup_id),
  key idx_customer_followup_customer (customer_id),
  key idx_customer_followup_user (follow_user_id)
) comment='客户跟进';

set @customer_followup_del_col_exists = (
  select count(1) from information_schema.columns
  where table_schema = database() and table_name = 'biz_customer_followup' and column_name = 'del_flag'
);
set @customer_followup_del_col_sql = if(@customer_followup_del_col_exists = 0,
  'alter table biz_customer_followup add column del_flag char(1) default ''0'' comment ''delete flag'' after task_status',
  'select 1'
);
prepare stmt from @customer_followup_del_col_sql;
execute stmt;
deallocate prepare stmt;

create table if not exists biz_customer_tag (
  tag_id bigint not null auto_increment,
  tag_name varchar(60) not null,
  tag_color varchar(20) default '#3b82f6',
  order_num int default 0,
  status char(1) default '0',
  create_by varchar(64) default '',
  create_time datetime default null,
  update_by varchar(64) default '',
  update_time datetime default null,
  remark varchar(500) default null,
  primary key (tag_id),
  unique key uk_customer_tag_name (tag_name)
) comment='客户标签';

create table if not exists biz_customer_tag_rel (
  customer_id bigint not null,
  tag_id bigint not null,
  primary key (customer_id, tag_id)
) comment='客户标签关系';

create table if not exists biz_customer_merge_log (
  merge_id bigint not null auto_increment,
  main_customer_id bigint not null,
  merged_customer_id bigint not null,
  merge_content varchar(1000) default null,
  create_by varchar(64) default '',
  create_time datetime default null,
  primary key (merge_id),
  key idx_customer_merge_main (main_customer_id),
  key idx_customer_merge_merged (merged_customer_id)
) comment='客户合并日志';

create table if not exists biz_contract (
  contract_id bigint not null auto_increment,
  contract_no varchar(40) not null,
  contract_name varchar(160) not null,
  customer_id bigint not null,
  customer_name varchar(120) not null,
  case_type varchar(50) not null,
  lawyer_id bigint default null,
  lawyer_name varchar(80) default null,
  owner_id bigint default null,
  dept_id bigint default null,
  sign_amount decimal(14,2) not null default 0,
  fee_type varchar(30) not null,
  sign_date date default null,
  effective_date date default null,
  expire_date date default null,
  sign_method varchar(30) default null,
  sign_status varchar(20) default '0',
  audit_status varchar(20) default '0',
  contract_status varchar(20) default '0',
  risk_level varchar(20) default '1',
  del_flag char(1) default '0',
  create_by varchar(64) default '',
  create_time datetime default null,
  update_by varchar(64) default '',
  update_time datetime default null,
  remark varchar(500) default null,
  primary key (contract_id),
  unique key uk_biz_contract_no (contract_no),
  key idx_contract_customer (customer_id),
  key idx_contract_owner (owner_id),
  key idx_contract_status (audit_status, sign_status, contract_status, del_flag)
) comment='合同档案';

create table if not exists biz_contract_template (
  template_id bigint not null auto_increment,
  template_name varchar(120) not null,
  case_type varchar(50) default null,
  file_name varchar(200) default null,
  file_url varchar(500) default null,
  version_no varchar(30) default null,
  status char(1) default '0',
  create_by varchar(64) default '',
  create_time datetime default null,
  update_by varchar(64) default '',
  update_time datetime default null,
  remark varchar(500) default null,
  primary key (template_id)
) comment='合同模板';

create table if not exists biz_contract_approval (
  approval_id bigint not null auto_increment,
  contract_id bigint not null,
  approval_action varchar(30) not null,
  approval_opinion varchar(500) not null,
  approver_id bigint default null,
  approver_name varchar(80) default null,
  create_by varchar(64) default '',
  create_time datetime default null,
  primary key (approval_id),
  key idx_contract_approval_contract (contract_id)
) comment='合同审批记录';

create table if not exists biz_contract_fee_plan (
  plan_id bigint not null auto_increment,
  contract_id bigint not null,
  period_no int not null,
  receivable_amount decimal(14,2) not null default 0,
  plan_receive_date date default null,
  received_amount decimal(14,2) default 0,
  confirm_status varchar(20) default '0',
  invoice_status varchar(20) default '0',
  finance_user_id bigint default null,
  create_by varchar(64) default '',
  create_time datetime default null,
  update_by varchar(64) default '',
  update_time datetime default null,
  remark varchar(500) default null,
  primary key (plan_id),
  key idx_contract_fee_contract (contract_id)
) comment='合同收费计划';

create table if not exists biz_contract_attachment (
  attachment_id bigint not null auto_increment,
  contract_id bigint not null,
  file_name varchar(200) not null,
  file_url varchar(500) not null,
  file_type varchar(80) default null,
  file_size bigint default null,
  create_by varchar(64) default '',
  create_time datetime default null,
  remark varchar(500) default null,
  primary key (attachment_id),
  key idx_contract_attachment_contract (contract_id)
) comment='合同附件';

create table if not exists biz_contract_status_log (
  log_id bigint not null auto_increment,
  contract_id bigint not null,
  from_status varchar(30) default null,
  to_status varchar(30) default null,
  action_type varchar(50) not null,
  content varchar(500) default null,
  create_by varchar(64) default '',
  create_time datetime default null,
  primary key (log_id),
  key idx_contract_status_contract (contract_id)
) comment='合同状态记录';

create table if not exists biz_contract_no_rule (
  rule_id bigint not null auto_increment,
  rule_name varchar(80) not null,
  prefix varchar(20) not null default 'HT',
  date_pattern varchar(20) not null default 'yyyyMMdd',
  serial_length int not null default 4,
  current_serial int not null default 0,
  status char(1) default '0',
  create_by varchar(64) default '',
  create_time datetime default null,
  update_by varchar(64) default '',
  update_time datetime default null,
  remark varchar(500) default null,
  primary key (rule_id)
) comment='合同编号规则';

insert into biz_contract_no_rule(rule_name, prefix, date_pattern, serial_length, current_serial, status, create_by, create_time)
select '默认合同编号规则','HT','yyyyMMdd',4,0,'0','admin',sysdate()
where not exists(select 1 from biz_contract_no_rule where status='0');

-- 菜单
update sys_menu set order_num = 4 where parent_id = 0 and path = 'system';
update sys_menu set order_num = 5 where parent_id = 0 and path = 'monitor';
update sys_menu set order_num = 6 where parent_id = 0 and path = 'tool';

insert into sys_menu(menu_name,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
select '客户中心',0,2,'customer',null,'','',1,0,'M','0','0','','peoples','admin',sysdate(),'客户中心目录'
where not exists(select 1 from sys_menu where parent_id=0 and path='customer');
insert into sys_menu(menu_name,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
select '合同中心',0,3,'contract',null,'','',1,0,'M','0','0','','documentation','admin',sysdate(),'合同中心目录'
where not exists(select 1 from sys_menu where parent_id=0 and path='contract');

update sys_menu
set menu_name='客户中心',
    order_num=2,
    component=null,
    query='',
    route_name='',
    is_frame=1,
    is_cache=0,
    menu_type='M',
    visible='0',
    status='0',
    perms='',
    icon='peoples',
    update_by='admin',
    update_time=sysdate()
where parent_id=0 and path='customer';

update sys_menu
set menu_name='合同中心',
    order_num=3,
    component=null,
    query='',
    route_name='',
    is_frame=1,
    is_cache=0,
    menu_type='M',
    visible='0',
    status='0',
    perms='',
    icon='documentation',
    update_by='admin',
    update_time=sysdate()
where parent_id=0 and path='contract';

set @customer_menu_id=(select menu_id from sys_menu where parent_id=0 and path='customer' limit 1);
set @contract_menu_id=(select menu_id from sys_menu where parent_id=0 and path='contract' limit 1);

insert into sys_menu(menu_name,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
select item.menu_name,item.parent_id,item.order_num,item.path,item.component,item.query,item.route_name,1,0,'C','0','0',item.perms,item.icon,'admin',sysdate(),item.menu_name
from (
 select '客户列表' menu_name,@customer_menu_id parent_id,1 order_num,'list' path,'customer/index' component,'{"module":"list"}' query,'CustomerList' route_name,'customer:list' perms,'list' icon union all
 select '联系人管理',@customer_menu_id,2,'contact','customer/index','{"module":"contact"}','CustomerContact','customer:contact:list','peoples' union all
 select '客户跟进',@customer_menu_id,3,'followup','customer/index','{"module":"followup"}','CustomerFollowup','customer:followup:list','time' union all
 select '客户标签',@customer_menu_id,4,'tag','customer/index','{"module":"tag"}','CustomerTag','customer:tag:list','tag' union all
 select '去重合并',@customer_menu_id,5,'merge','customer/index','{"module":"merge"}','CustomerMerge','customer:merge:list','tree' union all
 select '合同列表',@contract_menu_id,1,'list','contract/index','{"module":"list"}','ContractList','contract:list','list' union all
 select '合同模板',@contract_menu_id,2,'template','contract/index','{"module":"template"}','ContractTemplate','contract:template:list','documentation' union all
 select '合同审批',@contract_menu_id,3,'approval','contract/index','{"module":"approval"}','ContractApproval','contract:approval:list','validCode' union all
 select '收费计划',@contract_menu_id,4,'fee','contract/index','{"module":"fee"}','ContractFee','contract:fee:list','money' union all
 select '合同附件',@contract_menu_id,5,'attachment','contract/index','{"module":"attachment"}','ContractAttachment','contract:attachment:list','upload' union all
 select '状态记录',@contract_menu_id,6,'status','contract/index','{"module":"status"}','ContractStatus','contract:status:list','time' union all
 select '编号规则',@contract_menu_id,7,'rule','contract/index','{"module":"rule"}','ContractRule','contract:rule:list','number'
) item
where not exists(select 1 from sys_menu where parent_id=item.parent_id and path=item.path);

update sys_menu m
join (
 select '客户列表' menu_name,@customer_menu_id parent_id,1 order_num,'list' path,'customer/index' component,'{"module":"list"}' query,'CustomerList' route_name,'customer:list' perms,'list' icon union all
 select '联系人管理',@customer_menu_id,2,'contact','customer/index','{"module":"contact"}','CustomerContact','customer:contact:list','peoples' union all
 select '客户跟进',@customer_menu_id,3,'followup','customer/index','{"module":"followup"}','CustomerFollowup','customer:followup:list','time' union all
 select '客户标签',@customer_menu_id,4,'tag','customer/index','{"module":"tag"}','CustomerTag','customer:tag:list','tag' union all
 select '去重合并',@customer_menu_id,5,'merge','customer/index','{"module":"merge"}','CustomerMerge','customer:merge:list','tree' union all
 select '合同列表',@contract_menu_id,1,'list','contract/index','{"module":"list"}','ContractList','contract:list','list' union all
 select '合同模板',@contract_menu_id,2,'template','contract/index','{"module":"template"}','ContractTemplate','contract:template:list','documentation' union all
 select '合同审批',@contract_menu_id,3,'approval','contract/index','{"module":"approval"}','ContractApproval','contract:approval:list','validCode' union all
 select '收费计划',@contract_menu_id,4,'fee','contract/index','{"module":"fee"}','ContractFee','contract:fee:list','money' union all
 select '合同附件',@contract_menu_id,5,'attachment','contract/index','{"module":"attachment"}','ContractAttachment','contract:attachment:list','upload' union all
 select '状态记录',@contract_menu_id,6,'status','contract/index','{"module":"status"}','ContractStatus','contract:status:list','time' union all
 select '编号规则',@contract_menu_id,7,'rule','contract/index','{"module":"rule"}','ContractRule','contract:rule:list','number'
) item on m.parent_id=item.parent_id and m.path=item.path
set m.menu_name=item.menu_name,
    m.order_num=item.order_num,
    m.component=item.component,
    m.query=item.query,
    m.route_name=item.route_name,
    m.is_frame=1,
    m.is_cache=0,
    m.menu_type='C',
    m.visible='0',
    m.status='0',
    m.perms=item.perms,
    m.icon=item.icon,
    m.update_by='admin',
    m.update_time=sysdate()
where item.parent_id is not null;

set @customer_list_id=(select menu_id from sys_menu where parent_id=@customer_menu_id and path='list' limit 1);
set @customer_contact_id=(select menu_id from sys_menu where parent_id=@customer_menu_id and path='contact' limit 1);
set @customer_followup_id=(select menu_id from sys_menu where parent_id=@customer_menu_id and path='followup' limit 1);
set @customer_tag_id=(select menu_id from sys_menu where parent_id=@customer_menu_id and path='tag' limit 1);
set @customer_merge_id=(select menu_id from sys_menu where parent_id=@customer_menu_id and path='merge' limit 1);
set @contract_list_id=(select menu_id from sys_menu where parent_id=@contract_menu_id and path='list' limit 1);
set @contract_template_id=(select menu_id from sys_menu where parent_id=@contract_menu_id and path='template' limit 1);
set @contract_approval_id=(select menu_id from sys_menu where parent_id=@contract_menu_id and path='approval' limit 1);
set @contract_fee_id=(select menu_id from sys_menu where parent_id=@contract_menu_id and path='fee' limit 1);
set @contract_attachment_id=(select menu_id from sys_menu where parent_id=@contract_menu_id and path='attachment' limit 1);
set @contract_status_id=(select menu_id from sys_menu where parent_id=@contract_menu_id and path='status' limit 1);
set @contract_rule_id=(select menu_id from sys_menu where parent_id=@contract_menu_id and path='rule' limit 1);

insert into sys_menu(menu_name,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
select item.menu_name,item.parent_id,item.order_num,'#','','','',1,0,'F','0','0',item.perms,'#','admin',sysdate(),item.menu_name
from (
 select '客户查询' menu_name,@customer_list_id parent_id,1 order_num,'customer:query' perms union all
 select '客户新增',@customer_list_id,2,'customer:add' union all
 select '客户编辑',@customer_list_id,3,'customer:edit' union all
 select '客户删除',@customer_list_id,4,'customer:remove' union all
 select '客户导入',@customer_list_id,5,'customer:import' union all
 select '客户导出',@customer_list_id,6,'customer:export' union all
 select '客户标签分配',@customer_list_id,7,'customer:tag:assign' union all
 select '联系人新增',@customer_contact_id,1,'customer:contact:add' union all
 select '联系人编辑',@customer_contact_id,2,'customer:contact:edit' union all
 select '联系人删除',@customer_contact_id,3,'customer:contact:remove' union all
 select '客户跟进新增',@customer_followup_id,1,'customer:followup:add' union all
 select '客户跟进删除',@customer_followup_id,2,'customer:followup:remove' union all
 select '标签新增',@customer_tag_id,1,'customer:tag:add' union all
 select '标签编辑',@customer_tag_id,2,'customer:tag:edit' union all
 select '标签删除',@customer_tag_id,3,'customer:tag:remove' union all
 select '客户合并',@customer_merge_id,1,'customer:merge:merge' union all
 select '合同查询',@contract_list_id,1,'contract:query' union all
 select '合同新增',@contract_list_id,2,'contract:add' union all
 select '合同编辑',@contract_list_id,3,'contract:edit' union all
 select '合同删除',@contract_list_id,4,'contract:remove' union all
 select '合同导出',@contract_list_id,5,'contract:export' union all
 select '提交审批',@contract_list_id,6,'contract:submit' union all
 select '合同签署',@contract_list_id,7,'contract:sign' union all
 select '合同归档',@contract_list_id,8,'contract:archive' union all
 select '合同作废',@contract_list_id,9,'contract:void' union all
 select '合同终止',@contract_list_id,10,'contract:terminate' union all
 select '合同模板新增',@contract_template_id,1,'contract:template:add' union all
 select '合同模板编辑',@contract_template_id,2,'contract:template:edit' union all
 select '合同模板删除',@contract_template_id,3,'contract:template:remove' union all
 select '合同审批处理',@contract_approval_id,1,'contract:approval:handle' union all
 select '收费计划新增',@contract_fee_id,1,'contract:fee:add' union all
 select '收费计划编辑',@contract_fee_id,2,'contract:fee:edit' union all
 select '收费计划删除',@contract_fee_id,3,'contract:fee:remove' union all
 select '确认收款',@contract_fee_id,4,'contract:fee:confirm' union all
 select '驳回收款',@contract_fee_id,5,'contract:fee:reject' union all
 select '确认开票',@contract_fee_id,6,'contract:fee:invoice' union all
 select '合同附件新增',@contract_attachment_id,1,'contract:attachment:add' union all
 select '合同附件删除',@contract_attachment_id,2,'contract:attachment:remove' union all
 select '编号规则编辑',@contract_rule_id,1,'contract:rule:edit'
) item
where not exists(select 1 from sys_menu where perms=item.perms);

insert into sys_menu(menu_name,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
select '合同导入',@contract_list_id,11,'#','','','',1,0,'F','0','0','contract:import','#','admin',sysdate(),'合同导入'
where not exists(select 1 from sys_menu where perms='contract:import');

update sys_menu m
join (
 select @customer_list_id parent_id,1 order_num,'customer:query' perms union all
 select @customer_list_id,2,'customer:add' union all
 select @customer_list_id,3,'customer:edit' union all
 select @customer_list_id,4,'customer:remove' union all
 select @customer_list_id,5,'customer:import' union all
 select @customer_list_id,6,'customer:export' union all
 select @customer_list_id,7,'customer:tag:assign' union all
 select @customer_contact_id,1,'customer:contact:add' union all
 select @customer_contact_id,2,'customer:contact:edit' union all
 select @customer_contact_id,3,'customer:contact:remove' union all
 select @customer_followup_id,1,'customer:followup:add' union all
 select @customer_followup_id,2,'customer:followup:remove' union all
 select @customer_tag_id,1,'customer:tag:add' union all
 select @customer_tag_id,2,'customer:tag:edit' union all
 select @customer_tag_id,3,'customer:tag:remove' union all
 select @customer_merge_id,1,'customer:merge:merge' union all
 select @contract_list_id,1,'contract:query' union all
 select @contract_list_id,2,'contract:add' union all
 select @contract_list_id,3,'contract:edit' union all
 select @contract_list_id,4,'contract:remove' union all
 select @contract_list_id,5,'contract:export' union all
 select @contract_list_id,6,'contract:submit' union all
 select @contract_list_id,7,'contract:sign' union all
 select @contract_list_id,8,'contract:archive' union all
 select @contract_list_id,9,'contract:void' union all
 select @contract_list_id,10,'contract:terminate' union all
 select @contract_list_id,11,'contract:import' union all
 select @contract_template_id,1,'contract:template:add' union all
 select @contract_template_id,2,'contract:template:edit' union all
 select @contract_template_id,3,'contract:template:remove' union all
 select @contract_approval_id,1,'contract:approval:handle' union all
 select @contract_fee_id,1,'contract:fee:add' union all
 select @contract_fee_id,2,'contract:fee:edit' union all
 select @contract_fee_id,3,'contract:fee:remove' union all
 select @contract_fee_id,4,'contract:fee:confirm' union all
 select @contract_fee_id,5,'contract:fee:reject' union all
 select @contract_fee_id,6,'contract:fee:invoice' union all
 select @contract_attachment_id,1,'contract:attachment:add' union all
 select @contract_attachment_id,2,'contract:attachment:remove' union all
 select @contract_rule_id,1,'contract:rule:edit'
) item on m.perms=item.perms
set m.parent_id=item.parent_id,
    m.order_num=item.order_num,
    m.path='#',
    m.component='',
    m.query='',
    m.route_name='',
    m.menu_type='F',
    m.visible='0',
    m.status='0',
    m.icon='#',
    m.update_by='admin',
    m.update_time=sysdate()
where item.parent_id is not null;

insert into sys_dict_type(dict_name,dict_type,status,create_by,create_time,remark)
select item.dict_name,item.dict_type,'0','admin',sysdate(),item.remark
from (
 select '客户类型' dict_name,'law_customer_type' dict_type,'客户类型' remark union all
 select '是否标记','law_yes_no_flag','是否标记' union all
 select '客户等级','law_customer_level','客户等级' union all
 select '客户行业','law_customer_industry','客户行业' union all
 select '联系人关系','law_contact_relation','联系人关系' union all
 select '客户跟进方式','law_customer_follow_type','客户跟进方式' union all
 select '客户状态','law_customer_status','客户状态' union all
 select '合同案件类型','law_contract_case_type','合同案件类型' union all
 select '合同收费方式','law_contract_fee_type','合同收费方式' union all
 select '合同签订方式','law_contract_sign_method','合同签订方式' union all
 select '合同签订状态','law_contract_sign_status','合同签订状态' union all
 select '合同审核状态','law_contract_audit_status','合同审核状态' union all
 select '合同审批动作','law_contract_approval_action','合同审批动作' union all
 select '合同状态','law_contract_status','合同状态' union all
 select '合同状态记录动作','law_contract_status_action','合同状态记录动作' union all
 select '合同风险等级','law_contract_risk_level','合同风险等级' union all
 select '收款确认状态','law_contract_receive_status','收款确认状态' union all
 select '开票状态','law_contract_invoice_status','开票状态'
) item
where not exists(select 1 from sys_dict_type where dict_type=item.dict_type);

insert into sys_dict_data(dict_sort,dict_label,dict_value,dict_type,css_class,list_class,is_default,status,create_by,create_time,remark)
select item.sort,item.label,item.value,item.type,'',item.cls,item.def,'0','admin',sysdate(),item.label
from (
 select 1 sort,'是' label,'1' value,'law_yes_no_flag' type,'success' cls,'N' def union all select 2,'否','0','law_yes_no_flag','info','Y' union all
 select 1 sort,'个人' label,'personal' value,'law_customer_type' type,'primary' cls,'Y' def union all select 2,'企业','enterprise','law_customer_type','success','N' union all
 select 1,'高价值','1','law_customer_level','danger','N' union all select 2,'中价值','2','law_customer_level','warning','Y' union all select 3,'低价值','3','law_customer_level','info','N' union all
 select 1,'互联网','internet','law_customer_industry','primary','N' union all select 2,'制造业','manufacturing','law_customer_industry','success','N' union all select 3,'金融','finance','law_customer_industry','warning','N' union all select 4,'其他','other','law_customer_industry','info','Y' union all
 select 1,'决策人','decision','law_contact_relation','danger','N' union all select 2,'财务联系人','finance','law_contact_relation','warning','N' union all select 3,'法务接口人','legal','law_contact_relation','primary','N' union all select 4,'日常联系人','daily','law_contact_relation','info','Y' union all
 select 1,'电话','phone','law_customer_follow_type','primary','Y' union all select 2,'微信','wechat','law_customer_follow_type','success','N' union all select 3,'面谈','meeting','law_customer_follow_type','warning','N' union all select 4,'邮件','email','law_customer_follow_type','info','N' union all
 select 1,'正常','0','law_customer_status','success','Y' union all select 2,'停用','1','law_customer_status','info','N' union all select 3,'已合并','2','law_customer_status','warning','N' union all
 select 1,'民事','civil','law_contract_case_type','primary','Y' union all select 2,'刑事','criminal','law_contract_case_type','danger','N' union all select 3,'商事','business','law_contract_case_type','warning','N' union all select 4,'顾问','advisor','law_contract_case_type','success','N' union all
 select 1,'一次性','once','law_contract_fee_type','primary','Y' union all select 2,'分期','installment','law_contract_fee_type','warning','N' union all select 3,'风险代理','risk','law_contract_fee_type','danger','N' union all
 select 1,'线上签订','online','law_contract_sign_method','primary','Y' union all select 2,'线下签订','offline','law_contract_sign_method','info','N' union all
 select 1,'未签订','0','law_contract_sign_status','info','Y' union all select 2,'已签订','1','law_contract_sign_status','success','N' union all select 3,'部分签订','2','law_contract_sign_status','warning','N' union all
 select 1,'待审核','0','law_contract_audit_status','info','Y' union all select 2,'审核中','1','law_contract_audit_status','warning','N' union all select 3,'已通过','2','law_contract_audit_status','success','N' union all select 4,'已驳回','3','law_contract_audit_status','danger','N' union all select 5,'退回修改','4','law_contract_audit_status','warning','N' union all
 select 1,'通过','pass','law_contract_approval_action','success','Y' union all select 2,'驳回','reject','law_contract_approval_action','danger','N' union all select 3,'退回修改','back','law_contract_approval_action','warning','N' union all
 select 1,'草稿','0','law_contract_status','info','Y' union all select 2,'履约中','1','law_contract_status','primary','N' union all select 3,'已归档','2','law_contract_status','success','N' union all select 4,'已作废','3','law_contract_status','danger','N' union all select 5,'已终止','4','law_contract_status','warning','N' union all
 select 1,'创建合同','create','law_contract_status_action','primary','Y' union all select 2,'提交审批','submit','law_contract_status_action','warning','N' union all select 3,'审批处理','approval','law_contract_status_action','success','N' union all select 4,'合同签署','sign','law_contract_status_action','success','N' union all select 5,'合同归档','archive','law_contract_status_action','info','N' union all select 6,'合同作废','void','law_contract_status_action','danger','N' union all select 7,'合同终止','terminate','law_contract_status_action','danger','N' union all select 8,'新增收费计划','fee_create','law_contract_status_action','primary','N' union all select 9,'调整收费计划','fee_update','law_contract_status_action','warning','N' union all select 10,'删除收费计划','fee_delete','law_contract_status_action','danger','N' union all select 11,'确认收款','fee_confirm','law_contract_status_action','success','N' union all select 12,'驳回收款','fee_reject','law_contract_status_action','danger','N' union all select 13,'更新开票状态','fee_invoice','law_contract_status_action','success','N' union all select 14,'新增附件','attachment_add','law_contract_status_action','primary','N' union all select 15,'删除附件','attachment_delete','law_contract_status_action','danger','N' union all select 16,'客户合并迁移','customer_merge','law_contract_status_action','warning','N' union all
 select 1,'低','1','law_contract_risk_level','success','Y' union all select 2,'中','2','law_contract_risk_level','warning','N' union all select 3,'高','3','law_contract_risk_level','danger','N' union all
 select 1,'待确认','0','law_contract_receive_status','info','Y' union all select 2,'已确认','1','law_contract_receive_status','success','N' union all select 3,'已驳回','2','law_contract_receive_status','danger','N' union all
 select 1,'未开票','0','law_contract_invoice_status','info','Y' union all select 2,'已开票','1','law_contract_invoice_status','success','N' union all select 3,'部分开票','2','law_contract_invoice_status','warning','N'
) item
where not exists(select 1 from sys_dict_data where dict_type=item.type and dict_value=item.value);
