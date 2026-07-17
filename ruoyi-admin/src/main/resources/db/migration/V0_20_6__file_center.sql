create table file_object (
  file_object_id bigint not null auto_increment,
  logical_name varchar(255) not null,
  current_version_no int not null default 0,
  next_version_no int not null default 2,
  status varchar(16) not null default 'PENDING',
  created_by bigint not null,
  create_time datetime not null default current_timestamp,
  update_by bigint null,
  update_time datetime not null default current_timestamp,
  version int not null default 0,
  primary key (file_object_id),
  key idx_file_object_status_created (status,create_time,file_object_id),
  constraint chk_file_object_status check (status in ('PENDING','ACTIVE','DISABLED')),
  constraint chk_file_object_versions check (current_version_no >= 0 and next_version_no > current_version_no)
) engine=innodb comment='Logical governed file object';

create table file_object_version (
  file_version_id bigint not null auto_increment,
  file_object_id bigint not null,
  version_no int not null,
  storage_provider varchar(32) not null,
  object_key varchar(300) not null,
  original_file_name varchar(255) not null,
  content_type varchar(160) not null,
  size_bytes bigint not null,
  sha256 char(64) not null,
  created_by bigint not null,
  create_time datetime not null default current_timestamp,
  primary key (file_version_id),
  unique key uk_file_object_version (file_object_id,version_no),
  unique key uk_file_storage_object (storage_provider,object_key),
  key idx_file_version_hash (sha256),
  constraint chk_file_version_no check (version_no > 0),
  constraint chk_file_version_size check (size_bytes >= 0)
) engine=innodb comment='Immutable file content version';

create table file_business_relation (
  relation_id bigint not null auto_increment,
  action_id varchar(128) not null,
  file_object_id bigint not null,
  business_type varchar(64) not null,
  business_id bigint not null,
  material_type varchar(96) not null,
  visibility varchar(16) not null default 'BUSINESS',
  created_by bigint not null,
  created_dept_id bigint null,
  active tinyint not null default 1,
  create_time datetime not null default current_timestamp,
  primary key (relation_id),
  unique key uk_file_business_material (file_object_id,business_type,business_id,material_type,visibility),
  unique key uk_file_relation_action (created_by,action_id),
  key idx_file_business_lookup (business_type,business_id,active,file_object_id),
  key idx_file_material_lookup (material_type,active,file_object_id),
  constraint chk_file_relation_visibility check (visibility in ('BUSINESS','DEPARTMENT','PRIVATE')),
  constraint chk_file_relation_active check (active in (0,1))
) engine=innodb comment='File material relation and visibility boundary';

create table file_upload_intent (
  upload_intent_id char(36) not null,
  idempotency_key varchar(128) not null,
  request_fingerprint char(64) not null,
  file_object_id bigint not null,
  target_version_no int not null,
  object_key varchar(300) not null,
  original_file_name varchar(255) not null,
  content_type varchar(160) not null,
  expected_size bigint not null,
  expected_sha256 char(64) not null,
  actor_id bigint not null,
  status varchar(16) not null,
  completed_version_id bigint null,
  expires_at datetime not null,
  completed_at datetime null,
  create_time datetime not null default current_timestamp,
  primary key (upload_intent_id),
  unique key uk_file_upload_idempotency (actor_id,idempotency_key),
  unique key uk_file_upload_object_version (file_object_id,target_version_no),
  unique key uk_file_upload_object_key (object_key),
  key idx_file_upload_expiry (status,expires_at),
  constraint chk_file_upload_status check (status in ('REGISTERED','COMPLETED','EXPIRED')),
  constraint chk_file_upload_size check (expected_size >= 0)
) engine=innodb comment='Idempotent upload or version registration';

create table file_access_token (
  access_token_id bigint not null auto_increment,
  file_object_id bigint not null,
  file_version_id bigint not null,
  access_type varchar(16) not null,
  token_hash char(64) not null,
  actor_id bigint not null,
  actor_dept_id bigint null,
  expires_at datetime not null,
  consumed_at datetime null,
  create_time datetime not null default current_timestamp,
  primary key (access_token_id),
  unique key uk_file_access_token_hash (token_hash),
  key idx_file_access_token_expiry (expires_at,consumed_at),
  constraint chk_file_access_type check (access_type in ('PREVIEW','DOWNLOAD'))
) engine=innodb comment='Short-lived single-use opaque access token';

create table file_access_log (
  access_log_id bigint not null auto_increment,
  file_object_id bigint not null,
  file_version_id bigint not null,
  business_type varchar(64) not null,
  business_id bigint not null,
  access_type varchar(16) not null,
  actor_id bigint not null,
  actor_dept_id bigint null,
  client_ip varchar(64) null,
  accessed_at datetime not null,
  primary key (access_log_id),
  key idx_file_access_audit (file_object_id,accessed_at,access_log_id),
  key idx_file_access_actor (actor_id,accessed_at)
) engine=innodb comment='Append-only successful file access audit';

set @todo_menu=(select menu_id from sys_menu where perms='todo:list' order by menu_id limit 1);
insert into sys_menu(menu_name,parent_id,order_num,path,component,`query`,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
select x.name,@todo_menu,x.ord,'#','',null,null,1,0,'F','0','0',x.perm,'#','admin',sysdate() from (
select 'File upload' name,30 ord,'file:object:upload' perm union all
select 'File relation',31,'file:object:relate' union all
select 'File preview and download',32,'file:object:read' union all
select 'File access audit',33,'file:object:audit') x
where @todo_menu is not null and not exists(select 1 from sys_menu m where m.perms=x.perm);
