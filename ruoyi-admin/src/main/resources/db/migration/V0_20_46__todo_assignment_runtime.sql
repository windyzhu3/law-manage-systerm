create table todo_round_robin_cursor (
  strategy_key varchar(128) not null,
  last_user_id bigint null,
  version int not null default 0,
  update_time datetime not null,
  primary key (strategy_key)
) engine=innodb comment='Transactionally locked Todo assignment cursor';

create table sys_user_availability (
  availability_id bigint not null auto_increment,
  user_id bigint not null,
  status varchar(16) not null,
  effective_from datetime not null,
  effective_to datetime null,
  reason varchar(500) null,
  create_by varchar(64) null,
  create_time datetime not null,
  update_by varchar(64) null,
  update_time datetime not null,
  primary key (availability_id),
  unique key uk_sys_user_availability (user_id,status,effective_from),
  key idx_sys_user_availability_active (user_id,status,effective_from,effective_to),
  constraint chk_sys_user_availability_status check (status in ('AVAILABLE','UNAVAILABLE')),
  constraint chk_sys_user_availability_period check (effective_to is null or effective_to>effective_from)
) engine=innodb comment='Effective-dated user availability';

create table sys_user_delegation (
  delegation_id bigint not null auto_increment,
  from_user_id bigint not null,
  to_user_id bigint not null,
  status varchar(16) not null default 'ACTIVE',
  effective_from datetime not null,
  effective_to datetime null,
  reason varchar(500) null,
  create_by varchar(64) null,
  create_time datetime not null,
  update_by varchar(64) null,
  update_time datetime not null,
  primary key (delegation_id),
  unique key uk_sys_user_delegation (from_user_id,to_user_id,effective_from),
  key idx_sys_user_delegation_active (from_user_id,status,effective_from,effective_to),
  constraint chk_sys_user_delegation_users check (from_user_id<>to_user_id),
  constraint chk_sys_user_delegation_status check (status in ('ACTIVE','INACTIVE')),
  constraint chk_sys_user_delegation_period check (effective_to is null or effective_to>effective_from)
) engine=innodb comment='Effective-dated user delegation';
