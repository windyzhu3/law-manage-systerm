-- Schedule-backed TD-003 and TD-004 routes cannot advance unless due occurrences are materialized.
insert into sys_job(job_id,job_name,job_group,invoke_target,cron_expression,misfire_policy,
  concurrent,status,create_by,create_time,remark)
select coalesce(max(job_id),0)+1,'待办计划物化','LAW','todoScheduleTask.scan',
  '0/10 * * * * ?','3','1','0','flyway-v0.20.80',sysdate(),
  '每十秒物化到期待办计划，任务使用数据库幂等与并发围栏'
from sys_job
where not exists(select 1 from sys_job where invoke_target='todoScheduleTask.scan');

update sys_job
set job_name='待办计划物化',job_group='LAW',cron_expression='0/10 * * * * ?',
    misfire_policy='3',concurrent='1',status='0',update_by='flyway-v0.20.80',
    update_time=sysdate(),remark='每十秒物化到期待办计划，任务使用数据库幂等与并发围栏'
where invoke_target='todoScheduleTask.scan';
