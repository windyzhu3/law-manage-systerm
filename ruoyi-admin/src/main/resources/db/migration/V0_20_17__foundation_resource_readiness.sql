create table todo_foundation_resource_requirement (
  resource_id bigint not null auto_increment,
  gate_code varchar(8) not null,
  resource_type varchar(16) not null,
  resource_code varchar(100) not null,
  domain_code varchar(32) not null,
  delivery_phase varchar(16) not null,
  source_ref varchar(500) not null,
  source_status varchar(24) not null,
  decision_ref varchar(32) null,
  expected_values_json json null,
  minimum_active_items int not null default 1,
  remark varchar(1000) null,
  create_time datetime not null default current_timestamp,
  primary key (resource_id),
  unique key uk_todo_foundation_resource (resource_type,resource_code),
  key idx_todo_foundation_gate_phase (gate_code,delivery_phase,source_status),
  constraint chk_todo_foundation_resource_type check (resource_type in ('DICTIONARY','ROLE')),
  constraint chk_todo_foundation_resource_phase check (delivery_phase in ('PHASE_ONE','PHASE_TWO','CROSS_PHASE')),
  constraint chk_todo_foundation_source_status check (source_status in ('CONFIRMED','NEEDS_DECISION','CONFLICTING')),
  constraint chk_todo_foundation_minimum check (minimum_active_items>=1)
) engine=InnoDB default charset=utf8mb4 comment='v0.2 Foundation字典与稳定角色需求目录';

-- Dictionary type names are authoritative repository requirements. Business values are not
-- promoted into sys_dict_* here. Only values explicitly named by the repository are retained
-- as evidence; unresolved value sets stay NEEDS_DECISION until a later reviewed migration.
insert into todo_foundation_resource_requirement
(gate_code,resource_type,resource_code,domain_code,delivery_phase,source_ref,source_status,decision_ref,expected_values_json,minimum_active_items,remark)
values
('G-02','DICTIONARY','law_lead_tag_confirm_status','LEAD_CUSTOMER','PHASE_ONE','doc/v0.2-prd-readiness-gap-analysis.md:161','NEEDS_DECISION',null,null,1,'仓库仅确认字典类型，未确认业务值'),
('G-02','DICTIONARY','law_first_contact_result','LEAD_CUSTOMER','PHASE_ONE','doc/v0.2-prd-readiness-gap-analysis.md:161','NEEDS_DECISION',null,null,1,'仓库仅确认字典类型，未确认业务值'),
('G-02','DICTIONARY','law_lead_invalid_level','LEAD_CUSTOMER','PHASE_ONE','doc/v0.2-prd-readiness-gap-analysis.md:161','NEEDS_DECISION','Q-008',null,1,'无效分级值由Q-008确认'),
('G-02','DICTIONARY','law_lead_invalid_reason','LEAD_CUSTOMER','PHASE_ONE','doc/v0.2-prd-readiness-gap-analysis.md:161','NEEDS_DECISION','Q-008',null,1,'无效原因值由Q-008一并冻结'),
('G-02','DICTIONARY','law_retry_stage','LEAD_CUSTOMER','PHASE_ONE','doc/v0.2-prd-readiness-gap-analysis.md:161','NEEDS_DECISION','Q-007',null,1,'重试阶段与时段策略依赖Q-007'),
('G-02','DICTIONARY','law_followup_progress_type','LEAD_CUSTOMER','PHASE_ONE','doc/v0.2-prd-readiness-gap-analysis.md:162','NEEDS_DECISION',null,json_array(json_object('label','通话'),json_object('label','来访'),json_object('label','面聊'),json_object('label','报价'),json_object('label','外访'),json_object('label','加微对话')),6,'仓库明确六个标签，但未确认稳定dict_value'),
('G-02','DICTIONARY','law_customer_cooperation_status','LEAD_CUSTOMER','PHASE_ONE','doc/v0.2-prd-readiness-gap-analysis.md:163','NEEDS_DECISION',null,null,1,'仓库仅确认字典类型，未确认业务值'),
('G-02','DICTIONARY','law_customer_invalid_level','LEAD_CUSTOMER','PHASE_ONE','doc/v0.2-prd-readiness-gap-analysis.md:163','NEEDS_DECISION','Q-008',null,1,'无效分级值由Q-008确认'),

('G-02','DICTIONARY','law_business_line','QUOTE_CONTRACT','PHASE_ONE','doc/v0.2-prd-readiness-gap-analysis.md:185','CONFIRMED',null,json_array(json_object('value','NON_LITIGATION'),json_object('value','COMPREHENSIVE'),json_object('value','EXECUTION')),3,'仓库明确三条业务线稳定值'),
('G-02','DICTIONARY','law_quote_case_type','QUOTE_CONTRACT','PHASE_ONE','doc/v0.2-prd-readiness-gap-analysis.md:186','NEEDS_DECISION',null,null,1,'仓库仅确认字典类型，未确认业务值'),
('G-02','DICTIONARY','law_discount_level','QUOTE_CONTRACT','PHASE_ONE','doc/v0.2-prd-readiness-gap-analysis.md:186','NEEDS_DECISION',null,null,1,'仓库仅确认字典类型，未确认业务值'),
('G-02','DICTIONARY','law_conflict_check_stage','QUOTE_CONTRACT','PHASE_ONE','doc/v0.2-prd-readiness-gap-analysis.md:186','NEEDS_DECISION',null,null,1,'仓库仅确认字典类型，未确认业务值'),
('G-02','DICTIONARY','law_conflict_result','QUOTE_CONTRACT','PHASE_ONE','doc/v0.2-prd-readiness-gap-analysis.md:186','NEEDS_DECISION',null,null,1,'仓库仅确认字典类型，未确认业务值'),
('G-02','DICTIONARY','law_waiver_result','QUOTE_CONTRACT','PHASE_ONE','doc/v0.2-prd-readiness-gap-analysis.md:186','NEEDS_DECISION',null,null,1,'仓库仅确认字典类型，未确认业务值'),
('G-02','DICTIONARY','law_contract_sign_channel','QUOTE_CONTRACT','PHASE_ONE','doc/v0.2-prd-readiness-gap-analysis.md:187','NEEDS_DECISION',null,null,1,'仓库仅确认字典类型，未确认业务值'),
('G-02','DICTIONARY','law_contract_deal_rule','QUOTE_CONTRACT','PHASE_ONE','doc/v0.2-prd-readiness-gap-analysis.md:187','NEEDS_DECISION',null,null,1,'仓库仅确认字典类型，未确认业务值'),
('G-02','DICTIONARY','law_contract_termination_reason','QUOTE_CONTRACT','PHASE_ONE','doc/v0.2-prd-readiness-gap-analysis.md:187','NEEDS_DECISION',null,null,1,'仓库仅确认字典类型，未确认业务值'),

('G-02','DICTIONARY','law_receivable_trigger_type','FINANCE','PHASE_ONE','doc/v0.2-prd-readiness-gap-analysis.md:205','NEEDS_DECISION','Q-009',null,1,'收费节点清单依赖Q-009'),
('G-02','DICTIONARY','law_collection_status','FINANCE','PHASE_ONE','doc/v0.2-prd-readiness-gap-analysis.md:205','NEEDS_DECISION','Q-009',null,1,'催收状态依赖Q-009'),
('G-02','DICTIONARY','law_refund_status','FINANCE','PHASE_ONE','doc/v0.2-prd-readiness-gap-analysis.md:205','NEEDS_DECISION',null,null,1,'仓库以law_refund_status/reason简写，拆为状态类型'),
('G-02','DICTIONARY','law_refund_reason','FINANCE','PHASE_ONE','doc/v0.2-prd-readiness-gap-analysis.md:205','NEEDS_DECISION',null,null,1,'仓库以law_refund_status/reason简写，拆为原因类型'),
('G-02','DICTIONARY','law_risk_fee_calc_method','FINANCE','PHASE_TWO','doc/v0.2-prd-readiness-gap-analysis.md:205','NEEDS_DECISION','Q-012',null,1,'风险代理计算方式依赖Q-012'),

('G-02','DICTIONARY','law_transfer_material_type','CASE_TRANSFER','PHASE_ONE','doc/v0.2-prd-readiness-gap-analysis.md:225','NEEDS_DECISION',null,null,11,'PRD要求11项材料，但仓库未冻结材料编码'),
('G-02','DICTIONARY','law_material_review_status','CASE_TRANSFER','PHASE_ONE','doc/v0.2-prd-readiness-gap-analysis.md:225','NEEDS_DECISION',null,null,1,'仓库仅确认字典类型，未确认业务值'),
('G-02','DICTIONARY','law_case_classification_status','CASE_TRANSFER','PHASE_ONE','doc/v0.2-prd-readiness-gap-analysis.md:225','NEEDS_DECISION','Q-001',null,1,'分类与复核边界依赖Q-001'),

('G-02','DICTIONARY','law_assignment_level','COMPREHENSIVE','PHASE_ONE','doc/v0.2-prd-readiness-gap-analysis.md:247','NEEDS_DECISION','Q-004',null,1,'分派层级依赖Q-004'),
('G-02','DICTIONARY','law_case_reject_reason','COMPREHENSIVE','PHASE_ONE','doc/v0.2-prd-readiness-gap-analysis.md:247','NEEDS_DECISION','Q-005',null,1,'拒接审核边界依赖Q-005'),
('G-02','DICTIONARY','law_document_status','COMPREHENSIVE','PHASE_ONE','doc/v0.2-prd-readiness-gap-analysis.md:247','NEEDS_DECISION',null,null,1,'仓库仅确认字典类型，未确认业务值'),
('G-02','DICTIONARY','law_filing_result','COMPREHENSIVE','PHASE_ONE','doc/v0.2-prd-readiness-gap-analysis.md:247','NEEDS_DECISION',null,null,1,'仓库仅确认字典类型，未确认业务值'),
('G-02','DICTIONARY','law_direct_close_reason','COMPREHENSIVE','PHASE_ONE','doc/v0.2-prd-readiness-gap-analysis.md:247','NEEDS_DECISION',null,null,1,'仓库仅确认字典类型，未确认业务值'),
('G-02','DICTIONARY','law_trial_material_type','COMPREHENSIVE','PHASE_ONE','doc/v0.2-prd-readiness-gap-analysis.md:247','NEEDS_DECISION',null,null,1,'仓库仅确认字典类型，未确认业务值'),

('G-02','DICTIONARY','law_non_litigation_type','NON_LITIGATION','PHASE_TWO','doc/v0.2-prd-readiness-gap-analysis.md:259','NEEDS_DECISION',null,json_array(json_object('label','法律咨询'),json_object('label','文书撰写'),json_object('label','刑事会见'),json_object('label','律师函')),4,'仓库明确四个标签，但未确认稳定dict_value'),
('G-02','DICTIONARY','law_delivery_method','NON_LITIGATION','PHASE_TWO','doc/v0.2-prd-readiness-gap-analysis.md:260','NEEDS_DECISION',null,null,1,'仓库仅确认字典类型，未确认业务值'),
('G-02','DICTIONARY','law_receipt_status','NON_LITIGATION','PHASE_TWO','doc/v0.2-prd-readiness-gap-analysis.md:260','NEEDS_DECISION',null,null,1,'仓库仅确认字典类型，未确认业务值'),
('G-02','DICTIONARY','law_deliverable_type','NON_LITIGATION','PHASE_TWO','doc/v0.2-prd-readiness-gap-analysis.md:260','NEEDS_DECISION',null,null,1,'仓库仅确认字典类型，未确认业务值'),

('G-02','DICTIONARY','law_execution_reject_reason','EXECUTION','PHASE_TWO','doc/v0.2-prd-readiness-gap-analysis.md:272','NEEDS_DECISION','Q-003',null,1,'执行组织和业务值依赖Q-003'),
('G-02','DICTIONARY','law_execution_application_status','EXECUTION','PHASE_TWO','doc/v0.2-prd-readiness-gap-analysis.md:272','NEEDS_DECISION','Q-003',null,1,'执行组织和业务值依赖Q-003'),
('G-02','DICTIONARY','law_execution_service_type','EXECUTION','PHASE_TWO','doc/v0.2-prd-readiness-gap-analysis.md:272','NEEDS_DECISION','Q-003',null,1,'执行组织和业务值依赖Q-003'),
('G-02','DICTIONARY','law_execution_node_type','EXECUTION','PHASE_TWO','doc/v0.2-prd-readiness-gap-analysis.md:272','NEEDS_DECISION','Q-003',null,1,'执行组织和业务值依赖Q-003'),
('G-02','DICTIONARY','law_execution_report_cycle','EXECUTION','PHASE_TWO','doc/v0.2-prd-readiness-gap-analysis.md:267-272','NEEDS_DECISION','Q-003',null,3,'仓库仅明确5/15/30工作日周期，未冻结稳定值');

-- Stable role keys referenced by the repository. Existing role keys are requirements only;
-- this migration never creates or grants a role. Execution naming conflicts remain blocked by Q-003.
insert into todo_foundation_resource_requirement
(gate_code,resource_type,resource_code,domain_code,delivery_phase,source_ref,source_status,decision_ref,expected_values_json,minimum_active_items,remark)
values
('G-02','ROLE','sales','CROSS_DOMAIN','PHASE_ONE','ruoyi-admin/src/main/resources/db/migration/V0_20_10__v02_prd_definition_catalog.sql','CONFIRMED',null,null,1,'TD定义Owner fallback引用'),
('G-02','ROLE','case_manager','CROSS_DOMAIN','PHASE_ONE','ruoyi-admin/src/main/resources/db/migration/V0_20_10__v02_prd_definition_catalog.sql','CONFIRMED',null,null,1,'TD定义Owner fallback引用'),
('G-02','ROLE','finance_manager','CROSS_DOMAIN','PHASE_ONE','ruoyi-admin/src/main/resources/db/migration/V0_20_10__v02_prd_definition_catalog.sql','CONFIRMED',null,null,1,'TD定义Owner fallback引用'),
('G-02','ROLE','law_partner_manager','CROSS_DOMAIN','PHASE_ONE','ruoyi-admin/src/main/resources/db/migration/V0_20_10__v02_prd_definition_catalog.sql','CONFIRMED',null,null,1,'TD定义Owner fallback引用'),
('G-02','ROLE','lawyer','CROSS_DOMAIN','PHASE_ONE','ruoyi-admin/src/main/resources/db/migration/V0_20_10__v02_prd_definition_catalog.sql','CONFIRMED',null,null,1,'TD定义Owner fallback引用'),
('G-02','ROLE','intern_lawyer','CROSS_DOMAIN','PHASE_ONE','ruoyi-admin/src/main/resources/db/migration/V0_20_10__v02_prd_definition_catalog.sql','CONFIRMED',null,null,1,'TD定义Owner fallback引用'),
('G-02','ROLE','enforcement_primary_assistant','EXECUTION','PHASE_TWO','ruoyi-admin/src/main/resources/db/migration/V0_20_10__v02_prd_definition_catalog.sql','CONFLICTING','Q-003',null,1,'TD-019使用enforcement命名，与差距文档execution命名冲突'),
('G-02','ROLE','enforcement_secondary_assistant','EXECUTION','PHASE_TWO','ruoyi-admin/src/main/resources/db/migration/V0_20_10__v02_prd_definition_catalog.sql','CONFLICTING','Q-003',null,1,'TD-020/021使用enforcement命名，与差距文档execution命名冲突'),
('G-02','ROLE','execution_manager','EXECUTION','PHASE_TWO','doc/v0.2-prd-readiness-gap-analysis.md:273','CONFLICTING','Q-003',null,1,'差距文档命名，TD定义未引用'),
('G-02','ROLE','execution_assistant_l1','EXECUTION','PHASE_TWO','doc/v0.2-prd-readiness-gap-analysis.md:273','CONFLICTING','Q-003',null,1,'差距文档命名，与TD定义enforcement命名冲突'),
('G-02','ROLE','execution_assistant_l2','EXECUTION','PHASE_TWO','doc/v0.2-prd-readiness-gap-analysis.md:273','CONFLICTING','Q-003',null,1,'差距文档命名，与TD定义enforcement命名冲突');
