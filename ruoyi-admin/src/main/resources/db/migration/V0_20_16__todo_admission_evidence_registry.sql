create table todo_admission_evidence (
  evidence_id bigint not null auto_increment,
  evidence_code varchar(64) not null,
  gate_code varchar(8) not null,
  category varchar(32) not null,
  title varchar(200) not null,
  description varchar(1000) null,
  delivery_phase varchar(16) not null default 'PHASE_ONE',
  status varchar(16) not null default 'OPEN',
  owner_user_id bigint null,
  reviewer_user_id bigint null,
  due_at datetime null,
  artifact_ref varchar(1000) null,
  conclusion varchar(2000) null,
  reviewed_by varchar(64) null,
  reviewed_time datetime null,
  create_by varchar(64) not null default 'system',
  create_time datetime not null default current_timestamp,
  update_by varchar(64) null,
  update_time datetime null,
  version int not null default 0,
  primary key (evidence_id),
  unique key uk_todo_admission_evidence_code (evidence_code),
  key idx_todo_admission_gate_status (gate_code,status,delivery_phase,due_at),
  key idx_todo_admission_accountability (owner_user_id,reviewer_user_id,status),
  constraint chk_todo_admission_phase check (delivery_phase in ('PHASE_ONE','PHASE_TWO','CROSS_PHASE')),
  constraint chk_todo_admission_status check (status in ('OPEN','IN_REVIEW','APPROVED','REJECTED')),
  constraint chk_todo_admission_independent_reviewer check (owner_user_id is null or reviewer_user_id is null or owner_user_id<>reviewer_user_id)
) engine=InnoDB default charset=utf8mb4 comment='v0.2 Foundation准入证据登记';

create table todo_admission_evidence_action (
  action_id varchar(64) not null,
  evidence_id bigint not null,
  action_type varchar(32) not null default 'UPDATE_EVIDENCE',
  action_status varchar(16) not null default 'CLAIMED',
  request_fingerprint char(64) not null,
  operator_id bigint not null,
  operator_name varchar(64) not null,
  operator_dept_id bigint null,
  payload_json json not null,
  create_time datetime not null default current_timestamp,
  completed_time datetime null,
  primary key (action_id),
  key idx_todo_admission_action_evidence (evidence_id,create_time),
  constraint chk_todo_admission_action_status check (action_status in ('CLAIMED','APPLIED'))
) engine=InnoDB default charset=utf8mb4 comment='准入证据幂等动作账本';

insert into todo_admission_evidence(evidence_code,gate_code,category,title,description,delivery_phase,status)
values
('G02-DICTIONARY-ROLE','G-02','DICTIONARY_ROLE','业务字典与稳定角色键','业务线、无效分级、非诉/执行标签及角色映射的冻结清单。','PHASE_ONE','OPEN'),
('G04-HISTORICAL-MIGRATION','G-04','MIGRATION_PLAN','历史数据迁移方案','历史案件默认业务线、旧待办版本、回填校验与回滚方案。','PHASE_ONE','OPEN'),
('G05-FILE-SECURITY','G-05','SECURITY_REVIEW','文件中心安全评审','越权、令牌重放、下载审计、清理补偿和材料类型验收记录。','PHASE_ONE','OPEN'),
('G06-FINANCE-FORMULA','G-06','FINANCE_FORMULA','收费节点与风险代理公式','公式版本、舍入、税费、退款、催收Owner和重算边界的签字版本。','PHASE_ONE','OPEN'),
('G07-PHASE-ONE-ACCEPTANCE','G-07','ACCEPTANCE','阶段一黄金数据与业务验收','线索到综法归档的黄金数据、AT到E2E映射及业务验收责任记录。','PHASE_ONE','OPEN');

set @definition_menu=(select menu_id from sys_menu where component='todo/config/index' order by menu_id limit 1);
set @fallback_menu=(select menu_id from sys_menu where perms='todo:list' order by menu_id limit 1);
set @permission_parent=coalesce(@definition_menu,@fallback_menu);

insert into sys_menu(menu_name,parent_id,order_num,path,component,`query`,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
select 'Admission evidence view',@permission_parent,46,'#','',null,null,1,0,'F','0','0','todo:admission:view','#','admin',sysdate()
where @permission_parent is not null and not exists(select 1 from sys_menu where perms='todo:admission:view');

insert into sys_menu(menu_name,parent_id,order_num,path,component,`query`,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
select 'Admission evidence edit',@permission_parent,47,'#','',null,null,1,0,'F','0','0','todo:admission:edit','#','admin',sysdate()
where @permission_parent is not null and not exists(select 1 from sys_menu where perms='todo:admission:edit');

-- No role grants are created. Evidence review permissions remain independently assignable.
