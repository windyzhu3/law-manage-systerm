set @case_manager_role_id=(select role_id from sys_role where role_key='case_manager' and del_flag='0' limit 1);
set @finance_role_id=(select role_id from sys_role where role_key='finance_manager' and del_flag='0' limit 1);
set @partner_role_id=(select role_id from sys_role where role_key='law_partner_manager' and del_flag='0' limit 1);

alter table todo_instance add column template_code varchar(64) null after template_version_id;
alter table todo_instance add key idx_todo_template_code(template_code,status,todo_id);

insert into todo_template(template_code,template_name,business_type,current_version,status,create_by)
select x.code,x.name,x.business_type,1,'0','admin' from (
 select 'CONTRACT_REVIEW' code,'合同审核' name,'CONTRACT' business_type union all
 select 'CONTRACT_SIGN','合同签署','CONTRACT' union all
 select 'PAYMENT_CONFIRM','收款确认','CONTRACT' union all
 select 'INVOICE_HANDLE','开票办理','CONTRACT' union all
 select 'CASE_CREATE_CHECK','案件生成检查','CONTRACT' union all
 select 'CASE_ASSIGN','案件分配','CASE' union all
 select 'CASE_ACCEPT','律师接案确认','CASE' union all
 select 'CASE_REASSIGN','案件重新分配','CASE' union all
 select 'CASE_TRANSFER_REVIEW','转案审批','CASE' union all
 select 'MATTER_NODE_HANDLE','案件节点办理','MATTER' union all
 select 'MATTER_EXPENSE_REVIEW','案件费用审核','MATTER' union all
 select 'MATTER_DOCUMENT_SUPPLY','案件材料补充','MATTER' union all
 select 'CASE_CLOSE_CONFIRM','结案确认','MATTER' union all
 select 'CASE_ARCHIVE_CONFIRM','归档确认','MATTER'
) x where not exists(select 1 from todo_template t where t.template_code=x.code);

insert into todo_template_version(template_id,version_no,status,owner_rule_json,dod_rule_json,sla_rule_json,next_rule_json,ui_schema_json,published_by,published_time)
select t.template_id,1,'PUBLISHED',
 case
  when t.template_code in ('CONTRACT_REVIEW','CASE_TRANSFER_REVIEW') then json_quote(concat('ROLE:',@partner_role_id))
  when t.template_code in ('PAYMENT_CONFIRM','INVOICE_HANDLE','MATTER_EXPENSE_REVIEW') then json_quote(concat('ROLE:',@finance_role_id))
  when t.template_code in ('CASE_CREATE_CHECK','CASE_ASSIGN','CASE_REASSIGN','CASE_CLOSE_CONFIRM','CASE_ARCHIVE_CONFIRM') then json_quote(concat('ROLE:',@case_manager_role_id))
  when t.template_code='CASE_ACCEPT' then json_quote('PAYLOAD:lawyerId')
  else json_quote('PAYLOAD:ownerId') end,
 case
  when t.template_code='CONTRACT_REVIEW' then json_object('requiredFields',json_array('auditResult'),'requiredAttachments',json_array())
  when t.template_code='CONTRACT_SIGN' then json_object('requiredFields',json_array('signDate'),'requiredAttachments',json_array('SIGNED_CONTRACT'))
  when t.template_code='PAYMENT_CONFIRM' then json_object('requiredFields',json_array('paymentResult'),'requiredAttachments',json_array('PAYMENT_PROOF'))
  when t.template_code='INVOICE_HANDLE' then json_object('requiredFields',json_array('invoiceResult'),'requiredAttachments',json_array())
  when t.template_code='CASE_CREATE_CHECK' then json_object('requiredFields',json_array('createResult'),'requiredAttachments',json_array())
  when t.template_code='CASE_ASSIGN' then json_object('requiredFields',json_array('lawyerId'),'requiredAttachments',json_array())
  when t.template_code='CASE_ACCEPT' then json_object('requiredFields',json_array('confirmResult'),'requiredAttachments',json_array())
  when t.template_code='CASE_REASSIGN' then json_object('requiredFields',json_array('lawyerId'),'requiredAttachments',json_array())
  when t.template_code='CASE_TRANSFER_REVIEW' then json_object('requiredFields',json_array('approvalResult'),'requiredAttachments',json_array())
  when t.template_code='MATTER_NODE_HANDLE' then json_object('requiredFields',json_array('nodeResult'),'requiredAttachments',json_array())
  when t.template_code='MATTER_EXPENSE_REVIEW' then json_object('requiredFields',json_array('expenseResult'),'requiredAttachments',json_array())
  when t.template_code='MATTER_DOCUMENT_SUPPLY' then json_object('requiredFields',json_array('documentResult'),'requiredAttachments',json_array('CASE_DOCUMENT'))
  when t.template_code='CASE_CLOSE_CONFIRM' then json_object('requiredFields',json_array('closeResult'),'requiredAttachments',json_array())
  else json_object('requiredFields',json_array('archiveResult'),'requiredAttachments',json_array('ARCHIVE_PACKAGE')) end,
 json_object('calendarCode','DEFAULT','minutes',case when t.template_code in ('CONTRACT_REVIEW','CASE_ACCEPT') then 240 when t.template_code in ('CASE_ASSIGN','PAYMENT_CONFIRM') then 480 else 960 end),
 null,json_object('formCode',t.template_code,'businessType',t.business_type),'admin',sysdate()
from todo_template t
where t.template_code in ('CONTRACT_REVIEW','CONTRACT_SIGN','PAYMENT_CONFIRM','INVOICE_HANDLE','CASE_CREATE_CHECK','CASE_ASSIGN','CASE_ACCEPT','CASE_REASSIGN','CASE_TRANSFER_REVIEW','MATTER_NODE_HANDLE','MATTER_EXPENSE_REVIEW','MATTER_DOCUMENT_SUPPLY','CASE_CLOSE_CONFIRM','CASE_ARCHIVE_CONFIRM')
and not exists(select 1 from todo_template_version v where v.template_id=t.template_id and v.version_no=1);

insert into todo_trigger_rule(event_type,template_id,template_version_id,business_type,enabled,condition_json)
select x.event_type,t.template_id,v.version_id,x.business_type,'Y',x.condition_json from (
 select 'CONTRACT_SUBMITTED' event_type,'CONTRACT_REVIEW' code,'CONTRACT' business_type,null condition_json union all
 select 'CONTRACT_APPROVED','CONTRACT_SIGN','CONTRACT',null union all
 select 'CONTRACT_SIGNED','PAYMENT_CONFIRM','CONTRACT',null union all
 select 'PAYMENT_CONFIRMED','INVOICE_HANDLE','CONTRACT',null union all
 select 'INVOICE_HANDLED','CASE_CREATE_CHECK','CONTRACT',null union all
 select 'CASE_CREATED','CASE_ASSIGN','CASE',null union all
 select 'CASE_ASSIGNED','CASE_ACCEPT','CASE',null union all
 select 'CASE_REJECTED','CASE_REASSIGN','CASE',null union all
 select 'CASE_TRANSFER_REQUESTED','CASE_TRANSFER_REVIEW','CASE',null union all
 select 'CASE_TRANSFER_APPROVED','CASE_ACCEPT','CASE',json_object('requiresAcceptance',true) union all
 select 'MATTER_NODE_READY','MATTER_NODE_HANDLE','MATTER',null union all
 select 'MATTER_EXPENSE_SUBMITTED','MATTER_EXPENSE_REVIEW','MATTER',null union all
 select 'MATTER_DOCUMENT_REQUIRED','MATTER_DOCUMENT_SUPPLY','MATTER',null union all
 select 'ARCHIVE_APPLIED','CASE_CLOSE_CONFIRM','MATTER',null union all
 select 'CASE_CLOSED','CASE_ARCHIVE_CONFIRM','MATTER',null
) x join todo_template t on t.template_code=x.code join todo_template_version v on v.template_id=t.template_id and v.version_no=1
where not exists(select 1 from todo_trigger_rule r where r.event_type=x.event_type and r.template_version_id=v.version_id and r.business_type=x.business_type);

set @todo_menu=(select menu_id from sys_menu where perms='todo:list' order by menu_id limit 1);
insert into sys_menu(menu_name,parent_id,order_num,path,component,`query`,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
select x.name,@todo_menu,x.ord,'#','',null,null,1,0,'F','0','0',x.perm,'#','admin',sysdate() from (
 select '待办链路查询' name,30 ord,'todo:chain:query' perm union all
 select '运营中心查看',31,'todo:operations:list' union all
 select '强制完成',32,'todo:force:complete' union all
 select '强制取消',33,'todo:force:cancel' union all
 select '批量转派',34,'todo:batch:transfer' union all
 select 'SLA豁免',35,'todo:sla:waive' union all
 select '重新生成',36,'todo:regenerate' union all
 select '模板定义查看',37,'todo:definition:list' union all
 select '模板定义编辑',38,'todo:definition:edit' union all
 select '模板定义发布',39,'todo:definition:publish'
) x where not exists(select 1 from sys_menu m where m.perms=x.perm);
