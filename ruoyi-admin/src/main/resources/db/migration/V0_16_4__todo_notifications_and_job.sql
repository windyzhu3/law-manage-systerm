create table if not exists todo_notification (
  notification_id bigint not null auto_increment, todo_id bigint not null, user_id bigint not null,
  notification_type varchar(32) not null, title varchar(200) not null, content varchar(1000),
  status varchar(16) not null default 'UNREAD', read_time datetime, create_time datetime not null default current_timestamp,
  primary key(notification_id), unique key uk_todo_notification(todo_id,user_id,notification_type),
  key idx_todo_notification_user(user_id,status,create_time)
) engine=innodb comment='待办定向通知';
insert into sys_job(job_id,job_name,job_group,invoke_target,cron_expression,misfire_policy,concurrent,status,create_by,create_time,remark)
select coalesce(max(job_id),0)+1,'待办SLA扫描','LAW','todoSlaTask.scan','0 0/5 * * * ?','3','1','0','admin',sysdate(),'每五分钟扫描待办SLA阈值'
from sys_job where not exists(select 1 from sys_job where invoke_target='todoSlaTask.scan');
