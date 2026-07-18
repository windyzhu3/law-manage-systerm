create table todo_foundation_migration_requirement (
  requirement_id bigint not null auto_increment,
  gate_code varchar(8) not null,
  requirement_code varchar(64) not null,
  requirement_name varchar(200) not null,
  check_kind varchar(32) not null,
  source_status varchar(24) not null,
  source_ref varchar(500) not null,
  decision_ref varchar(32) null,
  sort_order int not null,
  remark varchar(1000) null,
  create_time datetime not null default current_timestamp,
  primary key (requirement_id),
  unique key uk_todo_foundation_migration_requirement (gate_code,requirement_code),
  key idx_todo_foundation_migration_status (gate_code,source_status,sort_order),
  constraint chk_todo_foundation_migration_kind check (check_kind in ('SOURCE_ONLY','CASE_COLUMN','TODO_VERSION_REFERENCE','TODO_VERSION_IMMUTABILITY')),
  constraint chk_todo_foundation_migration_source check (source_status in ('CONFIRMED','NEEDS_DECISION','NEEDS_EVIDENCE'))
) engine=InnoDB default charset=utf8mb4 comment='v0.2 Foundation历史迁移准入要求';

-- This catalog records repository truth only. It intentionally does not add a business-line
-- column, select a historical default, or rewrite any case or Todo instance.
insert into todo_foundation_migration_requirement
(gate_code,requirement_code,requirement_name,check_kind,source_status,source_ref,decision_ref,sort_order,remark)
values
('G-04','CASE_BUSINESS_LINE_SCHEMA','历史案件业务线字段','CASE_COLUMN','CONFIRMED','doc/v0.2-prd-readiness-gap-analysis.md:220',null,10,'仓库要求biz_case增加business_line；运行态必须真实存在该列'),
('G-04','HISTORICAL_CASE_DEFAULT','历史案件默认业务线策略','SOURCE_ONLY','NEEDS_DECISION','doc/v0.2-prd-readiness-gap-analysis.md:326',null,20,'仓库未给出默认业务线，研发不得代选'),
('G-04','UNCLASSIFIED_CASE_EXCEPTION_LIST','无法自动分类案件清单','SOURCE_ONLY','NEEDS_EVIDENCE','doc/v0.2-foundation-admission-report.md:105',null,30,'需提供无法分类案件的识别和人工处理清单'),
('G-04','BACKFILL_BATCH_IDEMPOTENCY','回填批次与幂等方案','SOURCE_ONLY','NEEDS_EVIDENCE','doc/v0.2-foundation-admission-report.md:105',null,40,'需定义批次、断点续跑和重复执行规则'),
('G-04','BACKFILL_VALIDATION_SQL','回填校验SQL','SOURCE_ONLY','NEEDS_EVIDENCE','doc/v0.2-foundation-admission-report.md:105',null,50,'需形成行数、枚举、关联与抽样校验SQL'),
('G-04','BACKFILL_ROLLBACK_SQL','回填回滚SQL','SOURCE_ONLY','NEEDS_EVIDENCE','doc/v0.2-foundation-admission-report.md:105',null,60,'需形成不破坏后续新增数据的回滚方案'),
('G-04','TODO_VERSION_REFERENCE','历史待办固定版本引用','TODO_VERSION_REFERENCE','CONFIRMED','ruoyi-admin/src/main/resources/db/migration/V0_16_1__todo_engine.sql:22',null,70,'todo_instance持有template_version_id；运行态不得存在孤儿版本引用'),
('G-04','TODO_VERSION_IMMUTABILITY','历史待办版本不可变策略','TODO_VERSION_IMMUTABILITY','CONFIRMED','docs/superpowers/specs/2026-07-16-v0.2-foundation-design.md:87',null,80,'历史实例继续固定版本和运行时快照，不做破坏性重写');
