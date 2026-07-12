insert into todo_template(template_code,template_name,business_type,current_version,status,create_by)
select 'LEAD_FIRST_CONTACT','线索首联','LEAD',1,'0','admin'
where not exists(select 1 from todo_template where template_code='LEAD_FIRST_CONTACT');
set @lead_first_template=(select template_id from todo_template where template_code='LEAD_FIRST_CONTACT' limit 1);
insert into todo_template_version(template_id,version_no,owner_rule_json,dod_rule_json,sla_rule_json,next_rule_json,published_by,published_time)
select @lead_first_template,1,json_quote('PAYLOAD:toOwnerId'),json_object('requiredFields',json_array('contactResult'),'requiredAttachments',json_array('CONTACT_PROOF')),json_object('calendarCode','DEFAULT','minutes',60),null,'admin',sysdate()
where not exists(select 1 from todo_template_version where template_id=@lead_first_template and version_no=1);
set @lead_first_version=(select version_id from todo_template_version where template_id=@lead_first_template and version_no=1 limit 1);
insert into todo_trigger_rule(event_type,template_id,template_version_id,business_type,enabled)
select 'LEAD_ASSIGNED',@lead_first_template,@lead_first_version,'LEAD','Y'
where not exists(select 1 from todo_trigger_rule where event_type='LEAD_ASSIGNED' and template_version_id=@lead_first_version and business_type='LEAD');
insert into todo_work_calendar(calendar_code,calendar_name,timezone,work_days,work_start,work_end,exception_json,status)
select 'DEFAULT','默认工作日历','Asia/Shanghai','1,2,3,4,5','09:00:00','18:00:00',json_object(),'0'
where not exists(select 1 from todo_work_calendar where calendar_code='DEFAULT');
