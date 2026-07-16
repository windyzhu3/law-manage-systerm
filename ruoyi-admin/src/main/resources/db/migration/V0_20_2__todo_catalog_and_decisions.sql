create table todo_event_catalog (
  event_catalog_id bigint not null auto_increment,
  event_type varchar(100) not null,
  payload_version int not null,
  business_object_type varchar(100) null,
  payload_schema_json json not null,
  owner_field_paths_json json null,
  condition_field_paths_json json null,
  default_value_field_paths_json json null,
  producer varchar(100) null,
  sample_payload_json json null,
  status varchar(20) not null default 'ACTIVE',
  create_by varchar(64) null,
  create_time datetime not null default current_timestamp,
  update_by varchar(64) null,
  update_time datetime null,
  primary key (event_catalog_id),
  unique key uk_todo_event_catalog_type_version (event_type, payload_version),
  key idx_todo_event_catalog_status (status)
) engine=innodb comment='Todo event payload schema catalog';

create table todo_decision (
  decision_id bigint not null auto_increment,
  decision_code varchar(64) not null,
  title varchar(200) not null,
  description varchar(1000) null,
  blocking char(1) not null default 'Y',
  status varchar(20) not null default 'OPEN',
  conclusion varchar(2000) null,
  decided_by varchar(64) null,
  decided_time datetime null,
  create_by varchar(64) null,
  create_time datetime not null default current_timestamp,
  update_by varchar(64) null,
  update_time datetime null,
  primary key (decision_id),
  unique key uk_todo_decision_code (decision_code),
  key idx_todo_decision_blocking_status (blocking, status)
) engine=innodb comment='Todo definition business decision registry';
