-- 案管中心初始化脚本，可重复执行

create table if not exists biz_case (
  case_id bigint(20) not null auto_increment comment '案件ID',
  case_no varchar(64) not null comment '案件编号',
  case_name varchar(200) not null comment '案件名称',
  customer_id bigint(20) comment '客户ID',
  customer_name varchar(200) comment '客户名称',
  contract_id bigint(20) not null comment '来源合同ID',
  contract_no varchar(64) comment '来源合同编号',
  case_type varchar(50) comment '案件类型',
  urgency varchar(50) default 'normal' comment '紧急程度',
  case_status varchar(50) default 'pending' comment '案件状态',
  priority varchar(50) default 'medium' comment '优先级',
  main_lawyer_id bigint(20) comment '主办律师ID',
  main_lawyer_name varchar(64) comment '主办律师',
  assistant_lawyer_ids varchar(500) comment '协办律师ID集合',
  assistant_lawyer_names varchar(500) comment '协办律师集合',
  estimated_workload decimal(10,2) default 24.00 comment '预计工作量',
  estimated_cycle varchar(50) comment '预计处理周期',
  plan_start_date date comment '预计开案日期',
  current_node varchar(100) comment '当前节点',
  owner_id bigint(20) comment '负责人',
  dept_id bigint(20) comment '部门',
  del_flag char(1) default '0' comment '删除标志',
  create_by varchar(64) default '' comment '创建者',
  create_time datetime comment '创建时间',
  update_by varchar(64) default '' comment '更新者',
  update_time datetime comment '更新时间',
  remark varchar(500) comment '备注',
  primary key (case_id),
  unique key uk_biz_case_no (case_no),
  unique key uk_biz_case_contract (contract_id),
  key idx_biz_case_status (case_status, urgency, case_type, del_flag),
  key idx_biz_case_lawyer (main_lawyer_id, dept_id)
) engine=innodb auto_increment=100 comment='案管案件表';

create table if not exists biz_case_assignment (
  assignment_id bigint(20) not null auto_increment comment '分案ID',
  case_id bigint(20) not null comment '案件ID',
  assign_method varchar(50) comment '分配方式',
  main_lawyer_id bigint(20) not null comment '主办律师ID',
  main_lawyer_name varchar(64) comment '主办律师',
  assistant_lawyer_ids varchar(500) comment '协办律师ID集合',
  assistant_lawyer_names varchar(500) comment '协办律师集合',
  priority varchar(50) comment '优先级',
  estimated_cycle varchar(50) comment '预计周期',
  plan_start_date date comment '预计开案日期',
  assign_reason varchar(50) comment '分配原因',
  notify_flag char(1) default 'Y' comment '是否通知',
  create_by varchar(64) default '' comment '创建者',
  create_time datetime comment '创建时间',
  remark varchar(500) comment '备注',
  primary key (assignment_id),
  key idx_case_assignment_case (case_id),
  key idx_case_assignment_lawyer (main_lawyer_id)
) engine=innodb auto_increment=100 comment='案件分配记录';

create table if not exists biz_case_transfer (
  transfer_id bigint(20) not null auto_increment comment '转案ID',
  transfer_no varchar(64) not null comment '转案单号',
  case_id bigint(20) not null comment '案件ID',
  from_lawyer_id bigint(20) comment '当前律师ID',
  from_lawyer_name varchar(64) comment '当前律师',
  to_lawyer_id bigint(20) comment '拟转入律师ID',
  to_lawyer_name varchar(64) comment '拟转入律师',
  applicant_id bigint(20) comment '申请人ID',
  applicant_name varchar(64) comment '申请人',
  transfer_reason varchar(50) comment '转案原因',
  risk_level varchar(50) comment '风险等级',
  detail varchar(1000) comment '转案详情',
  transfer_status varchar(50) default 'pending' comment '审批状态',
  current_node varchar(100) comment '当前节点',
  approval_opinion varchar(500) comment '审批意见',
  approver_id bigint(20) comment '审批人ID',
  approver_name varchar(64) comment '审批人',
  create_by varchar(64) default '' comment '创建者',
  create_time datetime comment '创建时间',
  update_by varchar(64) default '' comment '更新者',
  update_time datetime comment '更新时间',
  primary key (transfer_id),
  unique key uk_biz_case_transfer_no (transfer_no),
  key idx_case_transfer_case (case_id),
  key idx_case_transfer_status (transfer_status, risk_level)
) engine=innodb auto_increment=100 comment='案件转案审批';

create table if not exists biz_lawyer_profile (
  profile_id bigint(20) not null auto_increment comment '律师配置ID',
  user_id bigint(20) not null comment '用户ID',
  lawyer_role varchar(50) default 'lawyer' comment '律师角色',
  specialties varchar(500) default 'business' comment '专业方向',
  load_limit decimal(10,2) default 100.00 comment '负载上限',
  avg_response_hours decimal(10,2) default 4.00 comment '平均响应时长',
  assign_enabled char(1) default 'Y' comment '是否可分案',
  create_by varchar(64) default '' comment '创建者',
  create_time datetime comment '创建时间',
  update_by varchar(64) default '' comment '更新者',
  update_time datetime comment '更新时间',
  remark varchar(500) comment '备注',
  primary key (profile_id),
  unique key uk_lawyer_profile_user (user_id)
) engine=innodb auto_increment=100 comment='律师案管配置';

create table if not exists biz_case_confirm (
  confirm_id bigint(20) not null auto_increment comment '确认ID',
  case_id bigint(20) not null comment '案件ID',
  confirm_type varchar(50) comment '确认类型',
  confirm_status varchar(50) default 'pending' comment '确认状态',
  confirm_user_id bigint(20) comment '确认人ID',
  confirm_user_name varchar(64) comment '确认人',
  content varchar(500) comment '确认内容',
  handler_id bigint(20) comment '处理人ID',
  handler_name varchar(64) comment '处理人',
  create_by varchar(64) default '' comment '创建者',
  create_time datetime comment '创建时间',
  update_by varchar(64) default '' comment '更新者',
  update_time datetime comment '更新时间',
  remark varchar(500) comment '备注',
  primary key (confirm_id),
  key idx_case_confirm_case (case_id),
  key idx_case_confirm_status (confirm_status)
) engine=innodb auto_increment=100 comment='案件待确认信息';

create table if not exists biz_case_status_log (
  log_id bigint(20) not null auto_increment comment '日志ID',
  case_id bigint(20) not null comment '案件ID',
  from_status varchar(50) comment '原状态',
  to_status varchar(50) comment '新状态',
  action_type varchar(50) comment '动作类型',
  content varchar(1000) comment '内容',
  create_by varchar(64) default '' comment '创建者',
  create_time datetime comment '创建时间',
  primary key (log_id),
  key idx_case_status_case (case_id)
) engine=innodb auto_increment=100 comment='案件状态记录';

insert into sys_dict_type(dict_name, dict_type, status, create_by, create_time, remark)
select item.dict_name,item.dict_type,'0','admin',sysdate(),item.dict_name
from (
 select '案件类型' dict_name,'law_case_type' dict_type union all
 select '案件紧急程度','law_case_urgency' union all
 select '案件状态','law_case_status' union all
 select '案件优先级','law_case_priority' union all
 select '案件分配方式','law_case_assign_method' union all
 select '案件分配原因','law_case_assign_reason' union all
 select '律师角色','law_lawyer_role' union all
 select '律师匹配等级','law_lawyer_match_level' union all
 select '律师负载状态','law_lawyer_load_status' union all
 select '转案审批状态','law_case_transfer_status' union all
 select '转案原因','law_case_transfer_reason' union all
 select '案件风险等级','law_case_risk_level' union all
 select '案件状态动作','law_case_status_action' union all
 select '案件确认状态','law_case_confirm_status'
) item
where not exists(select 1 from sys_dict_type where dict_type=item.dict_type);

update sys_dict_type t
join (
 select '案件类型' dict_name,'law_case_type' dict_type union all
 select '案件紧急程度','law_case_urgency' union all
 select '案件状态','law_case_status' union all
 select '案件优先级','law_case_priority' union all
 select '案件分配方式','law_case_assign_method' union all
 select '案件分配原因','law_case_assign_reason' union all
 select '律师角色','law_lawyer_role' union all
 select '律师匹配等级','law_lawyer_match_level' union all
 select '律师负载状态','law_lawyer_load_status' union all
 select '转案审批状态','law_case_transfer_status' union all
 select '转案原因','law_case_transfer_reason' union all
 select '案件风险等级','law_case_risk_level' union all
 select '案件状态动作','law_case_status_action' union all
 select '案件确认状态','law_case_confirm_status'
) item on item.dict_type=t.dict_type
set t.dict_name=item.dict_name, t.status='0', t.remark=item.dict_name;

insert into sys_dict_data(dict_sort,dict_label,dict_value,dict_type,css_class,list_class,is_default,status,create_by,create_time,remark)
select item.sort,item.label,item.value,item.type,'',item.class,item.def,'0','admin',sysdate(),item.label
from (
 select 1 sort,'民事' label,'civil' value,'law_case_type' type,'primary' class,'Y' def union all
 select 2,'刑事','criminal','law_case_type','danger','N' union all
 select 3,'商事','business','law_case_type','warning','N' union all
 select 4,'顾问','advisor','law_case_type','success','N' union all
 select 5,'公司法务','company','law_case_type','success','N' union all
 select 6,'金融资本','finance','law_case_type','warning','N' union all
 select 7,'知识产权','ip','law_case_type','primary','N' union all
 select 8,'劳动人事','labor','law_case_type','info','N' union all
 select 1,'普通','normal','law_case_urgency','info','Y' union all
 select 2,'紧急','urgent','law_case_urgency','warning','N' union all
 select 3,'较紧急','high','law_case_urgency','danger','N' union all
 select 1,'待分案','pending','law_case_status','warning','Y' union all
 select 2,'办理中','processing','law_case_status','primary','N' union all
 select 3,'转案中','transfering','law_case_status','warning','N' union all
 select 4,'待确认','confirming','law_case_status','info','N' union all
 select 5,'已归档','archived','law_case_status','success','N' union all
 select 6,'已终止','terminated','law_case_status','danger','N' union all
 select 1,'高','high','law_case_priority','danger','N' union all
 select 2,'中','medium','law_case_priority','warning','Y' union all
 select 3,'低','low','law_case_priority','success','N' union all
 select 1,'人工分配','manual','law_case_assign_method','primary','Y' union all
 select 2,'智能推荐','auto','law_case_assign_method','info','N' union all
 select 1,'专业匹配','specialty','law_case_assign_reason','success','Y' union all
 select 2,'负载均衡','load','law_case_assign_reason','primary','N' union all
 select 3,'客户指定','customer','law_case_assign_reason','warning','N' union all
 select 4,'团队协作','team','law_case_assign_reason','info','N' union all
 select 5,'其他','other','law_case_assign_reason','default','N' union all
 select 1,'合伙人','partner','law_lawyer_role','primary','N' union all
 select 2,'高级律师','senior','law_lawyer_role','success','N' union all
 select 3,'律师','lawyer','law_lawyer_role','info','Y' union all
 select 4,'实习律师','assistant','law_lawyer_role','warning','N' union all
 select 1,'高匹配','high','law_lawyer_match_level','success','Y' union all
 select 2,'中匹配','medium','law_lawyer_match_level','warning','N' union all
 select 3,'低匹配','low','law_lawyer_match_level','info','N' union all
 select 1,'高负载','high','law_lawyer_load_status','danger','N' union all
 select 2,'正常','normal','law_lawyer_load_status','success','Y' union all
 select 3,'空闲','idle','law_lawyer_load_status','primary','N' union all
 select 1,'待审批','pending','law_case_transfer_status','warning','Y' union all
 select 2,'已通过','passed','law_case_transfer_status','success','N' union all
 select 3,'已驳回','rejected','law_case_transfer_status','danger','N' union all
 select 4,'补充材料','supplement','law_case_transfer_status','info','N' union all
 select 1,'工作冲突','conflict','law_case_transfer_reason','warning','Y' union all
 select 2,'专业领域调整','specialty','law_case_transfer_reason','primary','N' union all
 select 3,'案件分工优化','optimize','law_case_transfer_reason','success','N' union all
 select 4,'客户原因','customer','law_case_transfer_reason','info','N' union all
 select 5,'其他','other','law_case_transfer_reason','default','N' union all
 select 1,'低风险','low','law_case_risk_level','success','Y' union all
 select 2,'中风险','medium','law_case_risk_level','warning','N' union all
 select 3,'高风险','high','law_case_risk_level','danger','N' union all
 select 1,'创建案件','create','law_case_status_action','primary','Y' union all
 select 2,'案件分配','assign','law_case_status_action','success','N' union all
 select 3,'接案确认','confirm','law_case_status_action','primary','N' union all
 select 4,'转案申请','transfer_request','law_case_status_action','warning','N' union all
 select 5,'转案审批','transfer_approve','law_case_status_action','success','N' union all
 select 6,'案件归档','archive','law_case_status_action','info','N' union all
 select 1,'待确认','pending','law_case_confirm_status','warning','Y' union all
 select 2,'已确认','accepted','law_case_confirm_status','success','N' union all
 select 3,'已拒绝','rejected','law_case_confirm_status','danger','N'
) item
where not exists(select 1 from sys_dict_data where dict_type=item.type and dict_value=item.value);

update sys_dict_data d
join (
 select 1 sort,'民事' label,'civil' value,'law_case_type' type,'primary' class,'Y' def union all
 select 2,'刑事','criminal','law_case_type','danger','N' union all
 select 3,'商事','business','law_case_type','warning','N' union all
 select 4,'顾问','advisor','law_case_type','success','N' union all
 select 5,'公司法务','company','law_case_type','success','N' union all
 select 6,'金融资本','finance','law_case_type','warning','N' union all
 select 7,'知识产权','ip','law_case_type','primary','N' union all
 select 8,'劳动人事','labor','law_case_type','info','N' union all
 select 1,'普通','normal','law_case_urgency','info','Y' union all
 select 2,'紧急','urgent','law_case_urgency','warning','N' union all
 select 3,'较紧急','high','law_case_urgency','danger','N' union all
 select 1,'待分案','pending','law_case_status','warning','Y' union all
 select 2,'办理中','processing','law_case_status','primary','N' union all
 select 3,'转案中','transfering','law_case_status','warning','N' union all
 select 4,'待确认','confirming','law_case_status','info','N' union all
 select 5,'已归档','archived','law_case_status','success','N' union all
 select 6,'已终止','terminated','law_case_status','danger','N' union all
 select 1,'高','high','law_case_priority','danger','N' union all
 select 2,'中','medium','law_case_priority','warning','Y' union all
 select 3,'低','low','law_case_priority','success','N' union all
 select 1,'人工分配','manual','law_case_assign_method','primary','Y' union all
 select 2,'智能推荐','auto','law_case_assign_method','info','N' union all
 select 1,'专业匹配','specialty','law_case_assign_reason','success','Y' union all
 select 2,'负载均衡','load','law_case_assign_reason','primary','N' union all
 select 3,'客户指定','customer','law_case_assign_reason','warning','N' union all
 select 4,'团队协作','team','law_case_assign_reason','info','N' union all
 select 5,'其他','other','law_case_assign_reason','default','N' union all
 select 1,'合伙人','partner','law_lawyer_role','primary','N' union all
 select 2,'高级律师','senior','law_lawyer_role','success','N' union all
 select 3,'律师','lawyer','law_lawyer_role','info','Y' union all
 select 4,'实习律师','assistant','law_lawyer_role','warning','N' union all
 select 1,'高匹配','high','law_lawyer_match_level','success','Y' union all
 select 2,'中匹配','medium','law_lawyer_match_level','warning','N' union all
 select 3,'低匹配','low','law_lawyer_match_level','info','N' union all
 select 1,'高负载','high','law_lawyer_load_status','danger','N' union all
 select 2,'正常','normal','law_lawyer_load_status','success','Y' union all
 select 3,'空闲','idle','law_lawyer_load_status','primary','N' union all
 select 1,'待审批','pending','law_case_transfer_status','warning','Y' union all
 select 2,'已通过','passed','law_case_transfer_status','success','N' union all
 select 3,'已驳回','rejected','law_case_transfer_status','danger','N' union all
 select 4,'补充材料','supplement','law_case_transfer_status','info','N' union all
 select 1,'工作冲突','conflict','law_case_transfer_reason','warning','Y' union all
 select 2,'专业领域调整','specialty','law_case_transfer_reason','primary','N' union all
 select 3,'案件分工优化','optimize','law_case_transfer_reason','success','N' union all
 select 4,'客户原因','customer','law_case_transfer_reason','info','N' union all
 select 5,'其他','other','law_case_transfer_reason','default','N' union all
 select 1,'低风险','low','law_case_risk_level','success','Y' union all
 select 2,'中风险','medium','law_case_risk_level','warning','N' union all
 select 3,'高风险','high','law_case_risk_level','danger','N' union all
 select 1,'创建案件','create','law_case_status_action','primary','Y' union all
 select 2,'案件分配','assign','law_case_status_action','success','N' union all
 select 3,'接案确认','confirm','law_case_status_action','primary','N' union all
 select 4,'转案申请','transfer_request','law_case_status_action','warning','N' union all
 select 5,'转案审批','transfer_approve','law_case_status_action','success','N' union all
 select 6,'案件归档','archive','law_case_status_action','info','N' union all
 select 1,'待确认','pending','law_case_confirm_status','warning','Y' union all
 select 2,'已确认','accepted','law_case_confirm_status','success','N' union all
 select 3,'已拒绝','rejected','law_case_confirm_status','danger','N'
) item on item.type=d.dict_type and item.value=d.dict_value
set d.dict_sort=item.sort,
    d.dict_label=item.label,
    d.list_class=item.class,
    d.is_default=item.def,
    d.status='0',
    d.remark=item.label;

insert into sys_menu(menu_name,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
select '案管中心',0,7,'case',null,'','Case',1,0,'M','0','0','','case','admin',sysdate(),'案管中心'
where not exists(select 1 from sys_menu where parent_id=0 and path='case');

update sys_menu
set menu_name='案管中心', order_num=7, path='case', component=null, route_name='Case', menu_type='M', visible='0', status='0', icon='case', remark='案管中心'
where parent_id=0 and path='case';

set @case_menu_id=(select menu_id from sys_menu where parent_id=0 and path='case' limit 1);

insert into sys_menu(menu_name,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
select item.menu_name,@case_menu_id,item.order_num,item.path,'case/index',item.query,item.route_name,1,0,'C','0','0',item.perms,item.icon,'admin',sysdate(),item.menu_name
from (
 select '待分案' menu_name,1 order_num,'pending' path,'{"module":"pending"}' query,'CasePending' route_name,'case:pending:list' perms,'list' icon union all
 select '分案记录',2,'assign','{"module":"assign"}','CaseAssign','case:assign:list','documentation' union all
 select '转案审批',3,'transfer','{"module":"transfer"}','CaseTransfer','case:transfer:list','validCode' union all
 select '律师负载',4,'lawyer','{"module":"lawyer"}','CaseLawyer','case:lawyer:list','peoples' union all
 select '待确认信息',5,'confirm','{"module":"confirm"}','CaseConfirm','case:confirm:list','message' union all
 select '状态记录',6,'status','{"module":"status"}','CaseStatus','case:status:list','time'
) item
where not exists(select 1 from sys_menu where parent_id=@case_menu_id and path=item.path);

update sys_menu m
join (
 select '待分案' menu_name,1 order_num,'pending' path,'{"module":"pending"}' query,'CasePending' route_name,'case:pending:list' perms,'list' icon union all
 select '分案记录',2,'assign','{"module":"assign"}','CaseAssign','case:assign:list','documentation' union all
 select '转案审批',3,'transfer','{"module":"transfer"}','CaseTransfer','case:transfer:list','validCode' union all
 select '律师负载',4,'lawyer','{"module":"lawyer"}','CaseLawyer','case:lawyer:list','peoples' union all
 select '待确认信息',5,'confirm','{"module":"confirm"}','CaseConfirm','case:confirm:list','message' union all
 select '状态记录',6,'status','{"module":"status"}','CaseStatus','case:status:list','time'
) item on item.path=m.path and m.parent_id=@case_menu_id
set m.menu_name=item.menu_name,
    m.order_num=item.order_num,
    m.component='case/index',
    m.query=item.query,
    m.route_name=item.route_name,
    m.menu_type='C',
    m.visible='0',
    m.status='0',
    m.perms=item.perms,
    m.icon=item.icon,
    m.remark=item.menu_name;

set @case_pending_id=(select menu_id from sys_menu where parent_id=@case_menu_id and path='pending' limit 1);
set @case_assign_id=(select menu_id from sys_menu where parent_id=@case_menu_id and path='assign' limit 1);
set @case_transfer_id=(select menu_id from sys_menu where parent_id=@case_menu_id and path='transfer' limit 1);
set @case_lawyer_id=(select menu_id from sys_menu where parent_id=@case_menu_id and path='lawyer' limit 1);
set @case_confirm_id=(select menu_id from sys_menu where parent_id=@case_menu_id and path='confirm' limit 1);
set @case_status_id=(select menu_id from sys_menu where parent_id=@case_menu_id and path='status' limit 1);

insert into sys_menu(menu_name,parent_id,order_num,path,component,query,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time,remark)
select item.menu_name,item.parent_id,item.order_num,'#','','','',1,0,'F','0','0',item.perms,'#','admin',sysdate(),item.menu_name
from (
 select '待分案查询' menu_name,@case_pending_id parent_id,1 order_num,'case:pending:query' perms union all
 select '案件分配',@case_pending_id,2,'case:pending:assign' union all
 select '批量分配',@case_pending_id,3,'case:pending:batchAssign' union all
 select '分案记录查询',@case_assign_id,1,'case:assign:query' union all
 select '转案查询',@case_transfer_id,1,'case:transfer:query' union all
 select '发起转案',@case_transfer_id,2,'case:transfer:add' union all
 select '转案审批',@case_transfer_id,3,'case:transfer:approve' union all
 select '律师负载查询',@case_lawyer_id,1,'case:lawyer:query' union all
 select '律师配置',@case_lawyer_id,2,'case:lawyer:config' union all
 select '负载页分配',@case_lawyer_id,3,'case:lawyer:assign' union all
 select '确认查询',@case_confirm_id,1,'case:confirm:query' union all
 select '确认处理',@case_confirm_id,2,'case:confirm:handle' union all
 select '状态记录查询',@case_status_id,1,'case:status:list'
) item
where item.parent_id is not null and not exists(select 1 from sys_menu where parent_id=item.parent_id and perms=item.perms);

update sys_menu m
join (
 select '待分案查询' menu_name,@case_pending_id parent_id,1 order_num,'case:pending:query' perms union all
 select '案件分配',@case_pending_id,2,'case:pending:assign' union all
 select '批量分配',@case_pending_id,3,'case:pending:batchAssign' union all
 select '分案记录查询',@case_assign_id,1,'case:assign:query' union all
 select '转案查询',@case_transfer_id,1,'case:transfer:query' union all
 select '发起转案',@case_transfer_id,2,'case:transfer:add' union all
 select '转案审批',@case_transfer_id,3,'case:transfer:approve' union all
 select '律师负载查询',@case_lawyer_id,1,'case:lawyer:query' union all
 select '律师配置',@case_lawyer_id,2,'case:lawyer:config' union all
 select '负载页分配',@case_lawyer_id,3,'case:lawyer:assign' union all
 select '确认查询',@case_confirm_id,1,'case:confirm:query' union all
 select '确认处理',@case_confirm_id,2,'case:confirm:handle' union all
 select '状态记录查询',@case_status_id,1,'case:status:list'
) item on item.parent_id=m.parent_id and item.perms=m.perms
set m.menu_name=item.menu_name,
    m.order_num=item.order_num,
    m.path='#',
    m.component='',
    m.query='',
    m.route_name='',
    m.menu_type='F',
    m.visible='0',
    m.status='0',
    m.icon='#',
    m.remark=item.menu_name;

-- 案管中心建议系统角色。系统角色负责权限/数据范围，律师业务身份仍由 biz_lawyer_profile.lawyer_role 维护。
insert into sys_role(role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,status,del_flag,create_by,create_time,remark)
select item.role_name,item.role_key,item.role_sort,item.data_scope,1,1,'0','0','admin',sysdate(),item.remark
from (
 select '案管员' role_name,'case_manager' role_key,30 role_sort,'4' data_scope,'案管中心分案、转案审批、律师档案维护' remark union all
 select '律师' role_name,'lawyer' role_key,31 role_sort,'5' data_scope,'律师本人相关案件处理' remark union all
 select '实习律师' role_name,'intern_lawyer' role_key,32 role_sort,'5' data_scope,'协办相关案件查看与处理' remark union all
 select '合伙人/法务经理' role_name,'law_partner_manager' role_key,33 role_sort,'4' data_scope,'部门及以下案管审批与管理' remark
) item
where not exists(select 1 from sys_role r where r.role_key=item.role_key and r.del_flag='0');

set @case_manager_role_id=(select role_id from sys_role where role_key='case_manager' and del_flag='0' limit 1);
set @lawyer_role_id=(select role_id from sys_role where role_key='lawyer' and del_flag='0' limit 1);
set @intern_lawyer_role_id=(select role_id from sys_role where role_key='intern_lawyer' and del_flag='0' limit 1);
set @law_partner_manager_role_id=(select role_id from sys_role where role_key='law_partner_manager' and del_flag='0' limit 1);

insert into sys_role_menu(role_id,menu_id)
select @case_manager_role_id,m.menu_id
from sys_menu m
where @case_manager_role_id is not null
  and (m.menu_id=@case_menu_id or m.parent_id=@case_menu_id or m.perms like 'case:%')
  and not exists(select 1 from sys_role_menu rm where rm.role_id=@case_manager_role_id and rm.menu_id=m.menu_id);

insert into sys_role_menu(role_id,menu_id)
select @law_partner_manager_role_id,m.menu_id
from sys_menu m
where @law_partner_manager_role_id is not null
  and (m.menu_id=@case_menu_id or m.parent_id=@case_menu_id or m.perms like 'case:%')
  and not exists(select 1 from sys_role_menu rm where rm.role_id=@law_partner_manager_role_id and rm.menu_id=m.menu_id);

insert into sys_role_menu(role_id,menu_id)
select @lawyer_role_id,m.menu_id
from sys_menu m
where @lawyer_role_id is not null
  and (
    m.menu_id=@case_menu_id
    or m.perms in ('case:pending:list','case:pending:query','case:assign:list','case:assign:query','case:lawyer:list','case:lawyer:query','case:confirm:list','case:confirm:query','case:confirm:handle','case:transfer:list','case:transfer:query','case:transfer:add','case:status:list')
    or m.path in ('pending','assign','lawyer','confirm','transfer','status')
  )
  and not exists(select 1 from sys_role_menu rm where rm.role_id=@lawyer_role_id and rm.menu_id=m.menu_id);

insert into sys_role_menu(role_id,menu_id)
select @intern_lawyer_role_id,m.menu_id
from sys_menu m
where @intern_lawyer_role_id is not null
  and (
    m.menu_id=@case_menu_id
    or m.perms in ('case:pending:list','case:pending:query','case:lawyer:list','case:lawyer:query','case:status:list')
    or m.path in ('pending','lawyer','status')
  )
  and not exists(select 1 from sys_role_menu rm where rm.role_id=@intern_lawyer_role_id and rm.menu_id=m.menu_id);
