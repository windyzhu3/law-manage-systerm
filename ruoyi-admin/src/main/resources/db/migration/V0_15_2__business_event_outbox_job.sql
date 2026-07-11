insert into sys_job (
    job_id, job_name, job_group, invoke_target, cron_expression,
    misfire_policy, concurrent, status, create_by, create_time, remark
)
select coalesce(max(job_id), 0) + 1, '业务事件Outbox消费', 'LAW',
       'businessEventOutboxTask.processPending', '0/10 * * * * ?',
       '3', '1', '0', 'admin', sysdate(), '每10秒处理待消费业务事件，禁止并发执行'
from sys_job
where not exists (
    select 1 from sys_job where invoke_target='businessEventOutboxTask.processPending'
);
