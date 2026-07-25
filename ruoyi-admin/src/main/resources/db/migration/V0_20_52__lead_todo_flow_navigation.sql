-- Establish the permanent schema required by the lead source-governance
-- migration. MySQL DDL auto-commits, so this migration intentionally contains
-- schema only. V0.20.53 owns every permanent data mutation and can therefore
-- fail and retry without leaving a partially governed lead catalogue.
create table biz_lead_source_governance_audit (
  audit_id bigint not null auto_increment,
  migration_code varchar(32) not null,
  lead_id bigint not null,
  original_source_code varchar(40) null,
  governed_source_code varchar(40) not null,
  governance_result varchar(16) not null,
  create_by varchar(64) not null,
  create_time datetime not null default current_timestamp,
  primary key (audit_id),
  unique key uk_biz_lead_source_governance (migration_code,lead_id),
  key idx_biz_lead_source_governance_code (governed_source_code,lead_id),
  constraint chk_biz_lead_source_governance_result
    check (governance_result in ('RETAINED','REMAPPED'))
) engine=innodb comment='Lead source governance migration audit';
