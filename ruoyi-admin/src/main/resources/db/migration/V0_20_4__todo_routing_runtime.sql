alter table todo_instance
  modify column next_idempotency_key varchar(300) null,
  add column definition_hash char(64) null after dod_snapshot_json,
  add column ui_schema_snapshot json null after definition_hash,
  add column sla_snapshot json null after ui_schema_snapshot,
  add column route_node_key varchar(96) null after sla_snapshot,
  add column route_token json null after route_node_key,
  add column occurrence_key varchar(300) null after route_token,
  add column payload_schema_version int null after occurrence_key,
  add unique key uk_todo_route_occurrence (occurrence_key),
  add key idx_todo_route_root_node (root_todo_id,route_node_key,todo_id);

create table todo_route_token (
  route_token_id bigint not null auto_increment,
  root_todo_id bigint not null,
  node_key varchar(96) not null,
  branch_key varchar(96) not null,
  occurrence int not null,
  definition_hash char(64) not null,
  route_token json not null,
  status varchar(16) not null,
  arrived_at datetime not null default current_timestamp,
  primary key (route_token_id),
  unique key uk_todo_route_token_arrival (root_todo_id,node_key,branch_key,occurrence),
  key idx_todo_route_token_join (root_todo_id,node_key,occurrence,status)
) engine=innodb comment='Persisted routing branch token arrivals';

create table todo_route_join (
  root_todo_id bigint not null,
  node_key varchar(96) not null,
  occurrence int not null,
  join_mode varchar(8) not null,
  required_branches json not null,
  status varchar(16) not null default 'WAITING',
  advanced_at datetime null,
  version int not null default 0,
  create_time datetime not null default current_timestamp,
  primary key (root_todo_id,node_key,occurrence),
  constraint chk_todo_route_join_mode check (join_mode in ('ANY','ALL')),
  constraint chk_todo_route_join_status check (status in ('WAITING','ADVANCED'))
) engine=innodb comment='Serialized routing join advancement state';
