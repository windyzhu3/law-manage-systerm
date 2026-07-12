insert into sys_dict_type(dict_name,dict_type,status,create_by,create_time,remark)
select '待办状态','law_todo_status','0','admin',sysdate(),'Todo Engine阶段一状态'
where not exists(select 1 from sys_dict_type where dict_type='law_todo_status');

insert into sys_dict_type(dict_name,dict_type,status,create_by,create_time,remark)
select '待办优先级','law_todo_priority','0','admin',sysdate(),'Todo Engine待办优先级'
where not exists(select 1 from sys_dict_type where dict_type='law_todo_priority');

insert into sys_dict_type(dict_name,dict_type,status,create_by,create_time,remark)
select '待办SLA状态','law_todo_sla_status','0','admin',sysdate(),'Todo Engine SLA状态'
where not exists(select 1 from sys_dict_type where dict_type='law_todo_sla_status');

insert into sys_dict_data(dict_sort,dict_label,dict_value,dict_type,css_class,list_class,is_default,status,create_by,create_time,remark)
select x.sort,x.label,x.value,x.type,'',x.list_class,'N','0','admin',sysdate(),x.label from (
select 1 sort,'待领取' label,'CREATED' value,'law_todo_status' type,'info' list_class union all
select 2,'已领取','CLAIMED','law_todo_status','primary' union all
select 3,'办理中','IN_PROGRESS','law_todo_status','primary' union all
select 4,'已提交','SUBMITTED','law_todo_status','warning' union all
select 5,'已退回','RETURNED','law_todo_status','danger' union all
select 6,'已完成','COMPLETED','law_todo_status','success' union all
select 7,'已取消','CANCELLED','law_todo_status','info' union all
select 1,'普通','NORMAL','law_todo_priority','info' union all
select 2,'高','HIGH','law_todo_priority','warning' union all
select 3,'紧急','URGENT','law_todo_priority','danger' union all
select 1,'正常','NORMAL','law_todo_sla_status','success' union all
select 2,'已提醒','REMINDED','law_todo_sla_status','warning' union all
select 3,'已超时','OVERDUE','law_todo_sla_status','danger' union all
select 4,'已升级','ESCALATED','law_todo_sla_status','danger') x
where not exists(select 1 from sys_dict_data d where d.dict_type=x.type and d.dict_value=x.value);
