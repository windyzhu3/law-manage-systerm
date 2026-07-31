-- Generalize the existing immutable schedule plan without rewriting historical schedule facts.
alter table todo_schedule_plan
  add column schedule_purpose varchar(32) not null default 'LEAD_RETRY' after business_id,
  add column idempotency_key varchar(192) null after schedule_purpose;

-- Preserve the canonical semantic key for the first historical plan. A legacy suffix makes
-- duplicate semantic rows deterministic and unique; a null historical source uses its plan ID.
create temporary table tmp_todo_schedule_idempotency_backfill(
  plan_id bigint not null,
  idempotency_key varchar(192) not null,
  primary key(plan_id),
  unique key uk_tmp_todo_schedule_idempotency(idempotency_key)
);

insert into tmp_todo_schedule_idempotency_backfill(plan_id,idempotency_key)
select plan_id,
       case when semantic_rank=1 then semantic_key
            else concat(semantic_key,':LEGACY_PLAN_',plan_id) end
from (
  select plan_id,semantic_key,
         row_number() over(partition by semantic_key order by plan_id) semantic_rank
  from (
    select plan_id,
           concat('LEAD_RETRY:',business_id,':',
             case when previous_todo_id is null then concat('LEGACY_PLAN_',plan_id)
                  else cast(previous_todo_id as char) end) semantic_key
    from todo_schedule_plan
  ) semantic_rows
) ranked_rows;

update todo_schedule_plan plan
join tmp_todo_schedule_idempotency_backfill backfill on backfill.plan_id=plan.plan_id
set plan.idempotency_key=backfill.idempotency_key;

-- Fail closed before the permanent constraints are installed. The generated mapping is designed
-- to make this guard pass for empty tables, null source IDs and duplicate historical semantics.
create temporary table tmp_todo_schedule_idempotency_guard(
  valid_backfill tinyint not null,
  constraint chk_tmp_todo_schedule_idempotency_guard check(valid_backfill=1)
);

insert into tmp_todo_schedule_idempotency_guard(valid_backfill)
select case when
  (select count(*) from todo_schedule_plan)
    =(select count(*) from tmp_todo_schedule_idempotency_backfill)
  and (select count(*) from todo_schedule_plan where idempotency_key is null)=0
  and (select count(*) from todo_schedule_plan)
    =(select count(distinct idempotency_key) from todo_schedule_plan)
  then 1 else 0 end;

drop temporary table tmp_todo_schedule_idempotency_guard;
drop temporary table tmp_todo_schedule_idempotency_backfill;

alter table todo_schedule_plan
  modify column idempotency_key varchar(192) not null,
  add unique key uk_todo_schedule_plan_idempotency(idempotency_key),
  add key idx_todo_schedule_plan_purpose(schedule_purpose,business_type,business_id,status);
