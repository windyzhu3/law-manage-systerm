-- Business-admin configuration resources for the Todo Engine.
alter table todo_event_catalog add column event_name varchar(128) null after event_type;
alter table todo_event_catalog add column description varchar(500) null after event_name;
alter table todo_event_catalog add column source_module varchar(64) null after producer;
alter table todo_event_catalog add column schema_status varchar(16) not null default 'INCOMPLETE' after sample_payload_json;
alter table todo_event_catalog add column version int not null default 0 after schema_status;
alter table todo_event_catalog add constraint chk_todo_event_schema_status check (schema_status in ('INCOMPLETE','READY'));

create table todo_validator_metadata (
  validator_metadata_id bigint not null auto_increment,
  validator_code varchar(128) not null,
  validator_name varchar(128) not null,
  description varchar(500) null,
  business_types_json json not null,
  parameter_schema_json json not null,
  example_parameters_json json not null,
  status varchar(16) not null default 'ACTIVE',
  version int not null default 0,
  create_by varchar(64) not null default 'migration',
  create_time datetime not null default current_timestamp,
  update_by varchar(64) null,
  update_time datetime null,
  primary key (validator_metadata_id),
  unique key uk_todo_validator_metadata_code (validator_code),
  key idx_todo_validator_metadata_status (status),
  constraint chk_todo_validator_metadata_status check (status in ('ACTIVE','DISABLED'))
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

update todo_event_catalog set event_name='归档申请已提交',description='案件归档申请提交后触发',source_module='matter',
 payload_schema_json=JSON_OBJECT('type','object','properties',JSON_OBJECT('archiveId',JSON_OBJECT('type','integer','title','归档申请ID'),'applicantId',JSON_OBJECT('type','integer','title','申请人'),'action',JSON_OBJECT('type','string','title','申请动作')),'required',JSON_ARRAY('archiveId','applicantId')),
 sample_payload_json=JSON_OBJECT('archiveId',1001,'applicantId',1,'action','apply'),schema_status='READY',version=version+1,update_by='migration',update_time=current_timestamp where event_type='ARCHIVE_APPLIED' and payload_version=1;
update todo_event_catalog set event_name='案件已分配',description='案管完成案件分配后触发',source_module='case',
 payload_schema_json=JSON_OBJECT('type','object','properties',JSON_OBJECT('assignmentId',JSON_OBJECT('type','integer','title','分案记录ID'),'lawyerId',JSON_OBJECT('type','integer','title','承办律师'),'lawyerName',JSON_OBJECT('type','string','title','律师姓名'),'ownerId',JSON_OBJECT('type','integer','title','负责人')),'required',JSON_ARRAY('assignmentId','lawyerId')),
 sample_payload_json=JSON_OBJECT('assignmentId',1001,'lawyerId',21,'lawyerName','张律师','ownerId',21),schema_status='READY',version=version+1,update_by='migration',update_time=current_timestamp where event_type='CASE_ASSIGNED' and payload_version=1;
update todo_event_catalog set event_name='案件分类为综法',description='案件完成综法分类后触发',source_module='case',
 payload_schema_json=JSON_OBJECT('type','object','properties',JSON_OBJECT('classification',JSON_OBJECT('type','string','title','案件分类'),'partnerLawyerId',JSON_OBJECT('type','integer','title','合伙人律师'),'caseManagerId',JSON_OBJECT('type','integer','title','案管负责人')),'required',JSON_ARRAY('classification')),
 sample_payload_json=JSON_OBJECT('classification','COMPREHENSIVE','partnerLawyerId',21,'caseManagerId',31),schema_status='READY',version=version+1,update_by='migration',update_time=current_timestamp where event_type='CASE_CLASSIFIED_COMPREHENSIVE' and payload_version=1;
update todo_event_catalog set event_name='案件分类为执行',description='案件完成执行分类后触发',source_module='case',
 payload_schema_json=JSON_OBJECT('type','object','properties',JSON_OBJECT('classification',JSON_OBJECT('type','string','title','案件分类'),'primaryAssistantId',JSON_OBJECT('type','integer','title','一级助理'),'caseManagerId',JSON_OBJECT('type','integer','title','案管负责人')),'required',JSON_ARRAY('classification')),
 sample_payload_json=JSON_OBJECT('classification','ENFORCEMENT','primaryAssistantId',41,'caseManagerId',31),schema_status='READY',version=version+1,update_by='migration',update_time=current_timestamp where event_type='CASE_CLASSIFIED_ENFORCEMENT' and payload_version=1;
update todo_event_catalog set event_name='案件分类为非诉',description='案件完成非诉分类后触发',source_module='case',
 payload_schema_json=JSON_OBJECT('type','object','properties',JSON_OBJECT('classification',JSON_OBJECT('type','string','title','案件分类'),'assigneeId',JSON_OBJECT('type','integer','title','承办人'),'caseManagerId',JSON_OBJECT('type','integer','title','案管负责人')),'required',JSON_ARRAY('classification')),
 sample_payload_json=JSON_OBJECT('classification','NON_LITIGATION','assigneeId',21,'caseManagerId',31),schema_status='READY',version=version+1,update_by='migration',update_time=current_timestamp where event_type='CASE_CLASSIFIED_NON_LITIGATION' and payload_version=1;
update todo_event_catalog set event_name='案件已结案',description='事项完成结案确认后触发',source_module='matter',
 payload_schema_json=JSON_OBJECT('type','object','properties',JSON_OBJECT('action',JSON_OBJECT('type','string','title','结案动作'),'archiveNo',JSON_OBJECT('type','string','title','归档编号'),'assistantId',JSON_OBJECT('type','integer','title','律师助理')),'required',JSON_ARRAY('action')),
 sample_payload_json=JSON_OBJECT('action','pass','archiveNo','JG20260001','assistantId',41),schema_status='READY',version=version+1,update_by='migration',update_time=current_timestamp where event_type='CASE_CLOSED' and payload_version=1;
update todo_event_catalog set event_name='案件已创建',description='合同满足条件并生成案件后触发',source_module='case',
 payload_schema_json=JSON_OBJECT('type','object','properties',JSON_OBJECT('contractId',JSON_OBJECT('type','integer','title','合同ID'),'ownerId',JSON_OBJECT('type','integer','title','负责人'),'caseSource',JSON_OBJECT('type','string','title','案件来源')),'required',JSON_ARRAY('contractId')),
 sample_payload_json=JSON_OBJECT('contractId',1001,'ownerId',21,'caseSource','CONTRACT'),schema_status='READY',version=version+1,update_by='migration',update_time=current_timestamp where event_type='CASE_CREATED' and payload_version=1;
update todo_event_catalog set event_name='案件交接已接收',description='案管接收案件交接后触发',source_module='case',
 payload_schema_json=JSON_OBJECT('type','object','properties',JSON_OBJECT('handoffId',JSON_OBJECT('type','integer','title','交接记录ID'),'acceptedBy',JSON_OBJECT('type','integer','title','接收人'),'classification',JSON_OBJECT('type','string','title','案件分类')),'required',JSON_ARRAY('handoffId','acceptedBy')),
 sample_payload_json=JSON_OBJECT('handoffId',1001,'acceptedBy',31,'classification','COMPREHENSIVE'),schema_status='READY',version=version+1,update_by='migration',update_time=current_timestamp where event_type='CASE_HANDOFF_ACCEPTED' and payload_version=1;
update todo_event_catalog set event_name='案件交接已提交',description='销售提交案件交接后触发',source_module='case',
 payload_schema_json=JSON_OBJECT('type','object','properties',JSON_OBJECT('handoffId',JSON_OBJECT('type','integer','title','交接记录ID'),'submittedBy',JSON_OBJECT('type','integer','title','提交人'),'caseManagerId',JSON_OBJECT('type','integer','title','案管负责人')),'required',JSON_ARRAY('handoffId','submittedBy')),
 sample_payload_json=JSON_OBJECT('handoffId',1001,'submittedBy',11,'caseManagerId',31),schema_status='READY',version=version+1,update_by='migration',update_time=current_timestamp where event_type='CASE_HANDOFF_SUBMITTED' and payload_version=1;
update todo_event_catalog set event_name='律师拒绝接案',description='律师拒绝接案确认后触发',source_module='case',
 payload_schema_json=JSON_OBJECT('type','object','properties',JSON_OBJECT('reasonCode',JSON_OBJECT('type','string','title','拒接原因'),'reason',JSON_OBJECT('type','string','title','拒接说明'),'lawyerId',JSON_OBJECT('type','integer','title','律师')),'required',JSON_ARRAY('reasonCode','lawyerId')),
 sample_payload_json=JSON_OBJECT('reasonCode','CONFLICT','reason','存在利益冲突','lawyerId',21),schema_status='READY',version=version+1,update_by='migration',update_time=current_timestamp where event_type='CASE_REJECTED' and payload_version=1;
update todo_event_catalog set event_name='转案审批通过',description='转案申请审批通过后触发',source_module='case',
 payload_schema_json=JSON_OBJECT('type','object','properties',JSON_OBJECT('transferId',JSON_OBJECT('type','integer','title','转案记录ID'),'targetLawyerId',JSON_OBJECT('type','integer','title','目标律师'),'approvedBy',JSON_OBJECT('type','integer','title','审批人')),'required',JSON_ARRAY('transferId','targetLawyerId')),
 sample_payload_json=JSON_OBJECT('transferId',1001,'targetLawyerId',22,'approvedBy',31),schema_status='READY',version=version+1,update_by='migration',update_time=current_timestamp where event_type='CASE_TRANSFER_APPROVED' and payload_version=1;
update todo_event_catalog set event_name='转案申请已提交',description='律师提交转案申请后触发',source_module='case',
 payload_schema_json=JSON_OBJECT('type','object','properties',JSON_OBJECT('transferId',JSON_OBJECT('type','integer','title','转案记录ID'),'targetLawyerId',JSON_OBJECT('type','integer','title','目标律师'),'reason',JSON_OBJECT('type','string','title','转案原因')),'required',JSON_ARRAY('transferId','targetLawyerId')),
 sample_payload_json=JSON_OBJECT('transferId',1001,'targetLawyerId',22,'reason','专业方向调整'),schema_status='READY',version=version+1,update_by='migration',update_time=current_timestamp where event_type='CASE_TRANSFER_REQUESTED' and payload_version=1;
update todo_event_catalog set event_name='利益冲突审查失败',description='合同前置利益冲突审查未通过时触发',source_module='contract',
 payload_schema_json=JSON_OBJECT('type','object','properties',JSON_OBJECT('subjectId',JSON_OBJECT('type','integer','title','审查主体ID'),'conflictReason',JSON_OBJECT('type','string','title','冲突原因'),'caseManagerId',JSON_OBJECT('type','integer','title','案管负责人')),'required',JSON_ARRAY('subjectId','conflictReason')),
 sample_payload_json=JSON_OBJECT('subjectId',1001,'conflictReason','代理关系冲突','caseManagerId',31),schema_status='READY',version=version+1,update_by='migration',update_time=current_timestamp where event_type='CONFLICT_SCREENING_FAILED' and payload_version=1;
update todo_event_catalog set event_name='合同审批通过',description='合同审批通过后触发',source_module='contract',
 payload_schema_json=JSON_OBJECT('type','object','properties',JSON_OBJECT('approvalId',JSON_OBJECT('type','integer','title','审批记录ID'),'reviewerId',JSON_OBJECT('type','integer','title','审批人'),'ownerId',JSON_OBJECT('type','integer','title','合同负责人')),'required',JSON_ARRAY('approvalId','reviewerId')),
 sample_payload_json=JSON_OBJECT('approvalId',1001,'reviewerId',31,'ownerId',11),schema_status='READY',version=version+1,update_by='migration',update_time=current_timestamp where event_type='CONTRACT_APPROVED' and payload_version=1;
update todo_event_catalog set event_name='合同已签署',description='合同完成签署后触发',source_module='contract',
 payload_schema_json=JSON_OBJECT('type','object','properties',JSON_OBJECT('signStatus',JSON_OBJECT('type','string','title','签署状态'),'signedAt',JSON_OBJECT('type','string','format','date-time','title','签署时间'),'ownerId',JSON_OBJECT('type','integer','title','合同负责人')),'required',JSON_ARRAY('signStatus')),
 sample_payload_json=JSON_OBJECT('signStatus','SIGNED','signedAt','2026-07-22T10:00:00','ownerId',11),schema_status='READY',version=version+1,update_by='migration',update_time=current_timestamp where event_type='CONTRACT_SIGNED' and payload_version=1;
update todo_event_catalog set event_name='合同已提交审批',description='合同提交审批后触发',source_module='contract',
 payload_schema_json=JSON_OBJECT('type','object','properties',JSON_OBJECT('approvalId',JSON_OBJECT('type','integer','title','审批记录ID'),'amount',JSON_OBJECT('type','number','title','合同金额'),'ownerId',JSON_OBJECT('type','integer','title','合同负责人')),'required',JSON_ARRAY('approvalId','ownerId')),
 sample_payload_json=JSON_OBJECT('approvalId',1001,'amount',100000,'ownerId',11),schema_status='READY',version=version+1,update_by='migration',update_time=current_timestamp where event_type='CONTRACT_SUBMITTED' and payload_version=1;
update todo_event_catalog set event_name='客户跟进停滞',description='客户超过跟进周期未更新时触发',source_module='customer',
 payload_schema_json=JSON_OBJECT('type','object','properties',JSON_OBJECT('customerId',JSON_OBJECT('type','integer','title','客户ID'),'ownerId',JSON_OBJECT('type','integer','title','负责人'),'lastFollowAt',JSON_OBJECT('type','string','format','date-time','title','最后跟进时间')),'required',JSON_ARRAY('customerId','ownerId')),
 sample_payload_json=JSON_OBJECT('customerId',1001,'ownerId',11,'lastFollowAt','2026-07-01T09:00:00'),schema_status='READY',version=version+1,update_by='migration',update_time=current_timestamp where event_type='CUSTOMER_PROGRESS_STALE' and payload_version=1;
update todo_event_catalog set event_name='成交已确认',description='客户完成成交确认后触发',source_module='contract',
 payload_schema_json=JSON_OBJECT('type','object','properties',JSON_OBJECT('paymentId',JSON_OBJECT('type','integer','title','付款记录ID'),'salesOwnerId',JSON_OBJECT('type','integer','title','销售负责人'),'dealAmount',JSON_OBJECT('type','number','title','成交金额')),'required',JSON_ARRAY('paymentId','salesOwnerId')),
 sample_payload_json=JSON_OBJECT('paymentId',1001,'salesOwnerId',11,'dealAmount',100000),schema_status='READY',version=version+1,update_by='migration',update_time=current_timestamp where event_type='DEAL_CONFIRMED' and payload_version=1;
update todo_event_catalog set event_name='执行接单已确认',description='执行团队确认接单后触发',source_module='matter',
 payload_schema_json=JSON_OBJECT('type','object','properties',JSON_OBJECT('primaryAssistantId',JSON_OBJECT('type','integer','title','一级助理'),'secondaryAssistantId',JSON_OBJECT('type','integer','title','二级助理'),'acceptanceResult',JSON_OBJECT('type','string','title','接单结果')),'required',JSON_ARRAY('primaryAssistantId','acceptanceResult')),
 sample_payload_json=JSON_OBJECT('primaryAssistantId',41,'secondaryAssistantId',42,'acceptanceResult','ACCEPTED'),schema_status='READY',version=version+1,update_by='migration',update_time=current_timestamp where event_type='ENFORCEMENT_ORDER_ACCEPTED' and payload_version=1;
update todo_event_catalog set event_name='执行服务节点就绪',description='执行案件进入可办理服务节点时触发',source_module='matter',
 payload_schema_json=JSON_OBJECT('type','object','properties',JSON_OBJECT('nodeId',JSON_OBJECT('type','integer','title','节点ID'),'ownerId',JSON_OBJECT('type','integer','title','节点负责人'),'nodeType',JSON_OBJECT('type','string','title','节点类型')),'required',JSON_ARRAY('nodeId','ownerId')),
 sample_payload_json=JSON_OBJECT('nodeId',1001,'ownerId',42,'nodeType','ENFORCEMENT_PROGRESS'),schema_status='READY',version=version+1,update_by='migration',update_time=current_timestamp where event_type='ENFORCEMENT_SERVICE_NODE_READY' and payload_version=1;
update todo_event_catalog set event_name='发票已处理',description='发票申请处理完成后触发',source_module='contract',
 payload_schema_json=JSON_OBJECT('type','object','properties',JSON_OBJECT('invoiceId',JSON_OBJECT('type','integer','title','发票ID'),'invoiceStatus',JSON_OBJECT('type','string','title','发票状态'),'ownerId',JSON_OBJECT('type','integer','title','合同负责人')),'required',JSON_ARRAY('invoiceId','invoiceStatus')),
 sample_payload_json=JSON_OBJECT('invoiceId',1001,'invoiceStatus','ISSUED','ownerId',11),schema_status='READY',version=version+1,update_by='migration',update_time=current_timestamp where event_type='INVOICE_HANDLED' and payload_version=1;
update todo_event_catalog set event_name='线索已分配',description='线索分配给销售后触发',source_module='lead',
 payload_schema_json=JSON_OBJECT('type','object','properties',JSON_OBJECT('assignmentId',JSON_OBJECT('type','integer','title','分配记录ID'),'ownerId',JSON_OBJECT('type','integer','title','线索负责人'),'ownerDeptId',JSON_OBJECT('type','integer','title','负责人部门')),'required',JSON_ARRAY('assignmentId','ownerId')),
 sample_payload_json=JSON_OBJECT('assignmentId',1001,'ownerId',11,'ownerDeptId',103),schema_status='READY',version=version+1,update_by='migration',update_time=current_timestamp where event_type='LEAD_ASSIGNED' and payload_version=1;
update todo_event_catalog set event_name='首联未接通',description='线索首次联系未接通时触发',source_module='lead',
 payload_schema_json=JSON_OBJECT('type','object','properties',JSON_OBJECT('ownerId',JSON_OBJECT('type','integer','title','线索负责人'),'attempts',JSON_OBJECT('type','integer','title','拨打次数'),'nextContactAt',JSON_OBJECT('type','string','format','date-time','title','下次联系时间')),'required',JSON_ARRAY('ownerId','attempts')),
 sample_payload_json=JSON_OBJECT('ownerId',11,'attempts',1,'nextContactAt','2026-07-22T14:00:00'),schema_status='READY',version=version+1,update_by='migration',update_time=current_timestamp where event_type='LEAD_FIRST_CONTACT_UNREACHABLE' and payload_version=1;
update todo_event_catalog set event_name='首联有效',description='线索首次联系有效并转入客户流程时触发',source_module='lead',
 payload_schema_json=JSON_OBJECT('type','object','properties',JSON_OBJECT('customerId',JSON_OBJECT('type','integer','title','客户ID'),'ownerId',JSON_OBJECT('type','integer','title','客户负责人'),'contactResult',JSON_OBJECT('type','string','title','联系结果')),'required',JSON_ARRAY('customerId','ownerId')),
 sample_payload_json=JSON_OBJECT('customerId',1001,'ownerId',11,'contactResult','VALID'),schema_status='READY',version=version+1,update_by='migration',update_time=current_timestamp where event_type='LEAD_FIRST_CONTACT_VALID' and payload_version=1;
update todo_event_catalog set event_name='疑似无效线索已标记',description='线索被标记为疑似无效后触发复核',source_module='lead',
 payload_schema_json=JSON_OBJECT('type','object','properties',JSON_OBJECT('reasonCode',JSON_OBJECT('type','string','title','无效原因'),'ownerId',JSON_OBJECT('type','integer','title','线索负责人'),'reviewerId',JSON_OBJECT('type','integer','title','复核人')),'required',JSON_ARRAY('reasonCode','ownerId')),
 sample_payload_json=JSON_OBJECT('reasonCode','INVALID_PHONE','ownerId',11,'reviewerId',12),schema_status='READY',version=version+1,update_by='migration',update_time=current_timestamp where event_type='LEAD_SUSPECT_INVALID_MARKED' and payload_version=1;
update todo_event_catalog set event_name='案件文档待补充',description='案件办理过程中产生材料补充要求时触发',source_module='matter',
 payload_schema_json=JSON_OBJECT('type','object','properties',JSON_OBJECT('documentType',JSON_OBJECT('type','string','title','文档类型'),'ownerId',JSON_OBJECT('type','integer','title','补充负责人'),'reason',JSON_OBJECT('type','string','title','补充原因')),'required',JSON_ARRAY('documentType','ownerId')),
 sample_payload_json=JSON_OBJECT('documentType','HEARING_MINUTES','ownerId',21,'reason','庭审笔录待上传'),schema_status='READY',version=version+1,update_by='migration',update_time=current_timestamp where event_type='MATTER_DOCUMENT_REQUIRED' and payload_version=1;
update todo_event_catalog set event_name='案件费用已提交',description='案件费用提交审核后触发',source_module='matter',
 payload_schema_json=JSON_OBJECT('type','object','properties',JSON_OBJECT('expenseId',JSON_OBJECT('type','integer','title','费用ID'),'amount',JSON_OBJECT('type','number','title','费用金额'),'ownerId',JSON_OBJECT('type','integer','title','费用负责人')),'required',JSON_ARRAY('expenseId')),
 sample_payload_json=JSON_OBJECT('expenseId',1001,'amount',1200,'ownerId',21),schema_status='READY',version=version+1,update_by='migration',update_time=current_timestamp where event_type='MATTER_EXPENSE_SUBMITTED' and payload_version=1;
update todo_event_catalog set event_name='庭审已完成',description='案件庭审节点完成后触发',source_module='matter',
 payload_schema_json=JSON_OBJECT('type','object','properties',JSON_OBJECT('hearingDate',JSON_OBJECT('type','string','format','date','title','开庭日期'),'secondaryLawyerId',JSON_OBJECT('type','integer','title','二级律师'),'internLawyerId',JSON_OBJECT('type','integer','title','实习律师')),'required',JSON_ARRAY('hearingDate')),
 sample_payload_json=JSON_OBJECT('hearingDate','2026-07-22','secondaryLawyerId',21,'internLawyerId',22),schema_status='READY',version=version+1,update_by='migration',update_time=current_timestamp where event_type='MATTER_HEARING_COMPLETED' and payload_version=1;
update todo_event_catalog set event_name='案件节点已就绪',description='案件当前办理节点可开始处理时触发',source_module='matter',
 payload_schema_json=JSON_OBJECT('type','object','properties',JSON_OBJECT('nodeId',JSON_OBJECT('type','integer','title','节点ID'),'ownerId',JSON_OBJECT('type','integer','title','节点负责人'),'nodeCode',JSON_OBJECT('type','string','title','节点编码')),'required',JSON_ARRAY('nodeId','ownerId')),
 sample_payload_json=JSON_OBJECT('nodeId',1001,'ownerId',21,'nodeCode','HEARING'),schema_status='READY',version=version+1,update_by='migration',update_time=current_timestamp where event_type='MATTER_NODE_READY' and payload_version=1;
update todo_event_catalog set event_name='非诉工作已完成',description='非诉成果完成并进入交付流程时触发',source_module='matter',
 payload_schema_json=JSON_OBJECT('type','object','properties',JSON_OBJECT('assigneeId',JSON_OBJECT('type','integer','title','承办人'),'deliveryResult',JSON_OBJECT('type','string','title','交付结果'),'signedAt',JSON_OBJECT('type','string','format','date-time','title','签收时间')),'required',JSON_ARRAY('assigneeId','deliveryResult')),
 sample_payload_json=JSON_OBJECT('assigneeId',21,'deliveryResult','DELIVERED','signedAt','2026-07-22T16:00:00'),schema_status='READY',version=version+1,update_by='migration',update_time=current_timestamp where event_type='NON_LITIGATION_WORK_COMPLETED' and payload_version=1;
update todo_event_catalog set event_name='回款已确认',description='财务确认回款后触发',source_module='contract',
 payload_schema_json=JSON_OBJECT('type','object','properties',JSON_OBJECT('planId',JSON_OBJECT('type','integer','title','收费计划ID'),'amount',JSON_OBJECT('type','number','title','回款金额'),'ownerId',JSON_OBJECT('type','integer','title','合同负责人')),'required',JSON_ARRAY('planId','amount')),
 sample_payload_json=JSON_OBJECT('planId',1001,'amount',50000,'ownerId',11),schema_status='READY',version=version+1,update_by='migration',update_time=current_timestamp where event_type='PAYMENT_CONFIRMED' and payload_version=1;
update todo_event_catalog set event_name='款项已全部收齐',description='合同应收款全部确认后触发',source_module='contract',
 payload_schema_json=JSON_OBJECT('type','object','properties',JSON_OBJECT('receivedAmount',JSON_OBJECT('type','number','title','已收金额'),'salesOwnerId',JSON_OBJECT('type','integer','title','销售负责人'),'paymentCompletedAt',JSON_OBJECT('type','string','format','date-time','title','收齐时间')),'required',JSON_ARRAY('receivedAmount','salesOwnerId')),
 sample_payload_json=JSON_OBJECT('receivedAmount',100000,'salesOwnerId',11,'paymentCompletedAt','2026-07-22T10:00:00'),schema_status='READY',version=version+1,update_by='migration',update_time=current_timestamp where event_type='PAYMENT_FULLY_RECEIVED' and payload_version=1;
update todo_event_catalog set event_name='报价接受且冲突已清除',description='客户接受报价且通过冲突审查后触发',source_module='contract',
 payload_schema_json=JSON_OBJECT('type','object','properties',JSON_OBJECT('quoteId',JSON_OBJECT('type','integer','title','报价ID'),'caseManagerId',JSON_OBJECT('type','integer','title','案管负责人'),'conflictCleared',JSON_OBJECT('type','boolean','title','冲突已清除')),'required',JSON_ARRAY('quoteId','conflictCleared')),
 sample_payload_json=JSON_OBJECT('quoteId',1001,'caseManagerId',31,'conflictCleared',true),schema_status='READY',version=version+1,update_by='migration',update_time=current_timestamp where event_type='QUOTE_ACCEPTED_CONFLICT_CLEARED' and payload_version=1;
update todo_event_catalog set event_name='报价折扣待审批',description='报价折扣超过阈值时触发审批',source_module='contract',
 payload_schema_json=JSON_OBJECT('type','object','properties',JSON_OBJECT('discountRate',JSON_OBJECT('type','number','title','折扣率'),'approvalOwnerId',JSON_OBJECT('type','integer','title','审批负责人'),'quoteAmount',JSON_OBJECT('type','number','title','报价金额')),'required',JSON_ARRAY('discountRate','approvalOwnerId')),
 sample_payload_json=JSON_OBJECT('discountRate',0.8,'approvalOwnerId',31,'quoteAmount',100000),schema_status='READY',version=version+1,update_by='migration',update_time=current_timestamp where event_type='QUOTE_DISCOUNT_APPROVAL_REQUESTED' and payload_version=1;
update todo_event_catalog set event_name='应收款到期',description='合同收费计划到期时触发',source_module='contract',
 payload_schema_json=JSON_OBJECT('type','object','properties',JSON_OBJECT('amount',JSON_OBJECT('type','number','title','应收金额'),'dueAt',JSON_OBJECT('type','string','format','date-time','title','到期时间'),'ownerId',JSON_OBJECT('type','integer','title','催收负责人')),'required',JSON_ARRAY('amount','dueAt','ownerId')),
 sample_payload_json=JSON_OBJECT('amount',50000,'dueAt','2026-07-31T18:00:00','ownerId',11),schema_status='READY',version=version+1,update_by='migration',update_time=current_timestamp where event_type='RECEIVABLE_DUE' and payload_version=1;
update todo_event_catalog set event_name='风险代理费已确认',description='风险代理费计算或审核确认后触发',source_module='matter',
 payload_schema_json=JSON_OBJECT('type','object','properties',JSON_OBJECT('riskFeeAmount',JSON_OBJECT('type','number','title','风险代理费'),'salesOwnerId',JSON_OBJECT('type','integer','title','销售负责人'),'confirmedBy',JSON_OBJECT('type','integer','title','确认人')),'required',JSON_ARRAY('riskFeeAmount','salesOwnerId')),
 sample_payload_json=JSON_OBJECT('riskFeeAmount',20000,'salesOwnerId',11,'confirmedBy',21),schema_status='READY',version=version+1,update_by='migration',update_time=current_timestamp where event_type='RISK_FEE_CONFIRMED' and payload_version=1;

insert into todo_validator_metadata(validator_code,validator_name,description,business_types_json,parameter_schema_json,example_parameters_json,status,create_by)
select x.validator_code,x.validator_name,x.description,x.business_types_json,JSON_OBJECT('type','object','properties',JSON_OBJECT()),JSON_OBJECT(),'ACTIVE','migration'
from (
  select 'LeadFirstContactValidator' validator_code,'线索首联校验器' validator_name,'校验线索首联业务状态' description,JSON_ARRAY('LEAD') business_types_json union all
  select 'ContractTodoValidator','合同校验器','校验合同、收费和签署状态',JSON_ARRAY('CONTRACT') union all
  select 'CaseTodoValidator','案件校验器','校验分案、接案和转案状态',JSON_ARRAY('CASE') union all
  select 'MatterTodoValidator','事项办理校验器','校验节点、费用和文档办理状态',JSON_ARRAY('MATTER') union all
  select 'ArchiveTodoValidator','结案归档校验器','校验结案和归档前置条件',JSON_ARRAY('MATTER')
) x where not exists(select 1 from todo_validator_metadata existing where existing.validator_code=x.validator_code);

set @todo_engine_directory_id=(select menu_id from sys_menu where menu_name='Todo Engine' and path='todo-engine' and menu_type='M' order by menu_id limit 1);
insert into sys_menu(menu_name,parent_id,order_num,path,component,`query`,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
select '配置资源',@todo_engine_directory_id,7,'todo-config-resource','todo/config/resource/index',null,'TodoConfigResource',1,0,'C','0','0','todo:resource:list','component','admin',sysdate()
where @todo_engine_directory_id is not null and not exists(select 1 from sys_menu where component='todo/config/resource/index');
set @todo_resource_page_id=(select menu_id from sys_menu where component='todo/config/resource/index' order by menu_id limit 1);
insert into sys_menu(menu_name,parent_id,order_num,path,component,`query`,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
select x.menu_name,@todo_resource_page_id,x.order_num,'#','',null,null,1,0,'F','0','0',x.perms,'#','admin',sysdate()
from (
  select '配置资源查询' menu_name,1 order_num,'todo:resource:list' perms union all
  select '配置资源详情',2,'todo:resource:query' union all
  select '配置资源新增',3,'todo:resource:add' union all
  select '配置资源编辑',4,'todo:resource:edit' union all
  select '配置资源启停',5,'todo:resource:status'
) x where @todo_resource_page_id is not null and not exists(select 1 from sys_menu existing where existing.perms=x.perms);
