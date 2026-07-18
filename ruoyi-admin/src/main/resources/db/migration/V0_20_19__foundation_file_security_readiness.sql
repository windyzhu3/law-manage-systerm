create table todo_foundation_file_security_requirement (
  requirement_id bigint not null auto_increment,
  gate_code varchar(8) not null,
  requirement_code varchar(64) not null,
  requirement_name varchar(200) not null,
  check_kind varchar(32) not null,
  source_status varchar(24) not null,
  source_ref varchar(500) not null,
  sort_order int not null,
  remark varchar(1000) null,
  create_time datetime not null default current_timestamp,
  primary key(requirement_id),
  unique key uk_todo_foundation_file_security(gate_code,requirement_code),
  constraint chk_todo_foundation_file_security_kind check(check_kind in ('OBJECT_MODEL','ACCESS_POLICY','TOKEN_CONTROL','ACCESS_AUDIT','CLEANUP_COMPENSATION','SOURCE_ONLY')),
  constraint chk_todo_foundation_file_security_source check(source_status in ('CONFIRMED','NEEDS_EVIDENCE','NEEDS_REVIEW'))
) engine=InnoDB default charset=utf8mb4 comment='v0.2 Foundation文件安全准入要求';

-- Readiness catalog only: no file row, role, permission or approval is changed here.
insert into todo_foundation_file_security_requirement
(gate_code,requirement_code,requirement_name,check_kind,source_status,source_ref,sort_order,remark)
values
('G-05','OBJECT_VERSION_RELATION_MODEL','对象版本与业务关系模型','OBJECT_MODEL','CONFIRMED','ruoyi-admin/src/main/resources/db/migration/V0_20_6__file_center.sql',10,'运行态必须存在file_object、file_object_version、file_business_relation'),
('G-05','OBJECT_RELATION_ACCESS_POLICY','对象关系级访问策略','ACCESS_POLICY','CONFIRMED','law-file/src/main/java/com/law/file/security/FileAccessPolicy.java',20,'授权必须同时校验用户、数据范围和业务关系'),
('G-05','SINGLE_USE_RELATION_TOKEN','单次关系绑定访问令牌','TOKEN_CONTROL','CONFIRMED','law-file/src/main/java/com/law/file/application/FileObjectService.java:229-236',30,'只存令牌哈希，绑定关系和用户，过期且单次消费'),
('G-05','ACCESS_AUDIT_TRAIL','预览下载访问审计','ACCESS_AUDIT','CONFIRMED','ruoyi-admin/src/main/resources/db/migration/V0_20_6__file_center.sql',40,'访问成功和失败均应保留对象、关系、用户、IP及结果'),
('G-05','STORAGE_CLEANUP_COMPENSATION','存储清理补偿','CLEANUP_COMPENSATION','CONFIRMED','law-file/src/main/java/com/law/file/application/FileCleanupRetryService.java',50,'失败清理任务持久化并支持租约重试'),
('G-05','PRD_MATERIAL_TYPE_E2E','PRD材料类型端到端验收','SOURCE_ONLY','NEEDS_EVIDENCE','doc/v0.2-foundation-admission-report.md:109',60,'需覆盖PRD材料上传、预览、下载和审计'),
('G-05','SECURITY_REVIEW_SIGNOFF','独立安全评审签字','SOURCE_ONLY','NEEDS_REVIEW','doc/v0.2-prd-readiness-gap-analysis.md:327',70,'需安全负责人完成越权、重放、审计与补偿评审');
