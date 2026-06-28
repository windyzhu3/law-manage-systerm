-- 案件中心初始化脚本，可重复执行

set @matter_col_exists = (select count(1) from information_schema.columns where table_schema = database() and table_name = 'biz_case' and column_name = 'risk_level');
set @matter_col_sql = if(@matter_col_exists = 0, 'alter table biz_case add column risk_level varchar(50) default ''medium'' comment ''风险等级'' after priority', 'select 1');
prepare stmt from @matter_col_sql; execute stmt; deallocate prepare stmt;

set @matter_col_exists = (select count(1) from information_schema.columns where table_schema = database() and table_name = 'biz_case' and column_name = 'case_stage');
set @matter_col_sql = if(@matter_col_exists = 0, 'alter table biz_case add column case_stage varchar(50) default ''opening'' comment ''办理阶段'' after risk_level', 'select 1');
prepare stmt from @matter_col_sql; execute stmt; deallocate prepare stmt;

set @matter_col_exists = (select count(1) from information_schema.columns where table_schema = database() and table_name = 'biz_case' and column_name = 'next_key_date');
set @matter_col_sql = if(@matter_col_exists = 0, 'alter table biz_case add column next_key_date date comment ''下次关键日期'' after current_node', 'select 1');
prepare stmt from @matter_col_sql; execute stmt; deallocate prepare stmt;

set @matter_col_exists = (select count(1) from information_schema.columns where table_schema = database() and table_name = 'biz_case' and column_name = 'recent_progress');
set @matter_col_sql = if(@matter_col_exists = 0, 'alter table biz_case add column recent_progress varchar(500) comment ''最近进度'' after next_key_date', 'select 1');
prepare stmt from @matter_col_sql; execute stmt; deallocate prepare stmt;

set @matter_col_exists = (select count(1) from information_schema.columns where table_schema = database() and table_name = 'biz_case' and column_name = 'last_progress_time');
set @matter_col_sql = if(@matter_col_exists = 0, 'alter table biz_case add column last_progress_time datetime comment ''最近进度时间'' after recent_progress', 'select 1');
prepare stmt from @matter_col_sql; execute stmt; deallocate prepare stmt;

set @matter_col_exists = (select count(1) from information_schema.columns where table_schema = database() and table_name = 'biz_case' and column_name = 'fee_status');
set @matter_col_sql = if(@matter_col_exists = 0, 'alter table biz_case add column fee_status varchar(50) default ''none'' comment ''费用状态'' after last_progress_time', 'select 1');
prepare stmt from @matter_col_sql; execute stmt; deallocate prepare stmt;

set @matter_col_exists = (select count(1) from information_schema.columns where table_schema = database() and table_name = 'biz_case' and column_name = 'archive_status');
set @matter_col_sql = if(@matter_col_exists = 0, 'alter table biz_case add column archive_status varchar(50) default ''none'' comment ''归档状态'' after fee_status', 'select 1');
prepare stmt from @matter_col_sql; execute stmt; deallocate prepare stmt;

set @matter_col_exists = (select count(1) from information_schema.columns where table_schema = database() and table_name = 'biz_case' and column_name = 'cause');
set @matter_col_sql = if(@matter_col_exists = 0, 'alter table biz_case add column cause varchar(200) comment ''案由'' after archive_status', 'select 1');
prepare stmt from @matter_col_sql; execute stmt; deallocate prepare stmt;

set @matter_col_exists = (select count(1) from information_schema.columns where table_schema = database() and table_name = 'biz_case' and column_name = 'dispute_amount');
set @matter_col_sql = if(@matter_col_exists = 0, 'alter table biz_case add column dispute_amount decimal(14,2) comment ''争议金额'' after cause', 'select 1');
prepare stmt from @matter_col_sql; execute stmt; deallocate prepare stmt;

set @matter_col_exists = (select count(1) from information_schema.columns where table_schema = database() and table_name = 'biz_case' and column_name = 'court_name');
set @matter_col_sql = if(@matter_col_exists = 0, 'alter table biz_case add column court_name varchar(200) comment ''法院/机构'' after dispute_amount', 'select 1');
prepare stmt from @matter_col_sql; execute stmt; deallocate prepare stmt;

set @matter_col_exists = (select count(1) from information_schema.columns where table_schema = database() and table_name = 'biz_case' and column_name = 'case_filing_no');
set @matter_col_sql = if(@matter_col_exists = 0, 'alter table biz_case add column case_filing_no varchar(100) comment ''法院案号'' after court_name', 'select 1');
prepare stmt from @matter_col_sql; execute stmt; deallocate prepare stmt;

set @matter_col_exists = (select count(1) from information_schema.columns where table_schema = database() and table_name = 'biz_case' and column_name = 'case_summary');
set @matter_col_sql = if(@matter_col_exists = 0, 'alter table biz_case add column case_summary varchar(1000) comment ''案件概况'' after case_filing_no', 'select 1');
prepare stmt from @matter_col_sql; execute stmt; deallocate prepare stmt;

create table if not exists biz_case_progress (
  progress_id bigint not null auto_increment comment '进度ID',
  case_id bigint not null comment '案件ID',
  record_time datetime comment '记录时间',
  record_user_id bigint comment '记录人ID',
  record_user_name varchar(64) comment '记录人',
  content varchar(1000) not null comment '进展内容',
  next_plan varchar(1000) comment '下一步计划',
  sync_customer char(1) default 'N' comment '是否同步客户',
  attachment_url varchar(500) comment '附件地址',
  attachment_name varchar(200) comment '附件名称',
  del_flag char(1) default '0',
  create_by varchar(64) default '',
  create_time datetime,
  update_by varchar(64) default '',
  update_time datetime,
  primary key (progress_id),
  key idx_case_progress_case (case_id, del_flag),
  key idx_case_progress_time (record_time)
) engine=innodb auto_increment=100 comment='案件进度记录';

create table if not exists biz_case_node (
  node_id bigint not null auto_increment comment '节点ID',
  case_id bigint not null comment '案件ID',
  node_name varchar(120) not null comment '节点名称',
  node_type varchar(50) comment '节点类型',
  node_status varchar(50) default 'pending' comment '节点状态',
  plan_date date not null comment '计划日期',
  actual_date date comment '实际日期',
  court_place varchar(200) comment '法院/地点',
  court_room varchar(100) comment '法庭/庭号',
  owner_id bigint comment '负责人ID',
  owner_name varchar(64) comment '负责人',
  remind_time datetime comment '提醒时间',
  remind_targets varchar(500) comment '提醒对象',
  del_flag char(1) default '0',
  create_by varchar(64) default '',
  create_time datetime,
  update_by varchar(64) default '',
  update_time datetime,
  remark varchar(500),
  primary key (node_id),
  key idx_case_node_case (case_id, del_flag),
  key idx_case_node_plan (plan_date, node_status)
) engine=innodb auto_increment=100 comment='案件关键节点';

create table if not exists biz_case_node_material (
  material_id bigint not null auto_increment comment '材料ID',
  node_id bigint not null comment '节点ID',
  material_name varchar(120) not null comment '材料名称',
  material_status varchar(50) default 'pending' comment '材料状态',
  file_url varchar(500) comment '文件地址',
  file_name varchar(200) comment '文件名称',
  create_by varchar(64) default '',
  create_time datetime,
  remark varchar(500),
  primary key (material_id),
  key idx_node_material_node (node_id)
) engine=innodb auto_increment=100 comment='案件节点材料清单';

set @matter_col_exists = (select count(1) from information_schema.columns where table_schema = database() and table_name = 'biz_case_node_material' and column_name = 'file_url');
set @matter_col_sql = if(@matter_col_exists = 0, 'alter table biz_case_node_material add column file_url varchar(500) comment ''文件地址'' after material_status', 'select 1');
prepare stmt from @matter_col_sql; execute stmt; deallocate prepare stmt;

set @matter_col_exists = (select count(1) from information_schema.columns where table_schema = database() and table_name = 'biz_case_node_material' and column_name = 'file_name');
set @matter_col_sql = if(@matter_col_exists = 0, 'alter table biz_case_node_material add column file_name varchar(200) comment ''文件名称'' after file_url', 'select 1');
prepare stmt from @matter_col_sql; execute stmt; deallocate prepare stmt;

create table if not exists biz_case_expense (
  expense_id bigint not null auto_increment comment '费用ID',
  expense_no varchar(64) not null comment '费用编号',
  case_id bigint not null comment '案件ID',
  expense_type varchar(50) comment '费用类型',
  amount decimal(14,2) not null comment '费用金额',
  occur_date date not null comment '发生日期',
  pay_status varchar(50) default 'pending' comment '付款状态',
  reimburse_status varchar(50) default 'pending' comment '报销状态',
  voucher_status varchar(50) default 'missing' comment '凭证状态',
  handler_id bigint comment '经办人ID',
  handler_name varchar(64) comment '经办人',
  voucher_url varchar(500) comment '凭证地址',
  voucher_name varchar(200) comment '凭证名称',
  del_flag char(1) default '0',
  create_by varchar(64) default '',
  create_time datetime,
  update_by varchar(64) default '',
  update_time datetime,
  remark varchar(500),
  primary key (expense_id),
  unique key uk_case_expense_no (expense_no),
  key idx_case_expense_case (case_id, del_flag)
) engine=innodb auto_increment=100 comment='案件费用记录';

create table if not exists biz_case_archive (
  archive_id bigint not null auto_increment comment '归档ID',
  case_id bigint not null comment '案件ID',
  close_result varchar(50) comment '结案结果',
  close_date date comment '结案日期',
  actual_received_amount decimal(14,2) comment '实际回款',
  fee_clear_status varchar(50) default 'uncleared' comment '费用结清状态',
  satisfaction int comment '客户满意度',
  summary varchar(1000) comment '办案总结',
  archive_status varchar(50) default 'pending' comment '归档状态',
  archive_no varchar(64) comment '归档编号',
  archiver_id bigint comment '归档人ID',
  archiver_name varchar(64) comment '归档人',
  readonly_flag char(1) default 'Y' comment '是否只读',
  create_by varchar(64) default '',
  create_time datetime,
  update_by varchar(64) default '',
  update_time datetime,
  primary key (archive_id),
  unique key uk_case_archive_case (case_id),
  unique key uk_case_archive_no (archive_no)
) engine=innodb auto_increment=100 comment='案件结案归档';

create table if not exists biz_case_archive_material (
  material_id bigint not null auto_increment comment '材料ID',
  archive_id bigint not null comment '归档ID',
  material_name varchar(120) not null comment '材料名称',
  material_status varchar(50) default 'pending' comment '材料状态',
  file_url varchar(500) comment '文件地址',
  file_name varchar(200) comment '文件名称',
  create_by varchar(64) default '',
  create_time datetime,
  remark varchar(500),
  primary key (material_id),
  key idx_archive_material_archive (archive_id)
) engine=innodb auto_increment=100 comment='案件归档材料';

create table if not exists biz_case_document (
  document_id bigint not null auto_increment comment '文档ID',
  case_id bigint not null comment '案件ID',
  document_type varchar(50) comment '文档类型',
  file_name varchar(200) not null comment '文件名称',
  file_url varchar(500) not null comment '文件地址',
  file_type varchar(50) comment '文件类型',
  file_size bigint comment '文件大小',
  del_flag char(1) default '0',
  create_by varchar(64) default '',
  create_time datetime,
  update_by varchar(64) default '',
  update_time datetime,
  remark varchar(500),
  primary key (document_id),
  key idx_case_document_case (case_id, del_flag)
) engine=innodb auto_increment=100 comment='案件文档资料';

create table if not exists biz_case_field_value (
  field_id bigint not null auto_increment comment '字段值ID',
  case_id bigint not null comment '案件ID',
  field_code varchar(80) not null comment '字段编码',
  field_name varchar(120) comment '字段名称',
  field_value varchar(1000) comment '字段值',
  order_num int default 0 comment '排序',
  create_by varchar(64) default '',
  create_time datetime,
  primary key (field_id),
  unique key uk_case_field (case_id, field_code)
) engine=innodb auto_increment=100 comment='案件类型差异字段值';

create table if not exists biz_case_field_config (
  config_id bigint not null auto_increment comment '字段配置ID',
  case_type varchar(64) not null comment '案件类型',
  field_code varchar(80) not null comment '字段编码',
  field_name varchar(120) not null comment '字段名称',
  field_type varchar(30) default 'text' comment '字段类型',
  required_flag char(1) default 'N' comment '是否必填',
  placeholder varchar(200) comment '占位提示',
  help_text varchar(500) comment '帮助说明',
  order_num int default 0 comment '排序',
  status char(1) default '0' comment '状态',
  create_by varchar(64) default '',
  create_time datetime,
  update_by varchar(64) default '',
  update_time datetime,
  primary key (config_id),
  unique key uk_case_type_field (case_type, field_code)
) engine=innodb auto_increment=100 comment='案件类型差异字段配置';

insert into sys_dict_type(dict_name, dict_type, status, create_by, create_time, remark)
select item.dict_name,item.dict_type,'0','admin',sysdate(),item.dict_name
from (
 select '案件办理阶段' dict_name,'law_case_stage' dict_type union all
 select '案件案由','law_case_cause' union all
 select '案件节点状态','law_case_node_status' union all
 select '案件节点类型','law_case_node_type' union all
 select '案件材料状态','law_case_material_status' union all
 select '案件文档类型','law_case_document_type' union all
 select '案件费用类型','law_case_expense_type' union all
 select '案件付款状态','law_case_pay_status' union all
 select '案件报销状态','law_case_reimburse_status' union all
 select '案件凭证状态','law_case_voucher_status' union all
 select '案件费用状态','law_case_fee_status' union all
 select '案件结案结果','law_case_close_result' union all
 select '案件费用结清状态','law_case_fee_clear_status' union all
 select '案件归档状态','law_case_archive_status' union all
 select '案件差异字段','law_case_field_code' union all
 select '是否同步客户','law_case_sync_customer'
) item
where not exists(select 1 from sys_dict_type where dict_type=item.dict_type);

insert into sys_dict_data(dict_sort,dict_label,dict_value,dict_type,css_class,list_class,is_default,status,create_by,create_time,remark)
select item.sort,item.label,item.value,item.type,'',item.class,item.def,'0','admin',sysdate(),item.label
from (
 select 1 sort,'开案审理' label,'opening' value,'law_case_stage' type,'primary' class,'Y' def union all
 select 2,'证据交换','evidence','law_case_stage','primary','N' union all
 select 3,'庭审/办理','hearing','law_case_stage','warning','N' union all
 select 4,'执行阶段','execution','law_case_stage','danger','N' union all
 select 5,'结案归档','archive','law_case_stage','success','N' union all
 select 1,'合同纠纷','contract_dispute','law_case_cause','primary','Y' union all
 select 2,'劳动争议','labor_dispute','law_case_cause','warning','N' union all
 select 3,'股权转让纠纷','equity_transfer','law_case_cause','primary','N' union all
 select 4,'知识产权侵权','ip_infringement','law_case_cause','success','N' union all
 select 5,'婚姻家事纠纷','marital_family','law_case_cause','danger','N' union all
 select 6,'债权债务纠纷','debt_collection','law_case_cause','warning','N' union all
 select 7,'行政争议','admin_dispute','law_case_cause','info','N' union all
 select 8,'刑事辩护','criminal_defense','law_case_cause','danger','N' union all
 select 9,'常年法律顾问','legal_advisor','law_case_cause','success','N' union all
 select 10,'其他','other','law_case_cause','default','N' union all
 select 1,'待开始','pending','law_case_node_status','info','Y' union all
 select 2,'当前节点','current','law_case_node_status','primary','N' union all
 select 3,'已完成','done','law_case_node_status','success','N' union all
 select 4,'超期','overdue','law_case_node_status','danger','N' union all
 select 1,'立案受理','filing','law_case_node_type','primary','Y' union all
 select 2,'证据交换','evidence','law_case_node_type','success','N' union all
 select 3,'开庭审理','hearing','law_case_node_type','warning','N' union all
 select 4,'判决送达','judgment','law_case_node_type','primary','N' union all
 select 5,'执行阶段','execution','law_case_node_type','danger','N' union all
 select 6,'归档','archive','law_case_node_type','info','N' union all
 select 1,'待准备','pending','law_case_material_status','warning','Y' union all
 select 2,'已准备','ready','law_case_material_status','success','N' union all
 select 3,'待补交','supplement','law_case_material_status','danger','N' union all
 select 1,'案件文档','case','law_case_document_type','primary','Y' union all
 select 2,'证据材料','evidence','law_case_document_type','success','N' union all
 select 3,'诉讼文书','litigation','law_case_document_type','warning','N' union all
 select 4,'合同协议','contract','law_case_document_type','primary','N' union all
 select 5,'沟通记录','communication','law_case_document_type','info','N' union all
 select 6,'归档材料','archive','law_case_document_type','success','N' union all
 select 7,'其他材料','other','law_case_document_type','default','N' union all
 select 1,'诉讼费','litigation','law_case_expense_type','primary','Y' union all
 select 2,'鉴定费','appraisal','law_case_expense_type','warning','N' union all
 select 3,'保全费','preservation','law_case_expense_type','danger','N' union all
 select 4,'差旅费','travel','law_case_expense_type','info','N' union all
 select 5,'律师费','lawyer','law_case_expense_type','success','N' union all
 select 6,'其他','other','law_case_expense_type','default','N' union all
 select 1,'待付款','pending','law_case_pay_status','warning','Y' union all
 select 2,'已付款','paid','law_case_pay_status','success','N' union all
 select 1,'待报销','pending','law_case_reimburse_status','warning','Y' union all
 select 2,'已报销','done','law_case_reimburse_status','success','N' union all
 select 3,'已驳回','rejected','law_case_reimburse_status','danger','N' union all
 select 1,'未上传','missing','law_case_voucher_status','danger','Y' union all
 select 2,'已上传','uploaded','law_case_voucher_status','success','N' union all
 select 1,'无费用','none','law_case_fee_status','info','Y' union all
 select 2,'部分未收','partial','law_case_fee_status','warning','N' union all
 select 3,'已收齐','settled','law_case_fee_status','success','N' union all
 select 1,'胜诉','win','law_case_close_result','success','Y' union all
 select 2,'败诉','lose','law_case_close_result','danger','N' union all
 select 3,'和解','settlement','law_case_close_result','primary','N' union all
 select 4,'调解','mediate','law_case_close_result','warning','N' union all
 select 5,'撤诉','withdraw','law_case_close_result','info','N' union all
 select 6,'其他','other','law_case_close_result','default','N' union all
 select 1,'未结清','uncleared','law_case_fee_clear_status','warning','Y' union all
 select 2,'已结清','cleared','law_case_fee_clear_status','success','N' union all
 select 1,'无归档','none','law_case_archive_status','info','Y' union all
 select 2,'待归档','pending','law_case_archive_status','warning','N' union all
 select 3,'已归档','archived','law_case_archive_status','success','N' union all
 select 1,'原告','plaintiff','law_case_field_code','primary','N' union all
 select 2,'被告','defendant','law_case_field_code','primary','N' union all
 select 3,'诉讼请求','claims','law_case_field_code','primary','N' union all
 select 4,'罪名','crime','law_case_field_code','danger','N' union all
 select 5,'办案机关','agency','law_case_field_code','warning','N' union all
 select 6,'权利类型','ip_right_type','law_case_field_code','success','N' union all
 select 7,'国家地区','country_region','law_case_field_code','info','N' union all
 select 1,'是','Y','law_case_sync_customer','success','N' union all
 select 2,'否','N','law_case_sync_customer','info','Y'
) item
where not exists(select 1 from sys_dict_data where dict_type=item.type and dict_value=item.value);

update biz_case_document
set document_type='case'
where document_type is null or document_type='';

insert into sys_dict_data(dict_sort,dict_label,dict_value,dict_type,css_class,list_class,is_default,status,create_by,create_time,remark)
select item.sort,item.label,item.value,'law_case_status','',item.class,'N','0','admin',sysdate(),item.label
from (
 select 7 sort,'结案中' label,'closing' value,'warning' class union all
 select 8,'已结案','closed','success'
) item
where not exists(select 1 from sys_dict_data where dict_type='law_case_status' and dict_value=item.value);

insert into sys_dict_data(dict_sort,dict_label,dict_value,dict_type,css_class,list_class,is_default,status,create_by,create_time,remark)
select item.sort,item.label,item.value,'law_case_status_action','',item.class,'N','0','admin',sysdate(),item.label
from (
 select 1 sort,'创建案件' label,'create' value,'primary' class union all
 select 20,'编辑案件','edit','primary' union all
 select 21,'新增进度','progress_add','success' union all
 select 22,'编辑进度','progress_edit','primary' union all
 select 23,'删除进度','progress_remove','danger' union all
 select 24,'新增节点','node_add','success' union all
 select 25,'编辑节点','node_edit','primary' union all
 select 26,'删除节点','node_remove','danger' union all
 select 27,'新增费用','expense_add','warning' union all
 select 28,'编辑费用','expense_edit','primary' union all
 select 29,'删除费用','expense_remove','danger' union all
 select 30,'新增文档','document_add','success' union all
 select 31,'删除文档','document_remove','danger' union all
 select 32,'结案申请','archive_apply','warning' union all
 select 33,'确认结案','archive_close','success' union all
 select 34,'确认归档','archive_confirm','success'
) item
where not exists(select 1 from sys_dict_data where dict_type='law_case_status_action' and dict_value=item.value);

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

update sys_menu
set parent_id=@matter_menu_id,
    order_num=6,
    path='document',
    component='matter/index',
    query='{"module":"document"}',
    route_name='MatterDocument',
    menu_type='C',
    icon='documentation',
    update_by='admin',
    update_time=sysdate()
where @matter_menu_id is not null
  and perms='matter:document:list'
  and menu_type='F';

insert into sys_menu(menu_name,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
select item.menu_name,@matter_menu_id,item.order_num,item.path,'matter/index',item.query,item.route_name,1,0,'C','0','0',item.perms,item.icon,'admin',sysdate(),item.menu_name
from (
 select '案件列表' menu_name,1 order_num,'list' path,'{"module":"list"}' query,'MatterList' route_name,'matter:list' perms,'list' icon union all
 select '我的案件',2,'mine','{"module":"mine"}','MatterMine','matter:mine:list','user' union all
 select '进度记录',3,'progress','{"module":"progress"}','MatterProgress','matter:progress:list','time' union all
 select '关键节点',4,'node','{"module":"node"}','MatterNode','matter:node:list','date' union all
 select '费用管理',5,'expense','{"module":"expense"}','MatterExpense','matter:expense:list','money' union all
 select '文档资料',6,'document','{"module":"document"}','MatterDocument','matter:document:list','documentation' union all
 select '结案归档',7,'archive','{"module":"archive"}','MatterArchive','matter:archive:list','folder' union all
 select '状态记录',8,'status','{"module":"status"}','MatterStatus','matter:status:list','time'
) item
where not exists(select 1 from sys_menu where parent_id=@matter_menu_id and path=item.path);

update sys_menu m
join (
 select '案件列表' menu_name,1 order_num,'list' path,'{"module":"list"}' query,'MatterList' route_name,'matter:list' perms,'list' icon union all
 select '我的案件',2,'mine','{"module":"mine"}','MatterMine','matter:mine:list','user' union all
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
 select '我的案件查询','matter:mine:list',1,'matter:mine:query' union all
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
where not exists(select 1 from sys_menu where perms=item.perms);

update sys_menu btn
join (
 select '案件查询' menu_name,'matter:list' parent_perm,1 order_num,'matter:query' perms union all
 select '我的案件查询','matter:mine:list',1,'matter:mine:query' union all
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

set @matter_document_menu_id = (select menu_id from sys_menu where parent_id=@matter_menu_id and perms='matter:document:list' and menu_type='C' limit 1);

update sys_menu
set parent_id=@matter_document_menu_id,
    order_num=case perms when 'matter:document:add' then 1 when 'matter:document:remove' then 2 else order_num end,
    update_by='admin',
    update_time=sysdate()
where @matter_document_menu_id is not null
  and perms in ('matter:document:add','matter:document:remove');

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
    or (m.parent_id=@matter_menu_id and m.path in ('mine','progress','node','expense','document','archive','status'))
    or m.perms in (
      'matter:mine:list','matter:mine:query',
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
    or (m.parent_id=@matter_menu_id and m.path in ('mine','progress','node','document','status'))
    or m.perms in (
      'matter:mine:list','matter:mine:query',
      'matter:progress:list','matter:progress:add',
      'matter:node:list',
      'matter:document:list',
      'matter:status:list'
    )
  )
  and not exists(select 1 from sys_role_menu rm where rm.role_id=@intern_lawyer_role_id and rm.menu_id=m.menu_id);

insert into sys_dict_data(dict_sort,dict_label,dict_value,dict_type,css_class,list_class,is_default,status,create_by,create_time,remark)
select item.sort,item.label,item.value,'law_case_type','',item.class,'N','0','admin',sysdate(),item.label
from (
 select 9 sort,'行政诉讼' label,'admin_litigation' value,'warning' class union all
 select 10,'涉外业务','foreign','info'
) item
where not exists(select 1 from sys_dict_data where dict_type='law_case_type' and dict_value=item.value);

insert into biz_case_field_config(case_type,field_code,field_name,field_type,required_flag,placeholder,help_text,order_num,status,create_by,create_time)
select item.case_type,item.field_code,item.field_name,item.field_type,item.required_flag,item.placeholder,item.help_text,item.order_num,'0','admin',sysdate()
from (
 select 'civil' case_type,'plaintiff' field_code,'原告信息' field_name,'text' field_type,'Y' required_flag,'请输入原告名称、联系方式或主体信息' placeholder,'民事诉讼需明确原告主体。' help_text,1 order_num union all
 select 'civil','defendant','被告信息','text','Y','请输入被告名称、联系方式或主体信息','民事诉讼需明确被告主体。',2 union all
 select 'civil','claims','诉讼请求','textarea','Y','请输入主要诉讼请求','建议按请求项分条记录。',3 union all
 select 'civil','jurisdiction_basis','管辖依据','textarea','Y','请输入法院管辖依据','用于立案审查和风险判断。',4 union all
 select 'civil','limitation_deadline','诉讼时效截止日期','date','Y','请选择诉讼时效截止日期','临近时效需重点提示。',5 union all
 select 'civil','evidence_catalog','证据目录','textarea','Y','请输入证据目录或材料包说明','可后续上传完整证据材料。',6 union all
 select 'civil','property_preservation','财产保全申请','switch','N','','是否考虑保全措施。',7 union all
 select 'civil','dispute_focus','关键争议点','textarea','Y','请输入关键争议点','用于办案策略和节点安排。',8 union all
 select 'business','arbitration_org','仲裁机构','text','Y','请输入仲裁机构','商事仲裁需明确机构。',1 union all
 select 'business','contract_no','合同编号','text','Y','请输入争议合同编号','用于关联争议基础文件。',2 union all
 select 'business','arbitration_clause','仲裁条款','textarea','Y','请输入仲裁条款或约定','判断管辖与程序风险。',3 union all
 select 'business','preservation_apply','保全申请','switch','N','','是否申请财产/证据保全。',4 union all
 select 'business','core_dispute','核心争议点','textarea','Y','请输入核心争议点','用于仲裁请求与证据组织。',5 union all
 select 'criminal','suspect_name','犯罪嫌疑人/被告人','text','Y','请输入姓名','刑事案件核心当事人。',1 union all
 select 'criminal','id_card','身份证号','text','Y','请输入身份证号','用于委托、会见和文书。',2 union all
 select 'criminal','crime','罪名','text','Y','请输入涉嫌罪名','用于判断案件阶段和辩护方向。',3 union all
 select 'criminal','agency','办案机关','text','Y','请输入公安/检察院/法院','记录当前承办机关。',4 union all
 select 'criminal','criminal_stage','案件阶段','text','Y','请输入侦查/审查起诉/审判等','刑事案件阶段影响工作重点。',5 union all
 select 'criminal','detention_place','羁押地点','text','N','请输入看守所或羁押地点','便于安排会见。',6 union all
 select 'criminal','compulsory_measure','强制措施','text','Y','请输入拘留、逮捕、取保候审等','刑事案件需持续跟踪强制措施变化。',7 union all
 select 'criminal','family_contact','家属联系人','text','Y','请输入家属联系人','用于沟通授权和案情反馈。',8 union all
 select 'criminal','family_mobile','家属联系电话','text','Y','请输入家属联系电话','用于沟通授权和案情反馈。',9 union all
 select 'criminal','meeting_status','会见情况','textarea','Y','请输入已会见次数、最近会见时间和重点内容','律师办理刑事案件的重要过程记录。',10 union all
 select 'criminal','bail_pending','是否取保候审','switch','Y','','记录强制措施方向。',11 union all
 select 'criminal','defense_direction','主要辩护方向','textarea','Y','请输入无罪/罪轻/量刑等方向','用于办案策略。',12 union all
 select 'ip','right_type','权利类型','text','Y','请输入商标/专利/著作权等','知识产权案件基础字段。',1 union all
 select 'ip','right_owner','权利人','text','Y','请输入权利人','用于主体和授权审查。',2 union all
 select 'ip','right_name','权利名称','text','Y','请输入商标/专利/著作权名称','记录权利客体。',3 union all
 select 'ip','registration_no','注册号/专利号','text','Y','请输入注册号/专利号','用于权属校验。',4 union all
 select 'ip','infringer','侵权方','text','Y','请输入侵权方信息','用于取证与诉讼对象。',5 union all
 select 'ip','infringement_type','侵权类型','text','Y','请输入侵权类型','如仿冒、盗版、不正当竞争。',6 union all
 select 'ip','platform_region','侵权平台/地域','text','Y','请输入平台或地域','用于取证和管辖判断。',7 union all
 select 'ip','evidence_method','取证方式','text','Y','请输入公证、截图、购买取证等','影响证据效力。',8 union all
 select 'ip','rights_goal','维权目标','textarea','Y','请输入停止侵权、赔偿、和解等目标','用于设计办理路线。',9 union all
 select 'ip','loss_amount','估算损失金额','number','Y','请输入估算损失金额','用于评估诉请和风险。',10 union all
 select 'admin_litigation','administrative_org','被诉行政机关','text','Y','请输入行政机关名称','行政诉讼核心被告。',1 union all
 select 'admin_litigation','administrative_action','行政行为类型','text','Y','请输入处罚、许可、征收等','用于判断案由。',2 union all
 select 'admin_litigation','reconsideration_status','复议状态','text','Y','请输入是否复议及结果','影响起诉条件。',3 union all
 select 'admin_litigation','document_no','行政文书编号','text','Y','请输入行政文书编号','用于文书和期限追踪。',4 union all
 select 'admin_litigation','claims','诉讼请求','textarea','Y','请输入诉讼请求','明确撤销、确认违法或履行职责等。',5 union all
 select 'admin_litigation','filing_basis','立案依据','textarea','Y','请输入立案依据','用于审查起诉条件。',6 union all
 select 'foreign','country_region','国家/地区','text','Y','请输入国家或地区','涉外业务基础维度。',1 union all
 select 'foreign','applicable_law','适用法律','text','Y','请输入适用法律','用于风险和策略判断。',2 union all
 select 'foreign','jurisdiction_clause','管辖约定','textarea','Y','请输入管辖约定','用于争议解决路径。',3 union all
 select 'foreign','service_abroad','境外送达','text','Y','请输入送达方式或状态','影响周期和节点。',4 union all
 select 'foreign','translation_language','翻译语言','text','Y','请输入翻译语言','用于材料准备。',5 union all
 select 'foreign','foreign_party','境外当事人信息','textarea','Y','请输入境外当事人信息','用于主体识别和送达。',6
) item
where not exists(select 1 from biz_case_field_config c where c.case_type=item.case_type and c.field_code=item.field_code);

insert into biz_case_field_config(case_type,field_code,field_name,field_type,required_flag,placeholder,help_text,order_num,status,create_by,create_time)
select item.case_type,item.field_code,item.field_name,item.field_type,item.required_flag,item.placeholder,item.help_text,item.order_num,'0','admin',sysdate()
from (
 select 'labor' case_type,'employee_name' field_code,'劳动者姓名' field_name,'text' field_type,'Y' required_flag,'请输入劳动者姓名' placeholder,'劳动人事案件需先明确劳动者主体。' help_text,1 order_num union all
 select 'labor','employer_name','用人单位','text','Y','请输入用人单位名称','用于确认劳动关系与仲裁被申请人。',2 union all
 select 'labor','employment_period','用工期间','text','Y','请输入入职、离职或争议期间','用于计算工资、补偿金和仲裁时效。',3 union all
 select 'labor','claim_items','仲裁/诉讼请求','textarea','Y','请输入工资、赔偿金、经济补偿等请求','建议按请求项分条记录。',4 union all
 select 'labor','salary_base','工资基数','number','Y','请输入工资或平均工资基数','用于测算经济补偿、赔偿和加班费。',5 union all
 select 'labor','evidence_materials','劳动证据材料','textarea','Y','请输入合同、工资流水、考勤、社保等材料','决定劳动关系和金额主张的证明强度。',6 union all
 select 'labor','arbitration_deadline','仲裁时效截止日','date','Y','请选择仲裁时效截止日','劳动争议时效临近时需优先处理。',7 union all
 select 'labor','settlement_intention','调解意向','textarea','N','请输入调解方案或底线','便于庭前或仲裁阶段谈判。',8 union all
 select 'advisor','service_scope','顾问服务范围','textarea','Y','请输入常法服务范围','明确合同审查、合规咨询、培训等服务边界。',1 union all
 select 'advisor','contact_window','客户对接窗口','text','Y','请输入客户联系人或部门','用于日常顾问服务协同。',2 union all
 select 'advisor','monthly_hours','月度服务时长','number','N','请输入约定服务小时数','用于跟踪顾问工作量。',3 union all
 select 'advisor','review_sla','文件审查时限','text','Y','请输入响应时限或交付标准','常法服务需要明确时效承诺。',4 union all
 select 'advisor','compliance_topics','重点合规事项','textarea','Y','请输入重点合规议题','用于年度服务计划和风险提示。',5 union all
 select 'advisor','renewal_date','续约提醒日期','date','N','请选择续约提醒日期','便于合同续签和客户维护。',6 union all
 select 'company','company_name','公司名称','text','Y','请输入公司名称','公司法务案件需明确主体。',1 union all
 select 'company','business_matter','业务事项','text','Y','请输入股权、治理、投融资等事项','用于区分公司治理、交易和合规类型。',2 union all
 select 'company','shareholder_structure','股权结构','textarea','Y','请输入股东及持股比例','影响决策权限和争议处理路径。',3 union all
 select 'company','resolution_requirement','决议/授权要求','textarea','Y','请输入董事会、股东会或授权要求','用于判断文件有效性。',4 union all
 select 'company','transaction_amount','交易/争议金额','number','N','请输入金额','用于风险分级和审批安排。',5 union all
 select 'company','filing_requirement','工商/备案事项','textarea','N','请输入变更登记、备案或披露要求','便于安排后续节点。',6 union all
 select 'finance','institution_party','金融机构/交易方','text','Y','请输入金融机构或交易方','金融资本案件需明确交易主体。',1 union all
 select 'finance','product_type','金融产品类型','text','Y','请输入借贷、基金、资管、担保等类型','用于判断监管和合同结构。',2 union all
 select 'finance','principal_amount','本金/投资金额','number','Y','请输入本金或投资金额','用于测算风险暴露。',3 union all
 select 'finance','guarantee_info','担保/增信措施','textarea','Y','请输入抵押、质押、保证等信息','影响清收和诉讼策略。',4 union all
 select 'finance','default_status','违约情况','textarea','Y','请输入逾期、违约、触发事件等情况','用于判断催收和处置路径。',5 union all
 select 'finance','regulatory_focus','监管关注点','textarea','N','请输入合规或监管关注点','金融业务需关注合规边界。',6
) item
where not exists(select 1 from biz_case_field_config c where c.case_type=item.case_type and c.field_code=item.field_code);

insert into sys_dict_data(dict_sort,dict_label,dict_value,dict_type,css_class,list_class,is_default,status,create_by,create_time,remark)
select min(c.order_num) + 100, min(c.field_name), c.field_code, 'law_case_field_code', '', 'primary', 'N', '0', 'admin', sysdate(), min(c.field_name)
from biz_case_field_config c
where c.status='0'
  and not exists(select 1 from sys_dict_data d where d.dict_type='law_case_field_code' and d.dict_value=c.field_code)
group by c.field_code;
