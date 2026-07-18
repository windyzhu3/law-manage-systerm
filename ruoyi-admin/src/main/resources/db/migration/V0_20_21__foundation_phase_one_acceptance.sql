create table todo_foundation_acceptance_requirement (
  requirement_id bigint not null auto_increment,
  gate_code varchar(8) not null,
  requirement_code varchar(64) not null,
  requirement_name varchar(200) not null,
  check_kind varchar(32) not null,
  source_ref varchar(500) not null,
  sort_order int not null,
  remark varchar(1000) null,
  create_time datetime not null default current_timestamp,
  primary key (requirement_id),
  unique key uk_todo_foundation_acceptance_requirement (gate_code,requirement_code),
  constraint chk_todo_foundation_acceptance_kind check (check_kind in
    ('SCOPE_MANIFEST','AT_CATALOG','SCENARIO_CATALOG','GOLDEN_DATASET','AT_MAPPING','ACCEPTOR_ASSIGNMENT','INDEPENDENT_REVIEW','MOCK_SEPARATION'))
) engine=InnoDB default charset=utf8mb4 comment='v0.2 Foundation阶段一验收准入要求';

create table todo_acceptance_scenario (
  scenario_id bigint not null auto_increment,
  scenario_code varchar(64) not null,
  scenario_name varchar(200) not null,
  delivery_phase varchar(16) not null default 'PHASE_ONE',
  business_path varchar(500) not null,
  preconditions_json json not null,
  steps_json json not null,
  expected_outcomes_json json not null,
  dataset_ref varchar(1000) null,
  dataset_checksum char(64) null,
  dataset_version int null,
  owner_user_id bigint null,
  acceptor_user_id bigint null,
  reviewer_user_id bigint null,
  due_at datetime null,
  status varchar(16) not null default 'DRAFT',
  conclusion varchar(2000) null,
  reviewed_by varchar(64) null,
  reviewed_time datetime null,
  create_by varchar(64) not null,
  create_time datetime not null default current_timestamp,
  update_by varchar(64) null,
  update_time datetime null,
  version int not null default 0,
  primary key (scenario_id),
  unique key uk_todo_acceptance_scenario_code (scenario_code),
  key idx_todo_acceptance_scenario_status (delivery_phase,status,due_at),
  key idx_todo_acceptance_scenario_accountability (owner_user_id,acceptor_user_id,reviewer_user_id),
  constraint chk_todo_acceptance_scenario_phase check (delivery_phase in ('PHASE_ONE')),
  constraint chk_todo_acceptance_scenario_status check (status in ('DRAFT','IN_REVIEW','APPROVED','REJECTED')),
  constraint chk_todo_acceptance_scenario_reviewer check
    (reviewer_user_id is null or ((owner_user_id is null or reviewer_user_id<>owner_user_id)
      and (acceptor_user_id is null or reviewer_user_id<>acceptor_user_id))),
  constraint chk_todo_acceptance_dataset_version check (dataset_version is null or dataset_version>0)
) engine=InnoDB default charset=utf8mb4 comment='阶段一验收场景及黄金数据元数据';

create table todo_acceptance_ref_mapping (
  mapping_id bigint not null auto_increment,
  acceptance_ref varchar(64) not null,
  template_code varchar(16) not null,
  dimension_code varchar(16) not null,
  scenario_id bigint null,
  planned_test_ref varchar(1000) null,
  evidence_note varchar(2000) null,
  owner_user_id bigint null,
  reviewer_user_id bigint null,
  due_at datetime null,
  status varchar(16) not null default 'UNMAPPED',
  conclusion varchar(2000) null,
  reviewed_by varchar(64) null,
  reviewed_time datetime null,
  create_by varchar(64) not null default 'system',
  create_time datetime not null default current_timestamp,
  update_by varchar(64) null,
  update_time datetime null,
  version int not null default 0,
  primary key (mapping_id),
  unique key uk_todo_acceptance_ref (acceptance_ref),
  key idx_todo_acceptance_mapping_scope (template_code,dimension_code,status),
  key idx_todo_acceptance_mapping_scenario (scenario_id,status),
  key idx_todo_acceptance_mapping_accountability (owner_user_id,reviewer_user_id,due_at),
  constraint chk_todo_acceptance_dimension check (dimension_code in ('OWNER','SLA','DOD','ROUTE','HANDLER','UI')),
  constraint chk_todo_acceptance_mapping_status check (status in ('UNMAPPED','MAPPED','IN_REVIEW','APPROVED','REJECTED')),
  constraint chk_todo_acceptance_mapping_reviewer check
    (owner_user_id is null or reviewer_user_id is null or owner_user_id<>reviewer_user_id)
) engine=InnoDB default charset=utf8mb4 comment='阶段一模板AT验收映射槽位';

create table todo_acceptance_action (
  action_id varchar(64) not null,
  entity_type varchar(16) not null,
  entity_id bigint null,
  action_type varchar(32) not null,
  action_status varchar(16) not null default 'CLAIMED',
  request_fingerprint char(64) not null,
  operator_id bigint not null,
  operator_name varchar(64) not null,
  operator_dept_id bigint null,
  payload_json json not null,
  result_entity_id bigint null,
  create_time datetime not null default current_timestamp,
  completed_time datetime null,
  primary key (action_id),
  key idx_todo_acceptance_action_entity (entity_type,entity_id,create_time),
  constraint chk_todo_acceptance_action_entity check (entity_type in ('SCENARIO','MAPPING')),
  constraint chk_todo_acceptance_action_status check (action_status in ('CLAIMED','APPLIED'))
) engine=InnoDB default charset=utf8mb4 comment='阶段一验收治理幂等动作账本';

insert into todo_foundation_acceptance_requirement
  (gate_code,requirement_code,requirement_name,check_kind,source_ref,sort_order,remark)
values
('G-07','PHASE_ONE_SCOPE_MANIFEST','阶段一19模板范围','SCOPE_MANIFEST','doc/v0.2-prd-readiness-gap-analysis.md:296-306',10,'TD-001至TD-016、TD-022、TD-023、TD-025'),
('G-07','PHASE_ONE_AT_CATALOG','阶段一114项AT目录','AT_CATALOG','doc/v0.2-foundation-template-matrix.md',20,'每模板OWNER/SLA/DOD/ROUTE/HANDLER/UI六类'),
('G-07','PHASE_ONE_SCENARIO_CATALOG','阶段一E2E场景清单','SCENARIO_CATALOG','doc/v0.2-prd-readiness-gap-analysis.md:306',30,'场景必须版本化并经独立评审'),
('G-07','PHASE_ONE_GOLDEN_DATASET','黄金数据版本与校验和','GOLDEN_DATASET','doc/v0.2-foundation-admission-report.md:117',40,'不得在迁移中虚构测试数据'),
('G-07','PHASE_ONE_AT_MAPPING','114项AT到场景映射','AT_MAPPING','doc/v0.2-foundation-template-matrix.md',50,'必须逐项关联场景与计划测试引用'),
('G-07','PHASE_ONE_ACCEPTOR_ASSIGNMENT','业务验收责任','ACCEPTOR_ASSIGNMENT','doc/v0.2-prd-readiness-gap-analysis.md:321',60,'Owner、验收人、截止时间必须明确'),
('G-07','PHASE_ONE_INDEPENDENT_REVIEW','独立验收评审','INDEPENDENT_REVIEW','doc/v0.2-foundation-admission-report.md:117',70,'Reviewer不得由Owner或验收人代替'),
('G-07','MOCK_E2E_SEPARATION','Mock测试与业务验收分离','MOCK_SEPARATION','ruoyi-ui/playwright.config.js',80,'现有静态dist加Mock API测试不自动抵扣G-07');

insert into todo_acceptance_ref_mapping
  (acceptance_ref,template_code,dimension_code,status,create_by)
select distinct acceptance.acceptance_ref,
       catalog.template_code,
       substring_index(acceptance.acceptance_ref,'-',-1),
       'UNMAPPED',
       'system'
from todo_prd_definition_catalog catalog
join json_table(catalog.acceptance_refs_json,'$[*]'
  columns(acceptance_ref varchar(64) path '$')) acceptance on true
where catalog.template_code in
  ('TD-001','TD-002','TD-003','TD-004','TD-005','TD-006','TD-007','TD-008','TD-009','TD-010',
   'TD-011','TD-012','TD-013','TD-014','TD-015','TD-016','TD-022','TD-023','TD-025');

-- This migration creates governance slots only. It never creates scenarios, data, users, signatures,
-- approvals, published templates or enabled trigger rules.
